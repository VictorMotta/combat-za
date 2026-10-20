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

// Do servidor para cada jogador (party.Parties): os OUTROS membros da party dele, para o quadrinho da party e o brilho através
// da parede no seu jogo (vazio = sem party); quem te convidou ("" = nenhum convite), se você é o líder (a tela da party) e se a
// party é a do Open Parties and Claims (aí o próprio OPAC já põe a party no mapa do Xaero: o nosso não repete)
public record PartyPayload(List<Membro> membros, String convite, boolean souLider, boolean doOpac)
    implements CustomPacketPayload {

  // Onde ele está: a posição e o mundo ("minecraft:overworld"; "" = offline). O mapa do Xaero mostra a party longe (o seu jogo
  // só sabe a posição de quem está perto); noutro mundo, o quadrinho fica apagado
  public record Onde(double x, double y, double z, String mundo) {
    public static final StreamCodec<ByteBuf, Onde> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, Onde::x,
        ByteBufCodecs.DOUBLE, Onde::y,
        ByteBufCodecs.DOUBLE, Onde::z,
        ByteBufCodecs.STRING_UTF8, Onde::mundo,
        Onde::new);
  }

  // Um membro: quem é, o nome, a vida (0 a 1, de 5 em 5%), se está numa batalha, se é o líder e onde está
  public record Membro(UUID jogador, String nome, float vida, boolean emBatalha, boolean lider, Onde onde) {
    public static final StreamCodec<ByteBuf, Membro> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, Membro::jogador,
        ByteBufCodecs.STRING_UTF8, Membro::nome,
        ByteBufCodecs.FLOAT, Membro::vida,
        ByteBufCodecs.BOOL, Membro::emBatalha,
        ByteBufCodecs.BOOL, Membro::lider,
        Onde.STREAM_CODEC, Membro::onde,
        Membro::new);
  }

  public static final CustomPacketPayload.Type<PartyPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "party"));

  public static final StreamCodec<ByteBuf, PartyPayload> STREAM_CODEC = StreamCodec.composite(
      Membro.STREAM_CODEC.apply(ByteBufCodecs.list()), PartyPayload::membros,
      ByteBufCodecs.STRING_UTF8, PartyPayload::convite,
      ByteBufCodecs.BOOL, PartyPayload::souLider,
      ByteBufCodecs.BOOL, PartyPayload::doOpac,
      PartyPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
