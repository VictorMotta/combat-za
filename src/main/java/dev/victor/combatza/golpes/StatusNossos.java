package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import net.minecraft.world.entity.ai.Brain;

public class StatusNossos {
  private static final Map<UUID, String> SIGLAS = new HashMap<>();

  private StatusNossos() {
  }

  public static void anotar(Pokemon pokemon, String sigla) {
    SIGLAS.put(pokemon.getUuid(), sigla);
  }

  public static void esquecer(Pokemon pokemon) {
    SIGLAS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    SIGLAS.clear();
  }

  public static boolean ehNosso(Pokemon pokemon) {
    String sigla = EfeitosDeStatus.siglaDoStatus(pokemon);
    return !sigla.isEmpty() && sigla.equals(SIGLAS.get(pokemon.getUuid()));
  }

  // Dormindo, de qualquer sono: o de cansaço do Cobblemon (à noite, ou de dia para os noturnos) ou o de um golpe
  public static boolean estaDormindo(Pokemon pokemon) {
    return "slp".equals(EfeitosDeStatus.siglaDoStatus(pokemon));
  }

  public static boolean semSonoDeCansaco(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    return GerenciarCombates.estaEmCombate(pokemon) || ehNosso(pokemon);
  }

  public static void acordarSeDormeDeCansaco(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if (!"slp".equals(EfeitosDeStatus.siglaDoStatus(pokemon)) || ehNosso(pokemon)) {
      return;
    }
    pokemon.setStatus(null);
    Brain<?> cerebro = entidade.getBrain();
    cerebro.eraseMemory(CobblemonMemories.POKEMON_DROWSY);
    cerebro.eraseMemory(CobblemonMemories.POKEMON_SLEEPING);
    cerebro.useDefaultActivity();
    CobblemonCombatZA.LOGGER.info("ACORDOU (dormia de cansaço): {}", pokemon.getSpecies().getName());
  }
}
