package dev.victor.combatza.habilidades;

import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.ClimasFortes;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.golpes.AplicarEfeitos;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.Dano;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Precisao;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 21/10 (habilidades, lote da entrada e das imunidades): as habilidades que SEGURAM um golpe ou reagem a ele (Showdown,
// onTryHit, onFoeTryMove, onAnyTryMove, onSourceTryHeal, onAfterSetStatus e onTakeItem):
// - Antirruído (golpes de som), Blindado (golpes de bola e de bomba), Ouro de Qualidade (golpes de status de outro) e Defesa
//   Mágica (só o golpe super efetivo pega): o golpe não faz nada nele ("Mas X é imune!");
// - Filho do Vento: o golpe de vento não faz nada nele, e o Ataque dele sobe 1;
// - Espelho Mágico: o golpe de status que dá para refletir (Rosnado, Onda de Choque, Tóxico, Esporo...) volta para quem
//   usou;
// - Presença Régia, Corpo Vívido e Cauda Armadura: o golpe RÁPIDO de um inimigo (prioridade, que no tempo real sai sem
//   carregar: Ataque Rápido, Jato d'Água... e os que Travessura, Asas de Vendaval e Triagem deixam rápidos) não pega nele
//   nem nos do lado dele na batalha;
// - Umidade: com ela na batalha, Explosão, Autodestruição, Explosão Mental e Explosão Nebulosa falham (umidadeNaBatalha:
//   para o Resultado, que também não fere com ela);
// - Lodo Líquido: quem drena a vida dele (Gigadreno, Absorver, Comedor de Sonhos...) perde essa vida em vez de ganhar;
// - Sincronia: quem deu queimadura, paralisia ou veneno nele com um golpe fica com o mesmo;
// - Marionete Venenosa (só a Pecharunt): o veneno que ela dá com um golpe também confunde;
// - Viscosidade: ninguém tira o item dele (seguraOItem: pronta para quem um dia tirar itens, como o Mágico e o Furto).
// Quebra-Molde, Teravoltagem e Turbochama passam por cima das que seguram golpes (no Showdown, elas são "breakable")
public class HabilidadesDeProtecao {
  private static final String ANTIRRUIDO = "soundproof";
  private static final String BLINDADO = "bulletproof";
  private static final String OURO_DE_QUALIDADE = "goodasgold";
  private static final String DEFESA_MAGICA = "wonderguard";
  private static final String FILHO_DO_VENTO = "windrider";
  private static final String ESPELHO_MAGICO = "magicbounce";
  private static final String UMIDADE = "damp";
  private static final String LODO_LIQUIDO = "liquidooze";
  private static final String SINCRONIA = "synchronize";
  private static final String MARIONETE_VENENOSA = "poisonpuppeteer";
  private static final String PECHARUNT = "Pecharunt";
  private static final String VISCOSIDADE = "stickyhold";
  private static final String FARPA_GRUDENTA = "stickybarb";
  private static final String ESCUDO_MAGICO = "magicguard";
  private static final String ESPINHOS_TOXICOS = "toxicspikes";
  private static final Set<String> BARRAM_PRIORIDADE = Set.of("queenlymajesty", "dazzling", "armortail");
  private static final Set<String> EXPLOSOES = Set.of("explosion", "selfdestruct", "mindblown", "mistyexplosion");
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  // O Espelho Mágico refletindo: o golpe que volta não volta de novo (dois Espelhos Mágicos), como no Showdown
  private static boolean refletindo = false;

  private HabilidadesDeProtecao() {
  }

  // ControleDeGolpes (o acerto de dano e o de status, antes do desvio): a habilidade do alvo segurou o golpe? true = não
  // pega nele (já avisou; o Filho do Vento já subiu o Ataque, o Espelho Mágico já devolveu o golpe)
  public static boolean bloqueou(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity entidade) || entidade == atacante || !entidade.isAlive()) {
      return false;
    }
    Pokemon pokemon = entidade.getPokemon();
    Pokemon doAtacante = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    Pokemon protetor = protetorDaPrioridade(doAtacante, golpe, pokemon);
    if (protetor != null) {
      CobblemonCombatZA.LOGGER.info("PRIORIDADE BARRADA: {} não pegou em {} ({} de {})", golpe.getName(),
          pokemon.getSpecies().getName(), Habilidades.atual(protetor), protetor.getSpecies().getName());
      Feedback.historico(execucao.getJogador(), doAtacante, Component.translatable("cobblemon.battle.cant.generic",
          doAtacante.getDisplayName(false), golpe.getDisplayName()).append(" (").append(de(protetor)).append(")"));
      return true;
    }
    // O golpe de status de quem tem Poder do Micélio passa pela habilidade do alvo (HabilidadesDePrioridade)
    String segura = HabilidadesDePrioridade.ignoraHabilidade(execucao) ? null : quemSegura(doAtacante, golpe, pokemon);
    if (segura == null || ESPELHO_MAGICO.equals(segura) && refletindo) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("HABILIDADE SEGUROU: {} não pegou em {} ({})", golpe.getName(),
        pokemon.getSpecies().getName(), segura);
    if (ESPELHO_MAGICO.equals(segura)) {
      refletir(execucao, atacante, entidade);
    } else if (FILHO_DO_VENTO.equals(segura)) {
      Feedback.historico(execucao.getJogador(), pokemon, Component.translatable(FRASE + "absorveu",
          pokemon.getDisplayName(false), golpe.getDisplayName(), Habilidades.nome(pokemon)));
      HabilidadesDeEstagio.mudarProprio(pokemon, Map.of("atk", 1));
    } else {
      Feedback.historico(execucao.getJogador(), pokemon, Component.translatable("cobblemon.battle.immune",
          pokemon.getDisplayName(false)).append(" (").append(Habilidades.nome(pokemon)).append(")"));
    }
    return true;
  }

  // ControleDeGolpes.aplicarImpacto (na hora do golpe sair): a Umidade de alguém na batalha não deixa explodir; o Sol
  // extremo e a Chuva torrencial apagam os golpes de Água e de Fogo (ClimasFortes). true = o golpe não faz nada
  public static boolean golpeFalhou(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    Pokemon umido = EXPLOSOES.contains(golpe.getName()) && !quebraOMolde(pokemon) ? umidadeNaBatalha(pokemon) : null;
    if (umido != null) {
      CobblemonCombatZA.LOGGER.info("UMIDADE: {} não pôde usar {} (Umidade de {})", pokemon.getSpecies().getName(),
          golpe.getName(), umido.getSpecies().getName());
      Feedback.historico(execucao.getJogador(), pokemon, Component.translatable("cobblemon.battle.cant.generic",
          pokemon.getDisplayName(false), golpe.getDisplayName()).append(" (").append(de(umido)).append(")"));
      return true;
    }
    return ClimasFortes.apagou(execucao);
  }

  // A IA (EscolhaDoGolpe) não escolhe o golpe que não vai fazer nada: a Umidade, o clima forte, a habilidade do alvo
  public static boolean golpeVaiFalhar(Pokemon atacante, Move golpe, Entity alvo) {
    if (EXPLOSOES.contains(golpe.getName()) && !quebraOMolde(atacante) && umidadeNaBatalha(atacante) != null
        || ClimasFortes.vaiApagar(atacante, golpe)) {
      return true;
    }
    if (!(alvo instanceof PokemonEntity entidade) || entidade.getPokemon() == atacante) {
      return false;
    }
    Pokemon pokemon = entidade.getPokemon();
    return quemSegura(atacante, golpe, pokemon) != null || protetorDaPrioridade(atacante, golpe, pokemon) != null;
  }

  // Alguém com Umidade na batalha (ou, fora dela, no campo pessoal) de "pokemon"; null = ninguém. O Resultado (Aftermath)
  // também não fere com ela
  public static Pokemon umidadeNaBatalha(Pokemon pokemon) {
    Arena arena = Arenas.de(pokemon);
    if (arena == null) {
      return null;
    }
    for (PokemonEntity entidade : arena.pokemons()) {
      if (entidade.isAlive() && Habilidades.tem(entidade.getPokemon(), UMIDADE)) {
        return entidade.getPokemon();
      }
    }
    return null;
  }

  // EfeitosDeVida.drenar: o Lodo Líquido de quem foi drenado. true = quem drenou perde a vida em vez de ganhar (com o Escudo
  // Mágico, que não leva dano indireto, nem perde nem ganha)
  public static boolean lodoLiquido(Execucao execucao, PokemonEntity atacante, Entity alvo, double pv) {
    if (!(alvo instanceof PokemonEntity drenado) || !Habilidades.tem(drenado.getPokemon(), LODO_LIQUIDO)
        || !atacante.isAlive()) {
      return false;
    }
    Pokemon pokemon = atacante.getPokemon();
    if (Habilidades.tem(pokemon, ESCUDO_MAGICO)) {
      CobblemonCombatZA.LOGGER.info("LODO LÍQUIDO: {} não drenou nada (Escudo Mágico)", pokemon.getSpecies().getName());
      return true;
    }
    int perdeu = (int) Math.max(1, Math.round(pv));
    CobblemonCombatZA.LOGGER.info("LODO LÍQUIDO: {} drenou {} e perdeu {} PV", pokemon.getSpecies().getName(),
        drenado.getPokemon().getSpecies().getName(), perdeu);
    Feedback.historico(execucao.getJogador(), pokemon, Component.translatable("cobblemon.battle.damage.liquidooze",
        pokemon.getDisplayName(false)).append(Component.translatable("cobblemon_combat_za.pv.perdeu", perdeu)));
    Vida.ferir(atacante, perdeu);
    return true;
  }

  // AplicarStatus: o golpe de "execucao" deu o status a "quem". A Sincronia devolve (menos sono e congelado, e nunca o
  // veneno dos Espinhos Tóxicos); a Marionete Venenosa da Pecharunt confunde quem ela envenenou
  public static void statusAplicado(Execucao execucao, PokemonEntity quem, String sigla) {
    Pokemon vitima = quem.getPokemon();
    Pokemon fonte = execucao.getPokemon();
    if (vitima == fonte || ESPINHOS_TOXICOS.equals(execucao.getGolpe().getName())) {
      return;
    }
    PokemonEntity daFonte = fonte.getEntity();
    if (Habilidades.tem(vitima, SINCRONIA) && !"slp".equals(sigla) && !"frz".equals(sigla) && daFonte != null
        && daFonte.isAlive()) {
      CobblemonCombatZA.LOGGER.info("SINCRONIA: {} devolve {} para {}", vitima.getSpecies().getName(), sigla,
          fonte.getSpecies().getName());
      AplicarStatus.pelaHabilidade(execucao, daFonte, sigla, de(vitima));
    }
    if (("psn".equals(sigla) || "tox".equals(sigla)) && Habilidades.tem(fonte, MARIONETE_VENENOSA)
        && PECHARUNT.equals(fonte.getSpecies().getName())) {
      CobblemonCombatZA.LOGGER.info("MARIONETE VENENOSA: {} envenenou {} e tenta confundir", fonte.getSpecies().getName(),
          vitima.getSpecies().getName());
      Confusao.tentar(execucao, quem);
    }
  }

  // Para quem tirar o item de outro (o Mágico, o Furto, o Ladrão...): a Viscosidade do dono segura. true = o item fica
  // (já avisou). A Farpa Grudenta passa mesmo assim, como nos jogos
  public static boolean seguraOItem(Pokemon dono, Pokemon ladrao) {
    if (dono == ladrao || dono.isFainted() || !Habilidades.tem(dono, VISCOSIDADE) || quebraOMolde(ladrao)
        || FARPA_GRUDENTA.equals(ItensSegurados.id(dono))) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("VISCOSIDADE: o item de {} não saiu", dono.getSpecies().getName());
    ItensSegurados.avisar(dono, Component.translatable(FRASE + "segurou_o_item", dono.getDisplayName(false),
        Habilidades.nome(dono)));
    return true;
  }

  public static void limpar() {
    refletindo = false;
  }

  // Qual habilidade do alvo segura este golpe (null = nenhuma). Sem efeito nenhum: a IA também pergunta
  private static String quemSegura(Pokemon atacante, Move golpe, Pokemon alvo) {
    if (quebraOMolde(atacante)) {
      return null;
    }
    String habilidade = Habilidades.atual(alvo);
    boolean status = golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
    boolean segura = switch (habilidade) {
      case ANTIRRUIDO -> EfeitosDosGolpes.ehDeSom(golpe);
      case BLINDADO -> EfeitosDosGolpes.temMarca(golpe, "bullet");
      case FILHO_DO_VENTO -> EfeitosDosGolpes.temMarca(golpe, "wind");
      case OURO_DE_QUALIDADE -> status;
      case ESPELHO_MAGICO -> status && EfeitosDosGolpes.temMarca(golpe, "reflectable");
      case DEFESA_MAGICA -> !status && alvo.getEntity() != null
          && Dano.efetividadeContra(atacante, golpe, alvo.getEntity()) <= 1;
      default -> false;
    };
    return segura ? habilidade : null;
  }

  // Presença Régia, Corpo Vívido e Cauda Armadura: no alvo ou em alguém do lado dele na batalha; null = nenhum. Só contra o
  // golpe rápido de um inimigo
  private static Pokemon protetorDaPrioridade(Pokemon atacante, Move golpe, Pokemon alvo) {
    if (Lados.mesmoLado(atacante, alvo) || quebraOMolde(atacante) || !temPrioridade(atacante, golpe)) {
      return null;
    }
    if (BARRAM_PRIORIDADE.contains(Habilidades.atual(alvo))) {
      return alvo;
    }
    Arena arena = Arenas.de(alvo);
    if (arena == null) {
      return null;
    }
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon outro = entidade.getPokemon();
      if (Lados.mesmoLado(outro, alvo) && entidade.isAlive() && !outro.isFainted()
          && BARRAM_PRIORIDADE.contains(Habilidades.atual(outro))) {
        return outro;
      }
    }
    return null;
  }

  // O golpe "rápido" (a prioridade dos jogos, que no tempo real é sair sem carregar: Arquetipo.RAPIDO): a do próprio golpe
  // e a que as habilidades dão (HabilidadesDePrioridade: Travessura, Asas de Vendaval, Triagem). A Garra Rápida, a Baga
  // Custap e a Reação Rápida (sorteio) não contam (nos jogos, também não mudam a prioridade)
  public static boolean temPrioridade(Pokemon atacante, Move golpe) {
    return golpe.getTemplate().getPriority() > 0 || HabilidadesDePrioridade.ganhaPrioridade(atacante, golpe);
  }

  // O Espelho Mágico: o mesmo golpe, agora de quem tem o espelho em quem usou (o desvio e as habilidades de quem usou valem)
  private static void refletir(Execucao execucao, PokemonEntity atacante, PokemonEntity espelho) {
    Pokemon dono = espelho.getPokemon();
    Feedback.historico(execucao.getJogador(), dono, Component.translatable("cobblemon.battle.ability.magicbounce",
        dono.getDisplayName(false), execucao.getGolpe().getDisplayName()));
    if (!atacante.isAlive() || espelho.getServer() == null) {
      return;
    }
    Execucao refletida = Execucao.daIa(execucao.getJogador(), dono, execucao.getGolpe(), execucao.getSlot(),
        espelho.getServer().getTickCount(), atacante, true);
    refletindo = true;
    try {
      if (!bloqueou(refletida, espelho, atacante) && !Precisao.desviou(refletida, espelho, atacante)) {
        AplicarEfeitos.depoisDeAcertar(refletida, espelho, atacante);
      }
    } finally {
      refletindo = false;
    }
  }

  private static boolean quebraOMolde(Pokemon atacante) {
    return QuebraMolde.quebra(atacante);
  }

  // "Umidade de Psyduck"
  private static Component de(Pokemon pokemon) {
    return Component.translatable(FRASE + "de", Habilidades.nome(pokemon), pokemon.getDisplayName(false));
  }
}
