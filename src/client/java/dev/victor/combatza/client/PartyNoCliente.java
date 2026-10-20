package dev.victor.combatza.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.victor.combatza.rede.PartyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

// A party no seu jogo (o servidor manda: party.Parties): os outros membros, para o quadrinho (PartyNaTela) e o brilho através
// da parede (mixin BrilhoDaPartyMixin)
public class PartyNoCliente {
  private static List<PartyPayload.Membro> membros = List.of();
  private static Set<UUID> uuids = Set.of();
  private static String convite = "";
  private static boolean souLider = false;
  private static boolean doOpac = false;
  // Muda a cada pacote: a tela da party se remonta
  private static int versao = 0;

  private PartyNoCliente() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(PartyPayload.TIPO, (pacote, contexto) -> {
      membros = pacote.membros();
      Set<UUID> novos = new HashSet<>();
      membros.forEach(membro -> novos.add(membro.jogador()));
      uuids = novos;
      convite = pacote.convite();
      souLider = pacote.souLider();
      doOpac = pacote.doOpac();
      versao++;
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      membros = List.of();
      uuids = Set.of();
      convite = "";
      souLider = false;
    });
  }

  public static List<PartyPayload.Membro> membros() {
    return membros;
  }

  public static boolean ehDaParty(UUID jogador) {
    return uuids.contains(jogador);
  }

  public static String convite() {
    return convite;
  }

  public static boolean souLider() {
    return souLider;
  }

  // A party é a do Open Parties and Claims (ele mesmo põe no mapa do Xaero)
  public static boolean doOpac() {
    return doOpac;
  }

  public static int versao() {
    return versao;
  }
}
