package dev.victor.combatza.habilidades;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.campo.Terreno;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.itens.ItensDeDano;
import net.minecraft.network.chat.Component;

// 21/10 (habilidades, lote das formas: "as que mudam a forma ou o tipo"): o TIPO que vale agora. Nos jogos, algumas
// habilidades trocam o tipo do Pokémon no meio da batalha, e a troca some quando ele volta para a bola:
// - Proteico (Protean) e Líbero (Libero): ao usar um golpe, ele vira do tipo do golpe (uma vez por entrada, como na 9ª
//   geração; voltou para a bola, vale de novo);
// - Troca de Cor (Color Change): levou um golpe de dano, vira do tipo desse golpe;
// - Mimetismo (Mimicry): o tipo do campo (Elétrico, Planta, Fada no Enevoado, Psíquico); sem campo, o de sempre;
// - Multitipo (Arceus) e Sistema Alfa (Silvally): o tipo da Placa ou da Memória que ele segura (sem nenhuma, Normal). Com o
//   Mega Showdown a forma já muda com o item; aqui vale mesmo sem ele (Showdown, conditions.js: "arceus" e "silvally").
// Quem pergunta o tipo (o STAB e a efetividade no Dano, as imunidades de status e de clima pela AplicarStatus.temTipo, a
// Pedra Oculta, o Lodo Escuro) passa por aqui, e não pelo pokemon.getPrimaryType() direto. As trocas só valem na nossa
// batalha: voltou para a bola, desmaiou ou a batalha acabou, ele volta aos tipos da forma dele
public class TiposNaBatalha {
  private static final String PROTEICO = "protean";
  private static final String LIBERO = "libero";
  private static final String TROCA_DE_COR = "colorchange";
  private static final String MIMETISMO = "mimicry";
  private static final String MULTITIPO = "multitype";
  private static final String SISTEMA_ALFA = "rkssystem";
  // Showdown: o Proteico não pega nos golpes "do futuro" (flag futuremove)
  private static final Set<String> GOLPES_DO_FUTURO = Set.of("futuresight", "doomdesire");
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  private record Troca(Pokemon pokemon, List<ElementalType> tipos) {
  }

  private static final Map<UUID, Troca> TROCADOS = new HashMap<>();
  // Quem já usou o Proteico (ou o Líbero) nesta entrada
  private static final Map<UUID, Pokemon> PROTEICO_USADO = new HashMap<>();

  private TiposNaBatalha() {
  }

  // Os tipos de agora: a troca da batalha, o item do Arceus e do Silvally, ou os da forma (o primeiro é o principal)
  public static List<ElementalType> de(Pokemon pokemon) {
    Troca troca = TROCADOS.get(pokemon.getUuid());
    if (troca != null) {
      return troca.tipos();
    }
    ElementalType doItem = doItem(pokemon);
    if (doItem != null) {
      return List.of(doItem);
    }
    ElementalType segundo = pokemon.getSecondaryType();
    return segundo == null ? List.of(pokemon.getPrimaryType()) : List.of(pokemon.getPrimaryType(), segundo);
  }

  public static ElementalType primario(Pokemon pokemon) {
    return de(pokemon).get(0);
  }

  // null = só tem um tipo
  public static ElementalType secundario(Pokemon pokemon) {
    List<ElementalType> tipos = de(pokemon);
    return tipos.size() > 1 ? tipos.get(1) : null;
  }

  public static boolean tem(Pokemon pokemon, ElementalType tipo) {
    return de(pokemon).contains(tipo);
  }

  // Multitipo e Sistema Alfa: o tipo da Placa / da Memória (sem nenhuma, Normal); null = não é o caso
  private static ElementalType doItem(Pokemon pokemon) {
    if (Habilidades.tem(pokemon, MULTITIPO)) {
      ElementalType daPlaca = ItensDeDano.tipoDaPlaca(pokemon);
      return daPlaca != null ? daPlaca : ElementalTypes.NORMAL;
    }
    if (Habilidades.tem(pokemon, SISTEMA_ALFA)) {
      ElementalType daMemoria = ItensDeDano.tipoDaMemoria(pokemon);
      return daMemoria != null ? daMemoria : ElementalTypes.NORMAL;
    }
    return null;
  }

  // Showdown, onPrepareHit do Proteico e do Líbero: o golpe começou (HabilidadesDeForma.aoComecarGolpe, na batalha ou no golpe
  // que começa uma) e ele vira do tipo do golpe, se ainda não é só desse tipo. Uma vez por entrada
  public static void aoUsarGolpe(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    if (!Habilidades.tem(pokemon, PROTEICO) && !Habilidades.tem(pokemon, LIBERO)
        || PROTEICO_USADO.containsKey(pokemon.getUuid()) || GOLPES_DO_FUTURO.contains(golpe.getName())) {
      return;
    }
    ElementalType tipo = ClimaNosGolpes.tipo(pokemon, golpe);
    List<ElementalType> agora = de(pokemon);
    if (agora.size() == 1 && agora.get(0) == tipo) {
      return;
    }
    PROTEICO_USADO.put(pokemon.getUuid(), pokemon);
    trocar(pokemon, tipo);
  }

  // Showdown, onAfterMoveSecondary da Troca de Cor: levou um golpe de dano (e continua de pé) que não é do tipo dele
  public static void depoisDeApanhar(Execucao execucao, PokemonEntity atingido) {
    Pokemon pokemon = atingido.getPokemon();
    if (!Habilidades.tem(pokemon, TROCA_DE_COR) || !atingido.isAlive() || pokemon.isFainted() || Desmaio.esta(atingido)
        || !GerenciarCombates.estaEmCombate(pokemon)) {
      return;
    }
    ElementalType tipo = ClimaNosGolpes.tipo(execucao.getPokemon(), execucao.getGolpe());
    if (!tem(pokemon, tipo)) {
      trocar(pokemon, tipo);
    }
  }

  // Showdown, onTerrainChange do Mimetismo: conferido a cada meio segundo na batalha (HabilidadesDeForma)
  public static void conferirMimetismo(Pokemon pokemon) {
    if (!Habilidades.tem(pokemon, MIMETISMO)) {
      return;
    }
    ElementalType doCampo = doCampo(CampoEBarreiras.terreno(pokemon));
    Troca troca = TROCADOS.get(pokemon.getUuid());
    if (doCampo == null && troca != null) {
      TROCADOS.remove(pokemon.getUuid());
      CobblemonCombatZA.LOGGER.info("TIPO: {} voltou aos tipos de sempre (Mimetismo, sem campo)",
          pokemon.getSpecies().getName());
      HabilidadesDeForma.avisar(pokemon, Component.translatable(FRASE + "tipo_voltou", pokemon.getDisplayName(false)));
    } else if (doCampo != null && (troca == null || !troca.tipos().equals(List.of(doCampo)))) {
      trocar(pokemon, doCampo);
    }
  }

  private static ElementalType doCampo(Terreno terreno) {
    return switch (terreno) {
      case ELETRICO -> ElementalTypes.ELECTRIC;
      case PLANTAS -> ElementalTypes.GRASS;
      case ENEVOADO -> ElementalTypes.FAIRY;
      case PSIQUICO -> ElementalTypes.PSYCHIC;
      default -> null;
    };
  }

  // "Charizard modificou seu tipo para Água!"
  private static void trocar(Pokemon pokemon, ElementalType tipo) {
    TROCADOS.put(pokemon.getUuid(), new Troca(pokemon, List.of(tipo)));
    CobblemonCombatZA.LOGGER.info("TIPO: {} virou do tipo {} ({})", pokemon.getSpecies().getName(), tipo.getName(),
        Habilidades.atual(pokemon));
    HabilidadesDeForma.avisar(pokemon, Component.translatable("cobblemon.battle.start.typechange",
        pokemon.getDisplayName(false), tipo.getDisplayName()));
  }

  // Voltou para a bola, desmaiou ou saiu da batalha: os tipos da forma voltam
  public static void esquecer(Pokemon pokemon) {
    TROCADOS.remove(pokemon.getUuid());
    PROTEICO_USADO.remove(pokemon.getUuid());
  }

  // A cada segundo: quem não está mais numa batalha (nem no meio do golpe que começa uma) perde a troca
  public static void varrer() {
    TROCADOS.values().removeIf(troca -> HabilidadesDeForma.foraDaBatalha(troca.pokemon()));
    PROTEICO_USADO.values().removeIf(HabilidadesDeForma::foraDaBatalha);
  }

  public static void limpar() {
    TROCADOS.clear();
    PROTEICO_USADO.clear();
  }
}
