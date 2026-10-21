package dev.victor.combatza.itens;

import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

// Lote 3 dos itens segurados: os que mexem na CARGA do golpe (Execucao). No tempo real, "agir antes" (a prioridade dos jogos)
// vira "o golpe sai sem a fase de carregar":
// - Garra Rápida: 20% das vezes, o golpe sai sem carregar;
// - Baga Custap (guardada com a vida baixa: Bagas): o próximo golpe sai sem carregar, uma vez;
// - Cauda Pesada (nos jogos, quem segura age por último): a carga demora 50% mais;
// - Erva do Poder: o golpe que carrega a mais (o Raio Solar fora do Sol) sai na hora, uma vez (a erva some).
// O Dado Viciado mora nas Cargas (os golpes de 2 a 5 acertos dão 4 ou 5) e o Metrônomo conta os golpes seguidos aqui
public class ItensDeCarga {
  private static final double CHANCE_DA_GARRA = 0.2;
  private static final double CAUDA_PESADA = 1.5;

  private ItensDeCarga() {
  }

  // A carga deste golpe pula? A Custap guardada (uma vez) ou a Garra Rápida (20%)
  public static boolean pulaACarga(Pokemon pokemon) {
    if (EstadoDosItens.usarCustap(pokemon)) {
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.activate.custapberry",
          pokemon.getDisplayName(false)));
      CobblemonCombatZA.LOGGER.info("ITEM: {} agiu na hora (Baga Custap)", pokemon.getSpecies().getName());
      return true;
    }
    RandomSource sorte = pokemon.getEntity() != null ? pokemon.getEntity().getRandom() : RandomSource.create();
    if (ItensSegurados.tem(pokemon, "quickclaw") && sorte.nextDouble() < CHANCE_DA_GARRA) {
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.activate.quickclaw",
          pokemon.getDisplayName(false)));
      CobblemonCombatZA.LOGGER.info("ITEM: {} agiu na hora (Garra Rápida)", pokemon.getSpecies().getName());
      return true;
    }
    return false;
  }

  // O ritmo da carga: com a Cauda Pesada, 50% mais lento
  public static double ritmo(Pokemon pokemon) {
    return ItensSegurados.tem(pokemon, "laggingtail") ? CAUDA_PESADA : 1.0;
  }

  // A Erva do Poder: a carga a mais some (uma vez). Devolve a carga a mais que fica
  public static long ervaDoPoder(Pokemon pokemon, long cargaAMais) {
    if (cargaAMais <= 0 || !ItensSegurados.disponivel(pokemon, "powerherb") || !GerenciarCombates.estaEmCombate(pokemon)) {
      return cargaAMais;
    }
    ItensSegurados.gastar(pokemon, "powerherb");
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.powerherb",
        pokemon.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("ITEM: {} carregou na hora (Erva do Poder)", pokemon.getSpecies().getName());
    return 0;
  }

  // O Dado Viciado: nos golpes de 2 a 5 acertos, 4 ou 5 (null = sem o dado, o sorteio de sempre)
  public static Integer dadoViciado(Pokemon pokemon, RandomSource sorte) {
    return ItensSegurados.tem(pokemon, "loadeddice") ? 4 + sorte.nextInt(2) : null;
  }
}
