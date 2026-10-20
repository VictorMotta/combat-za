package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.api.pokemon.status.Statuses;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatus;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class AplicarStatus {
  private AplicarStatus() {
  }

  public static void tentar(Execucao execucao, Entity quem, String sigla) {
    if (!(quem instanceof PokemonEntity pokemonEntity)
        || !(Statuses.getStatus(sigla) instanceof PersistentStatus status)) {
      return;
    }
    Pokemon pokemon = pokemonEntity.getPokemon();
    if (pokemon.isFainted()) {
      return;
    }
    Component motivo = porQueNaoPega(execucao, pokemon, sigla);
    if (motivo != null) {
      naoPegou(execucao, pokemon, sigla, motivo);
      return;
    }
    aplicar(execucao, pokemonEntity, sigla, status, Component.empty());
  }

  // Estática, Ponto de Veneno...: quando não pega, falha em silêncio, como no Showdown
  public static void pelaHabilidade(Execucao execucao, PokemonEntity quem, String sigla, Component deOnde) {
    Pokemon pokemon = quem.getPokemon();
    if (Statuses.getStatus(sigla) instanceof PersistentStatus status && !pokemon.isFainted()
        && porQueNaoPega(execucao, pokemon, sigla) == null) {
      aplicar(execucao, quem, sigla, status, Component.literal(" (").append(deOnde).append(")"));
    }
  }

  public static boolean temTipo(Pokemon pokemon, ElementalType tipo) {
    return pokemon.getPrimaryType() == tipo || pokemon.getSecondaryType() == tipo;
  }

  // Showdown: imunidade a "powder" (tipo Planta ou a habilidade Invólucro)
  public static boolean imuneAoPo(Pokemon pokemon) {
    return temTipo(pokemon, ElementalTypes.GRASS) || Habilidades.tem(pokemon, Habilidades.INVOLUCRO);
  }

  private static Component porQueNaoPega(Execucao execucao, Pokemon pokemon, String sigla) {
    if (pokemon.getStatus() != null) {
      return Component.literal("já está com outro status");
    }
    if (imunePeloTipo(pokemon, sigla) || (golpeDePo(execucao) && imuneAoPo(pokemon))) {
      return Component.literal("não é afetado");
    }
    if (Habilidades.imuneAoStatus(pokemon, sigla)) {
      return Component.literal("não é afetado: ").append(Habilidades.nome(pokemon));
    }
    // 5b.3: o Campo Enevoado, o Elétrico (sono) e a Salvaguarda
    Component doCampo = CampoEBarreiras.bloqueiaStatus(execucao, pokemon, sigla);
    if (doCampo != null) {
      return doCampo;
    }
    // 5b.2: o Sol forte não deixa congelar; a Folha Guardiã, no Sol, nada
    return ClimaNosGolpes.bloqueiaStatus(pokemon, sigla);
  }

  private static void aplicar(Execucao execucao, PokemonEntity pokemonEntity, String sigla, PersistentStatus status,
      Component deOnde) {
    Pokemon pokemon = pokemonEntity.getPokemon();
    pokemon.applyStatus(status);
    StatusNossos.anotar(pokemon, sigla);
    SonoECongelado.comecar(pokemonEntity, sigla, execucao.getJogador().server.getTickCount());
    CobblemonCombatZA.LOGGER.info("STATUS: {} ficou com {}{}", pokemon.getSpecies().getName(), sigla,
        deOnde.getString());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable(status.getApplyMessage(), pokemon.getDisplayName(false)).append(deOnde));
  }

  private static void naoPegou(Execucao execucao, Pokemon pokemon, String sigla, Component motivo) {
    CobblemonCombatZA.LOGGER.info("STATUS NÃO PEGOU: {} em {} ({})", sigla, pokemon.getSpecies().getName(),
        motivo.getString());
    Feedback.historico(execucao.getJogador(), pokemon, pokemon.getDisplayName(false).copy().append(" ").append(motivo));
  }

  private static boolean imunePeloTipo(Pokemon pokemon, String sigla) {
    return switch (sigla) {
      case "brn" -> temTipo(pokemon, ElementalTypes.FIRE);
      case "par" -> temTipo(pokemon, ElementalTypes.ELECTRIC);
      case "frz" -> temTipo(pokemon, ElementalTypes.ICE);
      case "psn", "tox" -> temTipo(pokemon, ElementalTypes.POISON) || temTipo(pokemon, ElementalTypes.STEEL);
      default -> false;
    };
  }

  private static boolean golpeDePo(Execucao execucao) {
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(execucao.getGolpe());
    return efeito != null && efeito.powder();
  }
}
