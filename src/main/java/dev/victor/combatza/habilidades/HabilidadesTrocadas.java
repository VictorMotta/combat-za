package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 21/10 (habilidades, lote das formas: as que "copiam outra habilidade"). Na nossa batalha, a habilidade de um Pokémon pode
// mudar até ele voltar para a bola (ou a batalha acabar), como nos jogos; a habilidade de verdade dele não muda:
// - Traçar (Trace): ao entrar, copia a habilidade de um inimigo da luta, sorteado (espera até haver um que dê para copiar);
// - Múmia (Mummy) e Aroma Persistente (Lingering Aroma): quem bate nele com um golpe de contato fica com a mesma habilidade;
// - Espírito Errante (Wandering Spirit): quem bate nele com contato TROCA de habilidade com ele;
// - Gás Neutralizador (Neutralizing Gas, Weezing de Galar): enquanto ele está na luta, as habilidades de todos os outros da
//   luta param de funcionar (menos as que nada cala, como Disfarce, Multitipo e Troca de Postura, e quem segura o Escudo de
//   Habilidade). Saiu da luta, elas voltam.
// Como todo o mod enxerga isso: Habilidades.atual(pokemon) pergunta aqui se ele está calado pelo gás ("") e, senão, se a
// habilidade foi trocada. A troca fica no mesmo lugar das trocas dos golpes de status (TrocaDeHabilidade: Troca de
// Habilidades, Atuar, Embarque...)
public class HabilidadesTrocadas {
  private static final String TRACAR = "trace";
  private static final String MUMIA = "mummy";
  private static final String AROMA_PERSISTENTE = "lingeringaroma";
  private static final String ESPIRITO_ERRANTE = "wanderingspirit";
  private static final String GAS = "neutralizinggas";
  private static final String ESCUDO_DE_HABILIDADE = "abilityshield";
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  // Showdown, as "flags" de cada habilidade (abilities.js): notrace (o Traçar não copia), cantsuppress (nada cala nem
  // substitui: Gás, Múmia, Aroma) e failskillswap (não troca: Espírito Errante)
  private static final Set<String> NAO_COPIA = Set.of("asoneglastrier", "asonespectrier", "battlebond", "comatose",
      "commander", "disguise", "embodyaspectcornerstone", "embodyaspecthearthflame", "embodyaspectteal",
      "embodyaspectwellspring", "flowergift", "forecast", "hungerswitch", "iceface", "illusion", "imposter", "multitype",
      "neutralizinggas", "poisonpuppeteer", "powerconstruct", "powerofalchemy", "protosynthesis", "quarkdrive", "receiver",
      "rkssystem", "schooling", "shieldsdown", "stancechange", "teraformzero", "terashell", "terashift", "trace", "zenmode",
      "zerotohero");
  private static final Set<String> NADA_CALA = Set.of("asoneglastrier", "asonespectrier", "battlebond", "comatose",
      "disguise", "gulpmissile", "iceface", "multitype", "powerconstruct", "rkssystem", "schooling", "shieldsdown",
      "stancechange", "terashift", "zenmode", "zerotohero");
  private static final Set<String> NAO_TROCA = Set.of("asoneglastrier", "asonespectrier", "battlebond", "comatose",
      "commander", "disguise", "embodyaspectcornerstone", "embodyaspecthearthflame", "embodyaspectteal",
      "embodyaspectwellspring", "hungerswitch", "iceface", "illusion", "multitype", "neutralizinggas", "poisonpuppeteer",
      "powerconstruct", "protosynthesis", "quarkdrive", "rkssystem", "schooling", "shieldsdown", "stancechange",
      "teraformzero", "terashell", "terashift", "wonderguard", "zenmode", "zerotohero");

  // Quem está calado pelo Gás Neutralizador agora (conferido a cada 5 ticks)
  private static final Set<UUID> CALADOS = new HashSet<>();
  // As batalhas com o gás valendo (para avisar quando ele começa e quando acaba)
  private static final Set<Combate> COM_GAS = new HashSet<>();
  // Quem entrou com o Traçar e ainda não achou o que copiar
  private static final Set<UUID> PARA_TRACAR = new HashSet<>();

  private HabilidadesTrocadas() {
  }

  // Calado pelo Gás Neutralizador agora? (Habilidades.atual)
  public static boolean calada(Pokemon pokemon) {
    return !CALADOS.isEmpty() && CALADOS.contains(pokemon.getUuid());
  }

  // A habilidade dele sem contar o gás: a trocada (TrocaDeHabilidade) ou a de verdade
  private static String semOGas(Pokemon pokemon) {
    String trocada = TrocaDeHabilidade.trocada(pokemon);
    return trocada != null ? trocada : pokemon.getAbility().getName();
  }

  // A habilidade dele passa a ser outra até ele sair da batalha (a dele mesma = desfaz a troca)
  public static void trocar(Pokemon pokemon, String habilidade) {
    if (habilidade.equals(pokemon.getAbility().getName())) {
      TrocaDeHabilidade.esquecer(pokemon);
    } else {
      TrocaDeHabilidade.definir(pokemon, habilidade);
    }
  }

  private static Component nomeDa(String habilidade) {
    AbilityTemplate modelo = Abilities.get(habilidade);
    return Component.translatable(modelo != null ? modelo.getDisplayName() : habilidade);
  }

  // Entrou na batalha: o Traçar procura o que copiar (agora ou quando aparecer um inimigo)
  public static void aoEntrar(Pokemon pokemon) {
    if (Habilidades.tem(pokemon, TRACAR) && !ItensSegurados.tem(pokemon, ESCUDO_DE_HABILIDADE)) {
      PARA_TRACAR.add(pokemon.getUuid());
    }
  }

  // Showdown, onUpdate do Traçar: um inimigo da luta sorteado, com uma habilidade que dá para copiar. Depois, a habilidade
  // copiada "entra" como se fosse dele: a Intimidação intimida, a Seca chama o Sol (ClimaNosGolpes e CampoEBarreiras leem
  // a habilidade por Habilidades.atual)
  public static void tentarTracar(PokemonEntity entidade, Arena arena, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    if (!PARA_TRACAR.contains(pokemon.getUuid())) {
      return;
    }
    if (!Habilidades.tem(pokemon, TRACAR)) {
      PARA_TRACAR.remove(pokemon.getUuid());
      return;
    }
    List<PokemonEntity> inimigos = inimigos(entidade, arena);
    List<PokemonEntity> copiaveis = new ArrayList<>();
    for (PokemonEntity inimigo : inimigos) {
      String dele = Habilidades.atual(inimigo.getPokemon());
      if (!dele.isEmpty() && !NAO_COPIA.contains(dele)) {
        copiaveis.add(inimigo);
      }
    }
    if (copiaveis.isEmpty()) {
      return;
    }
    PARA_TRACAR.remove(pokemon.getUuid());
    Pokemon copiado = copiaveis.get(entidade.getRandom().nextInt(copiaveis.size())).getPokemon();
    String habilidade = Habilidades.atual(copiado);
    trocar(pokemon, habilidade);
    HabilidadesDeForma.avisar(pokemon, Component.translatable("cobblemon.battle.ability.trace",
        pokemon.getDisplayName(false), copiado.getDisplayName(false), nomeDa(habilidade)));
    Intimidacao.aoEntrar(entidade, inimigos);
    ClimaNosGolpes.aoEntrar(arena, entidade, agora);
    CampoEBarreiras.aoEntrar(arena, entidade, agora);
  }

  private static List<PokemonEntity> inimigos(PokemonEntity entidade, Arena arena) {
    List<PokemonEntity> inimigos = new ArrayList<>();
    for (PokemonEntity outro : arena.pokemons()) {
      if (outro.isAlive() && !outro.getPokemon().isFainted()
          && !Lados.mesmoLado(outro.getPokemon(), entidade.getPokemon())) {
        inimigos.add(outro);
      }
    }
    return inimigos;
  }

  // Showdown, onDamagingHit da Múmia, do Aroma Persistente e do Espírito Errante: um golpe de contato que tirou vida
  public static void depoisDoContato(Execucao execucao, PokemonEntity atacante, Entity alvo, double pvTirados) {
    if (pvTirados <= 0 || !(alvo instanceof PokemonEntity atingido) || atingido == atacante
        || !ItensSegurados.fazContato(execucao.getPokemon(), execucao.getGolpe())
        || !GerenciarCombates.estaEmCombate(atingido.getPokemon())) {
      return;
    }
    Pokemon defensor = atingido.getPokemon();
    Pokemon quemBateu = atacante.getPokemon();
    String dele = Habilidades.atual(defensor);
    String doAtacante = semOGas(quemBateu);
    if ((MUMIA.equals(dele) || AROMA_PERSISTENTE.equals(dele)) && !NADA_CALA.contains(doAtacante)
        && !dele.equals(doAtacante)) {
      trocar(quemBateu, dele);
      Component frase = MUMIA.equals(dele)
          ? Component.translatable("cobblemon.battle.activate.mummy", quemBateu.getDisplayName(false))
          : Component.translatable("cobblemon.battle.activate.lingeringaroma", defensor.getDisplayName(false),
              quemBateu.getDisplayName(false));
      HabilidadesDeForma.avisar(quemBateu, frase);
    } else if (ESPIRITO_ERRANTE.equals(dele) && !NAO_TROCA.contains(doAtacante) && !dele.equals(doAtacante)) {
      trocar(quemBateu, ESPIRITO_ERRANTE);
      trocar(defensor, doAtacante);
      HabilidadesDeForma.avisar(defensor, Component.translatable(FRASE + "espirito_errante",
          defensor.getDisplayName(false), quemBateu.getDisplayName(false)));
    }
  }

  // A cada 5 ticks, em cada batalha: quem tem o Gás Neutralizador na luta cala os outros da luta
  public static void conferirOGas(Combate combate, List<PokemonEntity> naLuta) {
    boolean temGas = false;
    for (PokemonEntity entidade : naLuta) {
      if (entidade.isAlive() && GAS.equals(semOGas(entidade.getPokemon()))) {
        temGas = true;
      }
    }
    for (PokemonEntity entidade : naLuta) {
      Pokemon pokemon = entidade.getPokemon();
      String dele = semOGas(pokemon);
      boolean calado = temGas && !GAS.equals(dele) && !NADA_CALA.contains(dele)
          && !ItensSegurados.tem(pokemon, ESCUDO_DE_HABILIDADE);
      if (calado) {
        CALADOS.add(pokemon.getUuid());
      } else {
        CALADOS.remove(pokemon.getUuid());
      }
    }
    if (temGas && COM_GAS.add(combate)) {
      CobblemonCombatZA.LOGGER.info("GÁS NEUTRALIZADOR: as habilidades da batalha estão caladas");
      avisarABatalha(combate, Component.translatable(FRASE + "gas"));
    } else if (!temGas && COM_GAS.remove(combate)) {
      CobblemonCombatZA.LOGGER.info("GÁS NEUTRALIZADOR: acabou");
      avisarABatalha(combate, Component.translatable("cobblemon.battle.end.neutralizinggas"));
    }
  }

  private static void avisarABatalha(Combate combate, Component frase) {
    combate.getJogadores().forEach(jogador -> Feedback.historicoDaBatalha(jogador, null, frase));
  }

  // Saiu da luta (voltou para a bola, desmaiou, a batalha acabou): a habilidade dele volta, e o gás não cala mais
  public static void esquecer(Pokemon pokemon) {
    TrocaDeHabilidade.esquecer(pokemon);
    CALADOS.remove(pokemon.getUuid());
    PARA_TRACAR.remove(pokemon.getUuid());
  }

  // A cada segundo: as batalhas que acabaram e quem não está mais em nenhuma
  public static void varrer() {
    COM_GAS.retainAll(GerenciarCombates.todos());
  }

  public static void limpar() {
    CALADOS.clear();
    COM_GAS.clear();
    PARA_TRACAR.clear();
  }
}
