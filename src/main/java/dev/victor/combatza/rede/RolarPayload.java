package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor: "rolei, nesta direção". A direção é um giro em graus, como o do Minecraft (0 = sul, 90 =
// oeste); o servidor só repassa para quem está vendo, para a cambalhota sair para o lado certo
public record RolarPayload(float direcao) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<RolarPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "rolar"));

  public static final StreamCodec<ByteBuf, RolarPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.FLOAT, RolarPayload::direcao,
      RolarPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
