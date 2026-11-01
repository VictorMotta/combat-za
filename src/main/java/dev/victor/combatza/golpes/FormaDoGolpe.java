package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.api.moves.Move;

public enum FormaDoGolpe {
  UNICO,
  AREA_INIMIGOS,
  AREA_TODOS,
  // 5.6c: o lado inimigo inteiro nos jogos (Espinhos, Pedra Oculta): aqui, uma armadilha no chão (Armadilhas)
  ARMADILHA,
  SEM_ALVO;

  public static FormaDoGolpe de(Move golpe) {
    if (GolpesDeAliado.NO_ALIADO.contains(golpe.getName())) {
      return SEM_ALVO;
    }
    return switch (golpe.getTemplate().getTarget()) {
      case allAdjacentFoes -> AREA_INIMIGOS;
      case allAdjacent -> AREA_TODOS;
      case foeSide -> ARMADILHA;
      // 01/11: os de aliado (Mãozinha, Acupressão...) também: o aliado é achado pelo golpe (GolpesDeAliado)
      case self, all, allies, allySide, allyTeam, adjacentAlly, adjacentAllyOrSelf -> SEM_ALVO;
      default -> UNICO;
    };
  }
}
