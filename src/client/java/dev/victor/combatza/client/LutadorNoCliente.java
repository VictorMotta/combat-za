package dev.victor.combatza.client;

import java.util.List;
import java.util.UUID;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;

import dev.victor.combatza.ia.Parceiro;
import dev.victor.combatza.rede.LutadorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

// O lutador que o servidor avisou (LutadorPayload). Em batalha, o losango, o painel grande e o Z/X/C/V são dele, mesmo
// com a setinha em outro Pokémon: a setinha só escolhe quem entra no R. Fora de batalha (ou com o lugar vago), vale a
// setinha, como antes
public class LutadorNoCliente {
  private static UUID lutador = null;

  private LutadorNoCliente() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(LutadorPayload.TIPO,
        (pacote, contexto) -> lutador = pacote.pokemon().orElse(null));
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> lutador = null);
  }

  public static boolean ehOLutador(Pokemon pokemon) {
    return lutador != null && pokemon != null && lutador.equals(pokemon.getUuid());
  }

  // A vaga do time de quem a HUD e as teclas comandam: o lutador, se ele está fora da bola; senão, o da setinha. 7.9: com
  // "todos de guarda", fora da batalha ninguém (-1): o losango dos golpes, o painel grande e as teclas de golpe param
  public static int vagaDoComandado() {
    int doLutador = vagaDoLutador();
    if (doLutador >= 0) {
      return doLutador;
    }
    return todosDeGuarda() ? -1 : CobblemonClient.INSTANCE.getStorage().getSelectedSlot();
  }

  // O parceiro (ia.Parceiro, do lado do servidor): na batalha, o lutador (com o lugar vago, ninguém); fora dela, o da
  // setinha, ou ninguém com "todos de guarda". -1 = ninguém
  public static int vagaDoParceiro() {
    if (HistoricoDaBatalha.emBatalha()) {
      return vagaDoLutador();
    }
    return todosDeGuarda() ? -1 : CobblemonClient.INSTANCE.getStorage().getSelectedSlot();
  }

  // O servidor avisa pelo anexo do jogador (ia.Parceiro.TODOS_DE_GUARDA)
  public static boolean todosDeGuarda() {
    Minecraft cliente = Minecraft.getInstance();
    return cliente.player != null && cliente.player.getAttachedOrElse(Parceiro.TODOS_DE_GUARDA, false);
  }

  // A vaga do lutador, se ele está fora da bola; -1 = sem lutador
  public static int vagaDoLutador() {
    if (lutador != null) {
      List<Pokemon> vagas = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
      for (int numero = 0; numero < vagas.size(); numero++) {
        Pokemon pokemon = vagas.get(numero);
        if (pokemon != null && pokemon.getUuid().equals(lutador) && pokemon.getState() instanceof SentOutState) {
          return numero;
        }
      }
    }
    return -1;
  }
}
