package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (a dupla): em quem o seu foco está travado agora (o número da entidade; -1 = sem foco). Vai
// quando ele muda. O Pokémon da dupla que luta sozinho ataca esse alvo (combate.Dupla)
public record FocoPayload(int alvo) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<FocoPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "foco"));

  public static final StreamCodec<ByteBuf, FocoPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,
      FocoPayload::alvo, FocoPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
