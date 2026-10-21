package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (21/10, treinadores): o que o jogador escolheu na telinha do treinador (TEMPO_REAL, POR_TURNOS
// ou FECHOU: fechou a tela, e nenhuma batalha começa)
public record RespostaDoTreinadorPayload(int escolha) implements CustomPacketPayload {
  public static final int TEMPO_REAL = 0;
  public static final int POR_TURNOS = 1;
  public static final int FECHOU = 2;

  public static final CustomPacketPayload.Type<RespostaDoTreinadorPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "resposta_do_treinador"));

  public static final StreamCodec<ByteBuf, RespostaDoTreinadorPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, RespostaDoTreinadorPayload::escolha,
      RespostaDoTreinadorPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
