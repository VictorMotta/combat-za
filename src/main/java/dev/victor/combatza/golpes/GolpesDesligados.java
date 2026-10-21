package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.rede.GolpesDesligadosPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 21/10 (golpes de status que faltavam): os golpes DESLIGADOS por um efeito da batalha: a Provocação (os de status) e o Bis
// (todos menos o preso). O servidor recusa (o seu aperto e a escolha da IA) e o seu jogo risca no losango, como o golpe preso
// pela Escolha (client.GolpesDesligadosNoCliente). Um lugar só, para os próximos (Desativar, Tormento, Bloqueio de Cura...)
public class GolpesDesligados {
  // O que o dono de cada Pokémon já sabe (a máscara: o bit 0 é o 1º golpe, o bit 3 o 4º)
  private static final Map<UUID, Integer> ENVIADOS = new HashMap<>();

  private GolpesDesligados() {
  }

  public static boolean bloqueia(Pokemon pokemon, int slot, Move golpe) {
    return Provocacao.bloqueia(pokemon, golpe) || Bis.bloqueia(pokemon, slot);
  }

  // O seu aperto (ControleDeGolpes.usarGolpe): o golpe desligado não sai, e a barra de ação diz por quê. Sem recarga, como
  // no golpe preso pela Escolha. true = recusou
  public static boolean recusou(ServerPlayer jogador, Pokemon pokemon, int slot, Move golpe) {
    Component porque = null;
    if (Provocacao.bloqueia(pokemon, golpe)) {
      porque = Component.translatable("cobblemon.battle.cant.taunt", pokemon.getDisplayName(false),
          golpe.getDisplayName());
    } else if (Bis.bloqueia(pokemon, slot)) {
      Move preso = Bis.golpePreso(pokemon);
      porque = Component.translatable("cobblemon_combat_za.golpe.so_o_bis", pokemon.getDisplayName(false),
          preso == null ? golpe.getDisplayName() : preso.getDisplayName());
    }
    if (porque == null) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("DESLIGADO: {} não pode usar {} ({})", pokemon.getSpecies().getName(), golpe.getName(),
        porque.getString());
    Feedback.barraDeAcao(jogador, Component.literal("🚫 ").append(porque));
    return true;
  }

  // Começou ou acabou a Provocação ou o Bis: o dono fica sabendo quais riscar (só quando muda)
  public static void avisarODono(Pokemon pokemon) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    if (dono == null) {
      // Dono fora do jogo: o cliente dele esquece tudo ao sair; quando ele voltar, o próximo aviso vai inteiro
      ENVIADOS.remove(pokemon.getUuid());
      return;
    }
    int mascara = mascara(pokemon);
    Integer antes = ENVIADOS.get(pokemon.getUuid());
    if (antes == null ? mascara == 0 : antes == mascara) {
      return;
    }
    if (mascara == 0) {
      ENVIADOS.remove(pokemon.getUuid());
    } else {
      ENVIADOS.put(pokemon.getUuid(), mascara);
    }
    if (ServerPlayNetworking.canSend(dono, GolpesDesligadosPayload.TIPO)) {
      ServerPlayNetworking.send(dono, new GolpesDesligadosPayload(pokemon.getUuid(), mascara));
    }
  }

  private static int mascara(Pokemon pokemon) {
    int mascara = 0;
    for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
      Move golpe = pokemon.getMoveSet().get(slot);
      if (golpe != null && bloqueia(pokemon, slot, golpe)) {
        mascara |= 1 << slot;
      }
    }
    return mascara;
  }

  public static void limpar() {
    ENVIADOS.clear();
  }
}
