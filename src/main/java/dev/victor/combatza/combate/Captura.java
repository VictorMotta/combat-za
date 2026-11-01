package dev.victor.combatza.combate;

import java.util.UUID;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.party.Parties;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

// Na captura, a Pokébola do Cobblemon (EmptyPokeBallEntity) deixa o Pokémon invisível e aponta o phasingTargetId dele para
// ela, até o fim dos balanços; se ele escapa, volta a ser visível e o phasingTargetId volta a -1. Enquanto isso, ele está
// "dentro da bola": sem placa, sem parênteses, sem foco, sem "?", sem notar ninguém e sem lutar (pedido dele, 19/10: "os
// parênteses ficaram onde ele estava, no nada, enquanto ele estava dentro da Pokébola")
public class Captura {
  private static final int SEM_ALVO_DE_FASE = -1;

  private Captura() {
  }

  public static boolean dentroDaBola(PokemonEntity pokemon) {
    return pokemon.isInvisible() || pokemon.getPhasingTargetId() != SEM_ALVO_DE_FASE;
  }

  // Dele, 31/10 ("na batalha, quando queremos pegar o desmaiado, o nosso Pokémon entra na frente e a bola não chega; às vezes
  // ele some por isso"): a Pokébola atravessa os seus Pokémon, os da sua party e os jogadores dela (BolaAtravessaMixin). Antes,
  // ela batia neles, o Cobblemon avisava "não é selvagem" e largava a bola no chão, e os 10 segundos do caído (Desmaio) corriam
  public static boolean bolaAtravessa(Projectile bola, Entity alvo) {
    if (!(bola instanceof EmptyPokeBallEntity) || !(bola.getOwner() instanceof Player quemJogou)) {
      return false;
    }
    if (alvo instanceof Player outro) {
      return Parties.saoAliados(quemJogou, outro);
    }
    UUID dono = alvo instanceof PokemonEntity pokemon ? pokemon.getOwnerUUID() : null;
    return dono != null
        && (dono.equals(quemJogou.getUUID()) || Parties.saoAliados(quemJogou, bola.level().getPlayerByUUID(dono)));
  }
}
