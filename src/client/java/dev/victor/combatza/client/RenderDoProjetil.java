package dev.victor.combatza.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.victor.combatza.golpes.Projetil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

// O projétil (golpes.Projetil) no mundo: uma bola que brilha na cor do tipo, sempre virada para a câmera (como a bola de
// fogo do dragão), em três camadas: o halo fraco, a bola e o miolo branco. O RenderType do raio (lightning) soma a cor ao
// que está atrás, e por isso parece luz. O rastro de pó sai do próprio Projetil; as partículas do Cobblemon, do servidor
public class RenderDoProjetil extends EntityRenderer<Projetil> {
  private static final int PEDACOS = 14;
  private static final float HALO = 1.5f;
  private static final float MIOLO = 0.45f;
  private static final float ALFA_DO_HALO = 0.30f;
  private static final float ALFA_DA_BOLA = 0.85f;
  private static final float ALFA_DO_MIOLO = 0.9f;
  private static final int BRANCO = 0xFFFFFF;

  public RenderDoProjetil(EntityRendererProvider.Context contexto) {
    super(contexto);
  }

  @Override
  public void render(Projetil projetil, float giro, float parcial, PoseStack pose, MultiBufferSource buffers, int luz) {
    if (!projetil.mostraABola()) {
      return;
    }
    pose.pushPose();
    pose.translate(0f, projetil.getBbHeight() / 2f, 0f);
    pose.mulPose(entityRenderDispatcher.cameraOrientation());
    VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
    Matrix4f matriz = pose.last().pose();
    float raio = projetil.raio();
    disco(vertices, matriz, raio * HALO, projetil.cor(), ALFA_DO_HALO);
    disco(vertices, matriz, raio, projetil.cor(), ALFA_DA_BOLA);
    disco(vertices, matriz, raio * MIOLO, BRANCO, ALFA_DO_MIOLO);
    pose.popPose();
    super.render(projetil, giro, parcial, pose, buffers, luz);
  }

  // Um disco em fatias, cada fatia um quadrilátero com o último ponto repetido (o RenderType desenha de 4 em 4), nas duas
  // voltas (sentido do relógio e o contrário), para aparecer de qualquer lado
  private static void disco(VertexConsumer vertices, Matrix4f matriz, float raio, int cor, float alfa) {
    float vermelho = ((cor >> 16) & 0xFF) / 255f;
    float verde = ((cor >> 8) & 0xFF) / 255f;
    float azul = (cor & 0xFF) / 255f;
    for (int i = 0; i < PEDACOS; i++) {
      double a = 2 * Math.PI * i / PEDACOS;
      double b = 2 * Math.PI * (i + 1) / PEDACOS;
      float ax = (float) Math.cos(a) * raio;
      float ay = (float) Math.sin(a) * raio;
      float bx = (float) Math.cos(b) * raio;
      float by = (float) Math.sin(b) * raio;
      vertices.addVertex(matriz, 0, 0, 0).setColor(vermelho, verde, azul, alfa);
      vertices.addVertex(matriz, ax, ay, 0).setColor(vermelho, verde, azul, alfa);
      vertices.addVertex(matriz, bx, by, 0).setColor(vermelho, verde, azul, alfa);
      vertices.addVertex(matriz, bx, by, 0).setColor(vermelho, verde, azul, alfa);
      vertices.addVertex(matriz, 0, 0, 0).setColor(vermelho, verde, azul, alfa);
      vertices.addVertex(matriz, bx, by, 0).setColor(vermelho, verde, azul, alfa);
      vertices.addVertex(matriz, ax, ay, 0).setColor(vermelho, verde, azul, alfa);
      vertices.addVertex(matriz, ax, ay, 0).setColor(vermelho, verde, azul, alfa);
    }
  }

  @Override
  public ResourceLocation getTextureLocation(Projetil projetil) {
    return InventoryMenu.BLOCK_ATLAS;
  }
}
