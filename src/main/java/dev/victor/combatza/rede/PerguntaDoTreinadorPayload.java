package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para o jogador (21/10, treinadores): "este treinador do RCT quer batalhar; em tempo real ou por turnos?". O
// seu jogo abre a telinha da escolha (client.TelaDoTreinador) com o nome do treinador
public record PerguntaDoTreinadorPayload(Component treinador) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<PerguntaDoTreinadorPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "pergunta_do_treinador"));

  public static final StreamCodec<RegistryFriendlyByteBuf, PerguntaDoTreinadorPayload> STREAM_CODEC = StreamCodec.composite(
      ComponentSerialization.STREAM_CODEC, PerguntaDoTreinadorPayload::treinador,
      PerguntaDoTreinadorPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
