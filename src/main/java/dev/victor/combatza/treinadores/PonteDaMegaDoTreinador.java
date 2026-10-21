package dev.victor.combatza.treinadores;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.gimmick.MegaGimmick;

// 21/10 (treinadores): a Mega do Pokémon do treinador pelo Mega Showdown. Como a mega.PonteDoMegaShowdown, esta é a ÚNICA
// classe do pacote que chama o Mega Showdown: o Java só carrega a classe quando ela é usada, e quem usa (MegaDoTreinador)
// confere antes se o mod está no jogo. Sem dono, o MegaGimmick.canMega pula as conferências do jogador (o bracelete, "um
// Mega por time": javap) e olha só a Mega Pedra segurada e a espécie; o megaEvolve toca a animação e troca a forma
final class PonteDaMegaDoTreinador {
  private PonteDaMegaDoTreinador() {
  }

  static boolean podeMegar(Pokemon pokemon) {
    return !MegaGimmick.isMega(pokemon) && MegaGimmick.canMega(pokemon);
  }

  static void megar(Pokemon pokemon) {
    MegaGimmick.megaEvolve(pokemon);
  }
}
