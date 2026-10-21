package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para quem desafiou (21/10, PvP): o desafio de batalha do Cobblemon saiu, e a tela do duelo abre para ele
// escolher quantos Pokémon cada um usa (client.TelaDoDuelo). "adversario" é o nome de quem foi desafiado; "maximo", até
// quantos dá (o menor time com vida dos dois); "sugerido", o número que já vale se ele não escolher. maximo 0 = feche a
// tela (o desafio acabou: recusado, cancelado ou expirou)
public record EscolhaDoDueloPayload(String adversario, int maximo, int sugerido) implements CustomPacketPayload {

  public static final int FECHAR = 0;

  public static final CustomPacketPayload.Type<EscolhaDoDueloPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "escolha_do_duelo"));

  public static final StreamCodec<ByteBuf, EscolhaDoDueloPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8, EscolhaDoDueloPayload::adversario,
      ByteBufCodecs.VAR_INT, EscolhaDoDueloPayload::maximo,
      ByteBufCodecs.VAR_INT, EscolhaDoDueloPayload::sugerido,
      EscolhaDoDueloPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
