package dev.victor.combatza.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.victor.combatza.client.PartyNoCliente;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

// Party: o contorno dos jogadores da sua party é verde (o Minecraft pinta o brilho com a cor do time; sem time, branco)
@Mixin(Entity.class)
public class CorDoBrilhoDaPartyMixin {
  private static final int VERDE_DA_PARTY = 0x55FF7A;

  @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
  private void corDaParty(CallbackInfoReturnable<Integer> info) {
    if ((Object) this instanceof Player jogador && PartyNoCliente.ehDaParty(jogador.getUUID())) {
      info.setReturnValue(VERDE_DA_PARTY);
    }
  }
}
