package dev.victor.combatza.rede;

import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para o dono: quantas cargas sobram num golpe de vários acertos (5.4b). 0 = nenhuma guardada
public record CargasPayload(UUID pokemon, int slotGolpe, int restantes) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<CargasPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "cargas"));

  public static final StreamCodec<ByteBuf, CargasPayload> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, CargasPayload::pokemon,
      ByteBufCodecs.VAR_INT, CargasPayload::slotGolpe,
      ByteBufCodecs.VAR_INT, CargasPayload::restantes,
      CargasPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
