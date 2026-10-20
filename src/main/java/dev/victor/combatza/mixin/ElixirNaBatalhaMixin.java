package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.item.interactive.ElixirItem;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.EterEElixir;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

// P1-7: o Elixir na nossa batalha (combate.EterEElixir): qualquer Pokémon pode ser escolhido (o PP nunca cai na nossa
// batalha) e o efeito é o nosso (a recarga). Fora dela, o do Cobblemon (o PP). Na espera da mochila, falha e não é gasto
@Mixin(ElixirItem.class)
public class ElixirNaBatalhaMixin {
  @Inject(method = "canUseOnPokemon", at = @At("HEAD"), cancellable = true, remap = false)
  private void escolherOPokemon(ItemStack item, Pokemon pokemon, CallbackInfoReturnable<Boolean> info) {
    if (EterEElixir.podeEscolher(pokemon)) {
      info.setReturnValue(true);
    }
  }

  @Inject(method = "applyToPokemon", at = @At("HEAD"), cancellable = true, remap = false)
  private void naNossaBatalha(ServerPlayer jogador, ItemStack item, Pokemon pokemon,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> info) {
    switch (EterEElixir.elixir(jogador, item, pokemon, ((ElixirItem) (Object) this).getMax())) {
      case USOU -> info.setReturnValue(InteractionResultHolder.success(item));
      case NA_ESPERA -> info.setReturnValue(InteractionResultHolder.fail(item));
      default -> {
        // não é a nossa batalha: o Cobblemon devolve o PP
      }
    }
  }
}
