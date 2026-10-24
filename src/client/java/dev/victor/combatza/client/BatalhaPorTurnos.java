package dev.victor.combatza.client;

import com.cobblemon.mod.common.client.CobblemonClient;

// 23/10 (dele: "tá poluído quando entra em uma de turno"; escolheu "o mod sai da frente"): o seu jogo está numa batalha POR
// TURNOS do Cobblemon (jogando ou assistindo). Enquanto ela dura, a nossa tela some (o losango dos golpes, a fileira do time, a
// prévia, o foco, as placas, o medidor da Mega, o painel da batalha, a party e o olho) e as teclas dos golpes, do foco e da Mega
// não fazem nada: fica só a tela de batalha do Cobblemon. Quando ela acaba, tudo volta. O servidor confere de novo
// (combate.BatalhaDoCobblemon)
public final class BatalhaPorTurnos {
  private BatalhaPorTurnos() {
  }

  public static boolean agora() {
    return CobblemonClient.INSTANCE.getBattle() != null;
  }
}
