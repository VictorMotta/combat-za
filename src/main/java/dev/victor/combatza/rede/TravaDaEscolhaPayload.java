package dev.victor.combatza.rede;

import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para o dono (9.1): o seu Pokémon com um item da Escolha travou no golpe "slot" (−1 = destravou). O seu jogo risca
// os outros três no losango (itens.TravaDaEscolha)
public record TravaDaEscolhaPayload(UUID pokemon, int slot) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<TravaDaEscolhaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "trava_da_escolha"));

  public static final StreamCodec<ByteBuf, TravaDaEscolhaPayload> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, TravaDaEscolhaPayload::pokemon,
      ByteBufCodecs.INT, TravaDaEscolhaPayload::slot,
      TravaDaEscolhaPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
