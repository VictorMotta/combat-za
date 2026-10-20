package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para quem vê o atacante (5.4g): "vire o corpo deste Pokémon para este alvo, por tantos ticks". O visual da
// batalha do Cobblemon sai na direção do CORPO no seu jogo, e o servidor não manda o giro do corpo
public record AlinharPayload(int atacante, int alvo, int ticks) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<AlinharPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "alinhar"));

  public static final StreamCodec<ByteBuf, AlinharPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, AlinharPayload::atacante,
      ByteBufCodecs.VAR_INT, AlinharPayload::alvo,
      ByteBufCodecs.VAR_INT, AlinharPayload::ticks,
      AlinharPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
