package dev.victor.combatza.campo;

import java.util.List;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.golpes.Feedback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// P5-10: onde o clima, o campo e as barreiras valem. Na batalha, o campo dela, quem está nela e os Pokémon da luta (os
// selvagens e os lutadores). Fora da batalha (Arenas), o campo pessoal do jogador, ele e os Pokémon dele fora da bola
public record Arena(CampoDaBatalha campo, List<ServerPlayer> jogadores, List<PokemonEntity> pokemons, boolean naBatalha) {

  // Começou ou acabou um efeito. Na batalha, um cartão no histórico (com "quem", o rosto dele; sem, sem rosto). Fora dela, o
  // começo vai na barra de ação; o fim e as mudanças do mundo ficam só no ícone da passiva
  public void avisar(Pokemon quem, Component frase) {
    for (ServerPlayer jogador : jogadores) {
      if (naBatalha) {
        Feedback.historicoDaBatalha(jogador, quem, frase);
      } else if (quem != null) {
        Feedback.barraDeAcao(jogador, frase);
      }
    }
  }
}
