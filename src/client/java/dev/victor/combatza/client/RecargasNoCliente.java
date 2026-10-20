package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.rede.RecargaPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

// O que o servidor avisou sobre as recargas dos nossos Pokémon. O relógio é o do mundo (getGameTime), que para junto
// com o servidor quando o jogo pausa, igual à recarga de verdade
public class RecargasNoCliente {
  public record Recarga(long acabaNoTick, int duracao) {
  }

  private static final Map<UUID, Recarga[]> RECARGAS = new HashMap<>();

  private RecargasNoCliente() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(RecargaPayload.TIPO, (pacote, contexto) -> {
      Recarga[] doPokemon = RECARGAS.computeIfAbsent(pacote.pokemon(), uuid -> new Recarga[ControleDeGolpes.MAXIMO_DE_GOLPES]);
      doPokemon[pacote.slotGolpe()] = new Recarga(agora() + pacote.ticks(), pacote.ticks());
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> RECARGAS.clear());
  }

  // Quanto falta (0 a 1) da recarga deste golpe; 0 = pronto
  public static float fracaoQueFalta(UUID pokemon, int slot) {
    long falta = ticksQueFaltam(pokemon, slot);
    Recarga recarga = falta > 0 ? RECARGAS.get(pokemon)[slot] : null;
    return recarga == null ? 0f : (float) falta / recarga.duracao();
  }

  public static long ticksQueFaltam(UUID pokemon, int slot) {
    Recarga[] doPokemon = RECARGAS.get(pokemon);
    if (doPokemon == null || doPokemon[slot] == null) {
      return 0;
    }
    return Math.max(0, doPokemon[slot].acabaNoTick() - agora());
  }

  private static long agora() {
    Minecraft cliente = Minecraft.getInstance();
    return cliente.level == null ? 0 : cliente.level.getGameTime();
  }
}
