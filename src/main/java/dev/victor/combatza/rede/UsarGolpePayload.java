package dev.victor.combatza.rede;

import java.util.Optional;

import org.joml.Vector3f;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// "alvoTravado" = o número da entidade do foco (lock-on) do cliente, ou -1 sem foco: com ele, o golpe vai nesse alvo
// mesmo que o jogador não esteja virado para ele (na 3ª pessoa, a câmera e o jogador podem olhar para lados diferentes).
// 6.11: "marcador" = para onde o Pokémon vai antes de soltar o golpe, com o foco: um deslocamento no chão a partir dos pés do
// alvo travado (o y não conta). Vazio = o golpe sai de onde ele está, como sempre
public record UsarGolpePayload(int slotGolpe, int slotPokemon, int alvoTravado, Optional<Vector3f> marcador)
    implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<UsarGolpePayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "usar_golpe"));

  public static final StreamCodec<ByteBuf, UsarGolpePayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, UsarGolpePayload::slotGolpe,
      ByteBufCodecs.VAR_INT, UsarGolpePayload::slotPokemon,
      ByteBufCodecs.VAR_INT, UsarGolpePayload::alvoTravado,
      ByteBufCodecs.optional(ByteBufCodecs.VECTOR3F), UsarGolpePayload::marcador,
      UsarGolpePayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}