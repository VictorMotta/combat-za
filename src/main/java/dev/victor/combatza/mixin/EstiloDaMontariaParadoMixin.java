package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.api.riding.behaviour.RidingController;

import dev.victor.combatza.golpes.CorridaMontada;

// 7b: durante a corrida montada, o estilo da montaria (chão, água ou ar) fica parado. O RidingController do Cobblemon troca o
// estilo a cada tick, e sem condutor ele acharia que um bicho de chão que pulou um degrau está voando (javap no
// canTransitionToAir). "remap = false": é uma classe do Cobblemon, os nomes dela não passam pelo mapeamento do Minecraft
@Mixin(value = RidingController.class, remap = false)
public class EstiloDaMontariaParadoMixin {
  @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
  private void paradoNaCorrida(CallbackInfo info) {
    if (CorridaMontada.semCondutor(((RidingController) (Object) this).getEntity())) {
      info.cancel();
    }
  }
}
