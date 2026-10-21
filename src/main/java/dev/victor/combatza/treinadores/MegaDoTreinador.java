package dev.victor.combatza.treinadores;

import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import net.fabricmc.loader.api.FabricLoader;

// 21/10 (treinadores, decisão dele: "o Pokémon do treinador segurando a Mega Pedra mega-evolui"). Como nos jogos: no máximo
// UMA Mega por batalha (BatalhaDeTreinador), assim que o Pokémon sai da bola; a animação do Mega Showdown deixa ele parado
// uns 6 segundos, e ele só entra na luta depois dela (sem levar dano, como o seu: BatalhasDeTreinador). Sem o Mega Showdown
// no servidor, ou se ele mudar por dentro (LinkageError), a Mega fica desligada e o Pokémon luta normal
final class MegaDoTreinador {
  private static final String MOD_DA_MEGA = "mega_showdown";
  private static boolean ponteQuebrada = false;

  private MegaDoTreinador() {
  }

  static boolean podeMegar(Pokemon pokemon) {
    if (ponteQuebrada || !FabricLoader.getInstance().isModLoaded(MOD_DA_MEGA)) {
      return false;
    }
    try {
      return PonteDaMegaDoTreinador.podeMegar(pokemon);
    } catch (LinkageError | RuntimeException erro) {
      quebrou(erro);
      return false;
    }
  }

  static boolean megar(Pokemon pokemon) {
    try {
      PonteDaMegaDoTreinador.megar(pokemon);
      return true;
    } catch (LinkageError | RuntimeException erro) {
      quebrou(erro);
      return false;
    }
  }

  private static void quebrou(Throwable erro) {
    if (!ponteQuebrada) {
      ponteQuebrada = true;
      CobblemonCombatZA.LOGGER.error("TREINADOR: a Mega do Mega Showdown não funcionou; os treinadores lutam sem Mega",
          erro);
    }
  }
}
