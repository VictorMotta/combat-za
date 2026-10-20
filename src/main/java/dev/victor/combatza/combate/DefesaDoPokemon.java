package dev.victor.combatza.combate;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// (dele, 19/10: "o dano que eles estão dando no meu Pokémon não está legal; meu Pokémon aguenta só 3 porradas de um zumbi,
// nem eu estou assim"; "veja no FoF como é feito"): o golpe de um MONSTRO num Pokémon com dono.
// O Cobblemon dá ao corpo do Pokémon uma vida pela curva dele (PV elevado a 0,701: 30 PV = 11 de vida, 60 PV = 18) e, entre
// mobs, o Minecraft não aplica a dificuldade: um zumbi (3) tira de 1/4 a 1/3 de um Pokémon de nível baixo. O Fight or
// Flight quase não mudava isso (a curva dele dá o mesmo) e cortava o dano pela defesa. Agora:
// - nunca mais do que tiraria de VOCÊ (de 20 de vida: o zumbi, 15%);
// - e a defesa protege como no Fight or Flight: a maior entre Defesa e Defesa Especial, até 62,5% com 161 ou mais
// Os nossos golpes (os dos selvagens) não passam por aqui: quem ataca neles é um Pokémon
public class DefesaDoPokemon {
  private static final double VIDA_DE_UM_JOGADOR = 20.0;
  // Os números do Fight or Flight para o Pokémon de um jogador: "defense_stat_limit" 161 e 0,5 × 1,25 de redução máxima
  private static final double DEFESA_MAXIMA = 161.0;
  private static final double REDUCAO_MAXIMA = 0.625;

  private DefesaDoPokemon() {
  }

  // O dano que fica, ou o mesmo se não for golpe de monstro num Pokémon com dono
  public static float danoDeMonstro(PokemonEntity vitima, DamageSource fonte, float dano) {
    if (vitima.level().isClientSide() || dano <= 0 || vitima.getPokemon().isWild()
        || !(fonte.getEntity() instanceof LivingEntity atacante) || atacante instanceof PokemonEntity
        || atacante instanceof Player) {
      return dano;
    }
    Pokemon pokemon = vitima.getPokemon();
    double comoVoce = Math.min(1.0, vitima.getMaxHealth() / VIDA_DE_UM_JOGADOR);
    double defesa = Math.min(1.0, Math.max(pokemon.getDefence(), pokemon.getSpecialDefence()) / DEFESA_MAXIMA);
    // P5-10: o Refletir, a Tela de Luz e o Véu de Aurora do seu lado também valem contra monstros
    double barreira = CampoEBarreiras.contraMonstro(pokemon, fonte);
    float novo = (float) (dano * comoVoce * (1 - defesa * REDUCAO_MAXIMA) * barreira);
    if (CobblemonCombatZA.LOGGER.isInfoEnabled()) {
      CobblemonCombatZA.LOGGER.info("DEFESA: {} levou {} de {} → {} (como você ×{}, defesa −{}%, barreira ×{})",
          pokemon.getSpecies().getName(), String.format("%.1f", dano), atacante.getName().getString(),
          String.format("%.1f", novo), String.format("%.2f", comoVoce), Math.round(defesa * REDUCAO_MAXIMA * 100),
          String.format("%.2f", barreira));
    }
    return novo;
  }
}
