package dev.victor.combatza.rede;

import java.util.List;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para um jogador (7.1): os selvagens que estão te notando. "tipo" −1 = ainda notando (o "?" em cima dele e o
// olho no alto da tela acendendo, "progresso" de 0 a 1); 0, 1 e 2 = notou de vez e é agressivo, pacífico ou arisco (o
// susto do olho; 7.1b, pedido dele: sem "!")
public record SinaisPayload(List<Sinal> sinais) implements CustomPacketPayload {
  public static final int NOTANDO = -1;

  public record Sinal(int entidade, float progresso, int tipo) {
    public static final StreamCodec<ByteBuf, Sinal> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, Sinal::entidade,
        ByteBufCodecs.FLOAT, Sinal::progresso,
        ByteBufCodecs.VAR_INT, Sinal::tipo,
        Sinal::new);
  }

  public static final CustomPacketPayload.Type<SinaisPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "sinais"));

  public static final StreamCodec<ByteBuf, SinaisPayload> STREAM_CODEC = StreamCodec.composite(
      Sinal.STREAM_CODEC.apply(ByteBufCodecs.list()), SinaisPayload::sinais,
      SinaisPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
