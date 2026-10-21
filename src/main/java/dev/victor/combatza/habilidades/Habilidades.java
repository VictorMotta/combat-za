package dev.victor.combatza.habilidades;

import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

public class Habilidades {
  public static final String CABECA_DE_PEDRA = "rockhead";
  public static final String CURA_NATURAL = "naturalcure";
  public static final String PELE_VERTENTE = "shedskin";
  public static final String INABALAVEL = "steadfast";
  public static final String ESCUDO_DE_POEIRA = "shielddust";
  public static final String GRACA_SERENA = "serenegrace";
  public static final String INVOLUCRO = "overcoat";
  public static final String FORCA_INTERIOR = "innerfocus";
  public static final String RITMO_PROPRIO = "owntempo";

  private static final Map<String, Set<String>> IMUNES_AO_STATUS = Map.of(
      "limber", Set.of("par"),
      "insomnia", Set.of("slp"),
      "vitalspirit", Set.of("slp"),
      "immunity", Set.of("psn", "tox"),
      "pastelveil", Set.of("psn", "tox"),
      "waterveil", Set.of("brn"),
      "waterbubble", Set.of("brn"),
      "thermalexchange", Set.of("brn"),
      "magmaarmor", Set.of("frz"),
      "purifyingsalt", Set.of("brn", "par", "slp", "frz", "psn", "tox"));

  private static final Set<String> NAO_DEIXAM_CAIR_NADA = Set.of("clearbody", "whitesmoke", "fullmetalbody");
  private static final Map<String, String> NAO_DEIXAM_CAIR_UM = Map.of(
      "hypercutter", "atk",
      "bigpecks", "def",
      "keeneye", "accuracy",
      "illuminate", "accuracy",
      "mindseye", "accuracy");
  private static final Set<String> IGNORAM_INTIMIDACAO = Set.of("innerfocus", "owntempo", "oblivious", "scrappy");

  private Habilidades() {
  }

  // 21/10 (golpes de status que faltavam): a habilidade que vale AGORA na batalha. Quase sempre a do Cobblemon; a Troca de
  // Habilidades, o Atuar, o Embarque, o Raio Simples, a Semente de Preocupação e o Ácido Gástrico trocam por um tempo
  // (TrocaDeHabilidade; "" = apagada). Quem lê a habilidade para decidir algo na batalha deve passar por aqui
  // 21/10 (lote das formas): o Traçar, a Múmia, o Aroma Persistente e o Espírito Errante trocam no mesmo lugar; o Gás
  // Neutralizador cala ("", HabilidadesTrocadas)
  public static String atual(Pokemon pokemon) {
    if (HabilidadesTrocadas.calada(pokemon)) {
      return TrocaDeHabilidade.SUPRIMIDA;
    }
    String trocada = TrocaDeHabilidade.trocada(pokemon);
    return trocada != null ? trocada : pokemon.getAbility().getName();
  }

  public static boolean tem(Pokemon pokemon, String habilidade) {
    // 21/10 (habilidades, lote da conta do dano): o Quebra-Molde de quem está golpeando pode ignorar (QuebraMolde)
    return habilidade.equals(QuebraMolde.queVale(pokemon));
  }

  public static boolean tem(Entity entidade, String habilidade) {
    return entidade instanceof PokemonEntity pokemonEntity && tem(pokemonEntity.getPokemon(), habilidade);
  }

  public static MutableComponent nome(Pokemon pokemon) {
    String trocada = TrocaDeHabilidade.trocada(pokemon);
    return trocada != null ? TrocaDeHabilidade.nomeDe(trocada)
        : Component.translatable(pokemon.getAbility().getDisplayName());
  }

  // Showdown, onSetStatus: a habilidade recusa o status
  public static boolean imuneAoStatus(Pokemon pokemon, String sigla) {
    // 21/10 (lote das formas): o Comatoso e o Minior na Forma Meteoro não pegam nenhum
    if (HabilidadesDeForma.imuneATodoStatus(pokemon)) {
      return true;
    }
    Set<String> siglas = IMUNES_AO_STATUS.get(QuebraMolde.queVale(pokemon));
    return siglas != null && siglas.contains(sigla);
  }

  // Showdown, onTryBoost: só vale para a queda que vem de OUTRO Pokémon
  public static boolean naoDeixaCair(Pokemon pokemon, String atributo) {
    String habilidade = QuebraMolde.queVale(pokemon);
    return NAO_DEIXAM_CAIR_NADA.contains(habilidade) || atributo.equals(NAO_DEIXAM_CAIR_UM.get(habilidade));
  }

  public static boolean ignoraIntimidacao(Pokemon pokemon) {
    return IGNORAM_INTIMIDACAO.contains(atual(pokemon));
  }
}
