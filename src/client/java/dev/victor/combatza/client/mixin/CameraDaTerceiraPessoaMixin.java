package dev.victor.combatza.client.mixin;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.victor.combatza.client.Foco;
import dev.victor.combatza.client.ZoomDaCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// No fim da Camera.setup, o Minecraft (e o Better Third Person, se estiver instalado) já pôs a câmera da 3ª pessoa atrás
// do personagem. Aqui ela vai para a distância do zoom (ZoomDaCamera, Alt + rodinha) e, com foco, para o ombro direito
// (Foco), como no Z-A. Se houver parede no caminho, ela para antes
@Mixin(Camera.class)
public abstract class CameraDaTerceiraPessoaMixin {
  private static final double FOLGA_DA_PAREDE = 0.2;

  @Shadow
  protected abstract void setPosition(Vec3 posicao);

  @Inject(method = "setup", at = @At("TAIL"))
  private void ajustarATerceiraPessoa(BlockGetter mundo, Entity entidade, boolean deFora, boolean deFrente,
      float parcial, CallbackInfo info) {
    boolean deCostas = deFora && !deFrente;
    double distancia = ZoomDaCamera.distancia();
    float[] ombro = Foco.deslocamentoDoOmbro(deCostas);
    if (!deCostas || (distancia < 0 && ombro == null)) {
      return;
    }
    Camera camera = (Camera) (Object) this;
    Vec3 posicao = camera.getPosition();
    if (distancia > 0) {
      Vector3f frente = camera.getLookVector();
      Vec3 olhos = entidade.getEyePosition(parcial);
      posicao = semAtravessar(mundo, entidade, olhos,
          olhos.subtract(frente.x() * distancia, frente.y() * distancia, frente.z() * distancia));
    }
    if (ombro != null) {
      Vector3f esquerda = camera.getLeftVector();
      Vector3f cima = camera.getUpVector();
      posicao = semAtravessar(mundo, entidade, posicao, posicao.add(
          -esquerda.x() * ombro[0] + cima.x() * ombro[1],
          -esquerda.y() * ombro[0] + cima.y() * ombro[1],
          -esquerda.z() * ombro[0] + cima.z() * ombro[1]));
    }
    setPosition(posicao);
  }

  // A câmera vai de "de" até "para", mas para um pouco antes de um bloco no caminho
  private static Vec3 semAtravessar(BlockGetter mundo, Entity entidade, Vec3 de, Vec3 para) {
    BlockHitResult batida = mundo.clip(new ClipContext(de, para, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
        entidade));
    if (batida.getType() == HitResult.Type.MISS) {
      return para;
    }
    double ate = Math.max(0, batida.getLocation().distanceTo(de) - FOLGA_DA_PAREDE);
    return de.add(para.subtract(de).normalize().scale(ate));
  }
}
