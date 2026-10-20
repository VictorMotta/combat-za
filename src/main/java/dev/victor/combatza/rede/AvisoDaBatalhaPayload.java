package dev.victor.combatza.rede;

import java.util.List;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do servidor para o jogador: o aviso de começo e de fim da batalha, que fica acima do histórico. O título ("Batalha!",
// "Fim da batalha"), a frase ("Contra Watchog", "Você venceu!"), o tipo (dá a cor: comecou, venceu, perdeu, acabou) e
// os rostos dos selvagens. "ganhos" (8.2b): uma linha por Pokémon, com o XP que ele ganhou ("Pikachu: +120 XP")
public record AvisoDaBatalhaPayload(Component titulo, Component texto, String tipo, List<Rosto> rostos,
    List<Component> ganhos) implements CustomPacketPayload {

  public record Rosto(String especie, List<String> aspectos) {
    public static final StreamCodec<ByteBuf, Rosto> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, Rosto::especie,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Rosto::aspectos,
        Rosto::new);
  }

  public static final CustomPacketPayload.Type<AvisoDaBatalhaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "aviso_da_batalha"));

  public static final StreamCodec<RegistryFriendlyByteBuf, AvisoDaBatalhaPayload> STREAM_CODEC = StreamCodec.composite(
      ComponentSerialization.STREAM_CODEC, AvisoDaBatalhaPayload::titulo,
      ComponentSerialization.STREAM_CODEC, AvisoDaBatalhaPayload::texto,
      ByteBufCodecs.STRING_UTF8, AvisoDaBatalhaPayload::tipo,
      Rosto.STREAM_CODEC.apply(ByteBufCodecs.list()), AvisoDaBatalhaPayload::rostos,
      ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), AvisoDaBatalhaPayload::ganhos,
      AvisoDaBatalhaPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
