package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (01/11, a tecla de sinal da party): o ponto do mundo para onde você estava olhando
public record SinalDaPartyPayload(double x, double y, double z) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<SinalDaPartyPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "sinal_da_party"));

  public static final StreamCodec<ByteBuf, SinalDaPartyPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.DOUBLE, SinalDaPartyPayload::x, ByteBufCodecs.DOUBLE, SinalDaPartyPayload::y,
      ByteBufCodecs.DOUBLE, SinalDaPartyPayload::z, SinalDaPartyPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
