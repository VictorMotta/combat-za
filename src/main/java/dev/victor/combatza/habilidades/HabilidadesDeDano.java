package dev.victor.combatza.habilidades;

import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Balanceamento;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.world.entity.Entity;

public class HabilidadesDeDano {
  private static final String TECNICO = "technician";
  private static final String PELE_SECA = "dryskin";
  private static final Set<String> DOBRAM_O_ATAQUE = Set.of("hugepower", "purepower");
  private static final String TENACIDADE = "guts";
  private static final String BOLHA_DE_AGUA = "waterbubble";
  private static final String ADAPTABILIDADE = "adaptability";
  private static final String LEVITACAO = "levitate";
  private static final String ROBUSTEZ = "sturdy";
  private static final Set<String> SEM_CRITICO = Set.of("shellarmor", "battlearmor");
  private static final Set<String> FILTRAM = Set.of("filter", "solidrock", "prismarmor");
  private static final Set<String> ACERTAM_FANTASMA = Set.of("scrappy", "mindseye");

  // Supercrescimento, Incêndio, Dilúvio e Enxame: o tipo que ganha força com 1/3 da vida ou menos
  private static final Map<String, ElementalType> FORCA_NO_APERTO = Map.of(
      "overgrow", ElementalTypes.GRASS,
      "blaze", ElementalTypes.FIRE,
      "torrent", ElementalTypes.WATER,
      "swarm", ElementalTypes.BUG);

  // De quem apanha: o ataque de quem bate cai pela metade contra esses tipos
  private static final Map<String, Set<ElementalType>> RESISTEM = Map.of(
      "thickfat", Set.of(ElementalTypes.FIRE, ElementalTypes.ICE),
      "heatproof", Set.of(ElementalTypes.FIRE),
      "waterbubble", Set.of(ElementalTypes.FIRE),
      "purifyingsalt", Set.of(ElementalTypes.GHOST));

  private HabilidadesDeDano() {
  }

  // Showdown, onBasePower
  public static double poder(Pokemon atacante, Pokemon defensor, Move golpe, double poder) {
    double resultado = poder;
    if (Habilidades.tem(atacante, TECNICO) && poder <= 60) {
      resultado *= 1.5;
    }
    if (Habilidades.tem(defensor, PELE_SECA) && golpe.getType() == ElementalTypes.FIRE) {
      resultado *= 1.25;
    }
    return resultado;
  }

  // Showdown, onModifyAtk/onModifySpA (de quem bate) e onSourceModifyAtk/onSourceModifySpA (de quem apanha)
  public static double ataque(Pokemon atacante, Pokemon defensor, Move golpe, boolean fisico) {
    String habilidade = atacante.getAbility().getName();
    ElementalType tipo = golpe.getType();
    double multiplicador = 1.0;
    if (fisico && DOBRAM_O_ATAQUE.contains(habilidade)) {
      multiplicador *= 2;
    }
    if (fisico && TENACIDADE.equals(habilidade) && atacante.getStatus() != null) {
      multiplicador *= 1.5;
    }
    if (tipo == FORCA_NO_APERTO.get(habilidade) && Vida.pvAtual(atacante) <= atacante.getMaxHealth() / 3.0) {
      multiplicador *= 1.5;
    }
    if (tipo == ElementalTypes.FIRE && Absorcao.fogoAceso(atacante)) {
      multiplicador *= 1.5;
    }
    if (tipo == ElementalTypes.WATER && BOLHA_DE_AGUA.equals(habilidade)) {
      multiplicador *= 2;
    }
    Set<ElementalType> resistidos = RESISTEM.get(defensor.getAbility().getName());
    if (resistidos != null && resistidos.contains(tipo)) {
      multiplicador *= 0.5;
    }
    return multiplicador;
  }

  public static double stab(Pokemon atacante) {
    return Habilidades.tem(atacante, ADAPTABILIDADE) ? Balanceamento.STAB_COM_ADAPTABILIDADE : Balanceamento.STAB;
  }

  // Tenacidade: com status, o Ataque não cai pela queimadura
  public static boolean ignoraQueimadura(Pokemon atacante) {
    return Habilidades.tem(atacante, TENACIDADE);
  }

  // Showdown, onCriticalHit
  public static boolean semCritico(Pokemon defensor) {
    return SEM_CRITICO.contains(defensor.getAbility().getName());
  }

  // Levitação: golpes de Terra não pegam (só os de dano; os de status ignoram imunidade de tipo)
  public static boolean imune(Move golpe, Pokemon defensor) {
    return golpe.getType() == ElementalTypes.GROUND && Habilidades.tem(defensor, LEVITACAO);
  }

  // Intrépido e Olho Mental: golpes Normal e Lutador acertam o tipo Fantasma
  public static boolean ignoraImunidade(Pokemon atacante, Move golpe, ElementalType tipoDoDefensor) {
    ElementalType tipo = golpe.getType();
    return tipoDoDefensor == ElementalTypes.GHOST && (tipo == ElementalTypes.NORMAL || tipo == ElementalTypes.FIGHTING)
        && ACERTAM_FANTASMA.contains(atacante.getAbility().getName());
  }

  // Showdown, onSourceModifyDamage: Filtro, Rocha Sólida e Armadura Prisma cortam o super efetivo
  public static double danoFinal(Pokemon defensor, double efetividade) {
    return efetividade > 1 && FILTRAM.contains(defensor.getAbility().getName()) ? 0.75 : 1.0;
  }

  // Showdown, onDamage: com a vida cheia, o golpe que derrubaria deixa 1 PV
  public static float robustez(Entity alvo, float dano) {
    if (!(alvo instanceof PokemonEntity entidade) || !Habilidades.tem(entidade.getPokemon(), ROBUSTEZ)) {
      return dano;
    }
    Pokemon pokemon = entidade.getPokemon();
    if (Vida.pvAtual(pokemon) < pokemon.getMaxHealth() || dano < entidade.getHealth()) {
      return dano;
    }
    CobblemonCombatZA.LOGGER.info("ROBUSTEZ: {} aguentou o golpe com 1 PV", pokemon.getSpecies().getName());
    return entidade.getHealth() - Vida.naVida(entidade, 1);
  }
}
