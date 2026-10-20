package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (7.7): a vaga do time em que está a setinha. A setinha é só do cliente, no Cobblemon; o
// servidor precisa dela para saber quem anda ao seu lado (ia.Parceiro). Vai quando ela muda e quando você entra no mundo
public record SetinhaPayload(int vaga) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<SetinhaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "setinha"));

  public static final StreamCodec<ByteBuf, SetinhaPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, SetinhaPayload::vaga,
      SetinhaPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
