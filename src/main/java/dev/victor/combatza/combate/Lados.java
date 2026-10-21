package dev.victor.combatza.combate;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.party.Parties;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public class Lados {
  private Lados() {
  }

  public static boolean ehAliado(Entity entidade) {
    return entidade instanceof Player || (entidade instanceof PokemonEntity pokemon && !pokemon.getPokemon().isWild());
  }

  // Etapa 8: o selvagem caído no chão (Desmaio) não é mais inimigo: os golpes passam por ele
  public static boolean ehInimigo(Entity entidade) {
    return entidade instanceof Enemy || (entidade instanceof PokemonEntity pokemon && pokemon.getPokemon().isWild()
        && !Desmaio.esta(pokemon));
  }

  // 7.2: do ponto de vista de quem ataca. Para o SELVAGEM, o inimigo é o lado de cá: os jogadores e os Pokémon deles (os
  // outros selvagens são aliados dele). Para o seu Pokémon, o de sempre (ehInimigo)
  public static boolean ehInimigoDe(PokemonEntity atacante, Entity entidade) {
    if (atacante != null && atacante.getPokemon().isWild()) {
      return (entidade instanceof Player jogador && !jogador.isSpectator())
          || (entidade instanceof PokemonEntity pokemon && !pokemon.getPokemon().isWild());
    }
    return ehInimigo(entidade);
  }

  // 7.2: no golpe do selvagem, você também entra na luta (pode levar o golpe); os monstros continuam de fora
  public static boolean entraNaLutaDe(PokemonEntity atacante, Entity entidade, boolean emBatalha) {
    if (atacante != null && atacante.getPokemon().isWild()) {
      return !emBatalha || entidade instanceof PokemonEntity || entidade instanceof Player;
    }
    return entraNaLuta(entidade, emBatalha);
  }

  // Fogo amigo (dele, 20/10: "pode colocar para dar menos 25% de dano, dar apenas 75% no aliado"): o golpe de um Pokémon com
  // dono num outro Pokémon com dono (o seu guarda, o Pokémon de outro jogador) tira 75%. Só a área em volta chega neles (os
  // outros golpes atravessam os aliados)
  private static final double DANO_NO_ALIADO = 0.75;

  public static double fogoAmigo(Execucao execucao, Entity alvo) {
    return !execucao.getPokemon().isWild() && alvo instanceof PokemonEntity atingido
        && !atingido.getPokemon().isWild() && atingido.getPokemon() != execucao.getPokemon() ? DANO_NO_ALIADO : 1.0;
  }

  // Party (dele, 20/10: "estranhos não entram na sua batalha, e os seus golpes não pegam neles nem nos Pokémon deles"): o golpe
  // de um Pokémon com dono passa pelo Pokémon de um estranho (o dono fora da sua party) e pelo selvagem que está na batalha de
  // outra pessoa (sem ninguém da sua party nela)
  public static boolean foraDaSuaLuta(Execucao execucao, Entity alvo) {
    Pokemon pokemon = execucao.getPokemon();
    if (pokemon.isWild() || !(alvo instanceof PokemonEntity atingido)) {
      return false;
    }
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (!atingido.getPokemon().isWild()) {
      return !Parties.saoAliados(dono, atingido.getPokemon().getOwnerPlayer());
    }
    return daBatalhaDeOutro(dono, atingido);
  }

  // O selvagem está numa batalha em que não há ninguém da sua party
  public static boolean daBatalhaDeOutro(ServerPlayer jogador, PokemonEntity selvagem) {
    Combate combate = GerenciarCombates.combateDe(selvagem.getPokemon());
    if (jogador == null || combate == null || combate.getJogadores().isEmpty()) {
      return false;
    }
    for (ServerPlayer quemLuta : combate.getJogadores()) {
      if (Parties.saoAliados(jogador, quemLuta)) {
        return false;
      }
    }
    return true;
  }

  // 7.4b: monstro = os hostis do Minecraft (Enemy), que não são Pokémon
  public static boolean ehMonstro(Entity entidade) {
    return entidade instanceof Enemy && !(entidade instanceof PokemonEntity);
  }

  // Quem o golpe pega além do alvo marcado (no caminho do projétil e do jato, e na área do ponto de chegada).
  // 7.4b: o golpe do GUARDA (o seu Pokémon pela IA, Guardas) só pega MONSTROS, dentro e fora da batalha: atravessa os
  // selvagens (a luta é do lutador, e um acerto sem querer começaria outra), você e os aliados. E o alvo dele, mesmo não
  // sendo monstro (o golem ou o aldeão que você bateu), também na área em volta do Pokémon
  public static boolean pegaNoGolpe(Execucao execucao, PokemonEntity atacante, Entity entidade, boolean emBatalha) {
    if (execucao.ehDoGuarda()) {
      return ehMonstro(entidade) || entidade == execucao.alvoMirado();
    }
    return ehInimigoDe(atacante, entidade) && entraNaLutaDe(atacante, entidade, emBatalha);
  }

  // Em batalha contra selvagem, os golpes do seu Pokémon atravessam os monstros e os bichos (zumbi, aranha, vaca...): só
  // Pokémon entram na luta, então o golpe não mira neles, não para neles e não pega neles (pedido dele, 18/10). Fora de
  // batalha, todo mundo entra, como antes
  public static boolean entraNaLuta(Entity entidade, boolean emBatalha) {
    return !emBatalha || entidade instanceof PokemonEntity;
  }
}
