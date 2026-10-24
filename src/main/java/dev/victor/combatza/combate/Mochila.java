package dev.victor.combatza.combate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.healing.PokemonHealedEvent;
import com.cobblemon.mod.common.item.battle.XStatItem;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.SemDerrubar;
import dev.victor.combatza.config.Configuracao;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Feedback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// 8.8 (decisões dele, 19/10): a mochila no meio da nossa batalha.
// - REMÉDIOS (as poções, o Restaurador, os reanimadores e os curativos de status): curam na hora, sem custo, mas depois VOCÊ
//   fica uns segundos sem poder usar outro ("é assim que é no Pokémon Z-A"; eram 7 s; em 20/10 ele pediu "uns 5 s,
//   configurável": Configuracao.segundosEntreItens). Só na batalha: fora
//   dela, à vontade. Bloqueado, o item não é gasto. As poções e os reanimadores passam pelo aviso de cura do Cobblemon
//   (POKEMON_HEALED, que dá para cancelar); os curativos de status não avisam nada, e vêm do CurativoNaBatalhaMixin;
// - os X (Ataque X, Defesa X, Ataque Especial X, Defesa Especial X, Velocidade X, Precisão X): no Cobblemon, só valem na
//   batalha por turnos; na nossa, sobem 2 estágios do seu lutador até a batalha acabar (o +2 do próprio Cobblemon), e também
//   esperam;
// - 9.1b: dar ou tirar o item segurado de um Pokémon seu também conta (SemTrocarItemNaBatalhaMixin);
// - os curativos de status já soltam o Pokémon: os nossos controles (sono, gelo, paralisia, queimadura, veneno) leem o status
//   do Cobblemon a cada tick (SonoECongelado.atualizar, Paralisia.atualizar, EfeitosDeStatus).
public class Mochila {
  private static final int ESTAGIOS_DO_X = 2;
  private static final String FRASE = "cobblemon_combat_za.mochila.";

  // Até que tick cada jogador espera para usar outro item da mochila na batalha
  private static final Map<UUID, Long> ESPERA_ATE = new HashMap<>();

  private Mochila() {
  }

  public static void registrar() {
    CobblemonEvents.POKEMON_HEALED.subscribe(Priority.NORMAL, SemDerrubar.evento("Mochila.POKEMON_HEALED", Mochila::aoCurar));
    UseItemCallback.EVENT.register(Mochila::aoUsarItem);
    ServerPlayConnectionEvents.DISCONNECT.register(SemDerrubar.aoSair("Mochila.DISCONNECT", (conexao, servidor) -> ESPERA_ATE.remove(conexao.getPlayer().getUUID())));
  }

  // Uma poção ou um reanimador (a fonte da cura é um item) num Pokémon seu
  private static void aoCurar(PokemonHealedEvent evento) {
    ServerPlayer dono = evento.getPokemon().getOwnerPlayer();
    if (!(evento.getSource() instanceof Item) || dono == null) {
      return;
    }
    if (podeUsar(dono)) {
      usou(dono);
    } else {
      evento.cancel();
    }
  }

  // Fora da batalha, sempre. Na batalha, só depois da espera (e avisa quanto falta)
  public static boolean podeUsar(ServerPlayer jogador) {
    if (!GerenciarCombates.estaEmCombate(jogador)) {
      return true;
    }
    long falta = ESPERA_ATE.getOrDefault(jogador.getUUID(), 0L) - jogador.server.getTickCount();
    if (falta <= 0) {
      return true;
    }
    Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "espere",
        (falta + SharedConstants.TICKS_PER_SECOND - 1) / SharedConstants.TICKS_PER_SECOND));
    return false;
  }

  // Usou um item da mochila: na batalha, começa a espera
  public static void usou(ServerPlayer jogador) {
    if (!GerenciarCombates.estaEmCombate(jogador)) {
      return;
    }
    int segundos = Configuracao.segundosEntreItens();
    if (segundos <= 0) {
      return;
    }
    ESPERA_ATE.put(jogador.getUUID(), jogador.server.getTickCount() + (long) segundos * SharedConstants.TICKS_PER_SECOND);
    Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "proximo", segundos));
  }

  // Os X: na nossa batalha, no seu lutador. Fora dela, o Cobblemon faz o de sempre (só vale na batalha por turnos)
  private static InteractionResultHolder<ItemStack> aoUsarItem(Player quem, Level mundo, InteractionHand mao) {
    ItemStack item = quem.getItemInHand(mao);
    if (mundo.isClientSide() || !(quem instanceof ServerPlayer jogador) || !(item.getItem() instanceof XStatItem x)
        || !GerenciarCombates.estaEmCombate(jogador)) {
      return InteractionResultHolder.pass(item);
    }
    Pokemon lutador = Lutadores.de(jogador);
    if (lutador == null) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "sem_lutador"));
      return InteractionResultHolder.fail(item);
    }
    if (!podeUsar(jogador)) {
      return InteractionResultHolder.fail(item);
    }
    String atributo = x.getStat().getShowdownId();
    if (Estagios.mudar(lutador, atributo, ESTAGIOS_DO_X, Long.MAX_VALUE) == 0) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "x_no_maximo", lutador.getDisplayName(false)));
      return InteractionResultHolder.fail(item);
    }
    Feedback.historico(jogador, lutador,
        Component.translatable(FRASE + "x", item.getHoverName(), lutador.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("MOCHILA: {} usou {} em {} ({} agora {})", jogador.getName().getString(),
        item.getHoverName().getString(), lutador.getSpecies().getName(), atributo, Estagios.de(lutador, atributo));
    item.consume(1, jogador);
    usou(jogador);
    return InteractionResultHolder.success(item);
  }

  public static void limpar() {
    ESPERA_ATE.clear();
  }
}
