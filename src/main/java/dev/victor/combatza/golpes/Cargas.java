package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.rede.CargasPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import dev.victor.combatza.itens.ItensDeCarga;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

// 5.4b (dele: "já viu no LoL que tem habilidades que só entram em cooldown depois que usa todas as cargas? tem que ser
// assim"): os golpes de vários acertos (Projétil de Semente, Chute Duplo, Ataque de Fúria...) viram cargas. Cada aperto solta UM
// acerto, com o poder de um acerto dos jogos (Projétil de Semente: 25). O número de cargas é o de acertos dos jogos: fixo (Chute
// Duplo 2, Chute Triplo 3) ou de 2 a 5 sorteado como no Showdown (35%, 35%, 15%, 15%; com Encadeado, sempre
// 5). Depois da primeira, as cargas saem sem a fase de carregar. A recarga só começa depois da última, ou quando passam
// 4 segundos sem usar a próxima (as que sobraram se perdem)
public class Cargas {
  private static final double SEGUNDOS_PARA_A_PROXIMA = 4.0;
  private static final String LIGACAO_DE_HABILIDADE = "skilllink";
  // Showdown, "multihit: [2, 5]": 2 ou 3 acertos em 35% das vezes cada, 4 ou 5 em 15% cada
  private static final int[] SORTEIO_DE_2_A_5 = { 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 5, 5, 5 };

  private static final class Carga {
    private final Pokemon pokemon;
    private final int slot;
    private final Move golpe;
    private final int total;
    private int usadas;
    private long expiraNoTick = Long.MAX_VALUE;

    private Carga(Pokemon pokemon, int slot, Move golpe, int total) {
      this.pokemon = pokemon;
      this.slot = slot;
      this.golpe = golpe;
      this.total = total;
    }

    private boolean ehDo(Move outro) {
      return golpe.getName().equals(outro.getName());
    }
  }

  private static final Map<UUID, Carga[]> CARGAS = new HashMap<>();

  private Cargas() {
  }

  public static boolean ehDeVariosAcertos(Move golpe) {
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(golpe);
    return efeito != null && efeito.multihit() != null && efeito.multihit().get(1) > 1;
  }

  // O aperto de um golpe de vários acertos: a primeira carga sorteia quantas são; as outras gastam uma. Devolve se este
  // acerto sai sem carregar (todos menos o primeiro)
  public static boolean aoUsar(Pokemon pokemon, int slot, Move golpe) {
    if (!ehDeVariosAcertos(golpe)) {
      return false;
    }
    Carga[] doPokemon = CARGAS.computeIfAbsent(pokemon.getUuid(), uuid -> new Carga[ControleDeGolpes.MAXIMO_DE_GOLPES]);
    Carga carga = doPokemon[slot];
    boolean primeira = carga == null || !carga.ehDo(golpe);
    if (primeira) {
      carga = new Carga(pokemon, slot, golpe, sortear(pokemon, golpe));
      doPokemon[slot] = carga;
    }
    carga.usadas++;
    carga.expiraNoTick = Long.MAX_VALUE;
    CobblemonCombatZA.LOGGER.info("CARGA: {} usou {} ({}/{})", pokemon.getSpecies().getName(), golpe.getName(),
        carga.usadas, carga.total);
    return !primeira;
  }

  // O acerto de agora (1, 2, 3...): o Chute Triplo e o Pinote Triplo ficam mais fortes a cada acerto
  public static int acertoAtual(Pokemon pokemon, Move golpe) {
    Carga[] doPokemon = CARGAS.get(pokemon.getUuid());
    if (doPokemon == null) {
      return 1;
    }
    for (Carga carga : doPokemon) {
      if (carga != null && carga.ehDo(golpe)) {
        return Math.max(1, carga.usadas);
      }
    }
    return 1;
  }

  // O fim de um acerto: se ainda sobram cargas, a recarga espera, e a próxima tem 4 segundos para sair. Devolve se
  // guardou (a recarga NÃO começa agora)
  public static boolean guardarParaDepois(Execucao execucao, long agora) {
    Carga carga = daExecucao(execucao);
    if (carga == null || carga.usadas >= carga.total) {
      return false;
    }
    carga.expiraNoTick = agora + Math.round(SEGUNDOS_PARA_A_PROXIMA * SharedConstants.TICKS_PER_SECOND);
    avisarODono(carga.pokemon, carga.slot, carga.total - carga.usadas);
    return true;
  }

  // Passaram os 4 segundos, ou o Pokémon voltou para a bola: as cargas que sobraram se perdem e a recarga começa
  public static void atualizar(long agora) {
    for (Carga[] doPokemon : List.copyOf(CARGAS.values())) {
      for (Carga carga : doPokemon) {
        if (carga != null && (agora >= carga.expiraNoTick
            || (carga.expiraNoTick != Long.MAX_VALUE && carga.pokemon.getEntity() == null))) {
          CobblemonCombatZA.LOGGER.info("CARGA: {} perdeu {} carga(s) de {}", carga.pokemon.getSpecies().getName(),
              carga.total - carga.usadas, carga.golpe.getName());
          Recargas.iniciarRecarga(carga.pokemon, carga.slot, carga.golpe, agora);
        }
      }
    }
  }

  // A recarga começou (Recargas): as cargas deste golpe acabaram
  public static void esquecer(Pokemon pokemon, int slot) {
    Carga[] doPokemon = CARGAS.get(pokemon.getUuid());
    if (doPokemon == null || doPokemon[slot] == null) {
      return;
    }
    doPokemon[slot] = null;
    avisarODono(pokemon, slot, 0);
  }

  public static void limpar() {
    CARGAS.clear();
  }

  private static Carga daExecucao(Execucao execucao) {
    Carga[] doPokemon = CARGAS.get(execucao.getPokemon().getUuid());
    Carga carga = doPokemon == null ? null : doPokemon[execucao.getSlot()];
    return carga != null && carga.ehDo(execucao.getGolpe()) ? carga : null;
  }

  private static int sortear(Pokemon pokemon, Move golpe) {
    List<Integer> acertos = EfeitosDosGolpes.de(golpe).multihit();
    int minimo = acertos.get(0);
    int maximo = acertos.get(1);
    if (minimo == maximo || Habilidades.tem(pokemon, LIGACAO_DE_HABILIDADE)) {
      return maximo;
    }
    RandomSource sorte = pokemon.getEntity() != null ? pokemon.getEntity().getRandom() : RandomSource.create();
    if (minimo == 2 && maximo == 5) {
      // Lote 3 dos itens: o Dado Viciado dá 4 ou 5
      Integer doDado = ItensDeCarga.dadoViciado(pokemon, sorte);
      return doDado != null ? doDado : SORTEIO_DE_2_A_5[sorte.nextInt(SORTEIO_DE_2_A_5.length)];
    }
    return sorte.nextIntBetweenInclusive(minimo, maximo);
  }

  // A HUD do dono mostra quantas cargas sobram no losango (0 = nenhuma guardada)
  private static void avisarODono(Pokemon pokemon, int slot, int restantes) {
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (dono != null && ServerPlayNetworking.canSend(dono, CargasPayload.TIPO)) {
      ServerPlayNetworking.send(dono, new CargasPayload(pokemon.getUuid(), slot, restantes));
    }
  }
}
