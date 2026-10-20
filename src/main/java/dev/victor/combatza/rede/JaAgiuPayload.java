package dev.victor.combatza.rede;

import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para o dono: "este Pokémon já agiu desde que saiu da bola" (true) ou "voltou ao começo" (false). O losango
// apaga os golpes que só saem logo que o Pokémon entra (o Fingimento)
public record JaAgiuPayload(UUID pokemon, boolean jaAgiu) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<JaAgiuPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "ja_agiu"));

  public static final StreamCodec<ByteBuf, JaAgiuPayload> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, JaAgiuPayload::pokemon,
      ByteBufCodecs.BOOL, JaAgiuPayload::jaAgiu,
      JaAgiuPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
