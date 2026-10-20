package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;

import dev.victor.combatza.golpes.CorridaMontada;
import dev.victor.combatza.rede.CorridaMontadaPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

// 7b: no seu jogo, a montaria que está correndo sozinha num golpe de contato (golpes.CorridaMontada). Começou: o seu jogo
// para de pilotar (o MontariaSemCondutorMixin pergunta ao CorridaMontada) e a montaria desliza até a posição do servidor.
// Acabou: ela vai para a posição do servidor e o controle volta. Por segurança, nenhuma passa de 5 s (se o aviso de fim se
// perder, o controle volta sozinho)
public class CorridaMontadaNoCliente {
  private static final long SEGURANCA = 5L * 20;
  private static final int PASSOS_DO_DESLIZE = 3;

  // Desde que tick (do mundo) cada montaria corre sozinha
  private static final Map<Integer, Long> DESDE = new HashMap<>();

  private CorridaMontadaNoCliente() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(CorridaMontadaPayload.TIPO, (pacote, contexto) -> chegou(pacote));
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      if (cliente.level == null || DESDE.isEmpty()) {
        return;
      }
      long agora = cliente.level.getGameTime();
      DESDE.entrySet().removeIf(par -> {
        if (agora - par.getValue() <= SEGURANCA) {
          return false;
        }
        CorridaMontada.marcarNoCliente(par.getKey(), false);
        return true;
      });
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      DESDE.clear();
      CorridaMontada.esquecerNoCliente();
    });
  }

  private static void chegou(CorridaMontadaPayload pacote) {
    Minecraft cliente = Minecraft.getInstance();
    Entity montaria = cliente.level == null ? null : cliente.level.getEntity(pacote.montaria());
    if (pacote.correndo()) {
      DESDE.put(pacote.montaria(), cliente.level == null ? 0 : cliente.level.getGameTime());
      CorridaMontada.marcarNoCliente(pacote.montaria(), true);
      if (montaria != null) {
        montaria.syncPacketPositionCodec(pacote.x(), pacote.y(), pacote.z());
        montaria.lerpTo(pacote.x(), pacote.y(), pacote.z(), montaria.getYRot(), montaria.getXRot(), PASSOS_DO_DESLIZE);
      }
      return;
    }
    DESDE.remove(pacote.montaria());
    if (montaria != null) {
      montaria.absMoveTo(pacote.x(), pacote.y(), pacote.z());
      montaria.syncPacketPositionCodec(pacote.x(), pacote.y(), pacote.z());
    }
    CorridaMontada.marcarNoCliente(pacote.montaria(), false);
  }
}
