package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;

public enum Arquetipo {
  RAPIDO(0, 3, 0, 3),
  CORPO_A_CORPO(5, 3, 0, 3),
  PROJETIL(10, 5, 7, 3),
  CANHAO(13, 7, 34, 3),
  STATUS(7, 3, 0, 3);

  private static final double PODER_CANHAO = 90.0;

  private final int carga;
  private final int impacto;
  private final int canalizacao;
  private final int recuperacao;

  Arquetipo(int carga, int impacto, int canalizacao, int recuperacao) {
    this.carga = carga;
    this.impacto = impacto;
    this.canalizacao = canalizacao;
    this.recuperacao = recuperacao;
  }

  public int ticksDeCarga() {
    return carga;
  }

  public int ticksAteImpacto() {
    return carga + impacto;
  }

  public int ticksTotais() {
    return carga + impacto + canalizacao + recuperacao;
  }

  public static Arquetipo classificar(Move golpe) {
    if (golpe.getTemplate().getPriority() > 0) {
      return RAPIDO;
    }
    if (golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) {
      return STATUS;
    }
    if (golpe.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL()) {
      return CORPO_A_CORPO;
    }

    return golpe.getPower() >= PODER_CANHAO ? CANHAO : PROJETIL;
  }

  public boolean voa() {
    return this == PROJETIL || this == CANHAO;
  }

}