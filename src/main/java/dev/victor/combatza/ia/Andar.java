package dev.victor.combatza.ia;

import java.util.Map;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.util.MoLangExtensionsKt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// Fazer um Pokémon andar até um ponto (7.1: o arisco fugindo; 7.2: o selvagem se posicionando na luta). O Cobblemon anda
// pelo "cérebro" (o alvo de caminhada, WALK_TARGET): só mandar andar (getNavigation().moveTo) não basta, porque no tick
// seguinte o cérebro escolheria outro passeio. Então o ponto vai para a memória também
public class Andar {
  // 8.6 (dele, 20/10: "os Pokémon selvagens estão correndo muito atrás da gente, deslizando, está impossível fugir deles
  // correndo"; escolheu "mais lento que você correndo"): contra VOCÊ, o selvagem anda a no máximo 85% da sua velocidade
  // correndo. A velocidade de um Pokémon no Cobblemon (PokemonMoveControl, javap) = a "walkSpeed" da espécie × 2,5 × o
  // atributo de velocidade × o multiplicador que a gente pede no moveTo. A sua, correndo = o seu atributo (0,1) × 1,3
  private static final double CONTA_DO_COBBLEMON = 2.5;
  private static final double SUA_CORRIDA = 1.3;
  private static final double FRACAO_DA_SUA_CORRIDA = 0.85;

  private Andar() {
  }

  // O multiplicador pedido, ou um menor, para ele não passar de 85% de você correndo
  public static float contraVoce(PokemonEntity pokemon, Player jogador, float pedida) {
    double daEspecie = MoLangExtensionsKt.resolveFloat(pokemon.getRuntime(),
        pokemon.getBehaviour().getMoving().getWalk().getWalkSpeed(), Map.of())
        * CONTA_DO_COBBLEMON * pokemon.getAttributeValue(Attributes.MOVEMENT_SPEED);
    if (daEspecie <= 0) {
      return pedida;
    }
    double suaCorrida = jogador.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * SUA_CORRIDA;
    return (float) Math.min(pedida, FRACAO_DA_SUA_CORRIDA * suaCorrida / daEspecie);
  }

  public static void ate(PokemonEntity pokemon, Vec3 destino, float velocidade) {
    pokemon.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
        new WalkTarget(new BlockPosTracker(BlockPos.containing(destino)), velocidade, 1));
    pokemon.getNavigation().moveTo(destino.x, destino.y, destino.z, velocidade);
  }

  // Para de andar: apaga o destino da memória e para o caminho (7.2: o selvagem para quando solta um golpe; se o golpe é
  // de contato, a corrida até o alvo anda por conta própria, sem o passeio do Cobblemon puxando para o destino velho)
  public static void parar(PokemonEntity pokemon) {
    pokemon.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    pokemon.getNavigation().stop();
  }

  // Um ponto "distancia" blocos para longe de quem, no chão
  public static void longeDe(PokemonEntity pokemon, Entity deQuem, double distancia, float velocidade) {
    Vec3 longe = pokemon.position().subtract(deQuem.position()).multiply(1, 0, 1);
    if (longe.lengthSqr() < 1.0E-4) {
      longe = pokemon.getLookAngle().multiply(-1, 0, -1);
    }
    ate(pokemon, pokemon.position().add(longe.normalize().scale(distancia)), velocidade);
  }
}
