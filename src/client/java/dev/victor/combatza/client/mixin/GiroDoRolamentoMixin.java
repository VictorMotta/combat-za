package dev.victor.combatza.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.victor.combatza.client.RolamentoNoCliente;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

// A cambalhota do rolamento (RolamentoNoCliente). No começo do setupRotations o desenho ainda está alinhado com o mundo
// (o Minecraft só depois vira o corpo para onde ele olha): girando aqui, a cambalhota sai na direção do rolamento, para
// qualquer lado que o corpo esteja virado. O Fabric não tem evento para girar o modelo de uma entidade
@Mixin(LivingEntityRenderer.class)
public class GiroDoRolamentoMixin {
  @Inject(method = "setupRotations", at = @At("HEAD"))
  private void cambalhota(LivingEntity entidade, PoseStack pose, float balanco, float giroDoCorpo, float parcial,
      float escala, CallbackInfo info) {
    RolamentoNoCliente.girar(entidade, pose, escala);
  }
}
