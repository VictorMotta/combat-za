package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para quem está perto (01/11, dele: "queria algo mais visual quando não pega e errou, a pessoa está tão entretida
// que nem lê o histórico"): um aviso grande no mundo, em cima de quem levou (ou de onde o golpe caiu): "errou", "desviou",
// "imune", "protegeu"... (a chave da frase, client.AvisosNaTela)
public record AvisoNoAlvoPayload(double x, double y, double z, String aviso) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<AvisoNoAlvoPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "aviso_no_alvo"));

  public static final StreamCodec<ByteBuf, AvisoNoAlvoPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.DOUBLE, AvisoNoAlvoPayload::x, ByteBufCodecs.DOUBLE, AvisoNoAlvoPayload::y,
      ByteBufCodecs.DOUBLE, AvisoNoAlvoPayload::z, ByteBufCodecs.STRING_UTF8, AvisoNoAlvoPayload::aviso,
      AvisoNoAlvoPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
