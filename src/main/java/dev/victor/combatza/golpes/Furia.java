package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

// 5.4d (dele: "como nos jogos"): Ultraje, Castigar, Dança das Pétalas e Ira Furiosa. Um aperto e o Pokémon perde o
// controle: ataca sozinho de 2 a 3 vezes seguidas (sorteado, como no Showdown) o mesmo alvo, cada vez com o dano cheio, e
// nesse tempo os outros golpes não saem. No fim, fica confuso de cansaço (a confusão da 4.6c; o Ritmo Próprio protege). Se
// o alvo cai ou o Pokémon volta para a bola no meio, a fúria acaba sem confusão. A recarga começa depois da última vez
public class Furia {
  private static final int VEZES_MINIMO = 2;
  private static final int VEZES_MAXIMO = 3;
  private static final int SEM_ALVO = -1;

  // Quantas vezes ainda faltam, depois da que está saindo agora
  private static final Map<UUID, Integer> FALTAM = new HashMap<>();

  private Furia() {
  }

  public static boolean ehDeFuria(Move golpe) {
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(golpe);
    return efeito != null && efeito.lockedmove();
  }

  public static boolean emFuria(Pokemon pokemon) {
    return FALTAM.containsKey(pokemon.getUuid());
  }

  // O fim de uma vez (AgendaDeGolpes): a próxima sai sozinha. Devolve se continuou (a recarga espera)
  public static boolean continuar(Execucao execucao, long agora) {
    if (!ehDeFuria(execucao.getGolpe())) {
      return false;
    }
    Pokemon pokemon = execucao.getPokemon();
    Integer guardado = FALTAM.remove(pokemon.getUuid());
    int faltam = guardado != null ? guardado
        : execucao.getEntidade().getRandom().nextIntBetweenInclusive(VEZES_MINIMO, VEZES_MAXIMO) - 1;
    if (faltam <= 0) {
      cansou(execucao);
      return false;
    }
    PokemonEntity entidade = pokemon.getEntity();
    Entity alvo = execucao.getMirada() instanceof EntityHitResult acerto ? acerto.getEntity() : null;
    if (entidade == null || entidade != execucao.getEntidade() || !entidade.isAlive()
        || (alvo != null && !alvo.isAlive())) {
      CobblemonCombatZA.LOGGER.info("FÚRIA: {} parou no meio (o alvo caiu ou ele voltou para a bola)",
          pokemon.getSpecies().getName());
      return false;
    }
    if (execucao.ehDaIa() && alvo == null) {
      return false;
    }
    FALTAM.put(pokemon.getUuid(), faltam - 1);
    // Mirando no chão, a próxima vai para onde o jogador olha agora. A da IA (o selvagem, o guarda), no mesmo alvo
    Execucao proxima = execucao.ehDaIa()
        ? AgendaDeGolpes.iniciarDaIa(execucao.getJogador(), pokemon, execucao.getGolpe(), execucao.getSlot(), agora, alvo,
            false)
        : AgendaDeGolpes.iniciar(execucao.getJogador(), pokemon, execucao.getGolpe(), execucao.getSlot(), agora,
            alvo != null ? alvo.getId() : SEM_ALVO, false);
    VisualDosGolpes.aoCarregar(proxima);
    if (!Corrida.precisaCorrer(proxima)) {
      VisualDosGolpes.irAteOAlvo(proxima);
    }
    CobblemonCombatZA.LOGGER.info("FÚRIA: {} ataca de novo com {} (faltam {} depois desta)",
        pokemon.getSpecies().getName(), execucao.getGolpe().getName(), faltam - 1);
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon_combat_za.status.continua_em_furia", pokemon.getDisplayName(false)));
    return true;
  }

  // A recarga começou (Recargas): a fúria acabou, mesmo cortada no meio (o golpe cancelado durante a carga)
  public static void esquecer(Pokemon pokemon) {
    FALTAM.remove(pokemon.getUuid());
  }

  public static void limpar() {
    FALTAM.clear();
  }

  private static void cansou(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade == null || entidade != execucao.getEntidade() || !entidade.isAlive()) {
      return;
    }
    CobblemonCombatZA.LOGGER.info("FÚRIA: {} se cansou de {}", pokemon.getSpecies().getName(),
        execucao.getGolpe().getName());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon_combat_za.status.cansou_da_furia", pokemon.getDisplayName(false)));
    Confusao.tentar(execucao, entidade);
  }
}
