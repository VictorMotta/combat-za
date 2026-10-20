package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.net.messages.server.BenchMovePacket;
import com.cobblemon.mod.common.net.serverhandling.storage.BenchMoveHandler;

import dev.victor.combatza.combate.GolpesTravados;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// O pedido do resumo do Pokémon para pôr um golpe GUARDADO no lugar de um dos 4, no servidor. Em combate, recusa
// (GolpesTravados). O mesmo jeito do TrocarAOrdemDosGolpesMixin
@Mixin(value = BenchMoveHandler.class, remap = false)
public class TrocarGolpeGuardadoMixin {
  @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
  private void emCombate(BenchMovePacket pacote, MinecraftServer servidor, ServerPlayer jogador, CallbackInfo info) {
    if (GolpesTravados.recusar(jogador)) {
      info.cancel();
    }
  }
}
