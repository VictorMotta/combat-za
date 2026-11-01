package dev.victor.combatza.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.rede.MarcaDaPartyPayload;
import dev.victor.combatza.rede.SinalDaPartyPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 01/11 (a tecla de sinal da party, escolha dele): você olha para um lugar e aperta a tecla; o seu jogo manda o ponto, e
// aqui ele vai para você e para os da sua party que estão no mesmo mundo (client.SinalNaTela mostra a marca por uns segundos,
// na tela e no mapa do Xaero). Um sinal por segundo, no máximo, e a até 160 blocos de você
public final class SinalDaParty {
  private static final int TICKS_ENTRE_SINAIS = SharedConstants.TICKS_PER_SECOND;
  private static final double LONGE_DEMAIS = 160;
  private static final Map<UUID, Long> ULTIMO = new HashMap<>();

  private SinalDaParty() {
  }

  public static void registrar() {
    PayloadTypeRegistry.playC2S().register(SinalDaPartyPayload.TIPO, SinalDaPartyPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(MarcaDaPartyPayload.TIPO, MarcaDaPartyPayload.STREAM_CODEC);
    ServerPlayNetworking.registerGlobalReceiver(SinalDaPartyPayload.TIPO,
        (pedido, contexto) -> sinalizou(contexto.player(), new Vec3(pedido.x(), pedido.y(), pedido.z())));
  }

  private static void sinalizou(ServerPlayer jogador, Vec3 ponto) {
    long agora = jogador.server.getTickCount();
    if (agora - ULTIMO.getOrDefault(jogador.getUUID(), Long.MIN_VALUE / 2) < TICKS_ENTRE_SINAIS
        || !Double.isFinite(ponto.x) || !Double.isFinite(ponto.y) || !Double.isFinite(ponto.z)
        || ponto.distanceTo(jogador.position()) > LONGE_DEMAIS) {
      return;
    }
    ULTIMO.put(jogador.getUUID(), agora);
    List<ServerPlayer> quemVe = new ArrayList<>();
    quemVe.add(jogador);
    for (ServerPlayer amigo : Parties.outrosOnline(jogador)) {
      if (amigo.level() == jogador.level()) {
        quemVe.add(amigo);
      }
    }
    MarcaDaPartyPayload marca = new MarcaDaPartyPayload(jogador.getGameProfile().getName(), ponto.x, ponto.y, ponto.z);
    for (ServerPlayer quem : quemVe) {
      if (ServerPlayNetworking.canSend(quem, MarcaDaPartyPayload.TIPO)) {
        ServerPlayNetworking.send(quem, marca);
      }
    }
    CobblemonCombatZA.LOGGER.info("PARTY: {} marcou um ponto ({} jogador(es) viram)", jogador.getGameProfile().getName(),
        quemVe.size());
  }

  public static void limpar() {
    ULTIMO.clear();
  }
}
