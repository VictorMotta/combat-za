package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.habilidades.QuebraMolde;
import dev.victor.combatza.habilidades.HabilidadesDeEstagio;
import dev.victor.combatza.habilidades.HabilidadesDePrioridade;
import dev.victor.combatza.itens.ItensDeEstagio;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class AplicarEfeitos {
  private AplicarEfeitos() {
  }

  public static void depoisDeAcertar(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    // 21/10 (golpes de status que faltavam): Provocação, Bis, Atração, Troca de Habilidades, Descondensar, Giro Rápido...
    GolpesDeStatus.depoisDeAcertar(execucao, atacante, alvo);
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(execucao.getGolpe());
    // 21/10 (habilidades, lote da conta do dano): o Fedor dá 10% de hesitar ao golpe de dano que não faz hesitar (até no
    // golpe sem efeito nenhum, como a Investida); a Força Absoluta corta os efeitos extras (e o "self" do golpe que tem
    // efeito extra), em troca do poder ×1,3 (HabilidadesDaConta)
    EfeitosDosGolpes.Secundario fedor = HabilidadesDaConta.fedor(atacante, execucao.getGolpe(), efeito, alvo);
    if (fedor != null) {
      aplicarSecundario(execucao, atacante, alvo, fedor);
    }

    if (efeito == null) {
      return;
    }
    boolean forcaAbsoluta = HabilidadesDaConta.forcaAbsoluta(execucao.getPokemon(), execucao.getGolpe());

    Entity quemRecebe = "self".equals(efeito.target()) ? atacante : alvo;
    if (efeito.boosts() != null) {
      // 5b.2: o Crescimento sobe 2 no Sol
      mudarAtributos(execucao, quemRecebe, ClimaNosGolpes.reforcos(execucao, efeito.boosts()));
    }
    if (efeito.status() != null) {
      AplicarStatus.tentar(execucao, quemRecebe, efeito.status());
    }

    if ("confusion".equals(efeito.volatileStatus())) {
      Confusao.tentar(execucao, quemRecebe);
    }
    if ("partiallytrapped".equals(efeito.volatileStatus())) {
      Preso.prender(execucao, quemRecebe);
    }

    if (!forcaAbsoluta && efeito.self() != null && efeito.self().boosts() != null) {
      mudarAtributos(execucao, atacante, efeito.self().boosts());
    }
    if (!forcaAbsoluta && efeito.secondaries() != null) {
      for (EfeitosDosGolpes.Secundario secundario : efeito.secondaries()) {
        aplicarSecundario(execucao, atacante, alvo, secundario);
      }
    }
  }

  private static void aplicarSecundario(Execucao execucao, PokemonEntity atacante, Entity alvo,
      EfeitosDosGolpes.Secundario secundario) {
    int chance = Habilidades.tem(atacante, Habilidades.GRACA_SERENA) ? secundario.chance() * 2 : secundario.chance();
    if (atacante.getRandom().nextInt(100) >= chance) {
      return;
    }
    if (secundario.self() == null && alvo instanceof PokemonEntity protegido
        && Habilidades.tem(protegido.getPokemon(), Habilidades.ESCUDO_DE_POEIRA)) {
      escudoDePoeira(execucao, protegido.getPokemon());
      return;
    }
    // Lote 3 dos itens: a Capa Furtiva, como o Escudo de Poeira (em silêncio, como no Showdown)
    if (secundario.self() == null && alvo instanceof PokemonEntity protegido
        && ItensSegurados.tem(protegido.getPokemon(), ItensSegurados.CAPA_FURTIVA)) {
      CobblemonCombatZA.LOGGER.info("ITEM: {} não sofreu o efeito extra de {} (Capa Furtiva)",
          protegido.getPokemon().getSpecies().getName(), execucao.getGolpe().getName());
      return;
    }
    if (secundario.boosts() != null) {
      mudarAtributos(execucao, alvo, secundario.boosts());
    }
    if (secundario.self() != null && secundario.self().boosts() != null) {
      mudarAtributos(execucao, atacante, secundario.self().boosts());
    }
    if (secundario.status() != null) {
      AplicarStatus.tentar(execucao, alvo, secundario.status());
    }
    if ("flinch".equals(secundario.volatileStatus())) {
      Flinch.aplicar(execucao, atacante, alvo);
    }
    if ("confusion".equals(secundario.volatileStatus())) {
      Confusao.tentar(execucao, alvo);
    }
  }

  // 5.6c: a Teia Pegajosa baixa a Velocidade de quem pisa, fora do acerto do golpe (Armadilhas)
  public static void mudarUmAtributo(Execucao execucao, Entity quem, String atributo, int quanto) {
    mudarAtributos(execucao, quem, Map.of(atributo, quanto));
  }

  private static void mudarAtributos(Execucao execucao, Entity quem, Map<String, Integer> mudancas) {
    if (!(quem instanceof PokemonEntity pokemonEntity)) {
      return;
    }
    Pokemon pokemon = pokemonEntity.getPokemon();
    boolean veioDeOutro = pokemon != execucao.getPokemon();
    long acabaNoTick = quandoAcaba(execucao);
    Map<String, Integer> mudaram = new HashMap<>();
    boolean amuletoAvisou = false;
    for (Map.Entry<String, Integer> mudanca : mudancas.entrySet()) {
      String atributo = mudanca.getKey();
      int pedido = mudanca.getValue();
      // 21/10 (habilidades, lote das reações): o golpe de status do Poder do Micélio passa pelo Corpo Claro e parecidas
      if (pedido < 0 && veioDeOutro && Habilidades.naoDeixaCair(pokemon, atributo)
          && !HabilidadesDePrioridade.ignoraHabilidade(execucao)) {
        quedaBloqueada(execucao, pokemon, atributo);
        continue;
      }
      // 21/10 (habilidades, lote da entrada e das imunidades): a Armadura Espelhada devolve a queda para quem baixou
      if (pedido < 0 && veioDeOutro && HabilidadesDeEstagio.espelhou(execucao.getPokemon(), pokemon, atributo, pedido)) {
        continue;
      }
      // Lote 3 dos itens: o Amuleto Purificador (a frase uma vez só, mesmo segurando dois atributos)
      if (pedido < 0 && veioDeOutro && ItensSegurados.tem(pokemon, ItensSegurados.AMULETO_PURIFICADOR)) {
        amuletoAvisou = amuletoAvisou || ItensDeEstagio.amuletoSegura(pokemon);
        continue;
      }
      // 5b.3: a Névoa do lado dele
      // 21/10: o golpe de quem tem Infiltrado passa pela Névoa
      if (pedido < 0 && veioDeOutro && CampoEBarreiras.bloqueiaQueda(pokemon) && !QuebraMolde.infiltra(execucao)) {
        CobblemonCombatZA.LOGGER.info("NÉVOA: {} protegido da queda de {}", pokemon.getSpecies().getName(), atributo);
        Feedback.historico(execucao.getJogador(), pokemon,
            Component.translatable("cobblemon.battle.activate.mist", pokemon.getDisplayName(false)));
        continue;
      }
      int mudou = Estagios.mudar(pokemon, atributo, pedido, acabaNoTick);
      Component frase = Component.translatable(Estagios.FRASE + comoMudou(pedido, mudou),
          Estagios.nomeDoAtributo(atributo, pokemon));
      CobblemonCombatZA.LOGGER.info("ESTÁGIO: {} (agora {})", frase.getString(), Estagios.de(pokemon, atributo));
      Feedback.historico(execucao.getJogador(), pokemon, frase);
      if (mudou != 0) {
        mudaram.put(atributo, mudou);
      }
    }
    // Lote 3 dos itens: a Erva Branca, a Mochila Ejetora e a Erva Imitadora reagem ao que mudou
    if (!mudaram.isEmpty()) {
      ItensDeEstagio.mudaram(pokemon, mudaram);
    }
    // 21/10: o Competitivo e o Desafiador, com a queda que veio de um inimigo
    if (!Lados.mesmoLado(execucao.getPokemon(), pokemon)) {
      HabilidadesDeEstagio.desafiar(pokemon, mudaram);
    }
  }

  private static void quedaBloqueada(Execucao execucao, Pokemon pokemon, String atributo) {
    Component nome = Estagios.nomeDoAtributo(atributo, pokemon);
    CobblemonCombatZA.LOGGER.info("ESTÁGIO BLOQUEADO: {} não caiu ({})", nome.getString(),
        Habilidades.atual(pokemon));
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable(Estagios.FRASE + "nao_caiu", nome, Habilidades.nome(pokemon)));
  }

  private static void escudoDePoeira(Execucao execucao, Pokemon protegido) {
    CobblemonCombatZA.LOGGER.info("ESCUDO DE POEIRA: {} não sofreu o efeito extra de {}",
        protegido.getSpecies().getName(), execucao.getGolpe().getName());
    Feedback.historico(execucao.getJogador(), protegido,
        Component.translatable("cobblemon_combat_za.habilidade.sem_efeito_extra", protegido.getDisplayName(false),
            Habilidades.nome(protegido)));
  }

  public static long quandoAcaba(Execucao execucao) {
    int pp = execucao.getGolpe().getTemplate().getPp();
    long duracao = Math.round(pp * Balanceamento.SEGUNDOS_POR_PP * SharedConstants.TICKS_PER_SECOND);
    return execucao.getJogador().server.getTickCount() + duracao;
  }

  // A chave da frase (Estagios.FRASE + "subiu_muito": "%s subiu muito!")
  public static String comoMudou(int pedido, int mudou) {
    if (mudou == 0) {
      return pedido > 0 ? "nao_sobe_mais" : "nao_cai_mais";
    }
    String direcao = mudou > 0 ? "subiu" : "caiu";
    return switch (Math.abs(mudou)) {
      case 1 -> direcao;
      case 2 -> direcao + "_muito";
      default -> direcao + "_drasticamente";
    };
  }
}
