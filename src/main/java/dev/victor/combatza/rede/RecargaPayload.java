package dev.victor.combatza.rede;

import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// O primeiro pacote do SERVIDOR para o CLIENTE: "o golpe deste slot, deste Pokémon, entrou em recarga por N ticks"
public record RecargaPayload(UUID pokemon, int slotGolpe, int ticks) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<RecargaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "recarga"));

  public static final StreamCodec<ByteBuf, RecargaPayload> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, RecargaPayload::pokemon,
      ByteBufCodecs.VAR_INT, RecargaPayload::slotGolpe,
      ByteBufCodecs.VAR_INT, RecargaPayload::ticks,
      RecargaPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
