package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (21/10, PvP): na tela do duelo, quem desafiou escolheu quantos Pokémon cada um usa (de 1 a 6).
// CANCELAR = ele desistiu do desafio (pvp.Duelos cancela o desafio do Cobblemon)
public record QuantosNoDueloPayload(int quantos) implements CustomPacketPayload {

  public static final int CANCELAR = 0;

  public static final CustomPacketPayload.Type<QuantosNoDueloPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "quantos_no_duelo"));

  public static final StreamCodec<ByteBuf, QuantosNoDueloPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, QuantosNoDueloPayload::quantos,
      QuantosNoDueloPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
