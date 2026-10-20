package dev.victor.combatza.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.victor.combatza.client.ZoomDaCamera;
import net.minecraft.client.MouseHandler;

// Antes de o Minecraft usar o giro da rodinha (trocar o item da barra), o zoom da câmera pode ficar com ele: só com o Alt
// seguro, na 3ª pessoa de costas ou, desde a 6.3i, na 1ª pessoa (ZoomDaCamera). O Fabric não tem evento para a rodinha fora
// das telas
@Mixin(MouseHandler.class)
public class RodinhaMixin {
  @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
  private void zoomDaTerceiraPessoa(long janela, double giroDeLado, double giro, CallbackInfo info) {
    if (ZoomDaCamera.rodinha(giro)) {
      info.cancel();
    }
  }
}
