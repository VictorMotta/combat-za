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

// Do servidor para quem está numa batalha: quem está nela. Cada jogador (o rosto vem da skin, pelo UUID) com o lutador
// dele, e os selvagens. Vazio = você não está mais em batalha. (O clima, o campo e as barreiras, que a 5b.1 pôs aqui, viraram
// os ícones das passivas na P5-10: rede.PassivasPayload)
public record PainelDaBatalhaPayload(List<Lado> jogadores, List<Foto> selvagens) implements CustomPacketPayload {

  // Um Pokémon no painel: a espécie e os aspectos (para a foto), a vida (0 a 1, a do servidor), se a foto é grande (o
  // lutador, os selvagens; os guardas, que eram as pequenas, saíram do painel na 7.8), o número da entidade no mundo (-1 =
  // na bola) e o UUID do Pokémon.
  // Com os dois últimos, o cliente lê a vida da mesma fonte que o resto da HUD (6.9c)
  public record Foto(String especie, List<String> aspectos, float vida, boolean grande, int entidade, UUID pokemon) {
    public static final StreamCodec<ByteBuf, Foto> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, Foto::especie,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Foto::aspectos,
        ByteBufCodecs.FLOAT, Foto::vida,
        ByteBufCodecs.BOOL, Foto::grande,
        ByteBufCodecs.VAR_INT, Foto::entidade,
        UUIDUtil.STREAM_CODEC, Foto::pokemon,
        Foto::new);
  }

  public record Lado(UUID jogador, String nome, List<Foto> pokemons) {
    public static final StreamCodec<ByteBuf, Lado> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, Lado::jogador,
        ByteBufCodecs.STRING_UTF8, Lado::nome,
        Foto.STREAM_CODEC.apply(ByteBufCodecs.list()), Lado::pokemons,
        Lado::new);
  }

  public static final CustomPacketPayload.Type<PainelDaBatalhaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "painel_da_batalha"));

  public static final StreamCodec<ByteBuf, PainelDaBatalhaPayload> STREAM_CODEC = StreamCodec.composite(
      Lado.STREAM_CODEC.apply(ByteBufCodecs.list()), PainelDaBatalhaPayload::jogadores,
      Foto.STREAM_CODEC.apply(ByteBufCodecs.list()), PainelDaBatalhaPayload::selvagens,
      PainelDaBatalhaPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
