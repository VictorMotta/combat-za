package dev.victor.combatza.rede;

import java.util.List;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do SERVIDOR para o CLIENTE: o que a placa precisa e o cliente não sabe sozinho sobre os selvagens perto do jogador
// (nível, gênero, status e estágios), e quais deles estão em combate com ele. Vai a lista inteira: o cliente troca a dele
// pela nova
public record PlacasPayload(List<Placa> placas) implements CustomPacketPayload {

  // Um estágio que está valendo: o atributo ("atk", "def"...) e quanto (-6 a +6, nunca 0)
  public record Estagio(String atributo, int valor) {
    public static final StreamCodec<ByteBuf, Estagio> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, Estagio::atributo,
        ByteBufCodecs.VAR_INT, Estagio::valor,
        Estagio::new);
  }

  // 8.1: "setas" = a chance de captura do Z-A para este jogador, de 0 a 4 (ChanceDeCaptura); 8.1d: "brilha" = 99% ou mais.
  // Etapa 8: "irritado" (a bola falhou: o ícone de raiva) e "desmaio" (caído no chão: os segundos para a bola; −1 = não está)
  public record Placa(int entidade, int nivel, String genero, String status, boolean emCombate,
      List<Estagio> estagios, int setas, boolean brilha, boolean irritado, int desmaio) {
    // O StreamCodec.composite do Minecraft 1.21.1 vai só até 6 campos; com mais, o codec é escrito à mão: cada campo, na
    // mesma ordem, na ida e na volta
    private static final StreamCodec<ByteBuf, List<Estagio>> ESTAGIOS = Estagio.STREAM_CODEC.apply(ByteBufCodecs.list());
    public static final StreamCodec<ByteBuf, Placa> STREAM_CODEC = StreamCodec.of(
        (saida, placa) -> {
          ByteBufCodecs.VAR_INT.encode(saida, placa.entidade());
          ByteBufCodecs.VAR_INT.encode(saida, placa.nivel());
          ByteBufCodecs.STRING_UTF8.encode(saida, placa.genero());
          ByteBufCodecs.STRING_UTF8.encode(saida, placa.status());
          ByteBufCodecs.BOOL.encode(saida, placa.emCombate());
          ESTAGIOS.encode(saida, placa.estagios());
          ByteBufCodecs.VAR_INT.encode(saida, placa.setas());
          ByteBufCodecs.BOOL.encode(saida, placa.brilha());
          ByteBufCodecs.BOOL.encode(saida, placa.irritado());
          ByteBufCodecs.INT.encode(saida, placa.desmaio());
        },
        entrada -> new Placa(ByteBufCodecs.VAR_INT.decode(entrada), ByteBufCodecs.VAR_INT.decode(entrada),
            ByteBufCodecs.STRING_UTF8.decode(entrada), ByteBufCodecs.STRING_UTF8.decode(entrada),
            ByteBufCodecs.BOOL.decode(entrada), ESTAGIOS.decode(entrada), ByteBufCodecs.VAR_INT.decode(entrada),
            ByteBufCodecs.BOOL.decode(entrada), ByteBufCodecs.BOOL.decode(entrada), ByteBufCodecs.INT.decode(entrada)));
  }

  public static final CustomPacketPayload.Type<PlacasPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "placas"));

  // Uma lista se escreve como "quantos são" e depois cada um com o codec da Placa
  public static final StreamCodec<ByteBuf, PlacasPayload> STREAM_CODEC = StreamCodec.composite(
      Placa.STREAM_CODEC.apply(ByteBufCodecs.list()), PlacasPayload::placas,
      PlacasPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
