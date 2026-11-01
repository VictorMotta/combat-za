package dev.victor.combatza.combate;

import com.cobblemon.mod.common.block.HealingMachineBlock;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.pvp.Duelos;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

// 01/11 (dele: "consegui usar a mesa de cura no meio do combate, isso não poderia acontecer"): na batalha (contra selvagem,
// treinador, raid ou no duelo), a mesa de cura do Cobblemon não funciona. A barra de ação diz por quê
public final class MesaDeCuraNaBatalha {
  private MesaDeCuraNaBatalha() {
  }

  // Uma linha no CobblemonCombatZA
  public static void registrar() {
    UseBlockCallback.EVENT.register((jogador, mundo, mao, acerto) -> {
      if (mundo.isClientSide() || !(jogador instanceof ServerPlayer quem)
          || !(mundo.getBlockState(acerto.getBlockPos()).getBlock() instanceof HealingMachineBlock) || !naBatalha(quem)) {
        return InteractionResult.PASS;
      }
      CobblemonCombatZA.LOGGER.info("MESA DE CURA: {} tentou usar na batalha", quem.getGameProfile().getName());
      Feedback.barraDeAcao(quem, Component.translatable("cobblemon_combat_za.batalha.sem_mesa_de_cura"));
      return InteractionResult.FAIL;
    });
  }

  private static boolean naBatalha(ServerPlayer jogador) {
    return GerenciarCombates.estaEmCombate(jogador) || BatalhasDeTreinador.estaEmBatalha(jogador)
        || Duelos.estaEmDuelo(jogador);
  }
}
