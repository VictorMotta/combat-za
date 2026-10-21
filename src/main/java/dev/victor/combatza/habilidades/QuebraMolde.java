package dev.victor.combatza.habilidades;

import java.util.Set;
import java.util.function.BooleanSupplier;

import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.golpes.Execucao;

// 21/10 (habilidades, lote da conta do dano): o GOLPE EM ANDAMENTO e o Quebra-Molde. No Showdown, enquanto um golpe
// acontece, a batalha sabe quem está golpeando (o "activeMove"); o Quebra-Molde (e a Teravoltagem e a Turbochama, que são
// iguais) faz esse golpe ignorar as habilidades dos outros que o atrapalhariam: as marcadas "breakable" (Levitação,
// Robustez, Absorve Volts, Fogo Brilhante, Multiescamas, Filtro, Gordura Espessa, Imunidade, Corpo Claro...). As que reagem
// ao golpe depois (Estática, Pele Áspera) e as que não atrapalham o golpe (Escudo Sombrio, Armadura Prisma) continuam
// valendo. Aqui, o impacto do golpe (AgendaDeGolpes), o projétil voando até o alvo (Projetil), cada pedaço do jato (Jatos) e
// a segunda pancada do Laço Familiar rodam "durante" o golpe. Nesse meio tempo, Habilidades.tem e as listas de habilidades
// (Habilidades, HabilidadesDeDano, Absorcao) perguntam aqui qual habilidade vale. O Infiltrado também lê o golpe em
// andamento: o golpe dele passa pelo Refletir, pela Tela de Luz e pelo Véu de Aurora (CampoEBarreiras)
public class QuebraMolde {
  private static final Set<String> QUEBRAM = Set.of("moldbreaker", "teravolt", "turboblaze");
  private static final String INFILTRADO = "infiltrator";
  // As habilidades "breakable" do Showdown (abilities.js): as que o golpe de quem tem Quebra-Molde ignora
  private static final Set<String> IGNORADAS = Set.of("armortail", "aromaveil", "aurabreak", "battlearmor", "bigpecks",
      "bulletproof", "clearbody", "contrary", "damp", "dazzling", "disguise", "dryskin", "eartheater", "filter", "flashfire",
      "flowergift", "flowerveil", "fluffy", "friendguard", "furcoat", "goodasgold", "grasspelt", "guarddog", "heatproof",
      "heavymetal", "hypercutter", "iceface", "icescales", "illuminate", "immunity", "innerfocus", "insomnia", "keeneye",
      "leafguard", "levitate", "lightmetal", "lightningrod", "limber", "magicbounce", "magmaarmor", "marvelscale",
      "mindseye", "mirrorarmor", "motordrive", "multiscale", "oblivious", "overcoat", "owntempo", "pastelveil", "punkrock",
      "purifyingsalt", "queenlymajesty", "sandveil", "sapsipper", "shellarmor", "shielddust", "simple", "snowcloak",
      "solidrock", "soundproof", "stickyhold", "stormdrain", "sturdy", "suctioncups", "sweetveil", "tangledfeet",
      "telepathy", "terashell", "thermalexchange", "thickfat", "unaware", "vitalspirit", "voltabsorb", "waterabsorb",
      "waterbubble", "waterveil", "wellbakedbody", "whitesmoke", "windrider", "wonderguard", "wonderskin");
  // O golpe que está acontecendo agora (null = nenhum). O servidor faz um golpe de cada vez, então basta um
  private static Execucao emAndamento = null;

  private QuebraMolde() {
  }

  // Roda "acao" durante o golpe (o golpe de antes volta no fim: um golpe pode acontecer dentro de outro)
  public static void durante(Execucao execucao, Runnable acao) {
    Execucao antes = emAndamento;
    emAndamento = execucao;
    try {
      acao.run();
    } finally {
      emAndamento = antes;
    }
  }

  // O mesmo, para o acerto que devolve se pegou (o pedaço do jato, a segunda pancada do Laço Familiar)
  public static boolean duranteOAcerto(Execucao execucao, BooleanSupplier acerto) {
    Execucao antes = emAndamento;
    emAndamento = execucao;
    try {
      return acerto.getAsBoolean();
    } finally {
      emAndamento = antes;
    }
  }

  // A habilidade de "pokemon" que vale agora: "" quando o golpe em andamento é de outro Pokémon, que tem Quebra-Molde, e ela
  // é das que ele ignora
  public static String queVale(Pokemon pokemon) {
    String habilidade = Habilidades.atual(pokemon);
    return ignora(pokemon, habilidade) ? "" : habilidade;
  }

  private static boolean ignora(Pokemon pokemon, String habilidade) {
    if (emAndamento == null || !IGNORADAS.contains(habilidade)) {
      return false;
    }
    Pokemon quemGolpeia = emAndamento.getPokemon();
    return !quemGolpeia.getUuid().equals(pokemon.getUuid()) && quebra(quemGolpeia);
  }

  // Tem Quebra-Molde (ou Teravoltagem, ou Turbochama)?
  public static boolean quebra(Pokemon pokemon) {
    return QUEBRAM.contains(Habilidades.atual(pokemon));
  }

  // O golpe em andamento é de quem tem Infiltrado? (passa pelo Refletir, pela Tela de Luz e pelo Véu de Aurora)
  public static boolean infiltrando() {
    return infiltra(emAndamento);
  }

  // O golpe é de quem tem Infiltrado? (passa também pela Salvaguarda e pela Névoa do outro lado)
  public static boolean infiltra(Execucao execucao) {
    return execucao != null && INFILTRADO.equals(Habilidades.atual(execucao.getPokemon()));
  }

  // O golpe em andamento (null = nenhum: a IA pensando, o tick do status...)
  public static Execucao emAndamento() {
    return emAndamento;
  }
}
