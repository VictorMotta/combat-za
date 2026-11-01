package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.victor.combatza.combate.Captura;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

// A Pokébola atravessa os seus Pokémon e os da sua party (combate.Captura.bolaAtravessa). O "em quem este projétil pode bater"
// é do Projectile do Minecraft (a Pokébola do Cobblemon, EmptyPokeBallEntity, não tem um próprio: javap), então o Mixin entra
// nele e só muda a resposta para a Pokébola
@Mixin(Projectile.class)
public class BolaAtravessaMixin {
  @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
  private void atravessa(Entity alvo, CallbackInfoReturnable<Boolean> info) {
    if (Captura.bolaAtravessa((Projectile) (Object) this, alvo)) {
      info.setReturnValue(false);
    }
  }
}
