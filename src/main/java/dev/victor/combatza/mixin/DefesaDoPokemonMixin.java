package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.combate.DefesaDoPokemon;
import net.minecraft.world.damagesource.DamageSource;

// O golpe de um monstro no seu Pokémon com o dano da DefesaDoPokemon. O Fabric não tem evento para TROCAR o dano (o
// ALLOW_DAMAGE só deixa ou não deixa), então: no começo do hurt do Pokémon, se o dano muda, este hurt para e chama o hurt de
// novo com o dano novo (a marca "recalculando" deixa a segunda chamada passar direto). Tudo o que o Cobblemon faz depois do
// dano (copiar a vida para o PV, desmaiar) acontece uma vez só, na segunda chamada
@Mixin(PokemonEntity.class)
public class DefesaDoPokemonMixin {
  @Unique
  private boolean recalculando;

  @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
  private void danoDeMonstro(DamageSource fonte, float dano, CallbackInfoReturnable<Boolean> info) {
    if (recalculando) {
      return;
    }
    PokemonEntity pokemon = (PokemonEntity) (Object) this;
    float novo = DefesaDoPokemon.danoDeMonstro(pokemon, fonte, dano);
    if (novo < dano) {
      recalculando = true;
      try {
        info.setReturnValue(pokemon.hurt(fonte, novo));
      } finally {
        recalculando = false;
      }
    }
  }
}
