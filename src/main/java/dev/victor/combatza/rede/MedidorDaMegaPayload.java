package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para o jogador (8.4): o medidor da Mega. "visivel": na batalha e com o bracelete; "carga": de 0 a 1 (na
// Mega, o tempo que falta); "megando": o Pokémon está Mega e o medidor esvazia; "pausado": o Mega está na bola (o relógio
// parou); "pronto": cheio e o lutador pode megar agora (o medidor brilha)
public record MedidorDaMegaPayload(boolean visivel, float carga, boolean megando, boolean pausado, boolean pronto)
    implements CustomPacketPayload {

  public static final MedidorDaMegaPayload ESCONDIDO = new MedidorDaMegaPayload(false, 0f, false, false, false);

  public static final CustomPacketPayload.Type<MedidorDaMegaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "medidor_da_mega"));

  public static final StreamCodec<ByteBuf, MedidorDaMegaPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.BOOL, MedidorDaMegaPayload::visivel,
      ByteBufCodecs.FLOAT, MedidorDaMegaPayload::carga,
      ByteBufCodecs.BOOL, MedidorDaMegaPayload::megando,
      ByteBufCodecs.BOOL, MedidorDaMegaPayload::pausado,
      ByteBufCodecs.BOOL, MedidorDaMegaPayload::pronto,
      MedidorDaMegaPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
