package dev.victor.combatza.itens;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.campo.Clima;
import net.minecraft.server.level.ServerPlayer;

// Lote 3 dos itens segurados: os que mexem no campo, no chão e no preso, consultados por quem cuida de cada coisa:
// - Balão de Ar: flutua (golpes de Terra não pegam, as armadilhas e o campo não alcançam) até levar um golpe (estoura:
//   ItensDoAcerto); Bola de Ferro: "aterra" (Terra pega até Voador e Levitação; o campo e as armadilhas alcançam);
// - Guarda-chuva Útil: o Sol e a Chuva não valem para quem segura (EfeitosDoCampo);
// - Óculos de Proteção: imune a golpes de pó (AplicarStatus) e à areia (EfeitosDoCampo, desde a 5b);
// - Botas Grossas: as armadilhas no chão não fazem nada (Armadilhas);
// - Garra de Aperto: o preso dura 7 turnos; Faixa de Amarrar: o preso tira 1/6 por turno (em vez de 1/8); Casca Descartável: quem
//   segura não fica parado preso (só leva o dano por turno) (Preso);
// - Bola de Fumaça: com ela em algum Pokémon do seu time e TODOS na bola (você "escolheu fugir"), fugir pede só 8 blocos de
//   distância dos selvagens (em vez de 16). Com Pokémon lutando, nada muda (para não fugir sem querer comandando de longe)
//   (Combate)
public class ItensDoCampo {
  private static final int TURNOS_DA_GARRA = 7;
  private static final double FRACAO_DA_FAIXA = 1.0 / 6;
  private static final float FUGA_COM_FUMACA = 8.0f;

  private ItensDoCampo() {
  }

  // Ainda flutua no Balão (segura, não estourou nesta batalha, sem a Bola de Ferro)
  public static boolean noBalao(Pokemon pokemon) {
    return ItensSegurados.disponivel(pokemon, ItensSegurados.BALAO_DE_AR)
        && !ItensSegurados.tem(pokemon, ItensSegurados.BOLA_DE_FERRO);
  }

  public static boolean aterrado(Pokemon pokemon) {
    return ItensSegurados.tem(pokemon, ItensSegurados.BOLA_DE_FERRO);
  }

  // O clima que vale para quem segura o Guarda-chuva Útil: sem Sol e sem Chuva
  public static Clima comGuardaChuva(Pokemon pokemon, Clima clima) {
    return (clima == Clima.SOL || clima == Clima.CHUVA) && ItensSegurados.tem(pokemon, ItensSegurados.GUARDA_CHUVA)
        ? Clima.NENHUM
        : clima;
  }

  public static boolean botas(Pokemon pokemon) {
    return ItensSegurados.tem(pokemon, ItensSegurados.BOTAS_GROSSAS);
  }

  // Os turnos de dano do preso: com a Garra de Aperto de quem prendeu, 7; senão, o sorteio de sempre
  public static int turnosDoPreso(Pokemon quemPrendeu, int sorteados) {
    return quemPrendeu != null && ItensSegurados.tem(quemPrendeu, "gripclaw") ? TURNOS_DA_GARRA : sorteados;
  }

  // A fração da vida por turno do preso: com a Faixa de Amarrar de quem prendeu, 1/6
  public static double fracaoDoPreso(Pokemon quemPrendeu, double normal) {
    return quemPrendeu != null && ItensSegurados.tem(quemPrendeu, "bindingband") ? FRACAO_DA_FAIXA : normal;
  }

  // A Casca Descartável: preso, mas sem ficar parado
  public static boolean cascaDescartavel(Pokemon pokemon) {
    return pokemon != null && ItensSegurados.tem(pokemon, "shedshell");
  }

  // A distância para fugir: com a Bola de Fumaça no time e todos na bola, 8 blocos
  public static float distanciaDeFuga(ServerPlayer jogador, float normal) {
    boolean temFumaca = false;
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (pokemon.getEntity() != null) {
        return normal;
      }
      temFumaca = temFumaca || ItensSegurados.tem(pokemon, "smokeball");
    }
    return temFumaca ? Math.min(normal, FUGA_COM_FUMACA) : normal;
  }
}
