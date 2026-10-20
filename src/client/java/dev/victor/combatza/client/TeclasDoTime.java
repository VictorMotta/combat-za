package dev.victor.combatza.client;

import org.lwjgl.glfw.GLFW;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.mojang.blaze3d.platform.InputConstants;

import dev.victor.combatza.rede.SetinhaPayload;
import dev.victor.combatza.rede.TrocarOLadoPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

// As setas ← e → trocam o Pokémon selecionado, porque a fileira do painel do time é deitada: esquerda = o anterior,
// direita = o próximo. As ↑ e ↓ do Cobblemon continuam valendo.
// 7.7: o servidor fica sabendo onde está a setinha (o Pokémon dela anda ao seu lado, ia.Parceiro), e a tecla "Trocar o
// lado do Pokémon" passa ele da direita para a esquerda e de volta. Sem tecla padrão (escolha dele): no perfil dele, todas
// as letras já têm dono
public class TeclasDoTime {
  // Nenhuma vaga avisada ainda (ao entrar num mundo, a setinha vai de novo)
  private static final int NAO_AVISADA = -2;

  private static KeyMapping anterior;
  private static KeyMapping proximo;
  private static KeyMapping lado;
  private static int setinhaAvisada = NAO_AVISADA;

  private TeclasDoTime() {
  }

  // 7.10: o tutorial mostra as teclas como estão nos Controles
  static KeyMapping anterior() {
    return anterior;
  }

  static KeyMapping proximo() {
    return proximo;
  }

  static KeyMapping lado() {
    return lado;
  }

  public static void registrar() {
    anterior = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.pokemon_anterior",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, TeclasDeGolpe.CATEGORIA));
    proximo = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.proximo_pokemon",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, TeclasDeGolpe.CATEGORIA));
    lado = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.lado_do_pokemon",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), TeclasDeGolpe.CATEGORIA));
    ClientPlayConnectionEvents.JOIN.register((conexao, remetente, cliente) -> setinhaAvisada = NAO_AVISADA);

    // O mesmo que as teclas do Cobblemon fazem (UpShiftPartyBinding e DownShiftPartyBinding): andar uma vaga no
    // time, pulando as vazias. É só no cliente, como no Cobblemon
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      while (anterior.consumeClick()) {
        CobblemonClient.INSTANCE.getStorage().shiftSelected(false);
      }
      while (proximo.consumeClick()) {
        CobblemonClient.INSTANCE.getStorage().shiftSelected(true);
      }
      while (lado.consumeClick()) {
        if (ClientPlayNetworking.canSend(TrocarOLadoPayload.TIPO)) {
          ClientPlayNetworking.send(new TrocarOLadoPayload());
        }
      }
      avisarASetinha();
    });
  }

  // A setinha mudou (por estas setas, pelas do Cobblemon ou pela rodinha): o servidor fica sabendo
  private static void avisarASetinha() {
    if (Minecraft.getInstance().player == null || !ClientPlayNetworking.canSend(SetinhaPayload.TIPO)) {
      return;
    }
    int vaga = CobblemonClient.INSTANCE.getStorage().getSelectedSlot();
    if (vaga != setinhaAvisada) {
      ClientPlayNetworking.send(new SetinhaPayload(vaga));
      setinhaAvisada = vaga;
    }
  }
}
