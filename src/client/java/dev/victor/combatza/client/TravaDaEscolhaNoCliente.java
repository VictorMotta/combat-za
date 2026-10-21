package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.rede.TravaDaEscolhaPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// 9.1: no seu jogo, o golpe em que cada Pokémon seu está preso pela Escolha (o servidor avisa) e o Colete de Ataque (o item do
// time já chega ao seu jogo): o losango risca os golpes desligados (HudDeCombate)
public class TravaDaEscolhaNoCliente {
  private static final Map<UUID, Integer> PRESOS = new HashMap<>();

  private TravaDaEscolhaNoCliente() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(TravaDaEscolhaPayload.TIPO, (pacote, contexto) -> {
      if (pacote.slot() < 0) {
        PRESOS.remove(pacote.pokemon());
      } else {
        PRESOS.put(pacote.pokemon(), pacote.slot());
      }
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> PRESOS.clear());
  }

  public static boolean desligado(Pokemon pokemon, int slot, Move golpe) {
    // 21/10: a Provocação e o Bis também riscam (GolpesDesligadosNoCliente)
    if (GolpesDesligadosNoCliente.desligado(pokemon.getUuid(), slot)) {
      return true;
    }
    if (ItensSegurados.COLETE_DE_ATAQUE.equals(ItensSegurados.idNoCliente(pokemon.heldItem()))
        && golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) {
      return true;
    }
    Integer preso = PRESOS.get(pokemon.getUuid());
    return preso != null && preso != slot;
  }
}
