package dev.victor.combatza.campo;

import net.minecraft.network.chat.Component;

// 5b.1: os climas da batalha, como na Geração 9 (o Granizo virou Neve). Cada um com o nome do Showdown, que dá as frases
// prontas do Cobblemon ("cobblemon.battle.weather.raindance.start" = "Começou a chover!"), e o nosso nome, que dá a
// etiqueta do painel ("cobblemon_combat_za.campo.chuva")
public enum Clima {
  NENHUM("", ""),
  CHUVA("raindance", "chuva"),
  SOL("sunnyday", "sol"),
  AREIA("sandstorm", "areia"),
  NEVE("snow", "neve");

  private final String doShowdown;
  private final String nome;

  Clima(String doShowdown, String nome) {
    this.doShowdown = doShowdown;
    this.nome = nome;
  }

  public String nome() {
    return nome;
  }

  public Component comecou() {
    return Component.translatable("cobblemon.battle.weather." + doShowdown + ".start");
  }

  public Component acabou() {
    return Component.translatable("cobblemon.battle.weather." + doShowdown + ".end");
  }
}
