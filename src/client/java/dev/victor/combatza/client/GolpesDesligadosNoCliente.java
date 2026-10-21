package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.rede.GolpesDesligadosPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// 21/10 (golpes de status que faltavam): no seu jogo, os golpes de cada Pokémon seu desligados pela Provocação ou pelo Bis (o
// servidor avisa, golpes.GolpesDesligados). O losango risca, junto com os da Escolha (TravaDaEscolhaNoCliente)
public class GolpesDesligadosNoCliente {
  private static final Map<UUID, Integer> DESLIGADOS = new HashMap<>();

  private GolpesDesligadosNoCliente() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(GolpesDesligadosPayload.TIPO, (pacote, contexto) -> {
      if (pacote.mascara() == 0) {
        DESLIGADOS.remove(pacote.pokemon());
      } else {
        DESLIGADOS.put(pacote.pokemon(), pacote.mascara());
      }
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> DESLIGADOS.clear());
  }

  public static boolean desligado(UUID pokemon, int slot) {
    Integer mascara = DESLIGADOS.get(pokemon);
    return mascara != null && (mascara & (1 << slot)) != 0;
  }
}
