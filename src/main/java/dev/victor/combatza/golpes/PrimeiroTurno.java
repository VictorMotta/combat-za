package dev.victor.combatza.golpes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.rede.JaAgiuPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class PrimeiroTurno {
  private static final Set<String> SO_NO_PRIMEIRO_TURNO = Set.of("fakeout", "firstimpression", "matblock");
  private static final Set<UUID> JA_AGIRAM = new HashSet<>();

  private PrimeiroTurno() {
  }

  // O losango (HudDeCombate) também pergunta, para apagar esses golpes depois da primeira ação
  public static boolean soNoPrimeiroTurno(Move golpe) {
    return SO_NO_PRIMEIRO_TURNO.contains(golpe.getName());
  }

  public static boolean passouDaHora(Pokemon pokemon, Move golpe) {
    return soNoPrimeiroTurno(golpe) && JA_AGIRAM.contains(pokemon.getUuid());
  }

  public static void agiu(Pokemon pokemon) {
    if (JA_AGIRAM.add(pokemon.getUuid())) {
      avisarODono(pokemon, true);
    }
  }

  public static void esquecer(Pokemon pokemon) {
    if (JA_AGIRAM.remove(pokemon.getUuid())) {
      avisarODono(pokemon, false);
    }
  }

  // Só o servidor sabe quem já agiu: a HUD do dono fica sabendo quando muda (JaAgiuPayload)
  private static void avisarODono(Pokemon pokemon, boolean jaAgiu) {
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (dono != null && ServerPlayNetworking.canSend(dono, JaAgiuPayload.TIPO)) {
      ServerPlayNetworking.send(dono, new JaAgiuPayload(pokemon.getUuid(), jaAgiu));
    }
  }

  public static void esquecerTime(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      esquecer(pokemon);
    }
  }

  public static void limpar() {
    JA_AGIRAM.clear();
  }
}
