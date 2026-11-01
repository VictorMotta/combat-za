package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (a dupla): a tecla "Trocar o controle na dupla". Não leva nada: o servidor passa as suas teclas
// para o outro Pokémon da dupla, e o que você comandava passa a lutar sozinho (combate.Dupla)
public record AlternarNaDuplaPayload() implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<AlternarNaDuplaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "alternar_na_dupla"));

  public static final StreamCodec<ByteBuf, AlternarNaDuplaPayload> STREAM_CODEC = StreamCodec
      .unit(new AlternarNaDuplaPayload());

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
