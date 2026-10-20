package dev.victor.combatza.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.victor.combatza.client.PartyNoCliente;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

// Party (dele, 20/10: "na party, vê através da parede"): no seu jogo, os jogadores da sua party brilham (o contorno do
// efeito de Brilho, que aparece através dos blocos). Só no seu jogo: ninguém mais vê, e eles não ganham efeito nenhum
@Mixin(Minecraft.class)
public class BrilhoDaPartyMixin {
  @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
  private void brilhaDaParty(Entity entidade, CallbackInfoReturnable<Boolean> info) {
    if (entidade instanceof Player jogador && PartyNoCliente.ehDaParty(jogador.getUUID())) {
      info.setReturnValue(true);
    }
  }
}
