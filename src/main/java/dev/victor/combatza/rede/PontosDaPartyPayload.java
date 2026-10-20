package dev.victor.combatza.rede;

import java.util.List;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para cada jogador (party.PontosDaParty → client.PontosNoXaero): os waypoints do conjunto "Party" dos OUTROS da
// sua party (os seus você já tem no seu Xaero), com o dono e o mundo de cada um. Vazio = sem party (some tudo do mapa)
public record PontosDaPartyPayload(List<Marca> marcas) implements CustomPacketPayload {

  // Um waypoint de alguém da party: de quem é (o UUID e o nome, para o "Casa (Victor)"), em que mundo e o ponto
  public record Marca(UUID dono, String nomeDoDono, String mundo, MeusPontosPayload.Ponto ponto) {
    public static final StreamCodec<ByteBuf, Marca> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, Marca::dono,
        ByteBufCodecs.STRING_UTF8, Marca::nomeDoDono,
        ByteBufCodecs.STRING_UTF8, Marca::mundo,
        MeusPontosPayload.Ponto.STREAM_CODEC, Marca::ponto,
        Marca::new);
  }

  public static final CustomPacketPayload.Type<PontosDaPartyPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "pontos_da_party"));

  public static final StreamCodec<ByteBuf, PontosDaPartyPayload> STREAM_CODEC = StreamCodec.composite(
      Marca.STREAM_CODEC.apply(ByteBufCodecs.list()), PontosDaPartyPayload::marcas,
      PontosDaPartyPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
