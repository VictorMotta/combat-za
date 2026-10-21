package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class Vida {
  private Vida() {
  }

  // O PV de quem tem dono é exato; o do selvagem só a entidade sabe (o hurt do Cobblemon não copia para ele)
  public static int pvAtual(Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    if (pokemon.isWild() && entidade != null) {
      return (int) Math.ceil(pokemon.getMaxHealth() * entidade.getHealth() / entidade.getMaxHealth());
    }
    return pokemon.getCurrentHealth();
  }

  // 8.1 (decisão dele: a vida conta na captura, e o capturado chega com a vida que tinha): depois de qualquer dano, o PV
  // do selvagem passa a ser o do corpo. O Cobblemon não faz isso (o hurt dele só copia em quem tem dono), e a captura lê o
  // PV: sem isso, bater não ajudava a capturar
  public static void guardarNoPv(LivingEntity vitima) {
    if (vitima instanceof PokemonEntity entidade && entidade.getPokemon().isWild() && entidade.isAlive()) {
      int pv = pvAtual(entidade.getPokemon());
      if (pv > 0) {
        entidade.getPokemon().setCurrentHealth(pv);
      }
    }
  }

  public static float naVida(PokemonEntity entidade, double pv) {
    return (float) (pv * entidade.getMaxHealth() / entidade.getPokemon().getMaxHealth());
  }

  public static float vidaDe(Entity entidade) {
    return entidade instanceof LivingEntity vivo ? vivo.getHealth() : 0f;
  }

  // Contra mob, 1 ponto de vida vale 1 PV
  public static double pvTirados(Entity alvo, float vidaAntes) {
    double perdeu = vidaAntes - vidaDe(alvo);
    if (alvo instanceof PokemonEntity entidade) {
      return perdeu * entidade.getPokemon().getMaxHealth() / entidade.getMaxHealth();
    }
    return perdeu;
  }

  public static int curar(PokemonEntity entidade, double pv) {
    Pokemon pokemon = entidade.getPokemon();
    int antes = pvAtual(pokemon);
    int depois = (int) Math.min(pokemon.getMaxHealth(), antes + Math.round(pv));
    pokemon.setCurrentHealth(depois);
    entidade.setHealth(naVida(entidade, depois));
    return depois - antes;
  }

  // Dano nele mesmo (recuo, custo de vida, Pele Áspera) em PV exato. O hurt dá o piscar vermelho, mas sozinho erra:
  // a vida da entidade é arredondada para cima e o Fight or Flight corta parte do dano
  public static void ferir(PokemonEntity entidade, double pv) {
    Pokemon pokemon = entidade.getPokemon();
    int depois = (int) Math.max(0, pvAtual(pokemon) - Math.round(pv));
    entidade.hurt(Dano.fonte(entidade, entidade), naVida(entidade, pv));
    if (depois == 0) {
      desmaiar(entidade);
    } else if (entidade.isAlive()) {
      pokemon.setCurrentHealth(depois);
      entidade.setHealth(naVida(entidade, depois));
    }
  }

  // O desmaio do próprio Cobblemon: zera a entidade, tira o status e avisa o evento POKEMON_FAINTED
  public static void desmaiar(PokemonEntity entidade) {
    entidade.getPokemon().setCurrentHealth(0);
  }
}
