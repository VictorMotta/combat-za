package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.rede.CargasPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// Quantas cargas sobram em cada golpe de vários acertos dos nossos Pokémon (CargasPayload): o número no losango
public class CargasNoCliente {
  private static final Map<UUID, int[]> RESTANTES = new HashMap<>();

  private CargasNoCliente() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(CargasPayload.TIPO, (pacote, contexto) -> RESTANTES
        .computeIfAbsent(pacote.pokemon(), uuid -> new int[ControleDeGolpes.MAXIMO_DE_GOLPES])[pacote.slotGolpe()] =
            pacote.restantes());
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> RESTANTES.clear());
  }

  public static int restantes(UUID pokemon, int slot) {
    int[] doPokemon = RESTANTES.get(pokemon);
    return doPokemon == null ? 0 : doPokemon[slot];
  }
}
