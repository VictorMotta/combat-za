package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 21/10 (golpes de status que faltavam): a PROVOCAÇÃO (Taunt). Como nos jogos (Showdown, moves.js), por 3 turnos (10,5 s)
// o provocado não usa golpes de status: eles ficam riscados no losango (GolpesDesligados) e a IA nem tenta. Falha em quem já
// está provocado; a Insensível e o Véu Aromático seguram; a Erva Mental cura na hora (GolpesDeStatus). O golpe de status que
// ele já estava carregando quando foi provocado sai normalmente (no tempo real não existe o "antes de agir" do turno)
public class Provocacao {
  private static final int TURNOS = 3;
  private static final String EU_PRIMEIRO = "mefirst";
  private static final String INSENSIVEL = "oblivious";

  private record Provocado(Pokemon pokemon, long acabaNoTick) {
  }

  private static final Map<UUID, Provocado> PROVOCADOS = new HashMap<>();

  private Provocacao() {
  }

  public static boolean provocado(Pokemon pokemon) {
    return PROVOCADOS.containsKey(pokemon.getUuid());
  }

  // O golpe está desligado pela Provocação? (todos os de status, menos o Eu Primeiro, como no Showdown)
  public static boolean bloqueia(Pokemon pokemon, Move golpe) {
    return provocado(pokemon) && golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()
        && !EU_PRIMEIRO.equals(golpe.getName());
  }

  // Pega? (sem avisar: a IA pergunta antes de escolher)
  public static boolean podePegar(Pokemon alvo) {
    return !alvo.isFainted() && !provocado(alvo) && GolpesDeStatus.quemProtegeDaMente(alvo, true) == null;
  }

  // A IA só provoca quem tem golpe de status para perder
  public static boolean convemContra(Pokemon alvo) {
    if (!podePegar(alvo)) {
      return false;
    }
    for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
      Move golpe = alvo.getMoveSet().get(slot);
      if (golpe != null && golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) {
        return true;
      }
    }
    return false;
  }

  public static void tentar(Execucao execucao, Entity alvo) {
    if (!(alvo instanceof PokemonEntity entidade) || entidade.getPokemon().isFainted()) {
      return;
    }
    Pokemon pokemon = entidade.getPokemon();
    if (provocado(pokemon)) {
      CobblemonCombatZA.LOGGER.info("PROVOCAÇÃO: {} já está provocado", pokemon.getSpecies().getName());
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
      return;
    }
    if (GolpesDeStatus.protegidoDaMente(pokemon, true)) {
      return;
    }
    long agora = execucao.getJogador().server.getTickCount();
    PROVOCADOS.put(pokemon.getUuid(), new Provocado(pokemon, agora + TURNOS * EfeitosDeStatus.ticksPorTurno()));
    CobblemonCombatZA.LOGGER.info("PROVOCAÇÃO: {} sem golpes de status por {} turnos", pokemon.getSpecies().getName(),
        TURNOS);
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.start.taunt", pokemon.getDisplayName(false)));
    GolpesDesligados.avisarODono(pokemon);
    GolpesDeStatus.ervaMental(pokemon);
  }

  public static void atualizar(long agora) {
    for (Provocado provocado : List.copyOf(PROVOCADOS.values())) {
      // Ganhou a Insensível no meio (a Troca de Habilidades): a provocação acaba, como no Showdown
      if (agora >= provocado.acabaNoTick() || Habilidades.tem(provocado.pokemon(), INSENSIVEL)) {
        acabar(provocado.pokemon(), true);
      }
    }
  }

  // Quem saiu da batalha deixa de estar provocado
  static void semBatalha() {
    for (Provocado provocado : List.copyOf(PROVOCADOS.values())) {
      if (!GerenciarCombates.estaEmCombate(provocado.pokemon())) {
        acabar(provocado.pokemon(), false);
      }
    }
  }

  // "comFrase": o fim natural avisa no histórico ("se livrou da provocação"); a volta para a bola, não
  public static void acabar(Pokemon pokemon, boolean comFrase) {
    if (PROVOCADOS.remove(pokemon.getUuid()) == null) {
      return;
    }
    CobblemonCombatZA.LOGGER.info("PROVOCAÇÃO: acabou para {}", pokemon.getSpecies().getName());
    if (comFrase) {
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.end.taunt", pokemon.getDisplayName(false)));
    }
    GolpesDesligados.avisarODono(pokemon);
  }

  public static void limpar() {
    PROVOCADOS.clear();
  }
}
