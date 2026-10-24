package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 21/10 (golpes de status que faltavam): o BIS (Encore). Como nos jogos (Showdown, moves.js), por 3 turnos (10,5 s) o alvo
// fica preso no ÚLTIMO golpe que usou desde que saiu da bola: os outros três ficam riscados no losango (GolpesDesligados) e
// a IA só usa aquele. Falha em quem ainda não usou golpe, em quem já está no Bis e quando o último golpe é dos que não
// repetem (a marca "failencore": o próprio Bis, Mímica, Metrônomo, Sonâmbulo...). O Véu Aromático segura; a Erva Mental cura.
// Se o golpe preso estiver em recarga, ele espera (como nos jogos, ele só pode repetir aquele)
public class Bis {
  private static final int TURNOS = 3;
  private static final String NAO_REPETE = "failencore";

  private record NoBis(Pokemon pokemon, int slot, long acabaNoTick) {
  }

  // O último golpe (o lugar dele, 0 a 3) que um Pokémon usou desde que saiu da bola
  private record Ultimo(Pokemon pokemon, int slot) {
  }

  private static final Map<UUID, NoBis> NO_BIS = new HashMap<>();
  private static final Map<UUID, Ultimo> ULTIMO = new HashMap<>();

  private Bis() {
  }

  // ControleDeGolpes: usou um golpe (o seu ou o da IA)
  public static void usou(Pokemon pokemon, int slot) {
    ULTIMO.put(pokemon.getUuid(), new Ultimo(pokemon, slot));
  }

  public static boolean noBis(Pokemon pokemon) {
    return NO_BIS.containsKey(pokemon.getUuid());
  }

  // O golpe está desligado pelo Bis? (todos menos o preso)
  public static boolean bloqueia(Pokemon pokemon, int slot) {
    NoBis bis = NO_BIS.get(pokemon.getUuid());
    return bis != null && bis.slot() != slot;
  }

  // O golpe em que ele está preso (null = sem Bis), para a frase do golpe recusado
  public static Move golpePreso(Pokemon pokemon) {
    NoBis bis = NO_BIS.get(pokemon.getUuid());
    return bis == null ? null : pokemon.getMoveSet().get(bis.slot());
  }

  // Pega? (sem avisar: a IA pergunta antes de escolher)
  public static boolean podePegar(Pokemon alvo) {
    return !alvo.isFainted() && !noBis(alvo) && ultimoQueRepete(alvo) >= 0
        && GolpesDeStatus.quemProtegeDaMente(alvo, false) == null;
  }

  // O lugar do último golpe, se ele pode repetir (−1 = nenhum)
  private static int ultimoQueRepete(Pokemon pokemon) {
    Ultimo ultimo = ULTIMO.get(pokemon.getUuid());
    Move golpe = ultimo == null ? null : pokemon.getMoveSet().get(ultimo.slot());
    return golpe == null || EfeitosDosGolpes.temMarca(golpe, NAO_REPETE) ? -1 : ultimo.slot();
  }

  public static void tentar(Execucao execucao, Entity alvo) {
    if (!(alvo instanceof PokemonEntity entidade) || entidade.getPokemon().isFainted()) {
      return;
    }
    Pokemon pokemon = entidade.getPokemon();
    int slot = ultimoQueRepete(pokemon);
    if (noBis(pokemon) || slot < 0) {
      CobblemonCombatZA.LOGGER.info("BIS: falhou em {} ({})", pokemon.getSpecies().getName(),
          noBis(pokemon) ? "já está no Bis" : "sem último golpe que repete");
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
      return;
    }
    if (GolpesDeStatus.protegidoDaMente(pokemon, false)) {
      return;
    }
    long agora = execucao.getJogador().server.getTickCount();
    NO_BIS.put(pokemon.getUuid(), new NoBis(pokemon, slot, agora + TURNOS * EfeitosDeStatus.ticksPorTurno()));
    CobblemonCombatZA.LOGGER.info("BIS: {} preso em {} por {} turnos", pokemon.getSpecies().getName(),
        pokemon.getMoveSet().get(slot) == null ? "?" : pokemon.getMoveSet().get(slot).getName(), TURNOS);
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.start.encore", pokemon.getDisplayName(false)));
    GolpesDesligados.avisarODono(pokemon);
    GolpesDeStatus.ervaMental(pokemon);
  }

  public static void atualizar(long agora) {
    for (NoBis bis : List.copyOf(NO_BIS.values())) {
      // O tempo acabou, ou ele não tem mais aquele golpe (trocou de golpes): o Bis acaba
      if (agora >= bis.acabaNoTick() || bis.pokemon().getMoveSet().get(bis.slot()) == null) {
        acabar(bis.pokemon(), true);
      }
    }
  }

  static void semBatalha() {
    for (NoBis bis : List.copyOf(NO_BIS.values())) {
      if (!GerenciarCombates.estaEmCombate(bis.pokemon())) {
        acabar(bis.pokemon(), false);
      }
    }
    // Quem não está mais fora da bola (o selvagem capturado ou que sumiu) não precisa do último golpe guardado
    ULTIMO.values().removeIf(ultimo -> ultimo.pokemon().getEntity() == null);
  }

  public static void acabar(Pokemon pokemon, boolean comFrase) {
    if (NO_BIS.remove(pokemon.getUuid()) == null) {
      return;
    }
    CobblemonCombatZA.LOGGER.info("BIS: acabou para {}", pokemon.getSpecies().getName());
    if (comFrase) {
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.end.encore", pokemon.getDisplayName(false)));
    }
    GolpesDesligados.avisarODono(pokemon);
  }

  // Voltou para a bola ou desmaiou: o "último golpe" começa do zero. (O começo da batalha não apaga: o golpe que começou a
  // batalha conta como o último)
  static void esquecerOUltimo(Pokemon pokemon) {
    ULTIMO.remove(pokemon.getUuid());
  }

  public static void limpar() {
    NO_BIS.clear();
    ULTIMO.clear();
  }
}
