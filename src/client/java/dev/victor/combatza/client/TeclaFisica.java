package dev.victor.combatza.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

// Lê uma tecla de Controles direto do teclado ou do mouse. O Minecraft só avisa UMA função por tecla (KeyMapping): com
// duas funções na mesma tecla, uma fica sem funcionar. Lendo direto, a nossa funciona junto com a outra (o Alt do zoom e
// o olhar livre da montaria do Cobblemon; a rodinha clicada do rolamento e as funções que já usam a rodinha)
public class TeclaFisica {
  private TeclaFisica() {
  }

  public static boolean estaApertada(KeyMapping tecla) {
    InputConstants.Key chave = KeyBindingHelper.getBoundKeyOf(tecla);
    long janela = Minecraft.getInstance().getWindow().getWindow();
    if (chave.getValue() < 0) {
      return false;
    }
    if (chave.getType() == InputConstants.Type.MOUSE) {
      return GLFW.glfwGetMouseButton(janela, chave.getValue()) == GLFW.GLFW_PRESS;
    }
    return chave.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(janela, chave.getValue());
  }
}
