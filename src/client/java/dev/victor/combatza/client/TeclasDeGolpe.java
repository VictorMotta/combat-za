package dev.victor.combatza.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public class TeclasDeGolpe {
  static final String CATEGORIA = "key.categories.cobblemon_combat_za";
  private static final int[] TECLAS_PADRAO = { GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V };
  private static final KeyMapping[] TECLAS = new KeyMapping[TECLAS_PADRAO.length];
  // Segurar a tecla não repete o golpe: só o aperto conta (ApertoDeTecla)
  private static final ApertoDeTecla[] APERTOS = new ApertoDeTecla[TECLAS_PADRAO.length];

  private TeclasDeGolpe() {
  }

  // 7.10: o tutorial mostra as teclas como estão nos Controles
  static KeyMapping tecla(int golpe) {
    return TECLAS[golpe];
  }

  static int quantas() {
    return TECLAS.length;
  }

  public static void registrar() {
    for (int i = 0; i < TECLAS.length; i++) {
      TECLAS[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
          "key.cobblemon_combat_za.golpe_" + (i + 1),
          InputConstants.Type.KEYSYM,
          TECLAS_PADRAO[i],
          CATEGORIA));
      APERTOS[i] = new ApertoDeTecla(TECLAS[i]);
    }

    // 6.11: o aperto só começa a prévia (PreviaDoGolpe); o golpe sai ao SOLTAR (ou é cancelado). Em batalha, o golpe vai para
    // o lutador, mesmo com a setinha em outro (LutadorNoCliente); com "todos de guarda", ninguém recebe comando
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      // 23/10: na batalha por turnos do Cobblemon, as teclas dos golpes não fazem nada (os apertos são jogados fora, e o
      // golpe que estava sendo segurado não sai ao soltar)
      if (BatalhaPorTurnos.agora()) {
        for (ApertoDeTecla aperto : APERTOS) {
          aperto.foiApertada();
        }
        PreviaDoGolpe.esquecer();
        return;
      }
      for (int i = 0; i < TECLAS.length; i++) {
        if (APERTOS[i].foiApertada() && LutadorNoCliente.vagaDoComandado() >= 0) {
          PreviaDoGolpe.apertou(i);
        }
      }
      PreviaDoGolpe.atualizar(client);
    });
  }
}
