package dev.victor.combatza.combate;

import com.cobblemon.mod.common.battles.BattleRegistry;

import net.minecraft.server.level.ServerPlayer;

// 23/10 (dele: "como deixamos a luta de turno também (...) só sei que tá poluído quando entra em uma de turno"; escolheu "o mod
// sai da frente"): o jogador está numa batalha POR TURNOS do Cobblemon (o treinador em "Por turnos", a dupla do RCT, o PvP em
// dupla). Nela o nosso mod não age: os golpes pelas teclas, a Mega, os guardas e as batalhas nossas ficam parados até ela
// acabar, e o jogo dele esconde a nossa tela (client.BatalhaPorTurnos)
public final class BatalhaDoCobblemon {
  private BatalhaDoCobblemon() {
  }

  public static boolean esta(ServerPlayer jogador) {
    return jogador != null && BattleRegistry.getBattleByParticipatingPlayer(jogador) != null;
  }
}
