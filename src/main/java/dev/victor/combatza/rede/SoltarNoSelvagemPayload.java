package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor: "o R: jogue o Pokémon desta vaga do time contra este selvagem" (no lugar do pedido de
// batalha por turnos do Cobblemon)
public record SoltarNoSelvagemPayload(int slotPokemon, int selvagem) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<SoltarNoSelvagemPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "soltar_no_selvagem"));

  public static final StreamCodec<ByteBuf, SoltarNoSelvagemPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, SoltarNoSelvagemPayload::slotPokemon,
      ByteBufCodecs.VAR_INT, SoltarNoSelvagemPayload::selvagem,
      SoltarNoSelvagemPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
