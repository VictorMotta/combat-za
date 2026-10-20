package dev.victor.combatza.captura;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Feedback;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// Etapa 8, o selvagem IRRITADO (especificado por ele na aula 2.5a, 10/10: "agressivo ≠ irritado"; feito em 20/10): a Pokébola
// FALHOU num selvagem → ele fica irritado, com um ícone em cima, e não dá para capturar (a bola falha na hora e volta para
// você). Para acalmar: os seus Pokémon tiram dele 15% da vida máxima desde que ele se irritou; as naturezas bravas (Brava,
// Firme, Travessa) pedem ×1,5, as calmas (Calma, Gentil, Tímida) ×0,75. Ou passa sozinho em 60 s
public class Irritados {
  private static final double PARTE_DA_VIDA = 0.15;
  private static final double BRAVA = 1.5;
  private static final double CALMA = 0.75;
  private static final Set<String> BRAVAS = Set.of("brave", "adamant", "naughty");
  private static final Set<String> CALMAS = Set.of("calm", "gentle", "timid");
  private static final int SEGUNDOS = 60;
  private static final String FRASE = "cobblemon_combat_za.captura.";

  private static final class Irritado {
    private double falta;
    private final long ate;

    private Irritado(double falta, long ate) {
      this.falta = falta;
      this.ate = ate;
    }
  }

  // Pelo UUID do Pokémon
  private static final Map<UUID, Irritado> IRRITADOS = new HashMap<>();

  private Irritados() {
  }

  public static void irritar(PokemonEntity selvagem, ServerPlayer quemJogou, long agora) {
    Pokemon pokemon = selvagem.getPokemon();
    String natureza = pokemon.getNature().getName().getPath();
    double multiplicador = BRAVAS.contains(natureza) ? BRAVA : CALMAS.contains(natureza) ? CALMA : 1.0;
    double falta = pokemon.getMaxHealth() * PARTE_DA_VIDA * multiplicador;
    IRRITADOS.put(pokemon.getUuid(), new Irritado(falta, agora + SEGUNDOS * (long) SharedConstants.TICKS_PER_SECOND));
    CobblemonCombatZA.LOGGER.info("IRRITADO: {} ({}) ficou irritado; acalma com {} PV de dano", pokemon.getSpecies().getName(),
        natureza, String.format("%.1f", falta));
    avisar(quemJogou, pokemon, Component.translatable(FRASE + "irritado", pokemon.getDisplayName(false)));
  }

  public static boolean esta(Pokemon pokemon) {
    return IRRITADOS.containsKey(pokemon.getUuid());
  }

  // Um golpe de um Pokémon seu tirou "pvTirados" do selvagem (ControleDeGolpes.acertar)
  public static void acertou(ServerPlayer quemBateu, Entity alvo, double pvTirados) {
    if (pvTirados <= 0 || !(alvo instanceof PokemonEntity selvagem)) {
      return;
    }
    Pokemon pokemon = selvagem.getPokemon();
    Irritado irritado = IRRITADOS.get(pokemon.getUuid());
    if (irritado == null) {
      return;
    }
    irritado.falta -= pvTirados;
    if (irritado.falta <= 0) {
      IRRITADOS.remove(pokemon.getUuid());
      CobblemonCombatZA.LOGGER.info("IRRITADO: {} se acalmou", pokemon.getSpecies().getName());
      avisar(quemBateu, pokemon, Component.translatable(FRASE + "acalmou", pokemon.getDisplayName(false)));
    }
  }

  // Passou o tempo: acalma sozinho
  public static void atualizar(long agora) {
    IRRITADOS.values().removeIf(irritado -> agora >= irritado.ate);
  }

  // Na batalha, no histórico; fora dela, na barra de ação
  public static void avisar(ServerPlayer jogador, Pokemon pokemon, Component frase) {
    if (jogador == null) {
      return;
    }
    if (GerenciarCombates.estaEmCombate(jogador)) {
      Feedback.historico(jogador, pokemon, frase);
    } else {
      Feedback.barraDeAcao(jogador, frase);
    }
  }

  public static void esquecer(Pokemon pokemon) {
    IRRITADOS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    IRRITADOS.clear();
  }
}
