package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (7.7): a tecla "Trocar o lado do Pokémon". Não leva nada: o servidor troca direita por
// esquerda e guarda no seu jogador (ia.Parceiro)
public record TrocarOLadoPayload() implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<TrocarOLadoPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "trocar_o_lado"));

  public static final StreamCodec<ByteBuf, TrocarOLadoPayload> STREAM_CODEC = StreamCodec.unit(new TrocarOLadoPayload());

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
