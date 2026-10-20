package dev.victor.combatza.golpes;

import java.util.Map;

import com.cobblemon.mod.common.api.types.ElementalType;

// As cores oficiais dos tipos nos jogos (usadas no raiozinho e na HUD)
public class CoresDosTipos {
  private static final int BRANCO = 0xFFFFFF;
  private static final Map<String, Integer> CORES = Map.ofEntries(
      Map.entry("normal", 0xA8A77A), Map.entry("fire", 0xEE8130), Map.entry("water", 0x6390F0),
      Map.entry("electric", 0xF7D02C), Map.entry("grass", 0x7AC74C), Map.entry("ice", 0x96D9D6),
      Map.entry("fighting", 0xC22E28), Map.entry("poison", 0xA33EA1), Map.entry("ground", 0xE2BF65),
      Map.entry("flying", 0xA98FF3), Map.entry("psychic", 0xF95587), Map.entry("bug", 0xA6B91A),
      Map.entry("rock", 0xB6A136), Map.entry("ghost", 0x735797), Map.entry("dragon", 0x6F35FC),
      Map.entry("dark", 0x705746), Map.entry("steel", 0xB7B7CE), Map.entry("fairy", 0xD685AD));

  private CoresDosTipos() {
  }

  // A cor em RGB (0xRRGGBB)
  public static int de(ElementalType tipo) {
    return CORES.getOrDefault(tipo.getShowdownId(), BRANCO);
  }
}
