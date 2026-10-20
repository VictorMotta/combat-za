package dev.victor.combatza.campo;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;

import net.minecraft.network.chat.Component;

// 5b.3: os campos (terrenos) da batalha: Elétrico, de Plantas, Enevoado e Psíquico. Cada um com o nome do Showdown (as frases
// prontas do Cobblemon, "cobblemon.battle.fieldstart.electricterrain"), o nosso nome (a etiqueta do painel) e o tipo que ele
// fortalece em quem está no chão (o Enevoado não fortalece nenhum)
public enum Terreno {
  NENHUM("", "", null),
  ELETRICO("electricterrain", "eletrico", ElementalTypes.ELECTRIC),
  PLANTAS("grassyterrain", "plantas", ElementalTypes.GRASS),
  ENEVOADO("mistyterrain", "enevoado", null),
  PSIQUICO("psychicterrain", "psiquico", ElementalTypes.PSYCHIC);

  private final String doShowdown;
  private final String nome;
  private final ElementalType fortalece;

  Terreno(String doShowdown, String nome, ElementalType fortalece) {
    this.doShowdown = doShowdown;
    this.nome = nome;
    this.fortalece = fortalece;
  }

  public String doShowdown() {
    return doShowdown;
  }

  public String nome() {
    return nome;
  }

  public ElementalType fortalece() {
    return fortalece;
  }

  public Component comecou() {
    return Component.translatable("cobblemon.battle.fieldstart." + doShowdown);
  }

  public Component acabou() {
    return Component.translatable("cobblemon.battle.fieldend." + doShowdown);
  }
}
