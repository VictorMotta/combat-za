package dev.victor.combatza.combate;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

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
}
