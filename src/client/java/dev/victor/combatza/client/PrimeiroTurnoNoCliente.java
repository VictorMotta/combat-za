package dev.victor.combatza.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import dev.victor.combatza.rede.JaAgiuPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// Quais Pokémon do time já agiram desde que saíram da bola (JaAgiuPayload): o losango apaga o Fingimento deles
public class PrimeiroTurnoNoCliente {
  private static final Set<UUID> JA_AGIRAM = new HashSet<>();

  private PrimeiroTurnoNoCliente() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(JaAgiuPayload.TIPO, (pacote, contexto) -> {
      if (pacote.jaAgiu()) {
        JA_AGIRAM.add(pacote.pokemon());
      } else {
        JA_AGIRAM.remove(pacote.pokemon());
      }
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> JA_AGIRAM.clear());
  }

  public static boolean jaAgiu(UUID pokemon) {
    return JA_AGIRAM.contains(pokemon);
  }
}
