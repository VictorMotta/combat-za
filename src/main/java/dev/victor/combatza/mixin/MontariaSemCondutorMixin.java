package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.golpes.CorridaMontada;
import net.minecraft.world.entity.LivingEntity;

// 7b: durante a corrida do golpe de contato montado, a montaria fica SEM condutor, no servidor e no seu jogo
// (CorridaMontada). Mixin porque o aviso do Cobblemon que escolhe o condutor (SELECT_DRIVER) não aceita "ninguém": o
// getControllingPassenger do PokemonEntity para logo no começo e devolve null. O Cobblemon e o Minecraft conferem esse null
// em todo lugar (procurado com javap): sem condutor, a montaria anda como um Pokémon comum
@Mixin(PokemonEntity.class)
public class MontariaSemCondutorMixin {
  @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
  private void semCondutorNaCorrida(CallbackInfoReturnable<LivingEntity> info) {
    if (CorridaMontada.semCondutor((PokemonEntity) (Object) this)) {
      info.setReturnValue(null);
    }
  }
}
