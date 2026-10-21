package dev.victor.combatza.golpes;

import java.util.Optional;
import java.util.function.Predicate;

import com.cobblemon.mod.common.api.moves.Move;

import dev.victor.combatza.combate.Lados;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Mira {

  public static final double ALCANCE = 24.0;
  private static final double RAIO_ASSISTENCIA_MIRA = 0.75;
  private static final double TOLERANCIA_DE_CHEGADA = 0.5;

  private Mira() {
  }

  // Com o foco (lock-on) do cliente, a mira é o alvo travado, se ele existe, está vivo, a até 24 blocos e não é aliado.
  // Senão, a mira de sempre: o que está no meio da visão do jogador. Em batalha, a mira passa pelos monstros
  // (Lados.entraNaLuta). E sempre passa pelos NPCs treinadores (Lados.ehNpc). 21/10 (PvP): no duelo, só o Pokémon do
  // adversário fica na mira (Lados.miraAtravessa)
  public static HitResult mirar(ServerPlayer jogador, int alvoTravado, boolean emBatalha) {
    Entity alvo = alvoTravado < 0 ? null : jogador.serverLevel().getEntity(alvoTravado);
    if (alvo instanceof LivingEntity vivo && vivo.isAlive() && !Lados.miraAtravessa(jogador, vivo) && !Lados.ehNpc(vivo)
        && Lados.entraNaLuta(vivo, emBatalha) && jogador.distanceTo(vivo) <= ALCANCE) {
      return new EntityHitResult(vivo, vivo.getBoundingBox().getCenter());
    }
    return mirar(jogador, emBatalha);
  }

  public static HitResult mirar(ServerPlayer jogador, boolean emBatalha) {
    Vec3 olho = jogador.getEyePosition();
    Vec3 fim = olho.add(jogador.getViewVector(1.0f).scale(ALCANCE));
    return primeiroNoCaminho(jogador, olho, fim, RAIO_ASSISTENCIA_MIRA,
        e -> !Lados.miraAtravessa(jogador, e) && !Lados.ehNpc(e) && Lados.entraNaLuta(e, emBatalha));
  }

  // 6.11: a mesma mira, de qualquer olho e olhar: a prévia do seu jogo (DesenhoDaPrevia) mira igual ao servidor
  public static HitResult mirarDe(Entity quem, Vec3 olho, Vec3 olhar, double alcance, Predicate<Entity> podeAcertar) {
    return primeiroNoCaminho(quem, olho, olho.add(olhar.scale(alcance)), RAIO_ASSISTENCIA_MIRA, podeAcertar);
  }

  // O chão embaixo (ou um pouco acima) de um ponto: um raio de 1 bloco acima até 6 abaixo; sem chão, o próprio ponto. Era
  // das armadilhas (5.6c); a prévia (6.11) usa o mesmo, nos dois lados
  public static Vec3 noChao(Level nivel, Vec3 ponto) {
    HitResult chao = nivel.clip(new ClipContext(ponto.add(0, 1, 0), ponto.subtract(0, 6, 0), ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE, CollisionContext.empty()));
    return chao.getType() == HitResult.Type.MISS ? ponto : chao.getLocation();
  }

  private static HitResult primeiroNoCaminho(Entity quemLanca, Vec3 inicio, Vec3 fim, double raio,
      Predicate<Entity> podeAcertar) {
    HitResult bloco = quemLanca.level().clip(
        new ClipContext(inicio, fim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, quemLanca));

    Vec3 fimReal = bloco.getType() == HitResult.Type.MISS ? fim : bloco.getLocation();

    AABB regiao = new AABB(inicio, fimReal).inflate(raio + 1.0);
    Entity maisProximo = null;
    double menorDistancia = Double.MAX_VALUE;

    for (Entity candidato : quemLanca.level().getEntities(quemLanca, regiao,
        e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && podeAcertar.test(e))) {
      Optional<Vec3> ponto = candidato.getBoundingBox().inflate(raio).clip(inicio, fimReal);
      if (ponto.isPresent()) {
        double distancia = inicio.distanceToSqr(ponto.get());
        if (distancia < menorDistancia) {
          maisProximo = candidato;
          menorDistancia = distancia;
        }
      }
    }
    if (maisProximo != null) {
      return new EntityHitResult(maisProximo);
    }
    return bloco;
  }

  // 5.4c: os golpes de prender só chegam a 8 blocos (Preso)
  public static double alcanceDo(Move golpe) {
    return Preso.ehDePrender(golpe) ? Preso.ALCANCE : ALCANCE;
  }

  public static boolean chegaAte(Entity atacante, Vec3 ponto) {
    HitResult bloco = atacante.level().clip(
        new ClipContext(atacante.getEyePosition(), ponto, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
            atacante));
    return bloco.getType() == HitResult.Type.MISS || bloco.getLocation().distanceTo(ponto) <= TOLERANCIA_DE_CHEGADA;
  }
}
