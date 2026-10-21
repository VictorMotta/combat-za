package dev.victor.combatza.habilidades;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.AplicarEfeitos;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

public class Absorcao {
  // atributo null = cura 1/4 da vida máxima
  private record Absorve(ElementalType tipo, String atributo, int estagios) {
  }

  private static final Map<String, Absorve> ABSORVEM = Map.of(
      "waterabsorb", new Absorve(ElementalTypes.WATER, null, 0),
      "voltabsorb", new Absorve(ElementalTypes.ELECTRIC, null, 0),
      "dryskin", new Absorve(ElementalTypes.WATER, null, 0),
      "eartheater", new Absorve(ElementalTypes.GROUND, null, 0),
      "lightningrod", new Absorve(ElementalTypes.ELECTRIC, "spa", 1),
      "stormdrain", new Absorve(ElementalTypes.WATER, "spa", 1),
      "motordrive", new Absorve(ElementalTypes.ELECTRIC, "spe", 1),
      "sapsipper", new Absorve(ElementalTypes.GRASS, "atk", 1),
      "wellbakedbody", new Absorve(ElementalTypes.FIRE, "def", 2));
  private static final String FOGO_BRILHANTE = "flashfire";
  private static final Set<UUID> FOGO_ACESO = new HashSet<>();

  private Absorcao() {
  }

  // Showdown, onTryHit: o golpe do tipo certo não faz nada (nem dano, nem efeito) e vira cura ou estágio
  public static boolean absorveu(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity entidade) || entidade == atacante) {
      return false;
    }
    Pokemon pokemon = entidade.getPokemon();
    Move golpe = execucao.getGolpe();
    if (Habilidades.tem(pokemon, FOGO_BRILHANTE) && golpe.getType() == ElementalTypes.FIRE) {
      acenderFogo(execucao, pokemon);
      return true;
    }
    Absorve absorve = ABSORVEM.get(pokemon.getAbility().getName());
    if (absorve == null || golpe.getType() != absorve.tipo()) {
      return false;
    }
    MutableComponent aviso = Component.translatable("cobblemon_combat_za.habilidade.absorveu",
        pokemon.getDisplayName(false), golpe.getDisplayName(), Habilidades.nome(pokemon));
    if (absorve.atributo() == null) {
      int curou = Vida.curar(entidade, pokemon.getMaxHealth() / 4.0);
      aviso.append(curou > 0 ? Component.translatable("cobblemon_combat_za.pv.ganhou", curou) : Component.empty());
    } else {
      int mudou = Estagios.mudar(pokemon, absorve.atributo(), absorve.estagios(), AplicarEfeitos.quandoAcaba(execucao));
      aviso.append(" ").append(Component.translatable(Estagios.FRASE + (mudou > 0 ? "subiu" : "nao_sobe_mais"),
          Estagios.nomeDoAtributo(absorve.atributo())));
    }
    CobblemonCombatZA.LOGGER.info("ABSORVEU: {} absorveu {} ({})", pokemon.getSpecies().getName(), golpe.getName(),
        pokemon.getAbility().getName());
    Feedback.historico(execucao.getJogador(), pokemon, aviso);
    return true;
  }

  public static boolean fogoAceso(Pokemon pokemon) {
    return FOGO_ACESO.contains(pokemon.getUuid());
  }

  public static void esquecer(Pokemon pokemon) {
    FOGO_ACESO.remove(pokemon.getUuid());
  }

  public static void limpar() {
    FOGO_ACESO.clear();
  }

  // Fica aceso até voltar para a bola ou desmaiar: os golpes de Fogo dele ficam 1,5x mais fortes
  private static void acenderFogo(Execucao execucao, Pokemon pokemon) {
    boolean acendeuAgora = FOGO_ACESO.add(pokemon.getUuid());
    CobblemonCombatZA.LOGGER.info("FOGO BRILHANTE: {} absorveu {} (aceso agora? {})", pokemon.getSpecies().getName(),
        execucao.getGolpe().getName(), acendeuAgora);
    Component aviso = acendeuAgora
        ? Component.translatable("cobblemon.battle.start.flashfire", pokemon.getDisplayName(false))
        : Component.translatable("cobblemon.battle.immune", pokemon.getDisplayName(false));
    Feedback.historico(execucao.getJogador(), pokemon, aviso);
  }
}
