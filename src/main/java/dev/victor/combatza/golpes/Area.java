package dev.victor.combatza.golpes;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Area {
  private Area() {
  }

  public static List<Entity> emVolta(Entity quemLanca, Vec3 centro, double raio, Predicate<Entity> podeAcertar) {
    AABB caixa = new AABB(centro, centro).inflate(raio);
    return quemLanca.level().getEntities(quemLanca, caixa,
        e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
            && e.getBoundingBox().getCenter().distanceToSqr(centro) <= raio * raio
            && podeAcertar.test(e)
            && Mira.chegaAte(e, centro));
  }

  public static double multiplicadorPelaDistancia(Entity alvo, Vec3 centro, double raio) {
    double distancia = Math.sqrt(alvo.getBoundingBox().distanceToSqr(centro));
    double fracao = Math.clamp(distancia / raio, 0.0, 1.0);
    return 1.0 - (1.0 - Balanceamento.DANO_NA_BORDA_DA_AREA) * fracao;
  }
}
