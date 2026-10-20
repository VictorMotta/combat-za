package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.ai.sensors.DrowsySensor;

import dev.victor.combatza.golpes.SonoECongelado;
import dev.victor.combatza.golpes.StatusNossos;

@Mixin(DrowsySensor.Companion.class)
public class SensorDeSonoMixin {
  @Inject(method = "drowsyLogic", at = @At("HEAD"), cancellable = true, remap = false)
  private void semSonoNoCombate(PokemonEntity entidade, CallbackInfo info) {
    if (SonoECongelado.dormindoPeloGolpe(entidade.getPokemon())) {
      entidade.getBrain().setMemory(CobblemonMemories.POKEMON_DROWSY, true);
      info.cancel();
    } else if (StatusNossos.semSonoDeCansaco(entidade)) {
      entidade.getBrain().eraseMemory(CobblemonMemories.POKEMON_DROWSY);
      info.cancel();
    }
  }
}
