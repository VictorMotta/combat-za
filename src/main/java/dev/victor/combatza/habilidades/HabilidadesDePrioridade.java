package dev.victor.combatza.habilidades;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.FormaDoGolpe;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 21/10 (habilidades, lote das reações): as habilidades de PRIORIDADE. No tempo real, "agir antes" vira "o golpe sai sem a
// fase de carregar" (como a Garra Rápida e a Baga Custap, ItensDeCarga), e "agir por último", "a carga demora 50% mais"
// (como a Cauda Pesada). Execucao pergunta aqui:
// - Travessura: os golpes de status saem sem carregar; mas não pegam em inimigo do tipo Sombrio ("é imune", como no
//   Showdown);
// - Asas de Vendaval: os golpes Voadores saem sem carregar, com a vida cheia;
// - Triagem: os golpes que curam (a marca "heal": Recuperar, Dreno de Gigante, Soco Dreno...) saem sem carregar;
// - Reação Rápida: 30% das vezes, o golpe de dano sai sem carregar (com a frase);
// - Poder do Micélio: os golpes de status carregam 50% mais devagar, mas passam pela habilidade do alvo (a Insônia não
//   segura o Esporo; o Corpo Claro não segura a queda).
// A prioridade que vem da habilidade também conta para o Campo Psíquico (CampoEBarreiras.bloqueiaPrioridade)
public class HabilidadesDePrioridade {
  private static final String TRAVESSURA = "prankster";
  private static final String ASAS_DE_VENDAVAL = "galewings";
  private static final String TRIAGEM = "triage";
  private static final String REACAO_RAPIDA = "quickdraw";
  private static final String PODER_DO_MICELIO = "myceliummight";
  private static final double CHANCE_DA_REACAO_RAPIDA = 0.3;
  private static final double CARGA_DO_MICELIO = 1.5;

  private HabilidadesDePrioridade() {
  }

  private static boolean deStatus(Move golpe) {
    return golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
  }

  // Showdown, onModifyPriority: a habilidade dá prioridade a este golpe (sem sorteio)
  public static boolean ganhaPrioridade(Pokemon pokemon, Move golpe) {
    return switch (Habilidades.atual(pokemon)) {
      case TRAVESSURA -> deStatus(golpe);
      case ASAS_DE_VENDAVAL -> golpe.getType() == ElementalTypes.FLYING && Vida.pvAtual(pokemon) >= pokemon.getMaxHealth();
      case TRIAGEM -> EfeitosDosGolpes.temMarca(golpe, "heal");
      default -> false;
    };
  }

  // Execucao: o golpe pula a carga? A prioridade da habilidade, ou a Reação Rápida (30%, só nos golpes de dano)
  public static boolean pulaACarga(Pokemon pokemon, Move golpe) {
    if (ganhaPrioridade(pokemon, golpe)) {
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} usou {} sem carregar ({})", pokemon.getSpecies().getName(),
          golpe.getName(), Habilidades.atual(pokemon));
      return true;
    }
    PokemonEntity entidade = pokemon.getEntity();
    if (!Habilidades.tem(pokemon, REACAO_RAPIDA) || deStatus(golpe) || entidade == null
        || entidade.getRandom().nextDouble() >= CHANCE_DA_REACAO_RAPIDA) {
      return false;
    }
    HabilidadesQueReagem.avisar(pokemon, Component.translatable("cobblemon_combat_za.habilidade.mais_rapido",
        pokemon.getDisplayName(false), Habilidades.nome(pokemon)));
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} agiu na hora (Reação Rápida)", pokemon.getSpecies().getName());
    return true;
  }

  // Execucao: o ritmo da carga (Poder do Micélio: os golpes de status, 50% mais devagar)
  public static double ritmo(Pokemon pokemon, Move golpe) {
    return Habilidades.tem(pokemon, PODER_DO_MICELIO) && deStatus(golpe) ? CARGA_DO_MICELIO : 1.0;
  }

  // Poder do Micélio: o golpe de status passa pela habilidade do alvo (AplicarStatus, AplicarEfeitos). A armadilha que ficou
  // no chão (os Espinhos Tóxicos) não é mais o golpe: quem pisa depois tem a habilidade de volta, como no Showdown
  public static boolean ignoraHabilidade(Execucao execucao) {
    return execucao != null && Habilidades.tem(execucao.getPokemon(), PODER_DO_MICELIO) && deStatus(execucao.getGolpe())
        && FormaDoGolpe.de(execucao.getGolpe()) != FormaDoGolpe.ARMADILHA;
  }

  // Travessura: o golpe de status num inimigo do tipo Sombrio falha ("Mas X é imune!"). true = falhou, já avisou
  public static boolean falhaNoSombrio(Execucao execucao, Entity alvo) {
    Pokemon pokemon = execucao.getPokemon();
    if (!Habilidades.tem(pokemon, TRAVESSURA) || !deStatus(execucao.getGolpe()) || !(alvo instanceof PokemonEntity atingido)
        || Lados.mesmoLado(atingido.getPokemon(), pokemon)
        || !AplicarStatus.temTipo(atingido.getPokemon(), ElementalTypes.DARK)) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} de {} não pega no tipo Sombrio {} (Travessura)",
        execucao.getGolpe().getName(), pokemon.getSpecies().getName(), atingido.getPokemon().getSpecies().getName());
    Feedback.historico(execucao.getJogador(), atingido,
        Component.translatable("cobblemon.battle.immune", atingido.getName()));
    return true;
  }
}
