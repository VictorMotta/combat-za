package dev.victor.combatza.habilidades;

import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class HabilidadesDeContato {
  private static final String FRASE = "cobblemon_combat_za.habilidade.";
  private static final Map<String, String> STATUS_NO_CONTATO = Map.of(
      "static", "par",
      "poisonpoint", "psn",
      "flamebody", "brn");
  private static final int CHANCE_DO_STATUS = 30;
  private static final String EFEITO_ESPORO = "effectspore";
  private static final Set<String> FEREM_NO_CONTATO = Set.of("roughskin", "ironbarbs");

  private HabilidadesDeContato() {
  }

  // Showdown, onDamagingHit: um golpe de contato tirou vida, e a habilidade de quem apanhou reage em quem bateu
  public static void reagir(Execucao execucao, PokemonEntity atacante, Entity alvo, double pvTirados) {
    if (pvTirados <= 0 || !(alvo instanceof PokemonEntity atingido)
        || !ItensSegurados.fazContato(execucao.getPokemon(), execucao.getGolpe())) {
      return;
    }
    Pokemon defensor = atingido.getPokemon();
    String habilidade = defensor.getAbility().getName();
    Component deOnde = Component.translatable(FRASE + "de", Habilidades.nome(defensor),
        defensor.getDisplayName(false));

    String sigla = STATUS_NO_CONTATO.get(habilidade);
    if (sigla != null && atacante.getRandom().nextInt(100) < CHANCE_DO_STATUS) {
      AplicarStatus.pelaHabilidade(execucao, atacante, sigla, deOnde);
    }
    if (EFEITO_ESPORO.equals(habilidade)) {
      esporos(execucao, atacante, deOnde);
    }
    if (FEREM_NO_CONTATO.contains(habilidade)) {
      ferir(execucao, atacante, deOnde);
    }
  }

  // 11% sono, 10% paralisia, 9% veneno; tipo Planta e Invólucro não pegam esporo
  private static void esporos(Execucao execucao, PokemonEntity atacante, Component deOnde) {
    if (AplicarStatus.imuneAoPo(atacante.getPokemon())) {
      return;
    }
    int sorteio = atacante.getRandom().nextInt(100);
    if (sorteio < 11) {
      AplicarStatus.pelaHabilidade(execucao, atacante, "slp", deOnde);
    } else if (sorteio < 21) {
      AplicarStatus.pelaHabilidade(execucao, atacante, "par", deOnde);
    } else if (sorteio < 30) {
      AplicarStatus.pelaHabilidade(execucao, atacante, "psn", deOnde);
    }
  }

  // 1/8 da vida máxima de quem bateu
  private static void ferir(Execucao execucao, PokemonEntity atacante, Component deOnde) {
    Pokemon pokemon = atacante.getPokemon();
    int pv = Math.max(1, pokemon.getMaxHealth() / 8);
    Vida.ferir(atacante, pv);
    CobblemonCombatZA.LOGGER.info("CONTATO: {} perdeu {} PV ({})", pokemon.getSpecies().getName(), pv,
        deOnde.getString());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable(FRASE + "feriu", pokemon.getDisplayName(false), deOnde, pv));
  }
}
