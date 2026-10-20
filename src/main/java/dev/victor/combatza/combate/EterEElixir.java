package dev.victor.combatza.combate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Recargas;
import dev.victor.combatza.rede.PassivasPayload.Passiva;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// P1-7 (dele, 20/10: "ou a gente transforma em outro item... de dar mais velocidade na recarga"; escolheu "comum acelera,
// Máximo zera"): na NOSSA batalha não existe PP (saiu na 3.3b), então o Éter e o Elixir mexem na recarga:
// - Éter: o golpe escolhido recarrega 2× mais rápido por 17,5 s; Elixir: os 4 golpes. Com o ícone de passiva contando;
// - Éter Máximo: o golpe escolhido fica pronto na hora; Elixir Máximo: os 4.
// Contam como item da mochila (a espera). Fora da nossa batalha (contra um treinador, na batalha do Cobblemon), eles devolvem o
// PP como sempre. Os Mixins EterNaBatalhaMixin e ElixirNaBatalhaMixin chamam aqui
public class EterEElixir {
  // O que aconteceu: não é a nossa batalha (o Cobblemon faz o de sempre), está na espera da mochila, ou usou
  public enum Uso { NAO_E_DA_BATALHA, NA_ESPERA, USOU }

  private record Aceleracao(String nome, long fim, long duracao) {
  }

  private static final int TURNOS = 5;
  private static final String FRASE = "cobblemon_combat_za.mochila.";
  // As acelerações que estão valendo, por Pokémon (para os ícones das passivas)
  private static final Map<UUID, List<Aceleracao>> ACELERADOS = new HashMap<>();

  private EterEElixir() {
  }

  // Na nossa batalha, qualquer Pokémon do jogador que não desmaiou pode receber (a recarga existe mesmo com PP cheio)
  public static boolean podeEscolher(Pokemon pokemon) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    return dono != null && GerenciarCombates.estaEmCombate(dono) && !pokemon.isFainted();
  }

  public static Uso eter(ServerPlayer jogador, ItemStack item, Pokemon pokemon, Move golpe, boolean maximo) {
    if (!GerenciarCombates.estaEmCombate(jogador)) {
      return Uso.NAO_E_DA_BATALHA;
    }
    if (!Mochila.podeUsar(jogador)) {
      return Uso.NA_ESPERA;
    }
    int slot = slotDe(pokemon, golpe);
    long agora = jogador.server.getTickCount();
    if (slot >= 0) {
      if (maximo) {
        Recargas.zerar(pokemon, slot, agora);
      } else {
        Recargas.acelerar(pokemon, slot, agora + duracao(), agora);
        anotar(pokemon, "eter", agora);
      }
    }
    Feedback.historicoDoLutador(jogador, pokemon, Component.translatable(FRASE + (maximo ? "eter_maximo" : "eter"),
        pokemon.getDisplayName(false), golpe.getDisplayName()));
    CobblemonCombatZA.LOGGER.info("MOCHILA: {} usou {} em {} ({})", jogador.getName().getString(),
        item.getHoverName().getString(), pokemon.getSpecies().getName(), golpe.getName());
    gastar(jogador, item, pokemon);
    return Uso.USOU;
  }

  public static Uso elixir(ServerPlayer jogador, ItemStack item, Pokemon pokemon, boolean maximo) {
    if (!GerenciarCombates.estaEmCombate(jogador)) {
      return Uso.NAO_E_DA_BATALHA;
    }
    if (!Mochila.podeUsar(jogador)) {
      return Uso.NA_ESPERA;
    }
    long agora = jogador.server.getTickCount();
    for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
      if (maximo) {
        Recargas.zerar(pokemon, slot, agora);
      } else {
        Recargas.acelerar(pokemon, slot, agora + duracao(), agora);
      }
    }
    if (!maximo) {
      anotar(pokemon, "elixir", agora);
    }
    Feedback.historicoDoLutador(jogador, pokemon, Component.translatable(FRASE + (maximo ? "elixir_maximo" : "elixir"),
        pokemon.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("MOCHILA: {} usou {} em {}", jogador.getName().getString(),
        item.getHoverName().getString(), pokemon.getSpecies().getName());
    gastar(jogador, item, pokemon);
    return Uso.USOU;
  }

  private static long duracao() {
    return TURNOS * EfeitosDeStatus.ticksPorTurno();
  }

  private static int slotDe(Pokemon pokemon, Move golpe) {
    for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
      Move doSlot = pokemon.getMoveSet().get(slot);
      if (doSlot != null && doSlot.getName().equals(golpe.getName())) {
        return slot;
      }
    }
    return -1;
  }

  private static void anotar(Pokemon pokemon, String nome, long agora) {
    List<Aceleracao> lista = ACELERADOS.computeIfAbsent(pokemon.getUuid(), uuid -> new ArrayList<>());
    lista.removeIf(aceleracao -> aceleracao.nome().equals(nome) || aceleracao.fim() <= agora);
    lista.add(new Aceleracao(nome, agora + duracao(), duracao()));
  }

  // Como o Cobblemon faz no Éter: o som do remédio, um a menos na mão e a garrafa vazia de volta (no criativo, nada some);
  // e começa a espera da mochila
  private static void gastar(ServerPlayer jogador, ItemStack item, Pokemon pokemon) {
    if (pokemon.getEntity() != null) {
      pokemon.getEntity().playSound(CobblemonSounds.MEDICINE_LIQUID_USE, 1f, 1f);
    }
    if (!jogador.isCreative()) {
      item.shrink(1);
      ItemStack garrafa = new ItemStack(Items.GLASS_BOTTLE);
      if (!jogador.getInventory().add(garrafa)) {
        jogador.drop(garrafa, false);
      }
    }
    Mochila.usou(jogador);
  }

  // Os ícones das passivas (campo.Passivas): as acelerações dos Pokémon do time dele que ainda valem
  public static List<Passiva> passivasDe(ServerPlayer jogador, long agora) {
    List<Passiva> passivas = new ArrayList<>();
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      List<Aceleracao> lista = ACELERADOS.get(pokemon.getUuid());
      if (lista == null) {
        continue;
      }
      lista.removeIf(aceleracao -> aceleracao.fim() <= agora);
      for (Aceleracao aceleracao : lista) {
        passivas.add(new Passiva(aceleracao.nome(), Passiva.SEU_LADO, segundos(aceleracao.fim() - agora),
            segundos(aceleracao.duracao())));
      }
    }
    return passivas;
  }

  private static int segundos(long ticks) {
    return (int) Math.max(0, Math.ceil(ticks / (double) SharedConstants.TICKS_PER_SECOND));
  }

  public static void limpar() {
    ACELERADOS.clear();
  }
}
