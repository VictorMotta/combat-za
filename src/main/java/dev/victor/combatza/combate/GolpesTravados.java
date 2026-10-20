package dev.victor.combatza.combate;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Feedback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// Dele, 18/10: "não pode deixar trocar de habilidade estando em um combate, porque senão eu abriria o painel e ficava
// trocando para tirar só defesa, depois trocaria para dar dano". Em combate, os golpes dos Pokémon do jogador não mudam: nem
// a ordem dos 4 (a recarga é guardada por vaga do losango: trocar a ordem tiraria um golpe da recarga), nem um golpe
// guardado entrando no lugar de outro. Os dois pedidos do resumo do Pokémon passam por aqui (TrocarAOrdemDosGolpesMixin e
// TrocarGolpeGuardadoMixin)
public class GolpesTravados {
  private GolpesTravados() {
  }

  // true = recusar o pedido. O resumo do Pokémon recebe de volta os golpes de verdade (update), para não mostrar uma troca
  // que não aconteceu
  public static boolean recusar(ServerPlayer jogador) {
    if (!GerenciarCombates.estaEmCombate(jogador)) {
      return false;
    }
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      pokemon.getMoveSet().update();
    }
    CobblemonCombatZA.LOGGER.info("GOLPES TRAVADOS: {} tentou trocar golpes em combate",
        jogador.getGameProfile().getName());
    Feedback.barraDeAcao(jogador, Component.literal("🔒 Em combate, os golpes não podem ser trocados!"));
    return true;
  }
}
