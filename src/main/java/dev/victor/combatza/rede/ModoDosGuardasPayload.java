package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (7.4): o modo que você escolheu na roda para os seus guardas ("passivo", "neutro" ou
// "agressivo"). O servidor confere e guarda no seu jogador (Guardas)
public record ModoDosGuardasPayload(String modo) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<ModoDosGuardasPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "modo_dos_guardas"));

  public static final StreamCodec<ByteBuf, ModoDosGuardasPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8, ModoDosGuardasPayload::modo,
      ModoDosGuardasPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
