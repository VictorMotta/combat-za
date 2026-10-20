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
    return switch (golpe.getTemplate().getTarget()) {
      case allAdjacentFoes -> AREA_INIMIGOS;
      case allAdjacent -> AREA_TODOS;
      case foeSide -> ARMADILHA;
      case self, all, allies, allySide, allyTeam -> SEM_ALVO;
      default -> UNICO;
    };
  }
}
