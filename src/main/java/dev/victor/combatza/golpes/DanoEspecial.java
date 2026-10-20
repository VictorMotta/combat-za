package dev.victor.combatza.golpes;

import java.util.List;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

public class DanoEspecial {
  private static final String DEVORADOR_DE_SONHOS = "dreameater";
  private static final String ESFORCO = "endeavor";
  private static final Set<String> DANO_POR_REGRA = Set.of("superfang", "naturesmadness", "ruination", "finalgambit",
      ESFORCO, "psywave");
  private static final Set<String> PODER_PELO_ALVO = Set.of("lowkick", "grassknot", "heavyslam", "heatcrash",
      "gyroball", "electroball", "crushgrip", "wringout", "hardpress", "punishment");
  private static final int[] PODER_DA_BOLA_ELETRICA = { 40, 60, 80, 120, 150 };
  private static final List<String> ATRIBUTOS = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");

  private DanoEspecial() {
  }

  // Showdown, onTryImmunity: golpes que falham por uma regra, e não pelo tipo
  public static boolean falha(Pokemon atacante, Move golpe, Entity alvo) {
    if (DEVORADOR_DE_SONHOS.equals(golpe.getName())) {
      return !(alvo instanceof PokemonEntity entidade
          && "slp".equals(EfeitosDeStatus.siglaDoStatus(entidade.getPokemon())));
    }
    if (ESFORCO.equals(golpe.getName()) && alvo instanceof PokemonEntity entidade) {
      return Vida.pvAtual(atacante) >= Vida.pvAtual(entidade.getPokemon());
    }
    return false;
  }

  // Showdown: "damage" nos dados (4 golpes) ou "damageCallback" no código (6 golpes)
  public static boolean temDanoFixo(Move golpe) {
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(golpe);
    return (efeito != null && efeito.damage() != null) || DANO_POR_REGRA.contains(golpe.getName());
  }

  // Em PV: sem atributos, sem STAB, sem crítico e sem sorteio de dano
  public static double danoFixo(Pokemon atacante, Move golpe, Pokemon defensor, RandomSource sorte) {
    double dano = switch (golpe.getName()) {
      case "superfang", "naturesmadness", "ruination" -> Math.max(1, Vida.pvAtual(defensor) / 2);
      case "finalgambit" -> Vida.pvAtual(atacante);
      case ESFORCO -> Vida.pvAtual(defensor) - Vida.pvAtual(atacante);
      case "psywave" -> Math.max(1, sorte.nextIntBetweenInclusive(50, 150) * atacante.getLevel() / 100);
      default -> doDado(atacante, EfeitosDosGolpes.de(golpe).damage());
    };
    CobblemonCombatZA.LOGGER.info("DANO FIXO: {} = {} PV", golpe.getName(), dano);
    return dano;
  }

  private static double doDado(Pokemon atacante, String dano) {
    return "level".equals(dano) ? atacante.getLevel() : Double.parseDouble(dano);
  }

  // Showdown: "basePowerCallback". Sem regra, vale o poder dos dados. Contra mob não há defensor: quem depende do
  // alvo usa o poder de reserva
  public static double poder(Pokemon atacante, Move golpe, Pokemon defensor, RandomSource sorte) {
    String nome = golpe.getName();
    if (defensor == null && PODER_PELO_ALVO.contains(nome)) {
      return Balanceamento.PODER_CONTRA_MOB;
    }
    double poder = switch (nome) {
      case "lowkick", "grassknot" -> pelaMassa(massa(defensor));
      case "heavyslam", "heatcrash" -> pelaDiferencaDeMassa(massa(atacante), massa(defensor));
      case "gyroball" -> Math.min(150, Math.floor(25 * velocidade(defensor) / velocidade(atacante)) + 1);
      case "electroball" ->
        PODER_DA_BOLA_ELETRICA[(int) Math.min(4, Math.floor(velocidade(atacante) / velocidade(defensor)))];
      case "crushgrip", "wringout" -> Math.max(1, Math.floor(120 * fracaoDaVida(defensor)));
      case "hardpress" -> Math.max(1, Math.floor(100 * fracaoDaVida(defensor)));
      case "punishment" -> Math.min(200, 60 + 20 * estagiosPositivos(defensor));
      case "eruption", "waterspout", "dragonenergy" -> Math.max(1, Math.floor(golpe.getPower() * fracaoDaVida(atacante)));
      case "reversal", "flail" -> pelaVidaQueFalta(atacante);
      case "return" -> Math.max(1, Math.floor(atacante.getFriendship() * 10 / 25.0));
      case "frustration" -> Math.max(1, Math.floor((255 - atacante.getFriendship()) * 10 / 25.0));
      case "magnitude" -> magnitude(sorte);
      // 5.4b: cada carga do Chute Triplo e do Pinote Triplo é mais forte (10, 20, 30; 20, 40, 60)
      case "triplekick", "tripleaxel" -> golpe.getPower() * Cargas.acertoAtual(atacante, golpe);
      default -> golpe.getPower();
    };
    if (Double.compare(poder, golpe.getPower()) != 0) {
      CobblemonCombatZA.LOGGER.info("PODER PELA REGRA: {} = {} (nos dados: {})", nome, poder, golpe.getPower());
    }
    return poder;
  }

  // Em hectogramas, a mesma unidade do Showdown (Snorlax = 4600)
  private static double massa(Pokemon pokemon) {
    return pokemon.getForm().getWeight();
  }

  private static double pelaMassa(double massa) {
    if (massa >= 2000) {
      return 120;
    }
    if (massa >= 1000) {
      return 100;
    }
    if (massa >= 500) {
      return 80;
    }
    if (massa >= 250) {
      return 60;
    }
    if (massa >= 100) {
      return 40;
    }
    return 20;
  }

  private static double pelaDiferencaDeMassa(double atacante, double defensor) {
    if (atacante >= defensor * 5) {
      return 120;
    }
    if (atacante >= defensor * 4) {
      return 100;
    }
    if (atacante >= defensor * 3) {
      return 80;
    }
    if (atacante >= defensor * 2) {
      return 60;
    }
    return 40;
  }

  private static double velocidade(Pokemon pokemon) {
    return Math.max(1, pokemon.getSpeed() * Estagios.multiplicador(pokemon, "spe") * Paralisia.multiplicador(pokemon));
  }

  private static double fracaoDaVida(Pokemon pokemon) {
    return (double) Vida.pvAtual(pokemon) / pokemon.getMaxHealth();
  }

  private static double pelaVidaQueFalta(Pokemon pokemon) {
    int razao = Math.max(1, Vida.pvAtual(pokemon) * 48 / pokemon.getMaxHealth());
    if (razao < 2) {
      return 200;
    }
    if (razao < 5) {
      return 150;
    }
    if (razao < 10) {
      return 100;
    }
    if (razao < 17) {
      return 80;
    }
    if (razao < 33) {
      return 40;
    }
    return 20;
  }

  private static int estagiosPositivos(Pokemon pokemon) {
    int soma = 0;
    for (String atributo : ATRIBUTOS) {
      soma += Math.max(0, Estagios.de(pokemon, atributo));
    }
    return soma;
  }

  private static double magnitude(RandomSource sorte) {
    int sorteio = sorte.nextInt(100);
    if (sorteio < 5) {
      return 10;
    }
    if (sorteio < 15) {
      return 30;
    }
    if (sorteio < 35) {
      return 50;
    }
    if (sorteio < 65) {
      return 70;
    }
    if (sorteio < 85) {
      return 90;
    }
    if (sorteio < 95) {
      return 110;
    }
    return 150;
  }
}
