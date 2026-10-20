package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (7.7): o R na batalha, com a setinha num Pokémon que já está fora da bola: "este entra na
// luta". No lugar do R do Cobblemon, que recolheria ele (combate.Lutadores.entrarQuemEstaFora)
public record EntrarNaLutaPayload(int vaga) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<EntrarNaLutaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "entrar_na_luta"));

  public static final StreamCodec<ByteBuf, EntrarNaLutaPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, EntrarNaLutaPayload::vaga,
      EntrarNaLutaPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
