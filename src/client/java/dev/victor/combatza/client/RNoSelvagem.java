package dev.victor.combatza.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;

import dev.victor.combatza.rede.EntrarNaLutaPayload;
import dev.victor.combatza.rede.SoltarNoSelvagemPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// O R do Cobblemon com um selvagem envolvido (RDoCobblemonMixin): se o Pokémon da setinha está na bola, o pedido vai pelo
// nosso pacote, e o servidor joga ele no meio do caminho até o selvagem (combate.SoltarNoSelvagem)
public class RNoSelvagem {
  private RNoSelvagem() {
  }

  // 7.7: na batalha, o R com a setinha num Pokémon que já está fora e não é o lutador: ele entra na luta ali mesmo
  // (combate.Lutadores.entrarQuemEstaFora), no lugar do R do Cobblemon, que recolheria ele. Fora da batalha, o R de sempre.
  // true = o pedido foi nosso
  public static boolean entrarNaLuta(Pokemon pokemon) {
    if (pokemon == null || !(pokemon.getState() instanceof SentOutState) || !HistoricoDaBatalha.emBatalha()
        || LutadorNoCliente.ehOLutador(pokemon) || !ClientPlayNetworking.canSend(EntrarNaLutaPayload.TIPO)) {
      return false;
    }
    ClientPlayNetworking.send(new EntrarNaLutaPayload(CobblemonClient.INSTANCE.getStorage().getSelectedSlot()));
    return true;
  }

  // true = o pedido foi nosso, e o Cobblemon não faz mais nada com este R
  public static boolean jogar(Pokemon pokemon, PokemonEntity selvagem) {
    if (pokemon == null || selvagem == null || pokemon.getState() instanceof SentOutState
        || !ClientPlayNetworking.canSend(SoltarNoSelvagemPayload.TIPO)) {
      return false;
    }
    ClientPlayNetworking.send(new SoltarNoSelvagemPayload(CobblemonClient.INSTANCE.getStorage().getSelectedSlot(),
        selvagem.getId()));
    return true;
  }
}
