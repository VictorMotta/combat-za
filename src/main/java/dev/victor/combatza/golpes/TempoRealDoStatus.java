package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class TempoRealDoStatus {
  private static final String CHAVE_ACABA_EM = CobblemonCombatZA.MOD_ID + ":status_acaba_em";
  private static final String CHAVE_SIGLA = CobblemonCombatZA.MOD_ID + ":status_sigla";
  private static final long MILISSEGUNDOS_POR_SEGUNDOS = 1000L;

  private TempoRealDoStatus() {
  }

  public static void atualizar(MinecraftServer servidor, long tick) {
    if (tick % SharedConstants.TICKS_PER_SECOND != 0) {
      return;
    }
    long agora = System.currentTimeMillis();
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
        conferir(pokemon, agora);
      }
      for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(jogador)) {
        conferir(pokemon, agora);
      }
    }
  }

  private static void conferir(Pokemon pokemon, long agora) {
    CompoundTag dados = pokemon.getPersistentData();
    PersistentStatusContainer status = pokemon.getStatus();
    if (status == null) {
      esquecer(pokemon, dados);
      return;
    }
    String sigla = status.getStatus().getShowdownName();
    if (!sigla.equals(dados.getString(CHAVE_SIGLA))) {
      dados.putLong(CHAVE_ACABA_EM, agora + status.getSecondsLeft() * MILISSEGUNDOS_POR_SEGUNDOS);
      dados.putString(CHAVE_SIGLA, sigla);
      pokemon.onChange(null);
      return;
    }
    if (agora >= dados.getLong(CHAVE_ACABA_EM)) {
      CobblemonCombatZA.LOGGER.info("STATUS ACABOU (tempo real): {} ({})", pokemon.getSpecies().getName(), sigla);
      pokemon.setStatus(null);
      esquecer(pokemon, dados);
    }
  }

  private static void esquecer(Pokemon pokemon, CompoundTag dados) {
    if (dados.contains(CHAVE_SIGLA)) {
      dados.remove(CHAVE_ACABA_EM);
      dados.remove(CHAVE_SIGLA);
      pokemon.onChange(null);
    }
  }
}
