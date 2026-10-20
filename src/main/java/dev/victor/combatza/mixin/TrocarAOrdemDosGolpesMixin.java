package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.net.messages.server.RequestMoveSwapPacket;
import com.cobblemon.mod.common.net.serverhandling.storage.RequestMoveSwapHandler;

import dev.victor.combatza.combate.GolpesTravados;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// O pedido do resumo do Pokémon para trocar a ORDEM de dois golpes, no servidor. Em combate, recusa (GolpesTravados).
// "handle" sem descritor escolhe só o primeiro método com esse nome (a regra do Mixin): o de verdade, e não a "ponte" que
// o Kotlin gera com o tipo genérico. remap = false: os nomes são do Cobblemon, não do Minecraft
@Mixin(value = RequestMoveSwapHandler.class, remap = false)
public class TrocarAOrdemDosGolpesMixin {
  @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
  private void emCombate(RequestMoveSwapPacket pacote, MinecraftServer servidor, ServerPlayer jogador, CallbackInfo info) {
    if (GolpesTravados.recusar(jogador)) {
      info.cancel();
    }
  }
}
