package dev.victor.combatza.combate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// 01/11 (dele: "esses itens eram para ter continuado comigo depois que a raid acabou?"; escolheu "botão direito na raid"): os
// APOIOS do Cobblemon Raid Dens (Ataque, Defesa e Cura, prêmios das raids de nível 3 a 7) eram itens da mochila da batalha
// por turnos da raid. Na raid em tempo real (Raids), você usa com o botão direito na luta contra o chefão, com os números do
// mod de raid (data/cobblemon/bag_items/cheer_*.js):
// - Ataque: Ataque e Ataque Especial ×1,5 para os Pokémon do seu time até a raid acabar; Defesa: Defesa e Defesa Especial ×1,5;
// - Cura: metade da vida e o status de cada Pokémon do seu time (os desmaiados não);
// cada um gasta um item e uma das vezes que a raid deixa você torcer (o limite dele, por jogador e por raid: PonteDaRaid)
public final class ApoiosDaRaid {
  private static final String MOD = "cobblemonraiddens";
  private static final double UM_E_MEIO = 1.5;
  private static final int PARTICULAS = 8;

  private enum Tipo {
    ATAQUE, DEFESA, CURA
  }

  private static final Map<String, Tipo> ITENS = Map.of("cheer_attack", Tipo.ATAQUE, "cheer_defense", Tipo.DEFESA,
      "cheer_heal", Tipo.CURA);

  // O Pokémon animado e os apoios que valem nele; e o chefão da raid em que ele ganhou (acaba quando ela acaba)
  private static final Map<UUID, Set<Tipo>> ANIMADOS = new HashMap<>();
  private static final Map<UUID, UUID> DA_RAID = new HashMap<>();

  private ApoiosDaRaid() {
  }

  public static void registrar() {
    UseItemCallback.EVENT.register(ApoiosDaRaid::aoUsarItem);
  }

  private static InteractionResultHolder<ItemStack> aoUsarItem(Player quem, Level mundo, InteractionHand mao) {
    ItemStack item = quem.getItemInHand(mao);
    Tipo tipo = tipo(item);
    if (tipo == null || mundo.isClientSide() || !(quem instanceof ServerPlayer jogador)) {
      return InteractionResultHolder.pass(item);
    }
    try {
      return usar(jogador, item, tipo) ? InteractionResultHolder.success(item) : InteractionResultHolder.fail(item);
    } catch (RuntimeException erro) {
      CobblemonCombatZA.LOGGER.error("ERRO EM ApoiosDaRaid: este apoio não foi usado", erro);
      return InteractionResultHolder.fail(item);
    }
  }

  private static Tipo tipo(ItemStack item) {
    var id = BuiltInRegistries.ITEM.getKey(item.getItem());
    return MOD.equals(id.getNamespace()) ? ITENS.get(id.getPath()) : null;
  }

  // true = usou (gastou o item e uma vez da raid)
  private static boolean usar(ServerPlayer jogador, ItemStack item, Tipo tipo) {
    PokemonEntity chefe = chefeDaLuta(jogador);
    if (chefe == null) {
      Feedback.barraDeAcao(jogador, Component.translatable("cobblemon_combat_za.raid.apoio_so_na_raid"));
      return false;
    }
    if (!Raids.gastarUmApoio(jogador, chefe)) {
      Feedback.barraDeAcao(jogador, Component.translatable("battle.cobblemonraiddens.cheer.cannot"));
      return false;
    }
    item.shrink(1);
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (pokemon.isFainted()) {
        continue;
      }
      if (tipo == Tipo.CURA) {
        curar(pokemon);
      } else {
        ANIMADOS.computeIfAbsent(pokemon.getUuid(), uuid -> new HashSet<>()).add(tipo);
        DA_RAID.put(pokemon.getUuid(), chefe.getUUID());
      }
      if (pokemon.getEntity() != null) {
        Feedback.particulas(pokemon.getEntity(), ParticleTypes.HAPPY_VILLAGER, PARTICULAS);
      }
    }
    Component frase = Component.translatable("battle.cobblemonraiddens.cheer." + nomeDoItem(tipo), jogador.getName());
    for (ServerPlayer quemLuta : GerenciarCombates.jogadoresNoCombateDe(chefe)) {
      Feedback.historicoDaBatalha(quemLuta, null, frase);
    }
    CobblemonCombatZA.LOGGER.info("RAID: {} usou o apoio {}", jogador.getGameProfile().getName(), tipo);
    return true;
  }

  private static String nomeDoItem(Tipo tipo) {
    return switch (tipo) {
      case ATAQUE -> "cheer_attack";
      case DEFESA -> "cheer_defense";
      case CURA -> "cheer_heal";
    };
  }

  private static void curar(Pokemon pokemon) {
    if (pokemon.getStatus() != null) {
      pokemon.setStatus(null);
    }
    double metade = pokemon.getMaxHealth() * 0.5;
    if (pokemon.getEntity() != null) {
      Vida.curar(pokemon.getEntity(), metade);
    } else {
      pokemon.setCurrentHealth((int) Math.min(pokemon.getMaxHealth(), pokemon.getCurrentHealth() + Math.floor(metade)));
    }
  }

  // O chefão da raid na batalha do jogador, se ele pode lutar com ele; null = não está numa raid
  private static PokemonEntity chefeDaLuta(ServerPlayer jogador) {
    Combate combate = GerenciarCombates.combateDoJogador(jogador);
    if (combate == null) {
      return null;
    }
    for (PokemonEntity selvagem : combate.getSelvagens()) {
      if (Raids.ehChefe(selvagem) && Raids.podeLutar(jogador, selvagem)) {
        return selvagem;
      }
    }
    return null;
  }

  // HabilidadesDaConta: o Apoio de Ataque (Ataque e Ataque Especial) e o de Defesa (Defesa e Defesa Especial)
  public static double ataque(Pokemon pokemon) {
    Set<Tipo> apoios = ANIMADOS.isEmpty() ? null : ANIMADOS.get(pokemon.getUuid());
    return apoios != null && apoios.contains(Tipo.ATAQUE) ? UM_E_MEIO : 1.0;
  }

  public static double defesa(Pokemon pokemon) {
    Set<Tipo> apoios = ANIMADOS.isEmpty() ? null : ANIMADOS.get(pokemon.getUuid());
    return apoios != null && apoios.contains(Tipo.DEFESA) ? UM_E_MEIO : 1.0;
  }

  // Raids, a cada segundo: o apoio acaba quando o chefão da raid sai das batalhas
  static void manterSo(Set<UUID> chefesNaLuta) {
    DA_RAID.values().removeIf(chefe -> !chefesNaLuta.contains(chefe));
    ANIMADOS.keySet().retainAll(DA_RAID.keySet());
  }

  public static void limpar() {
    ANIMADOS.clear();
    DA_RAID.clear();
  }
}
