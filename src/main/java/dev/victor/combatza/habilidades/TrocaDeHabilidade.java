package dev.victor.combatza.habilidades;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.GolpesDeStatus;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

// 21/10 (golpes de status que faltavam): a habilidade TROCADA durante a batalha. Troca de Habilidades (Skill Swap) troca a
// dos dois; Atuar (Role Play) copia a do alvo; Embarque (Entrainment) passa a sua para o alvo; Raio Simples (Simple Beam)
// deixa o alvo com Simplificar; Semente de Preocupação (Worry Seed) deixa o alvo com Insônia (e acorda quem dorme); Ácido
// Gástrico (Gastro Acid) apaga a habilidade do alvo. Como nos jogos (Showdown, moves.js), vale até ele voltar para a bola, desmaiar ou
// sair da batalha: a habilidade de verdade (a do Cobblemon, que aparece no resumo) nunca muda.
// Quem lê a habilidade na batalha passa por Habilidades.atual (o Habilidades.tem já passa). "definir" é público para outras
// cópias de habilidade (Rastrear, Múmia) usarem o mesmo lugar
public class TrocaDeHabilidade {
  // A habilidade apagada pelo Ácido Gástrico: nenhuma habilidade bate com "" (Habilidades.tem dá sempre false)
  public static final String SUPRIMIDA = "";
  private static final String FRASE = "cobblemon_combat_za.habilidade.";
  private static final String SIMPLIFICAR = "simple";
  private static final String INSONIA = "insomnia";
  private static final String PREGUICA = "truant";
  private static final String ESCUDO_DE_HABILIDADE = "abilityshield";
  private static final int CONFERIR_A_CADA_TICKS = 20;
  // Showdown, abilities.js: as habilidades que não saem do lugar (a flag "cantsuppress"): não podem ser apagadas nem trocadas
  private static final Set<String> NAO_SAEM = Set.of("asoneglastrier", "asonespectrier", "battlebond", "comatose",
      "disguise", "gulpmissile", "iceface", "multitype", "powerconstruct", "rkssystem", "schooling", "shieldsdown",
      "stancechange", "terashift", "zenmode", "zerotohero");
  // A flag "failskillswap" (a Troca de Habilidades falha se um dos dois tem uma destas)
  private static final Set<String> NAO_TROCAM = Set.of("asoneglastrier", "asonespectrier", "battlebond", "comatose",
      "commander", "disguise", "embodyaspectcornerstone", "embodyaspecthearthflame", "embodyaspectteal",
      "embodyaspectwellspring", "hungerswitch", "iceface", "illusion", "multitype", "neutralizinggas", "poisonpuppeteer",
      "powerconstruct", "protosynthesis", "quarkdrive", "rkssystem", "schooling", "shieldsdown", "stancechange",
      "teraformzero", "terashell", "terashift", "wonderguard", "zenmode", "zerotohero");
  // As flags "failroleplay" e "noentrain" têm a mesma lista: não dá para copiar (Atuar) nem passar adiante (Embarque)
  private static final Set<String> NAO_COPIAM = Set.of("asoneglastrier", "asonespectrier", "battlebond", "comatose",
      "commander", "disguise", "embodyaspectcornerstone", "embodyaspecthearthflame", "embodyaspectteal",
      "embodyaspectwellspring", "flowergift", "forecast", "hungerswitch", "iceface", "illusion", "imposter", "multitype",
      "neutralizinggas", "poisonpuppeteer", "powerconstruct", "powerofalchemy", "protosynthesis", "quarkdrive", "receiver",
      "rkssystem", "schooling", "shieldsdown", "stancechange", "teraformzero", "terashell", "terashift", "trace",
      "wonderguard", "zenmode", "zerotohero");

  private record Trocada(Pokemon pokemon, String habilidade) {
  }

  private static final Map<UUID, Trocada> TROCADAS = new HashMap<>();

  private TrocaDeHabilidade() {
  }

  // A habilidade que vale agora na batalha; null = a de sempre (a do Cobblemon)
  public static String trocada(Pokemon pokemon) {
    if (TROCADAS.isEmpty()) {
      return null;
    }
    Trocada trocada = TROCADAS.get(pokemon.getUuid());
    return trocada == null ? null : trocada.habilidade();
  }

  public static void definir(Pokemon pokemon, String habilidade) {
    TROCADAS.put(pokemon.getUuid(), new Trocada(pokemon, habilidade));
    CobblemonCombatZA.LOGGER.info("HABILIDADE TROCADA: {} agora tem {} (a de verdade: {})", pokemon.getSpecies().getName(),
        habilidade.isEmpty() ? "nenhuma" : habilidade, pokemon.getAbility().getName());
  }

  // O nome da habilidade, como aparece no jogo ("" = sem habilidade, pelo Ácido Gástrico)
  public static MutableComponent nomeDe(String habilidade) {
    if (habilidade.isEmpty()) {
      return Component.translatable(FRASE + "nenhuma");
    }
    AbilityTemplate modelo = Abilities.get(habilidade);
    return modelo == null ? Component.literal(habilidade) : Component.translatable(modelo.getDisplayName());
  }

  // Depois do acerto do golpe (GolpesDeStatus): quem usou e quem recebeu
  public static void peloGolpe(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity atingido) || atingido.getPokemon().isFainted()) {
      return;
    }
    Pokemon quemUsou = atacante.getPokemon();
    Pokemon doAlvo = atingido.getPokemon();
    String golpe = execucao.getGolpe().getName();
    if (!podePegar(golpe, quemUsou, doAlvo)) {
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} de {} em {} falhou", golpe, quemUsou.getSpecies().getName(),
          doAlvo.getSpecies().getName());
      GolpesDeStatus.avisar(doAlvo, Component.translatable("cobblemon_combat_za.golpe.falhou"));
      return;
    }
    if (escudo(golpe, quemUsou, doAlvo)) {
      return;
    }
    String dele = Habilidades.atual(quemUsou);
    String doOutro = Habilidades.atual(doAlvo);
    switch (golpe) {
      case "skillswap" -> {
        definir(quemUsou, doOutro);
        definir(doAlvo, dele);
        GolpesDeStatus.avisar(quemUsou, Component.translatable(FRASE + "trocou", quemUsou.getDisplayName(false),
            doAlvo.getDisplayName(false), nomeDe(doOutro), nomeDe(dele)));
      }
      case "roleplay" -> virar(quemUsou, doOutro);
      case "entrainment" -> virar(doAlvo, dele);
      case "simplebeam" -> virar(doAlvo, SIMPLIFICAR);
      case "worryseed" -> {
        virar(doAlvo, INSONIA);
        acordar(doAlvo);
      }
      case "gastroacid" -> {
        definir(doAlvo, SUPRIMIDA);
        GolpesDeStatus.avisar(doAlvo, Component.translatable("cobblemon.battle.endability", doAlvo.getDisplayName(false)));
      }
      default -> {
        // não é golpe de habilidade
      }
    }
  }

  // Showdown (onTryHit de cada golpe): quando o golpe falha. Também para a IA não gastar a vez (EscolhaDoGolpe)
  public static boolean podePegar(String golpe, Pokemon quemUsou, Pokemon doAlvo) {
    String dele = Habilidades.atual(quemUsou);
    String doOutro = Habilidades.atual(doAlvo);
    return switch (golpe) {
      case "skillswap" -> !dele.isEmpty() && !doOutro.isEmpty() && !NAO_TROCAM.contains(dele)
          && !NAO_TROCAM.contains(doOutro);
      case "roleplay" -> !doOutro.isEmpty() && !doOutro.equals(dele) && !NAO_COPIAM.contains(doOutro)
          && !NAO_SAEM.contains(dele);
      case "entrainment" -> quemUsou != doAlvo && !dele.isEmpty() && !dele.equals(doOutro) && !NAO_SAEM.contains(doOutro)
          && !PREGUICA.equals(doOutro) && !NAO_COPIAM.contains(dele);
      case "simplebeam" -> !NAO_SAEM.contains(doOutro) && !SIMPLIFICAR.equals(doOutro) && !PREGUICA.equals(doOutro);
      case "worryseed" -> !NAO_SAEM.contains(doOutro) && !INSONIA.equals(doOutro) && !PREGUICA.equals(doOutro);
      case "gastroacid" -> !doOutro.isEmpty() && !NAO_SAEM.contains(doOutro);
      default -> false;
    };
  }

  // O Escudo de Habilidade: a habilidade de quem segura não muda (avisa e devolve true)
  private static boolean escudo(String golpe, Pokemon quemUsou, Pokemon doAlvo) {
    List<Pokemon> mudam = switch (golpe) {
      case "skillswap" -> List.of(quemUsou, doAlvo);
      case "roleplay" -> List.of(quemUsou);
      default -> List.of(doAlvo);
    };
    for (Pokemon pokemon : mudam) {
      if (ItensSegurados.tem(pokemon, ESCUDO_DE_HABILIDADE)) {
        CobblemonCombatZA.LOGGER.info("HABILIDADE: {} segurou a habilidade com o Escudo de Habilidade",
            pokemon.getSpecies().getName());
        GolpesDeStatus.avisar(pokemon, pokemon.getDisplayName(false).copy().append(" ").append(Component.translatable(
            "cobblemon_combat_za.status.nao_e_afetado_por", ItensSegurados.nome(pokemon))));
        return true;
      }
    }
    return false;
  }

  private static void virar(Pokemon pokemon, String habilidade) {
    definir(pokemon, habilidade);
    GolpesDeStatus.avisar(pokemon, Component.translatable(FRASE + "virou", pokemon.getDisplayName(false),
        nomeDe(habilidade)));
  }

  // A Semente de Preocupação acorda quem dorme (a Insônia não deixa dormir); o sono nosso solta sozinho (SonoECongelado)
  private static void acordar(Pokemon pokemon) {
    PersistentStatusContainer status = pokemon.getStatus();
    if (status == null || !"slp".equals(EfeitosDeStatus.siglaDoStatus(pokemon))) {
      return;
    }
    pokemon.setStatus(null);
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} acordou com a Insônia", pokemon.getSpecies().getName());
    GolpesDeStatus.avisar(pokemon, Component.translatable(status.getStatus().getRemoveMessage(),
        pokemon.getDisplayName(false)));
  }

  // De tempos em tempos: quem saiu da batalha (o selvagem que fugiu, a batalha que acabou) volta à habilidade de sempre
  public static void atualizar(long agora) {
    if (agora % CONFERIR_A_CADA_TICKS != 0 || TROCADAS.isEmpty()) {
      return;
    }
    TROCADAS.values().removeIf(trocada -> !GerenciarCombates.estaEmCombate(trocada.pokemon()));
  }

  public static void esquecer(Pokemon pokemon) {
    if (TROCADAS.remove(pokemon.getUuid()) != null) {
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} voltou à de sempre ({})", pokemon.getSpecies().getName(),
          pokemon.getAbility().getName());
    }
  }

  public static void limpar() {
    TROCADAS.clear();
  }
}
