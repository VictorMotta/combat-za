package dev.victor.combatza.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import dev.victor.combatza.ia.Guardas;
import dev.victor.combatza.rede.ModoDosGuardasPayload;
import dev.victor.combatza.rede.TodosDeGuardaPayload;
import net.minecraft.Util;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// 7.4 (dele, 19/10: "um botão segurável que aparece uma roda para selecionar o que ele vai ser, exatamente igual é no ARK"):
// segurando a tecla (U, mudável nos Controles), a roda aparece no meio da tela com os 3 modos dos guardas. O mouse aponta
// um (ele cresce e acende); soltar a tecla escolhe (um clique também). Esc fecha sem mudar. A roda é uma "tela" (Screen) que
// não pausa o jogo: com ela aberta, o mouse fica solto e a câmera parada
public class RodaDosGuardas {
  private static KeyMapping tecla;

  private RodaDosGuardas() {
  }

  // 7.10: o tutorial mostra a tecla como está nos Controles
  static KeyMapping tecla() {
    return tecla;
  }

  public static void registrar() {
    tecla = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.modo_dos_guardas",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, TeclasDeGolpe.CATEGORIA));
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      while (tecla.consumeClick()) {
        if (cliente.screen == null && cliente.player != null) {
          cliente.setScreen(new Roda());
        }
      }
    });
  }

  private static final class Roda extends Screen {
    private static final float RAIO_DO_MEIO = 24f;
    private static final float RAIO_DA_OPCAO = 19f;
    private static final float DISTANCIA_DA_OPCAO = 58f;
    private static final float CRESCER_NA_ESCOLHIDA = 1.18f;
    private static final float PERTO_DO_MEIO = 14f;
    private static final float ANEL = 1.6f;
    private static final int ESCURECER = 0x50000000;
    private static final int FUNDO = 0xC0101418;
    private static final int BRANCO = 0xFFFFFFFF;
    private static final int CINZA = 0xFFB4B4BE;
    // Em volta do meio: o Neutro em cima, o Passivo embaixo à esquerda e o Agressivo embaixo à direita
    private static final Guardas.Modo[] MODOS = { Guardas.Modo.NEUTRO, Guardas.Modo.PASSIVO, Guardas.Modo.AGRESSIVO };
    private static final double[] ANGULOS = { 90, 210, 330 };
    private static final int[] CORES = { 0xFF5AA0FF, 0xFF6FCF97, 0xFFFF5A5A };

    // 7.9: soltar a tecla antes disso, sem apontar outro modo, é um TOQUE: liga ou desliga o "todos de guarda" (o botão
    // que ele pediu: "algum botão que faça isso", "configurável e predefinido"). Até lá, a roda nem aparece (sem piscar)
    private static final long TOQUE = 250;

    private final Guardas.Modo atual;
    private final long abriuEm = Util.getMillis();
    private int escolhida;

    private Roda() {
      super(Component.translatable("key.cobblemon_combat_za.modo_dos_guardas"));
      Minecraft cliente = Minecraft.getInstance();
      this.atual = Guardas.Modo.de(cliente.player == null ? "neutro" : cliente.player.getAttachedOrElse(Guardas.MODO, "neutro"));
      this.escolhida = indiceDe(atual);
    }

    private boolean aindaEhToque() {
      return Util.getMillis() - abriuEm < TOQUE;
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    // Sem o desfoque das telas do Minecraft: só um pouco mais escuro, para o mundo continuar à vista
    @Override
    public void renderBackground(GuiGraphics tela, int mouseX, int mouseY, float parcial) {
      tela.fill(0, 0, width, height, ESCURECER);
    }

    @Override
    public void render(GuiGraphics tela, int mouseX, int mouseY, float parcial) {
      if (aindaEhToque()) {
        return;
      }
      renderBackground(tela, mouseX, mouseY, parcial);
      float cx = width / 2f;
      float cy = height / 2f;
      apontar(mouseX - cx, mouseY - cy);
      Formas.circulo(tela, cx, cy, RAIO_DO_MEIO, FUNDO);
      for (int i = 0; i < MODOS.length; i++) {
        double angulo = Math.toRadians(ANGULOS[i]);
        float x = cx + (float) Math.cos(angulo) * DISTANCIA_DA_OPCAO;
        float y = cy - (float) Math.sin(angulo) * DISTANCIA_DA_OPCAO;
        boolean escolhidaAgora = i == escolhida;
        float raio = RAIO_DA_OPCAO * (escolhidaAgora ? CRESCER_NA_ESCOLHIDA : 1f);
        Formas.circulo(tela, x, y, raio, Formas.comAlfa(CORES[i], escolhidaAgora ? 0.95f : 0.55f));
        if (MODOS[i] == atual) {
          Formas.anel(tela, x, y, raio + 2.5f, ANEL, BRANCO);
        }
      }
      tela.flush();
      tela.drawCenteredString(font, Component.translatable("cobblemon_combat_za.guardas.titulo"), (int) cx,
          (int) cy - 4, BRANCO);
      for (int i = 0; i < MODOS.length; i++) {
        double angulo = Math.toRadians(ANGULOS[i]);
        int x = Math.round(cx + (float) Math.cos(angulo) * DISTANCIA_DA_OPCAO);
        int y = Math.round(cy - (float) Math.sin(angulo) * DISTANCIA_DA_OPCAO);
        tela.drawCenteredString(font, MODOS[i].nome(), x, y - 4, BRANCO);
      }
      tela.drawCenteredString(font, MODOS[escolhida].descricao(), (int) cx,
          (int) (cy + DISTANCIA_DA_OPCAO + RAIO_DA_OPCAO + 10), CINZA);
    }

    // O modo para onde o mouse aponta, pelo ângulo a partir do meio (perto do meio, fica o que estava)
    private void apontar(double dx, double dy) {
      if (Math.hypot(dx, dy) < PERTO_DO_MEIO) {
        return;
      }
      double angulo = Math.toDegrees(Math.atan2(-dy, dx));
      double menor = Double.MAX_VALUE;
      for (int i = 0; i < ANGULOS.length; i++) {
        double diferenca = Math.abs(((angulo - ANGULOS[i]) % 360 + 540) % 360 - 180);
        if (diferenca < menor) {
          menor = diferenca;
          escolhida = i;
        }
      }
    }

    @Override
    public boolean keyReleased(int tecla, int codigo, int modificadores) {
      if (RodaDosGuardas.tecla.matches(tecla, codigo)) {
        soltou();
        return true;
      }
      return super.keyReleased(tecla, codigo, modificadores);
    }

    @Override
    public boolean mouseReleased(double x, double y, int botao) {
      if (RodaDosGuardas.tecla.matchesMouse(botao)) {
        soltou();
        return true;
      }
      return super.mouseReleased(x, y, botao);
    }

    private void soltou() {
      if (aindaEhToque() && MODOS[escolhida] == atual) {
        if (ClientPlayNetworking.canSend(TodosDeGuardaPayload.TIPO)) {
          ClientPlayNetworking.send(new TodosDeGuardaPayload());
        }
        onClose();
        return;
      }
      escolher();
    }

    @Override
    public boolean mouseClicked(double x, double y, int botao) {
      if (botao == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        escolher();
        return true;
      }
      return super.mouseClicked(x, y, botao);
    }

    private void escolher() {
      if (MODOS[escolhida] != atual && ClientPlayNetworking.canSend(ModoDosGuardasPayload.TIPO)) {
        ClientPlayNetworking.send(new ModoDosGuardasPayload(MODOS[escolhida].name().toLowerCase()));
      }
      onClose();
    }

    private static int indiceDe(Guardas.Modo modo) {
      for (int i = 0; i < MODOS.length; i++) {
        if (MODOS[i] == modo) {
          return i;
        }
      }
      return 0;
    }
  }
}
