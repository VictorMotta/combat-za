package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.combate.Raids;
import net.minecraft.world.damagesource.DamageSource;

// 31/10 (raid em tempo real): o dano no chefão de uma raid vai para a vida da raid, não para o corpo dele (combate.Raids).
// No começo do hurt do Pokémon, antes de tudo: se o dano foi para a raid, o hurt para aqui e responde "pegou"
@Mixin(PokemonEntity.class)
public class DanoNoChefeDaRaidMixin {
  @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
  private void naVidaDaRaid(DamageSource fonte, float dano, CallbackInfoReturnable<Boolean> info) {
    if (Raids.desviarODano((PokemonEntity) (Object) this, fonte, dano)) {
      info.setReturnValue(true);
    }
  }
}
