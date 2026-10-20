package dev.victor.combatza.campo;

import net.minecraft.network.chat.Component;

// 5b.3: as barreiras de um lado da batalha (o seu lado = os lutadores dos jogadores; o outro = os selvagens), com o nome do
// Showdown (as frases do Cobblemon: "cobblemon.battle.sidestart.ally.reflect"), o nosso nome (a etiqueta do painel) e quantos
// turnos de 3,5 s duram
public enum Barreira {
  REFLETIR("reflect", "refletir", 5),
  TELA_DE_LUZ("lightscreen", "tela_de_luz", 5),
  VEU_DE_AURORA("auroraveil", "veu_de_aurora", 5),
  VENTO_DE_CAUDA("tailwind", "vento_de_cauda", 4),
  SALVAGUARDA("safeguard", "salvaguarda", 5),
  NEVOA("mist", "nevoa", 5),
  CANTICO_DA_SORTE("luckychant", "cantico", 5);

  private final String doShowdown;
  private final String nome;
  private final int turnos;

  Barreira(String doShowdown, String nome, int turnos) {
    this.doShowdown = doShowdown;
    this.nome = nome;
    this.turnos = turnos;
  }

  public String doShowdown() {
    return doShowdown;
  }

  public String nome() {
    return nome;
  }

  public int turnos() {
    return turnos;
  }

  // As telas (Refletir, Tela de Luz, Véu de Aurora) duram 8 turnos com a Argila de Luz
  public boolean ehTela() {
    return this == REFLETIR || this == TELA_DE_LUZ || this == VEU_DE_AURORA;
  }

  // "ally" para o seu lado, "opponent" para o dos selvagens: quem lê é sempre um jogador
  public Component comecou(boolean doSeuLado) {
    return Component.translatable("cobblemon.battle.sidestart." + (doSeuLado ? "ally." : "opponent.") + doShowdown);
  }

  public Component acabou(boolean doSeuLado) {
    return Component.translatable("cobblemon.battle.sideend." + (doSeuLado ? "ally." : "opponent.") + doShowdown);
  }
}
