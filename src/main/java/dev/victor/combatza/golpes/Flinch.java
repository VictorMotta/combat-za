package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class Flinch {
  private static final int PARTICULAS = 8;

  private Flinch() {
  }

  public static void aplicar(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof LivingEntity vivo)) {
      return;
    }
    if (vivo instanceof PokemonEntity comHabilidade
        && Habilidades.tem(comHabilidade.getPokemon(), Habilidades.FORCA_INTERIOR)) {
      Feedback.historico(execucao.getJogador(), vivo, vivo.getName().copy().append(" não hesitou: ")
          .append(Component.translatable(comHabilidade.getPokemon().getAbility().getDisplayName())).append("!"));
      CobblemonCombatZA.LOGGER.info("FLINCH BLOQUEADO: {} tem Força Interior", vivo.getName().getString());
      return;
    }
    vivo.knockback(Balanceamento.EMPURRAO_DO_FLINCH, atacante.getX() - vivo.getX(), atacante.getZ() - vivo.getZ());
    vivo.hurtMarked = true;
    Feedback.particulas(vivo, ParticleTypes.POOF, PARTICULAS);
    Feedback.historico(execucao.getJogador(), vivo, vivo.getName().copy().append(" hesitou!"));
    CobblemonCombatZA.LOGGER.info("FLINCH: {} hesitou com {}", vivo.getName().getString(),
        execucao.getGolpe().getName());
    if (vivo instanceof PokemonEntity pokemonEntity) {
      AgendaDeGolpes.cancelar(pokemonEntity.getPokemon(), execucao.getJogador().server.getTickCount());
      if (Habilidades.tem(pokemonEntity.getPokemon(), Habilidades.INABALAVEL)) {
        inabalavel(execucao, pokemonEntity.getPokemon());
      }
    }
  }

  // Showdown, onFlinch: quem hesita ganha +1 de Velocidade
  private static void inabalavel(Execucao execucao, Pokemon pokemon) {
    int mudou = Estagios.mudar(pokemon, "spe", 1, AplicarEfeitos.quandoAcaba(execucao));
    CobblemonCombatZA.LOGGER.info("INABALÁVEL: {} hesitou e a Velocidade subiu (agora {})",
        pokemon.getSpecies().getName(), Estagios.de(pokemon, "spe"));
    Feedback.historico(execucao.getJogador(), pokemon, pokemon.getDisplayName(false).copy()
        .append(mudou > 0 ? " hesitou, e a Velocidade subiu: " : " hesitou, e a Velocidade não pode subir mais: ")
        .append(Habilidades.nome(pokemon)).append("!"));
  }
}
