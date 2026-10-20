package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.item.interactive.EtherItem;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.EterEElixir;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// P1-7: o Éter na nossa batalha (combate.EterEElixir). O Cobblemon só deixa escolher o Pokémon e o golpe que perderam PP (javap:
// canUseOnPokemon e canUseOnMove comparam o PP com o máximo), e na nossa batalha o PP nunca cai: aqui ele deixa escolher
// qualquer um. No applyToPokemon, na nossa batalha, o efeito é o nosso (a recarga); fora dela, o do Cobblemon (o PP)
@Mixin(EtherItem.class)
public class EterNaBatalhaMixin {
  @Inject(method = "canUseOnPokemon", at = @At("HEAD"), cancellable = true, remap = false)
  private void escolherOPokemon(ItemStack item, Pokemon pokemon, CallbackInfoReturnable<Boolean> info) {
    if (EterEElixir.podeEscolher(pokemon)) {
      info.setReturnValue(true);
    }
  }

  @Inject(method = "canUseOnMove(Lnet/minecraft/world/item/ItemStack;Lcom/cobblemon/mod/common/pokemon/Pokemon;"
      + "Lcom/cobblemon/mod/common/api/moves/Move;)Z", at = @At("HEAD"), cancellable = true)
  private void escolherOGolpe(ItemStack item, Pokemon pokemon, Move golpe, CallbackInfoReturnable<Boolean> info) {
    if (EterEElixir.podeEscolher(pokemon)) {
      info.setReturnValue(true);
    }
  }

  @Inject(method = "applyToPokemon", at = @At("HEAD"), cancellable = true, remap = false)
  private void naNossaBatalha(ServerPlayer jogador, ItemStack item, Pokemon pokemon, Move golpe, CallbackInfo info) {
    if (EterEElixir.eter(jogador, item, pokemon, golpe, ((EtherItem) (Object) this).getMax())
        != EterEElixir.Uso.NAO_E_DA_BATALHA) {
      info.cancel();
    }
  }
}
