package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.network.chat.Component;

// 21/10 (habilidades, lote das reações): a CONTAGEM DO PERECER (a da Canção do Perecer dos jogos), que o Corpo Perecido
// começa: quem leva a contagem desmaia depois de 3 turnos (no tempo real, 3 × 3,5 s = 10,5 s). Começa em 3 e cai 1 a cada
// turno, com a frase do Cobblemon ("A contagem da Canção do Perecer de X está em 2!"); no 0, desmaia. Voltar para a bola
// acaba com a contagem (como trocar nos jogos), e sair da batalha também. O golpe Canção do Perecer ainda não existe no mod:
// quando existir, é só chamar comecar() em cada Pokémon da batalha
public class ContagemDoPerecer {
  private static final int TURNOS = 3;
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  private static final class Contagem {
    private final PokemonEntity entidade;
    private int restam = TURNOS;
    private long proximoTick;

    private Contagem(PokemonEntity entidade, long proximoTick) {
      this.entidade = entidade;
      this.proximoTick = proximoTick;
    }
  }

  private static final Map<UUID, Contagem> CONTAGENS = new HashMap<>();

  private ContagemDoPerecer() {
  }

  // Ele já está contando? (a contagem é da entidade: voltou para a bola, acabou)
  public static boolean contando(Pokemon pokemon) {
    Contagem contagem = CONTAGENS.get(pokemon.getUuid());
    return contagem != null && contagem.entidade == pokemon.getEntity();
  }

  // Showdown, Corpo Perecido (onDamagingHit com contato): o dono e quem encostou começam a contar (quem já conta, não de
  // novo)
  public static void corpoPerecido(PokemonEntity dono, PokemonEntity quemBateu, Component deOnde) {
    boolean anunciou = false;
    for (PokemonEntity entidade : List.of(dono, quemBateu)) {
      if (contando(entidade.getPokemon()) || !HabilidadesQueReagem.dePe(entidade)) {
        continue;
      }
      if (!anunciou) {
        HabilidadesQueReagem.avisar(dono.getPokemon(), Component.translatable(FRASE + "corpo_perecido", deOnde));
        anunciou = true;
      }
      comecar(entidade);
    }
  }

  public static void comecar(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    long agora = entidade.getServer() == null ? 0 : entidade.getServer().getTickCount();
    CONTAGENS.put(pokemon.getUuid(), new Contagem(entidade, agora + EfeitosDeStatus.ticksPorTurno()));
    avisarAContagem(pokemon, TURNOS);
    CobblemonCombatZA.LOGGER.info("PERECER: {} desmaia em {} turnos", pokemon.getSpecies().getName(), TURNOS);
  }

  // A cada tick (HabilidadesDoTurno): a cada turno a contagem cai 1; no 0, ele desmaia
  public static void atualizar(long agora) {
    List<PokemonEntity> desmaiam = new ArrayList<>();
    Iterator<Contagem> contagens = CONTAGENS.values().iterator();
    while (contagens.hasNext()) {
      Contagem contagem = contagens.next();
      PokemonEntity entidade = contagem.entidade;
      Pokemon pokemon = entidade.getPokemon();
      if (pokemon.getEntity() != entidade || !HabilidadesQueReagem.dePe(entidade)
          || !GerenciarCombates.estaEmCombate(pokemon)) {
        contagens.remove();
        continue;
      }
      if (agora < contagem.proximoTick) {
        continue;
      }
      contagem.restam--;
      contagem.proximoTick += EfeitosDeStatus.ticksPorTurno();
      avisarAContagem(pokemon, contagem.restam);
      if (contagem.restam <= 0) {
        contagens.remove();
        desmaiam.add(entidade);
      }
    }
    // Depois de andar pela lista: o desmaio dispara os avisos do Cobblemon e do mod
    for (PokemonEntity entidade : desmaiam) {
      Pokemon pokemon = entidade.getPokemon();
      CobblemonCombatZA.LOGGER.info("PERECER: {} desmaiou (a contagem chegou a 0)", pokemon.getSpecies().getName());
      HabilidadesQueReagem.ferir(entidade, Vida.pvAtual(pokemon));
    }
  }

  private static void avisarAContagem(Pokemon pokemon, int restam) {
    HabilidadesQueReagem.avisar(pokemon, Component.translatable("cobblemon.battle.start.perish",
        pokemon.getDisplayName(false), restam));
  }

  public static void esquecer(Pokemon pokemon) {
    CONTAGENS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    CONTAGENS.clear();
  }
}
