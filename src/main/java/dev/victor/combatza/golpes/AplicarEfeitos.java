package dev.victor.combatza.golpes;

import java.util.Map;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class AplicarEfeitos {
  private AplicarEfeitos() {
  }

  public static void depoisDeAcertar(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(execucao.getGolpe());

    if (efeito == null) {
      return;
    }

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

    if (efeito.self() != null && efeito.self().boosts() != null) {
      mudarAtributos(execucao, atacante, efeito.self().boosts());
    }
    if (efeito.secondaries() != null) {
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
    for (Map.Entry<String, Integer> mudanca : mudancas.entrySet()) {
      String atributo = mudanca.getKey();
      int pedido = mudanca.getValue();
      if (pedido < 0 && veioDeOutro && Habilidades.naoDeixaCair(pokemon, atributo)) {
        quedaBloqueada(execucao, pokemon, atributo);
        continue;
      }
      // 5b.3: a Névoa do lado dele
      if (pedido < 0 && veioDeOutro && CampoEBarreiras.bloqueiaQueda(pokemon)) {
        CobblemonCombatZA.LOGGER.info("NÉVOA: {} protegido da queda de {}", pokemon.getSpecies().getName(), atributo);
        Feedback.historico(execucao.getJogador(), pokemon,
            Component.translatable("cobblemon.battle.activate.mist", pokemon.getDisplayName(false)));
        continue;
      }
      int mudou = Estagios.mudar(pokemon, atributo, pedido, acabaNoTick);
      String frase = Estagios.nomeDoAtributo(atributo) + " de " + pokemon.getSpecies().getName() + " "
          + comoMudou(pedido, mudou);
      CobblemonCombatZA.LOGGER.info("ESTÁGIO: {} (agora {})", frase, Estagios.de(pokemon, atributo));
      Feedback.historico(execucao.getJogador(), pokemon, Component.literal(frase));
    }
  }

  private static void quedaBloqueada(Execucao execucao, Pokemon pokemon, String atributo) {
    String frase = Estagios.nomeDoAtributo(atributo) + " de " + pokemon.getSpecies().getName() + " não caiu";
    CobblemonCombatZA.LOGGER.info("ESTÁGIO BLOQUEADO: {} ({})", frase, pokemon.getAbility().getName());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.literal(frase + ": ").append(Habilidades.nome(pokemon)).append("!"));
  }

  private static void escudoDePoeira(Execucao execucao, Pokemon protegido) {
    CobblemonCombatZA.LOGGER.info("ESCUDO DE POEIRA: {} não sofreu o efeito extra de {}",
        protegido.getSpecies().getName(), execucao.getGolpe().getName());
    Feedback.historico(execucao.getJogador(), protegido, protegido.getDisplayName(false).copy()
        .append(" não sofreu o efeito extra: ").append(Habilidades.nome(protegido)).append("!"));
  }

  public static long quandoAcaba(Execucao execucao) {
    int pp = execucao.getGolpe().getTemplate().getPp();
    long duracao = Math.round(pp * Balanceamento.SEGUNDOS_POR_PP * SharedConstants.TICKS_PER_SECOND);
    return execucao.getJogador().server.getTickCount() + duracao;
  }

  private static String comoMudou(int pedido, int mudou) {
    if (mudou == 0) {
      return pedido > 0 ? "não pode subir mais!" : "não pode cair mais!";
    }
    String direcao = mudou > 0 ? "subiu" : "caiu";
    return switch (Math.abs(mudou)) {
      case 1 -> direcao + "!";
      case 2 -> direcao + " muito!";
      default -> direcao + " drasticamente!";
    };
  }
}
