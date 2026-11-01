package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para os da party (01/11, a tecla de sinal): quem marcou e onde (o seu jogo mostra por uns segundos)
public record MarcaDaPartyPayload(String quem, double x, double y, double z) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<MarcaDaPartyPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "marca_da_party"));

  public static final StreamCodec<ByteBuf, MarcaDaPartyPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8, MarcaDaPartyPayload::quem, ByteBufCodecs.DOUBLE, MarcaDaPartyPayload::x,
      ByteBufCodecs.DOUBLE, MarcaDaPartyPayload::y, ByteBufCodecs.DOUBLE, MarcaDaPartyPayload::z, MarcaDaPartyPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
