package dev.victor.combatza.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.combate.Mochila;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// 9.1b (dele, 20/10: "você pode dar o item para ele usar normal, só que entra aquela questão: usou um item de cura, só pode
// usar de novo depois de uns 5 s"): na batalha, dar ou tirar o item segurado conta como usar um item da mochila (Mochila): na
// espera, recusa ("Espere N s"); se trocou, começa a espera. O offerItem do Cobblemon é o único caminho em que o jogador dá ou
// tira o item (o clique no Pokémon e a roda; javap: os outros são a evolução, o comando e a IA). Recusar no começo é o mesmo
// que o Cobblemon faz com um Pokémon ocupado (devolve false, sem mexer em nada). Mixin porque o aviso HELD_ITEM_PRE, cancelado,
// faz o offerItem devolver a sua pilha da mão ao inventário (giveOrDropItemStack): duplicaria. O item de enfeite fica livre
@Mixin(PokemonEntity.class)
public class SemTrocarItemNaBatalhaMixin {
  @Inject(method = "offerItem", at = @At("HEAD"), cancellable = true, remap = false)
  private void naEspera(Player jogador, ItemStack item, boolean enfeite, CallbackInfoReturnable<Boolean> info) {
    if (!enfeite && jogador instanceof ServerPlayer dono && !Mochila.podeUsar(dono)) {
      info.setReturnValue(false);
    }
  }

  @Inject(method = "offerItem", at = @At("RETURN"), remap = false)
  private void trocou(Player jogador, ItemStack item, boolean enfeite, CallbackInfoReturnable<Boolean> info) {
    if (!enfeite && jogador instanceof ServerPlayer dono && Boolean.TRUE.equals(info.getReturnValue())) {
      Mochila.usou(dono);
    }
  }
}
