package dev.victor.combatza.itens;

import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.world.entity.Entity;

// 9.1, lote 1: os itens que mexem nos NÚMEROS do dano (Dano), com os números do Showdown (o items.js do Cobblemon):
// - os 18 de tipo (Carvão, Água Mística, Ímã...): o golpe daquele tipo ×1,2 (4915/4096);
// - Faixa Musculosa e Óculos de Sabedoria: o físico / o especial ×1,1 (4505/4096);
// - Faixa Profissional: o super efetivo ×1,2; Orbe da Vida: todo golpe ×1,3 (5324/4096; o recuo fica em ItensDeVida);
// - Faixa e Lentes da Escolha: Ataque / Ataque Especial ×1,5 (a trava fica em TravaDaEscolha); o Lenço, a Velocidade ×1,5;
// - Colete de Ataque: Defesa Especial ×1,5; Eviolita: Defesa e Defesa Especial ×1,5 em quem ainda evolui;
// - Cinta de Foco: com a vida cheia, o golpe que derrubaria deixa 1 PV (uma vez por batalha).
// Os de quem ataca valem também contra monstros e contra você (o item é de quem bate); os de quem apanha, só entre Pokémon
public class ItensDeDano {
  private static final double DO_TIPO = 4915.0 / 4096.0;
  private static final double FAIXA_OU_OCULOS = 4505.0 / 4096.0;
  private static final double FAIXA_PROFISSIONAL = 4915.0 / 4096.0;
  private static final double ORBE_DA_VIDA = 5324.0 / 4096.0;
  private static final double ESCOLHA = 1.5;
  private static final double COLETE_OU_EVIOLITA = 1.5;

  private static final Map<String, ElementalType> DE_TIPO = Map.ofEntries(
      Map.entry("charcoal", ElementalTypes.FIRE), Map.entry("mysticwater", ElementalTypes.WATER),
      Map.entry("magnet", ElementalTypes.ELECTRIC), Map.entry("miracleseed", ElementalTypes.GRASS),
      Map.entry("nevermeltice", ElementalTypes.ICE), Map.entry("blackbelt", ElementalTypes.FIGHTING),
      Map.entry("poisonbarb", ElementalTypes.POISON), Map.entry("softsand", ElementalTypes.GROUND),
      Map.entry("sharpbeak", ElementalTypes.FLYING), Map.entry("twistedspoon", ElementalTypes.PSYCHIC),
      Map.entry("silverpowder", ElementalTypes.BUG), Map.entry("hardstone", ElementalTypes.ROCK),
      Map.entry("spelltag", ElementalTypes.GHOST), Map.entry("dragonfang", ElementalTypes.DRAGON),
      Map.entry("blackglasses", ElementalTypes.DARK), Map.entry("metalcoat", ElementalTypes.STEEL),
      Map.entry("silkscarf", ElementalTypes.NORMAL), Map.entry("fairyfeather", ElementalTypes.FAIRY));

  private ItensDeDano() {
  }

  private static boolean fisico(Move golpe) {
    return golpe.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
  }

  // O poder do golpe (ao lado do HabilidadesDeDano.poder)
  public static double poder(Pokemon atacante, Move golpe, double poder) {
    String item = ItensSegurados.id(atacante);
    double multiplicador = 1.0;
    if (DE_TIPO.get(item) == golpe.getType()) {
      multiplicador *= DO_TIPO;
    }
    if (ItensSegurados.FAIXA_MUSCULOSA.equals(item) && fisico(golpe)
        || ItensSegurados.OCULOS_DE_SABEDORIA.equals(item) && !fisico(golpe)) {
      multiplicador *= FAIXA_OU_OCULOS;
    }
    return poder * multiplicador;
  }

  // O Ataque (físico) ou o Ataque Especial de quem bate
  public static double ataque(Pokemon atacante, boolean fisico) {
    String item = ItensSegurados.id(atacante);
    return fisico && ItensSegurados.FAIXA_DA_ESCOLHA.equals(item)
        || !fisico && ItensSegurados.LENTES_DA_ESCOLHA.equals(item) ? ESCOLHA : 1.0;
  }

  // A Defesa (físico) ou a Defesa Especial de quem apanha
  public static double defesa(Pokemon defensor, boolean fisico) {
    String item = ItensSegurados.id(defensor);
    if (!fisico && ItensSegurados.COLETE_DE_ATAQUE.equals(item)) {
      return COLETE_OU_EVIOLITA;
    }
    return ItensSegurados.EVIOLITA.equals(item) && aindaEvolui(defensor) ? COLETE_OU_EVIOLITA : 1.0;
  }

  private static boolean aindaEvolui(Pokemon pokemon) {
    return pokemon.getEvolutions().iterator().hasNext();
  }

  // O dano final (ao lado do HabilidadesDeDano.danoFinal)
  public static double danoFinal(Pokemon atacante, double efetividade) {
    String item = ItensSegurados.id(atacante);
    if (ItensSegurados.ORBE_DA_VIDA.equals(item)) {
      return ORBE_DA_VIDA;
    }
    return ItensSegurados.FAIXA_PROFISSIONAL.equals(item) && efetividade > 1.0 ? FAIXA_PROFISSIONAL : 1.0;
  }

  // Contra monstro e contra você: só os de quem bate (o poder, a Escolha, o Orbe)
  public static double doAtacante(Pokemon atacante, Move golpe) {
    return poder(atacante, golpe, 1.0) * ataque(atacante, fisico(golpe)) * danoFinal(atacante, 1.0);
  }

  // O Lenço da Escolha: a Velocidade ×1,5, na recarga e no ritmo da carga (como um estágio de Velocidade a mais)
  public static double velocidade(Pokemon pokemon) {
    return ItensSegurados.tem(pokemon, ItensSegurados.LENCO_DA_ESCOLHA) ? ESCOLHA : 1.0;
  }

  // A Cinta de Foco, depois da Robustez (no Showdown ela vem antes): vida cheia e um golpe que derrubaria = fica com 1 PV
  public static float cintaDeFoco(Entity alvo, float dano, boolean robustezAgiu) {
    if (robustezAgiu || !(alvo instanceof PokemonEntity entidade)) {
      return dano;
    }
    Pokemon pokemon = entidade.getPokemon();
    if (!ItensSegurados.tem(pokemon, ItensSegurados.CINTA_DE_FOCO)
        || ItensSegurados.usadoNestaBatalha(pokemon, ItensSegurados.CINTA_DE_FOCO)
        || Vida.pvAtual(pokemon) < pokemon.getMaxHealth() || dano < entidade.getHealth()) {
      return dano;
    }
    ItensSegurados.gastar(pokemon, ItensSegurados.CINTA_DE_FOCO);
    CobblemonCombatZA.LOGGER.info("ITEM: {} aguentou com 1 PV (Cinta de Foco)", pokemon.getSpecies().getName());
    return entidade.getHealth() - Vida.naVida(entidade, 1);
  }
}
