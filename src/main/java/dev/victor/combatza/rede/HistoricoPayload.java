package dev.victor.combatza.rede;

import java.util.List;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Do SERVIDOR para o CLIENTE: uma linha do histórico da batalha. Vai a frase e de quem ela fala: a espécie e os aspectos
// (para o cliente desenhar o rosto, mesmo que o Pokémon já tenha sumido; vazio quando não é Pokémon) e se é selvagem
public record HistoricoPayload(Component texto, String especie, List<String> aspectos, boolean doSelvagem)
    implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<HistoricoPayload> TIPO = new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "historico"));

  // A frase vai com o codec de Component do Minecraft: as traduções ("cobblemon.battle.used_move") viram texto lá no
  // cliente, no idioma de quem joga
  public static final StreamCodec<RegistryFriendlyByteBuf, HistoricoPayload> STREAM_CODEC = StreamCodec.composite(
      ComponentSerialization.STREAM_CODEC, HistoricoPayload::texto,
      ByteBufCodecs.STRING_UTF8, HistoricoPayload::especie,
      ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), HistoricoPayload::aspectos,
      ByteBufCodecs.BOOL, HistoricoPayload::doSelvagem,
      HistoricoPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return TIPO;
  }
}
