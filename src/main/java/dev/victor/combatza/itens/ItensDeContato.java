package dev.victor.combatza.itens;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 9.1, lote 1: o Elmo Rochoso. Quem bate encostando (golpe de contato) num Pokémon com o Elmo perde 1/6 da vida dele. No mesmo
// lugar das habilidades de contato (HabilidadesDeContato.reagir), depois do acerto
public class ItensDeContato {
  private static final int ELMO = 6;

  private ItensDeContato() {
  }

  public static void reagir(Execucao execucao, PokemonEntity atacante, Entity alvo, double pvTirados) {
    if (pvTirados <= 0 || !(alvo instanceof PokemonEntity atingido) || !atacante.isAlive()
        || !ItensSegurados.fazContato(execucao.getPokemon(), execucao.getGolpe())
        || !ItensSegurados.tem(atingido.getPokemon(), ItensSegurados.ELMO_ROCHOSO)) {
      return;
    }
    Pokemon pokemon = atacante.getPokemon();
    int pv = Math.max(1, pokemon.getMaxHealth() / ELMO);
    Feedback.historico(execucao.getJogador(), pokemon, Component.translatable("cobblemon_combat_za.item.elmo",
        pokemon.getDisplayName(false), ItensSegurados.nome(atingido.getPokemon()),
        atingido.getPokemon().getDisplayName(false), pv));
    Vida.ferir(atacante, pv);
    CobblemonCombatZA.LOGGER.info("ITEM: {} se feriu no Elmo Rochoso de {} −{} PV", pokemon.getSpecies().getName(),
        atingido.getPokemon().getSpecies().getName(), pv);
  }
}
