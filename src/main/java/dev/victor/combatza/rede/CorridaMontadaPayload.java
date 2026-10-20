package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para os jogadores (7b): a montaria começou ("correndo") ou parou de correr sozinha num golpe de contato. Leva a
// posição dela no servidor, para o seu jogo pôr a montaria no lugar certo sem tranco (golpes.CorridaMontada)
public record CorridaMontadaPayload(int montaria, boolean correndo, double x, double y, double z)
    implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<CorridaMontadaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "corrida_montada"));

  public static final StreamCodec<ByteBuf, CorridaMontadaPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, CorridaMontadaPayload::montaria,
      ByteBufCodecs.BOOL, CorridaMontadaPayload::correndo,
      ByteBufCodecs.DOUBLE, CorridaMontadaPayload::x,
      ByteBufCodecs.DOUBLE, CorridaMontadaPayload::y,
      ByteBufCodecs.DOUBLE, CorridaMontadaPayload::z,
      CorridaMontadaPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
