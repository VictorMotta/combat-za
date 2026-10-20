package dev.victor.combatza.rede;

import java.util.List;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// P5-10 (veto dele, 20/10: "um iconezinho com tempo contando, como se fosse uma passiva de RPG", dentro e fora da batalha):
// do servidor para cada jogador, os efeitos que valem para ele agora (o clima, o campo e as barreiras), para o seu jogo
// desenhar os ícones em cima do painel do time (client.PassivasNaTela). Vazio = nada valendo
public record PassivasPayload(List<Passiva> passivas) implements CustomPacketPayload {

  // Uma passiva: o nome ("chuva" vira "cobblemon_combat_za.campo.chuva" no seu jogo), de que lado (TODOS = o clima e o campo,
  // SEU_LADO, SELVAGENS), os segundos que faltam e quantos dura (SEM_RELOGIO = a chuva do mundo, que dura enquanto chover)
  public record Passiva(String nome, int lado, int segundos, int total) {
    public static final int TODOS = 0;
    public static final int SEU_LADO = 1;
    public static final int SELVAGENS = 2;
    public static final int SEM_RELOGIO = -1;
    public static final StreamCodec<ByteBuf, Passiva> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, Passiva::nome,
        ByteBufCodecs.VAR_INT, Passiva::lado,
        ByteBufCodecs.INT, Passiva::segundos,
        ByteBufCodecs.INT, Passiva::total,
        Passiva::new);
  }

  public static final CustomPacketPayload.Type<PassivasPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "passivas"));

  public static final StreamCodec<ByteBuf, PassivasPayload> STREAM_CODEC = StreamCodec.composite(
      Passiva.STREAM_CODEC.apply(ByteBufCodecs.list()), PassivasPayload::passivas,
      PassivasPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
