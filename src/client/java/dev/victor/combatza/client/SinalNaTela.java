package dev.victor.combatza.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import dev.victor.combatza.rede.MarcaDaPartyPayload;
import dev.victor.combatza.rede.SinalDaPartyPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 01/11 (a tecla de sinal da party, escolha dele): olhe para um lugar e aperte a tecla. Para você e para os da sua party no mesmo
// mundo (party.SinalDaParty), aparece por 15 segundos uma marca amarela ali, com o nome de quem marcou e a distância, e um som
// curto; com o Xaero, o ponto também entra no mapa (PontosNoXaero). Escolha minha (vetável): a tecla \ (a barra invertida), que
// estava livre no perfil dele
public final class SinalNaTela {
  private static final double ALCANCE = 128;
  private static final double SEM_NADA_NA_FRENTE = 64;
  private static final long DURA_MS = 15_000;
  private static final int AMARELO = 0xFFFFD84A;
  private static final int BRANCO = 0xFFFFFFFF;

  private record Marca(String quem, Vec3 ponto, long ateMs) {
  }

  private static final List<Marca> MARCAS = new ArrayList<>();
  private static KeyMapping tecla;

  private SinalNaTela() {
  }

  // O tutorial mostra a tecla como está nos Controles
  static KeyMapping tecla() {
    return tecla;
  }

  public static void registrar() {
    tecla = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.sinal_da_party",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, TeclasDeGolpe.CATEGORIA));
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      while (tecla.consumeClick()) {
        sinalizar(cliente);
      }
    });
    ClientPlayNetworking.registerGlobalReceiver(MarcaDaPartyPayload.TIPO, (marca, contexto) -> chegou(marca));
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> MARCAS.clear());
    HudRenderCallback.EVENT.register(SinalNaTela::desenhar);
  }

  // O ponto para onde você está olhando: o bloco (até 128 blocos); olhando para o céu, 64 blocos à frente
  private static void sinalizar(Minecraft cliente) {
    if (cliente.player == null || !ClientPlayNetworking.canSend(SinalDaPartyPayload.TIPO)) {
      return;
    }
    HitResult mira = cliente.player.pick(ALCANCE, 1f, false);
    Vec3 ponto = mira.getType() == HitResult.Type.MISS
        ? cliente.player.getEyePosition().add(cliente.player.getViewVector(1f).scale(SEM_NADA_NA_FRENTE))
        : mira.getLocation();
    ClientPlayNetworking.send(new SinalDaPartyPayload(ponto.x, ponto.y, ponto.z));
  }

  private static void chegou(MarcaDaPartyPayload marca) {
    Minecraft cliente = Minecraft.getInstance();
    MARCAS.removeIf(antiga -> antiga.quem().equals(marca.quem()));
    MARCAS.add(new Marca(marca.quem(), new Vec3(marca.x(), marca.y(), marca.z()), System.currentTimeMillis() + DURA_MS));
    if (cliente.player != null) {
      cliente.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.6f, 1.6f);
    }
    if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
      PontosNoXaero.sinal(marca.quem(), marca.x(), marca.y(), marca.z());
    }
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    Minecraft cliente = Minecraft.getInstance();
    long agora = System.currentTimeMillis();
    MARCAS.removeIf(marca -> agora >= marca.ateMs());
    if (MARCAS.isEmpty() || cliente.player == null || cliente.options.hideGui) {
      return;
    }
    Font fonte = cliente.font;
    for (Marca marca : MARCAS) {
      float[] onde = PlacasNaTela.naTela(marca.ponto(), tela);
      if (onde == null) {
        continue;
      }
      int x = Math.round(onde[0]);
      int y = Math.round(onde[1]);
      int metros = (int) Math.round(cliente.player.position().distanceTo(marca.ponto()));
      tela.drawCenteredString(fonte, Component.literal("◆"), x, y - fonte.lineHeight / 2, AMARELO);
      tela.drawCenteredString(fonte, Component.literal(marca.quem() + " · " + metros + " m"), x, y + fonte.lineHeight,
          BRANCO);
    }
  }
}
