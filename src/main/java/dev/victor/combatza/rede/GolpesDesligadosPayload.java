package dev.victor.combatza.rede;

import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para o dono (21/10, golpes de status que faltavam): quais golpes deste Pokémon estão desligados por um efeito da
// batalha (a Provocação, o Bis). "mascara": o bit 0 é o 1º golpe, o bit 3 o 4º; 0 = nenhum. O seu jogo risca no losango
// (golpes.GolpesDesligados, client.GolpesDesligadosNoCliente)
public record GolpesDesligadosPayload(UUID pokemon, int mascara) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<GolpesDesligadosPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "golpes_desligados"));

  public static final StreamCodec<ByteBuf, GolpesDesligadosPayload> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, GolpesDesligadosPayload::pokemon,
      ByteBufCodecs.VAR_INT, GolpesDesligadosPayload::mascara,
      GolpesDesligadosPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
