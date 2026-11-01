package dev.victor.combatza.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.victor.combatza.rede.AvisoNoAlvoPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

// 01/11 (dele: "queria algo mais visual quando não pega e errou, a pessoa está tão entretida que nem lê o histórico"): o aviso
// GRANDE no mundo, em cima de quem levou o golpe (ou de onde ele caiu): "ERROU!", "DESVIOU!", "IMUNE!", "PROTEGEU!"... Ele
// aparece em letras duas vezes maiores, sobe um pouco e some em 1,2 segundo. Quem manda é o servidor (golpes.Feedback.avisoNoAlvo)
public final class AvisosNaTela {
  private static final long DURA_MS = 1200;
  private static final long SOME_NOS_ULTIMOS_MS = 400;
  private static final float ESCALA = 2.0f;
  private static final float SOBE_PIXELS = 18f;
  private static final int MAXIMO = 12;
  private static final String FRASE = "cobblemon_combat_za.aviso.";
  // A cor de cada aviso (o resto: branco)
  private static final Map<String, Integer> CORES = Map.of("errou", 0xFFFFFF, "desviou", 0xFFE04A, "imune", 0xB48CFF,
      "protegeu", 0x5AE0FF, "bloqueou", 0x5AE0FF, "absorveu", 0x7CFF7C, "boneco", 0xD8D8D8, "nao_alcancou", 0xFFA040,
      "sem_efeito", 0xC0C0C0);

  private record Aviso(Vec3 ponto, Component texto, int cor, long desdeMs) {
  }

  private static final List<Aviso> AVISOS = new ArrayList<>();

  private AvisosNaTela() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(AvisoNoAlvoPayload.TIPO, (aviso, contexto) -> chegou(aviso));
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> AVISOS.clear());
    HudRenderCallback.EVENT.register(AvisosNaTela::desenhar);
  }

  private static void chegou(AvisoNoAlvoPayload aviso) {
    if (AVISOS.size() >= MAXIMO) {
      AVISOS.remove(0);
    }
    AVISOS.add(new Aviso(new Vec3(aviso.x(), aviso.y(), aviso.z()), Component.translatable(FRASE + aviso.aviso()),
        CORES.getOrDefault(aviso.aviso(), 0xFFFFFF), System.currentTimeMillis()));
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    Minecraft cliente = Minecraft.getInstance();
    long agora = System.currentTimeMillis();
    AVISOS.removeIf(aviso -> agora - aviso.desdeMs() >= DURA_MS);
    if (AVISOS.isEmpty() || cliente.player == null || cliente.options.hideGui) {
      return;
    }
    Font fonte = cliente.font;
    for (Aviso aviso : AVISOS) {
      float[] onde = PlacasNaTela.naTela(aviso.ponto(), tela);
      if (onde == null) {
        continue;
      }
      long passou = agora - aviso.desdeMs();
      float fracao = (float) passou / DURA_MS;
      long falta = DURA_MS - passou;
      int alfa = falta >= SOME_NOS_ULTIMOS_MS ? 255 : (int) (255 * falta / (float) SOME_NOS_ULTIMOS_MS);
      int cor = Math.max(alfa, 8) << 24 | aviso.cor();
      tela.pose().pushPose();
      tela.pose().translate(onde[0], onde[1] - SOBE_PIXELS * fracao, 0);
      tela.pose().scale(ESCALA, ESCALA, 1f);
      tela.drawCenteredString(fonte, aviso.texto(), 0, -fonte.lineHeight / 2, cor);
      tela.pose().popPose();
    }
  }
}
