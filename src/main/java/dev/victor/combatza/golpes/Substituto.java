package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 01/11 (as pendências, "como nos jogos"): o SUBSTITUTO. Quem usa paga 1/4 da vida e ganha um boneco com essa vida: os golpes
// de dano dos inimigos acertam o boneco (nem a vida dele, nem os efeitos extras, nem os de contato pegam), e os golpes de
// status deles não pegam. Os golpes de som e quem tem Infiltrado passam pelo boneco. O boneco some quando a vida dele acaba,
// ou quando o Pokémon volta para a bola (o Passar o Bastão leva junto; a Cauda Descendente faz um para quem entrar: Saidas)
public final class Substituto {
  private static final int PARTE = 4;
  private static final String INFILTRADO = "infiltrator";
  private static final int PARTICULAS = 8;

  // A vida que sobra no boneco de cada Pokémon (em PV)
  private static final Map<UUID, Double> BONECOS = new HashMap<>();

  private Substituto() {
  }

  // O golpe sem alvo (GolpesDeStatus.semAlvo). true = era o Substituto
  public static boolean semAlvo(Execucao execucao, PokemonEntity atacante) {
    if (!"substitute".equals(execucao.getGolpe().getName())) {
      return false;
    }
    Pokemon pokemon = execucao.getPokemon();
    int custo = pokemon.getMaxHealth() / PARTE;
    if (BONECOS.containsKey(pokemon.getUuid()) || Vida.pvAtual(pokemon) <= custo) {
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.fail.substitute"));
      return true;
    }
    Vida.ferir(atacante, custo);
    dar(pokemon, custo);
    return true;
  }

  // Um boneco com "pv" de vida (o Substituto, e o da Cauda Descendente para quem entra)
  static void dar(Pokemon pokemon, double pv) {
    BONECOS.put(pokemon.getUuid(), pv);
    if (pokemon.getEntity() != null) {
      Feedback.particulas(pokemon.getEntity(), ParticleTypes.CLOUD, PARTICULAS * 2);
    }
    CobblemonCombatZA.LOGGER.info("SUBSTITUTO: {} com um boneco de {} PV", pokemon.getSpecies().getName(),
        Math.round(pv));
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.start.substitute", pokemon.getDisplayName(false)));
  }

  // O Passar o Bastão leva o boneco (Saidas): quanto ele tinha (null = nenhum)
  static Double tirar(Pokemon pokemon) {
    return BONECOS.remove(pokemon.getUuid());
  }

  private static boolean passa(Execucao execucao, PokemonEntity atacante, Pokemon alvo) {
    Move golpe = execucao.getGolpe();
    return Lados.mesmoLado(execucao.getPokemon(), alvo) || EfeitosDosGolpes.ehDeSom(golpe)
        || EfeitosDosGolpes.temMarca(golpe, "bypasssub") || Habilidades.tem(atacante, INFILTRADO);
  }

  // ControleDeGolpes.acertar, antes do dano: o boneco leva o golpe de dano. true = o boneco segurou
  public static boolean segurou(Execucao execucao, PokemonEntity atacante, Entity alvo, float danoNaVida) {
    if (BONECOS.isEmpty() || !(alvo instanceof PokemonEntity entidade)) {
      return false;
    }
    Pokemon pokemon = entidade.getPokemon();
    Double vida = BONECOS.get(pokemon.getUuid());
    if (vida == null || passa(execucao, atacante, pokemon)) {
      return false;
    }
    double emPv = danoNaVida * pokemon.getMaxHealth() / entidade.getMaxHealth();
    double sobra = vida - emPv;
    Feedback.particulas(entidade, ParticleTypes.CLOUD, PARTICULAS);
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.activate.substitute",
        pokemon.getDisplayName(false)));
    if (sobra <= 0) {
      BONECOS.remove(pokemon.getUuid());
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.end.substitute",
          pokemon.getDisplayName(false)));
    } else {
      BONECOS.put(pokemon.getUuid(), sobra);
    }
    CobblemonCombatZA.LOGGER.info("SUBSTITUTO: o boneco de {} levou {} PV ({} sobrando)", pokemon.getSpecies().getName(),
        Math.round(emPv), Math.max(0, Math.round(sobra)));
    return true;
  }

  // ControleDeGolpes, no golpe de status: o boneco não deixa pegar. true = segurou
  public static boolean seguraStatus(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (BONECOS.isEmpty() || !(alvo instanceof PokemonEntity entidade) || entidade == atacante) {
      return false;
    }
    Pokemon pokemon = entidade.getPokemon();
    if (!BONECOS.containsKey(pokemon.getUuid()) || passa(execucao, atacante, pokemon)) {
      return false;
    }
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
    return true;
  }

  public static void esquecer(Pokemon pokemon) {
    BONECOS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    BONECOS.clear();
  }
}
