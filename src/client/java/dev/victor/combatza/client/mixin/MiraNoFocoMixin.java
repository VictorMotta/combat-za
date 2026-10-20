package dev.victor.combatza.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.victor.combatza.client.Foco;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

// 8.5 (dele, 20/10: "quando a gente crava com G no Pokémon, a nossa mira em cruz some"; escolheu "some enquanto está
// cravado"): com o foco num selvagem, a cruz do meio da tela não aparece (o marcador no alvo já mostra quem vai levar).
// Soltou o foco, ela volta. Mixin porque a Fabric API desta versão não tem um aviso para esconder a cruz: o Minecraft a
// desenha no Gui.renderCrosshair, e aqui o desenho para logo no começo
@Mixin(Gui.class)
public class MiraNoFocoMixin {
  @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
  private void semCruzNoFoco(GuiGraphics tela, DeltaTracker tempo, CallbackInfo info) {
    if (Foco.alvo() != null) {
      info.cancel();
    }
  }
}
