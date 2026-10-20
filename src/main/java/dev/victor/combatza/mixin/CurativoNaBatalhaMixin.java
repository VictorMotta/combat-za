package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.item.interactive.StatusCureItem;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.Mochila;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

// 8.8: os curativos de status (Antídoto, Despertador, Antiqueimadura, Anticongelante, Antiparalisante, Cura Total) também
// passam pela espera da mochila na batalha (Mochila; 5 s, configurável). As poções avisam a cura (POKEMON_HEALED); estes não avisam nada, por isso
// o Mixin: no começo do applyToPokemon do Cobblemon, na espera, o uso falha (e o item não é gasto); no fim, se curou,
// começa a espera
@Mixin(StatusCureItem.class)
public class CurativoNaBatalhaMixin {
  @Inject(method = "applyToPokemon", at = @At("HEAD"), cancellable = true)
  private void naEspera(ServerPlayer jogador, ItemStack item, Pokemon pokemon,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> info) {
    if (!Mochila.podeUsar(jogador)) {
      info.setReturnValue(InteractionResultHolder.fail(item));
    }
  }

  @Inject(method = "applyToPokemon", at = @At("RETURN"))
  private void curou(ServerPlayer jogador, ItemStack item, Pokemon pokemon,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> info) {
    if (info.getReturnValue().getResult().consumesAction()) {
      Mochila.usou(jogador);
    }
  }
}
