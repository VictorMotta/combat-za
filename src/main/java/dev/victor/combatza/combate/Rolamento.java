package dev.victor.combatza.combate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.rede.RolarPayload;
import dev.victor.combatza.rede.RolouPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

// O rolamento do treinador (pedido dele, 18/10; no Z-A é o botão Y, porque os selvagens atacam o treinador). Quem empurra
// o jogador é o cliente (no Minecraft, o movimento do jogador é do cliente: RolamentoNoCliente). Aqui o servidor confere
// a recarga, dá meio segundo de invencibilidade e avisa quem está vendo, para a cambalhota aparecer.
// Invencível = nenhum dano que tem um autor pega (Pokémon, monstro, flecha, explosão); queda, lava e afogamento continuam
public class Rolamento {
  private static final int TICKS_INVENCIVEL = 10;
  // Um pouco menos que o segundo do cliente: se o servidor atrasar um tick, o rolamento ainda vale
  private static final int TICKS_DE_RECARGA = 18;
  private static final float VOLUME = 0.7f;
  private static final float TOM = 1.3f;
  private static final int GRAOS_DE_POEIRA = 12;

  private static final Map<UUID, Long> INVENCIVEL_ATE = new HashMap<>();
  private static final Map<UUID, Long> PRONTO_NO_TICK = new HashMap<>();

  private Rolamento() {
  }

  public static void pediu(ServerPlayer jogador, RolarPayload pedido) {
    long agora = jogador.server.getTickCount();
    if (!jogador.isAlive() || jogador.isSpectator() || jogador.isPassenger() || !Float.isFinite(pedido.direcao())
        || agora < PRONTO_NO_TICK.getOrDefault(jogador.getUUID(), 0L)) {
      return;
    }
    PRONTO_NO_TICK.put(jogador.getUUID(), agora + TICKS_DE_RECARGA);
    INVENCIVEL_ATE.put(jogador.getUUID(), agora + TICKS_INVENCIVEL);
    poeiraESom(jogador);
    for (ServerPlayer quemVe : PlayerLookup.tracking(jogador)) {
      if (quemVe != jogador && ServerPlayNetworking.canSend(quemVe, RolouPayload.TIPO)) {
        ServerPlayNetworking.send(quemVe, new RolouPayload(jogador.getId(), pedido.direcao()));
      }
    }
  }

  // ALLOW_DAMAGE (CobblemonCombatZA.podeLevarDano): rolando, o jogador desvia de todo dano que tem um autor
  public static boolean estaDesviando(LivingEntity vitima, DamageSource fonte) {
    return vitima instanceof ServerPlayer jogador && (fonte.getEntity() != null || fonte.getDirectEntity() != null)
        && jogador.server.getTickCount() < INVENCIVEL_ATE.getOrDefault(jogador.getUUID(), 0L);
  }

  public static void esquecer(ServerPlayer jogador) {
    INVENCIVEL_ATE.remove(jogador.getUUID());
    PRONTO_NO_TICK.remove(jogador.getUUID());
  }

  public static void limpar() {
    INVENCIVEL_ATE.clear();
    PRONTO_NO_TICK.clear();
  }

  // Um "fuu" (o som do golpe que não pega) e a poeira do chão, como a da corrida
  private static void poeiraESom(ServerPlayer jogador) {
    ServerLevel nivel = jogador.serverLevel();
    nivel.playSound(null, jogador.getX(), jogador.getY(), jogador.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
        SoundSource.PLAYERS, VOLUME, TOM);
    BlockState chao = jogador.getBlockStateOn();
    if (!chao.isAir()) {
      nivel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chao), jogador.getX(), jogador.getY() + 0.1,
          jogador.getZ(), GRAOS_DE_POEIRA, 0.3, 0.05, 0.3, 0.1);
    }
  }
}
