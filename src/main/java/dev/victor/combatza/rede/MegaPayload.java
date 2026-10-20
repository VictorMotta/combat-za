package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (8.4): a tecla da Mega. Não leva nada: o servidor confere a batalha, o medidor e o lutador
// (mega.Mega), como em todo pedido nosso
public record MegaPayload() implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<MegaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "mega"));

  public static final StreamCodec<ByteBuf, MegaPayload> STREAM_CODEC = StreamCodec.unit(new MegaPayload());

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
