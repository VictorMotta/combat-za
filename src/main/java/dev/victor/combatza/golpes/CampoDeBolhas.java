package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import com.cobblemon.mod.common.api.moves.Move;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

// 5.6d (a ideia dele da 4.3c, "as bolhas aparecem na zona e pegam todos igual"; escolheu "bola que estoura em bolhas"): a
// Bolha voa como sempre e, ao chegar, no lugar da explosão, o círculo do golpe (3 blocos) se enche de bolhas subindo por 1
// segundo. O dano não muda: todos dentro levam o mesmo desde a 4.3c (area_sem_queda.json). São partículas do Minecraft: a
// bolha de verdade (BUBBLE) só vive dentro da água, então aqui são bolinhas azul-claras subindo e o "pop" de bolha estourando
public class CampoDeBolhas {
  private static final Set<String> CAMPOS = Set.of("bubble");
  private static final int TICKS_DO_CAMPO = 20;
  private static final int A_CADA_TICKS = 2;
  private static final int BOLHAS_POR_VEZ = 6;
  private static final int ESTOUROS_POR_VEZ = 3;
  private static final double ALTURA_MAXIMA = 1.6;
  // A partícula de pó multiplica a velocidade por 0,1: 0,6 para cima vira 0,06 bloco por tick
  private static final double SUBIDA = 0.6;
  private static final DustParticleOptions BOLHA = new DustParticleOptions(new Vector3f(0.62f, 0.85f, 1f), 1.2f);

  private record Campo(ServerLevel nivel, Vec3 centro, double raio, long acabaNoTick) {
  }

  private static final List<Campo> CAMPOS_ATIVOS = new ArrayList<>();

  private CampoDeBolhas() {
  }

  public static boolean ehCampo(Move golpe) {
    return CAMPOS.contains(golpe.getName());
  }

  public static void comecar(ServerLevel nivel, Vec3 centro, double raio) {
    CAMPOS_ATIVOS.add(new Campo(nivel, centro, raio, nivel.getServer().getTickCount() + TICKS_DO_CAMPO));
  }

  public static void atualizar(long agora) {
    CAMPOS_ATIVOS.removeIf(campo -> agora > campo.acabaNoTick());
    if (agora % A_CADA_TICKS != 0) {
      return;
    }
    for (Campo campo : CAMPOS_ATIVOS) {
      RandomSource sorte = campo.nivel().getRandom();
      for (int i = 0; i < BOLHAS_POR_VEZ; i++) {
        Vec3 ponto = pontoNoCirculo(campo, sorte);
        // count 0: o Minecraft usa os 3 números seguintes como a velocidade, e não como o espalhamento
        campo.nivel().sendParticles(BOLHA, ponto.x, ponto.y, ponto.z, 0, 0, SUBIDA, 0, 1);
      }
      for (int i = 0; i < ESTOUROS_POR_VEZ; i++) {
        Vec3 ponto = pontoNoCirculo(campo, sorte);
        campo.nivel().sendParticles(ParticleTypes.BUBBLE_POP, ponto.x, ponto.y, ponto.z, 1, 0, 0, 0, 0);
      }
    }
  }

  public static void limpar() {
    CAMPOS_ATIVOS.clear();
  }

  // Um ponto sorteado dentro do círculo (a raiz quadrada espalha por igual: sem ela, juntaria tudo no meio) e numa altura
  private static Vec3 pontoNoCirculo(Campo campo, RandomSource sorte) {
    double angulo = sorte.nextDouble() * 2 * Math.PI;
    double distancia = Math.sqrt(sorte.nextDouble()) * campo.raio();
    return campo.centro().add(Math.cos(angulo) * distancia, sorte.nextDouble() * ALTURA_MAXIMA,
        Math.sin(angulo) * distancia);
  }
}
