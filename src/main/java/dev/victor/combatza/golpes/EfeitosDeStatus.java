package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDoTurno;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class EfeitosDeStatus {
  private static final int TICKS_DAS_CHAMAS = 30;
  private static final int PARTICULAS = 6;
  private static final int TOXICO_MAXIMO = 15;

  private static final ResourceKey<DamageType> TIPO_STATUS_POKEMON = ResourceKey.create(Registries.DAMAGE_TYPE,
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "status_pokemon"));
  // Num Pokémon, o dano de status (uma parte da vida, como nos jogos) também ignora a armadura do Cobblemon
  private static final ResourceKey<DamageType> TIPO_STATUS_EM_POKEMON = ResourceKey.create(Registries.DAMAGE_TYPE,
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "status_em_pokemon"));

  private static final Map<UUID, Integer> TURNOS_DE_TOXICO = new HashMap<>();

  private EfeitosDeStatus() {
  }

  public static void atualizar(MinecraftServer servidor, long agora) {
    boolean virouSegundo = agora % SharedConstants.TICKS_PER_SECOND == 0;
    boolean virouTurno = agora % ticksPorTurno() == 0;
    if (!virouSegundo && !virouTurno) {
      return;
    }
    for (PokemonEntity entidade : pokemonsComStatus(servidor)) {
      String sigla = siglaDoStatus(entidade.getPokemon());
      if (!"tox".equals(sigla)) {
        esquecer(entidade.getPokemon());
      }
      if (virouSegundo) {
        mostrar(entidade, sigla);
        if ("par".equals(sigla)) {
          Paralisia.deixarLento(entidade);
        }
      }
      if (virouTurno) {
        aplicarTurno(entidade, sigla);
      }
    }
  }

  public static String siglaDoStatus(Pokemon pokemon) {
    PersistentStatusContainer status = pokemon.getStatus();
    return status == null ? "" : status.getStatus().getShowdownName();
  }

  public static boolean ehFogoDaQueimadura(LivingEntity vitima, DamageSource fonte) {
    return fonte.is(DamageTypes.ON_FIRE) && vitima instanceof PokemonEntity entidade
        && "brn".equals(siglaDoStatus(entidade.getPokemon()));
  }

  public static void esquecer(Pokemon pokemon) {
    TURNOS_DE_TOXICO.remove(pokemon.getUuid());
  }

  public static void limpar() {
    TURNOS_DE_TOXICO.clear();
  }

  // O dano "de status": sem atacante (o rolamento não desvia). A queimadura, o veneno e o preso (Preso) usam
  public static DamageSource fonteDoStatus(Entity entidade) {
    return entidade.damageSources().source(entidade instanceof PokemonEntity ? TIPO_STATUS_EM_POKEMON : TIPO_STATUS_POKEMON);
  }

  public static long ticksPorTurno() {
    return Math.round(Balanceamento.SEGUNDOS_POR_TURNO * SharedConstants.TICKS_PER_SECOND);
  }

  private static List<PokemonEntity> pokemonsComStatus(MinecraftServer servidor) {
    List<PokemonEntity> comStatus = new ArrayList<>();
    for (ServerLevel nivel : servidor.getAllLevels()) {
      for (Entity entidade : nivel.getAllEntities()) {
        if (entidade instanceof PokemonEntity pokemonEntity && pokemonEntity.getPokemon().getEntity() == pokemonEntity
            && pokemonEntity.getPokemon().getStatus() != null) {
          comStatus.add(pokemonEntity);
        }
      }
    }
    return comStatus;
  }

  private static void mostrar(PokemonEntity entidade, String sigla) {
    switch (sigla) {
      case "brn" -> entidade.setRemainingFireTicks(TICKS_DAS_CHAMAS);
      case "psn", "tox" -> Feedback.particulas(entidade, ParticleTypes.WITCH, PARTICULAS);
      case "par" -> Feedback.efeitoDoCobblemon(entidade, "paralysis_passive");
      case "frz" -> Feedback.particulas(entidade, ParticleTypes.SNOWFLAKE, PARTICULAS);
      case "slp" -> {
        if (SonoECongelado.dormindoPeloGolpe(entidade.getPokemon())) {
          Feedback.efeitoDoCobblemon(entidade, "sleep_actor");
        }
      }
      default -> {
        // sem status
      }
    }
  }

  private static void aplicarTurno(PokemonEntity entidade, String sigla) {
    Pokemon pokemon = entidade.getPokemon();
    if (Habilidades.tem(pokemon, Habilidades.PELE_VERTENTE)
        && entidade.getRandom().nextFloat() < Balanceamento.CHANCE_DA_PELE_VERTENTE) {
      trocarDePele(pokemon, sigla);
      return;
    }
    // 21/10 (habilidades, lote das reações): com Veneno Curador, o veneno cura 1/8 no lugar do dano
    if (HabilidadesDoTurno.venenoCurador(entidade, sigla)) {
      return;
    }
    int dezesseisAvos = switch (sigla) {
      case "brn" -> 1;
      case "psn" -> 2;
      case "tox" -> Math.min(TURNOS_DE_TOXICO.merge(pokemon.getUuid(), 1, Integer::sum), TOXICO_MAXIMO);
      default -> 0;
    };
    if (dezesseisAvos == 0) {
      return;
    }
    float dano = (float) (entidade.getMaxHealth() * dezesseisAvos / 16.0 * Balanceamento.MULTIPLICADOR_DANO_DE_STATUS);

    boolean pegou = entidade.hurt(fonteDoStatus(entidade), dano);
    if (CobblemonCombatZA.LOGGER.isInfoEnabled()) {
      CobblemonCombatZA.LOGGER.info("TURNO DE STATUS: {} ({}) perdeu {}/16 da vida = {} | aplicado? {}",
          pokemon.getSpecies().getName(), sigla, dezesseisAvos, String.format("%.1f", dano), pegou);
    }
  }

  // Showdown, onResidual: a cada turno, 1/3 de chance de se livrar do status (antes do dano do turno)
  private static void trocarDePele(Pokemon pokemon, String sigla) {
    pokemon.setStatus(null);
    CobblemonCombatZA.LOGGER.info("PELE VERTENTE: {} se livrou de {}", pokemon.getSpecies().getName(), sigla);
    Feedback.avisarQuemImporta(pokemon, Component.translatable("cobblemon_combat_za.status.se_curou",
        pokemon.getDisplayName(false), Habilidades.nome(pokemon)));
  }
}
