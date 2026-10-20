package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SonoECongelado {
  public static final ResourceLocation ID_PARADO = ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID,
      "parado");

  private record Parado(Pokemon pokemon, String sigla, long acabaNoTick) {
  }

  private static final Map<UUID, Parado> PARADOS = new HashMap<>();

  private SonoECongelado() {
  }

  public static void comecar(PokemonEntity entidade, String sigla, long agora) {
    int turnos = switch (sigla) {
      case "slp" -> entidade.getRandom().nextIntBetweenInclusive(Balanceamento.TURNOS_DE_SONO_MINIMO,
          Balanceamento.TURNOS_DE_SONO_MAXIMO);
      case "frz" -> turnosCongelado(entidade.getRandom());
      default -> 0;
    };
    comecar(entidade, sigla, turnos, agora);
  }

  public static void comecar(PokemonEntity entidade, String sigla, int turnos, long agora) {
    if (turnos == 0) {
      return;
    }
    Pokemon pokemon = entidade.getPokemon();
    CobblemonCombatZA.LOGGER.info("PARADO: {} ({}) por {} turno(s)", pokemon.getSpecies().getName(), sigla, turnos);
    PARADOS.put(pokemon.getUuid(), new Parado(pokemon, sigla, agora + turnos * EfeitosDeStatus.ticksPorTurno()));
    if ("slp".equals(sigla)) {
      entidade.getBrain().setMemory(CobblemonMemories.POKEMON_DROWSY, true);
    }
    travar(entidade);
  }

  private static int turnosCongelado(RandomSource sorte) {
    int turnos = 1;
    while (turnos < Balanceamento.TURNOS_CONGELADO_MAXIMO && sorte.nextFloat() >= Balanceamento.CHANCE_DE_DESCONGELAR) {
      turnos++;
    }
    return turnos;
  }

  public static void atualizar(long agora) {
    for (UUID uuid : List.copyOf(PARADOS.keySet())) {
      Parado parado = PARADOS.get(uuid);
      Pokemon pokemon = parado.pokemon();
      PokemonEntity entidade = pokemon.getEntity();
      if (!parado.sigla().equals(EfeitosDeStatus.siglaDoStatus(pokemon))) {
        soltar(pokemon);
      } else if (agora >= parado.acabaNoTick()) {
        terminar(pokemon);
      } else if (entidade != null) {
        travar(entidade);
      }
    }
  }

  public static boolean estaParado(Pokemon pokemon) {
    Parado parado = PARADOS.get(pokemon.getUuid());
    return parado != null && parado.sigla().equals(EfeitosDeStatus.siglaDoStatus(pokemon));
  }

  public static boolean dormindoPeloGolpe(Pokemon pokemon) {
    return estaParado(pokemon) && "slp".equals(EfeitosDeStatus.siglaDoStatus(pokemon));
  }

  public static void aoLevarDano(Entity vitima) {
    if (vitima instanceof PokemonEntity entidade && dormindoPeloGolpe(entidade.getPokemon())) {
      terminar(entidade.getPokemon());
    }
  }

  public static void descongelarComFogo(Entity alvo) {
    if (alvo instanceof PokemonEntity entidade && "frz".equals(EfeitosDeStatus.siglaDoStatus(entidade.getPokemon()))) {
      terminar(entidade.getPokemon());
    }
  }

  public static void limpar() {
    PARADOS.clear();
  }

  private static void terminar(Pokemon pokemon) {
    PersistentStatusContainer status = pokemon.getStatus();
    if (status != null) {
      Component mensagem = Component.translatable(status.getStatus().getRemoveMessage(), pokemon.getDisplayName(false));
      CobblemonCombatZA.LOGGER.info("FIM DO {}: {}", status.getStatus().getShowdownName(), mensagem.getString());
      Feedback.avisarQuemImporta(pokemon, mensagem);
      pokemon.setStatus(null);
    }
    soltar(pokemon);
  }

  private static void soltar(Pokemon pokemon) {
    PARADOS.remove(pokemon.getUuid());
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade == null) {
      return;
    }
    AttributeInstance velocidade = entidade.getAttribute(Attributes.MOVEMENT_SPEED);
    if (velocidade != null) {
      velocidade.removeModifier(ID_PARADO);
    }
    entidade.getBrain().eraseMemory(CobblemonMemories.POKEMON_DROWSY);
  }

  private static void travar(PokemonEntity entidade) {
    AttributeInstance velocidade = entidade.getAttribute(Attributes.MOVEMENT_SPEED);
    if (velocidade != null) {
      velocidade.addOrUpdateTransientModifier(
          new AttributeModifier(ID_PARADO, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    entidade.getNavigation().stop();
  }

}
