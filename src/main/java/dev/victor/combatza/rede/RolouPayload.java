package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para quem está vendo um jogador: "ele rolou nesta direção" (o número da entidade e o giro em graus)
public record RolouPayload(int entidade, float direcao) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<RolouPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "rolou"));

  public static final StreamCodec<ByteBuf, RolouPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, RolouPayload::entidade,
      ByteBufCodecs.FLOAT, RolouPayload::direcao,
      RolouPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
