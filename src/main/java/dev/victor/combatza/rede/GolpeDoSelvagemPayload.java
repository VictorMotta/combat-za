package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para quem vê o selvagem (7.2c): o golpe que ele começou a carregar (o nome do golpe no Cobblemon, o tipo e
// quantos ticks faltam para o impacto), para a faixa colorida com a barra de carga na placa dele. "ticks" 0 = a carga
// acabou (o impacto, ou ele perdeu o golpe)
public record GolpeDoSelvagemPayload(int entidade, String golpe, String tipo, int ticks) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<GolpeDoSelvagemPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "golpe_do_selvagem"));

  public static final StreamCodec<ByteBuf, GolpeDoSelvagemPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, GolpeDoSelvagemPayload::entidade,
      ByteBufCodecs.STRING_UTF8, GolpeDoSelvagemPayload::golpe,
      ByteBufCodecs.STRING_UTF8, GolpeDoSelvagemPayload::tipo,
      ByteBufCodecs.VAR_INT, GolpeDoSelvagemPayload::ticks,
      GolpeDoSelvagemPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
