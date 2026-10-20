package dev.victor.combatza.rede;

import dev.victor.combatza.CobblemonCombatZA;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do cliente para o servidor (7.9): o botão "todos de guarda" (um toque no U). Não leva nada: o servidor liga ou desliga,
// só fora da batalha (ia.Parceiro)
public record TodosDeGuardaPayload() implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<TodosDeGuardaPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "todos_de_guarda"));

  public static final StreamCodec<ByteBuf, TodosDeGuardaPayload> STREAM_CODEC =
      StreamCodec.unit(new TodosDeGuardaPayload());

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
