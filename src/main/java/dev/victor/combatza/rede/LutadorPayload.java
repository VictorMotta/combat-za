package dev.victor.combatza.rede;

import java.util.Optional;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para o cliente: "em batalha, o seu lutador é este Pokémon". Vazio = sem lutador (fora de batalha, ou com o
// lugar vago): aí a HUD volta a seguir a setinha. O Optional vira um "tem ou não tem" na rede (ByteBufCodecs.optional)
public record LutadorPayload(Optional<UUID> pokemon) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<LutadorPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "lutador"));

  public static final StreamCodec<ByteBuf, LutadorPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), LutadorPayload::pokemon,
      LutadorPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
