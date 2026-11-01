package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.combate.Raids;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class Vida {
  private Vida() {
  }

  // O PV de quem tem dono é exato; o do selvagem só a entidade sabe (o hurt do Cobblemon não copia para ele). 31/10: o do
  // chefão de uma raid é a fração da vida da raid (Raids)
  public static int pvAtual(Pokemon pokemon) {
    Integer doChefe = Raids.pvDoChefe(pokemon);
    if (doChefe != null) {
      return doChefe;
    }
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

  // Contra mob, 1 ponto de vida vale 1 PV. 31/10: no chefão de uma raid, o corpo não perde vida: o dano foi para a raid (Raids)
  public static double pvTirados(Entity alvo, float vidaAntes) {
    Double noChefe = Raids.ultimoDano(alvo);
    if (noChefe != null) {
      return noChefe;
    }
    double perdeu = vidaAntes - vidaDe(alvo);
    if (alvo instanceof PokemonEntity entidade) {
      return perdeu * entidade.getPokemon().getMaxHealth() / entidade.getMaxHealth();
    }
    return perdeu;
  }

  public static int curar(PokemonEntity entidade, double pv) {
    // 01/11: no Bloqueio de Cura, nada cura (GolpesQueMarcam)
    if (GolpesQueMarcam.semCura(entidade.getPokemon())) {
      return 0;
    }
    Integer noChefe = Raids.curarOChefe(entidade, pv);
    if (noChefe != null) {
      return noChefe;
    }
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
    // 31/10: no chefão de uma raid, o hurt já leva o dano para a raid (Raids); ele nunca desmaia por aqui
    if (Raids.ehChefe(entidade)) {
      entidade.hurt(Dano.fonte(entidade, entidade), naVida(entidade, pv));
      return;
    }
    Pokemon pokemon = entidade.getPokemon();
    int depois = (int) Math.max(0, pvAtual(pokemon) - Math.round(pv));
    entidade.hurt(Dano.fonte(entidade, entidade), naVida(entidade, pv));
    // 21/10: o selvagem da nossa batalha que caiu com isso fica caído no chão com 1 PV (Desmaio), para a captura, e o do
    // treinador volta para a bola (BatalhasDeTreinador): o desmaiar do Cobblemon (PV 0) mataria os dois
    if (Desmaio.esta(entidade) || BatalhasDeTreinador.desmaiou(entidade)) {
      return;
    }
    if (depois == 0) {
      desmaiar(entidade);
    } else if (entidade.isAlive()) {
      pokemon.setCurrentHealth(depois);
      entidade.setHealth(naVida(entidade, depois));
    }
  }

  // O desmaio do próprio Cobblemon: zera a entidade, tira o status e avisa o evento POKEMON_FAINTED
  // 31/10: o chefão de uma raid não desmaia de uma vez (o golpe que derruba na hora não pega nele: Raids)
  public static void desmaiar(PokemonEntity entidade) {
    if (Raids.ehChefe(entidade)) {
      return;
    }
    entidade.getPokemon().setCurrentHealth(0);
  }
}
