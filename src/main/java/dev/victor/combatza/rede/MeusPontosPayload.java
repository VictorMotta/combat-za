package dev.victor.combatza.rede;

import java.util.List;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do seu jogo para o servidor (client.PontosNoXaero → party.PontosDaParty): os waypoints do conjunto "Party" do seu Xaero num
// mundo ("minecraft:overworld"), para irem ao mapa da sua party. Vai quando o conjunto muda (e ao entrar no servidor)
public record MeusPontosPayload(String mundo, List<Ponto> pontos) implements CustomPacketPayload {
  // Os limites (o seu jogo corta antes de mandar; o servidor confere de novo): o pacote do jogo para o servidor tem de ser pequeno
  public static final int MAXIMO = 50;
  public static final int MAXIMO_DO_NOME = 32;
  public static final int MAXIMO_DAS_INICIAIS = 3;

  // Um waypoint: o nome, as letras do ícone, onde está e a cor (o número da cor no Xaero, de 0 a 20)
  public record Ponto(String nome, String iniciais, int x, int y, int z, int cor) {
    public static final StreamCodec<ByteBuf, Ponto> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, Ponto::nome,
        ByteBufCodecs.STRING_UTF8, Ponto::iniciais,
        ByteBufCodecs.VAR_INT, Ponto::x,
        ByteBufCodecs.VAR_INT, Ponto::y,
        ByteBufCodecs.VAR_INT, Ponto::z,
        ByteBufCodecs.VAR_INT, Ponto::cor,
        Ponto::new);
  }

  public static final CustomPacketPayload.Type<MeusPontosPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "meus_pontos"));

  public static final StreamCodec<ByteBuf, MeusPontosPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8, MeusPontosPayload::mundo,
      Ponto.STREAM_CODEC.apply(ByteBufCodecs.list()), MeusPontosPayload::pontos,
      MeusPontosPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
