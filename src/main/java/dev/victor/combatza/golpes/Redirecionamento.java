package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.habilidades.HabilidadesDaDupla;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

// 01/11 (o redirecionamento na dupla, decisão dele: "faz tudo"): o golpe de alvo único mirado num Pokémon pode ir parar num
// ALIADO dele, como nos jogos em dupla:
// - Siga-me e Pó de Raiva: quem usou vira o centro das atenções por um turno (3,5 s): todo golpe de alvo único mirado num
//   aliado dele vai nele (o Pó de Raiva não puxa quem é de Planta, tem Invólucro ou Óculos de Proteção);
// - Para-Raios e Drenagem: o golpe Elétrico (ou de Água) mirado num aliado vai em quem tem a habilidade (que absorve:
//   habilidades.Absorcao);
// - Propulsão de Cauda, Obstinação e o golpe Tiro Certeiro ignoram tudo isso.
// A troca acontece quando o golpe começa (Execucao): o seu e o da IA passam por aqui
public final class Redirecionamento {
  private static final int TICKS_DO_CENTRO = Math.round(3.5f * SharedConstants.TICKS_PER_SECOND);
  private static final int PARTICULAS = 10;

  private record Centro(boolean doPo, long ateOTick) {
  }

  // Quem está no centro das atenções (o Pokémon) e até quando
  private static final Map<UUID, Centro> CENTROS = new HashMap<>();

  private Redirecionamento() {
  }

  // ControleDeGolpes, no impacto do golpe em si mesmo: true = era o Siga-me ou o Pó de Raiva
  public static boolean usou(Execucao execucao, PokemonEntity atacante) {
    String nome = execucao.getGolpe().getName();
    if (!"followme".equals(nome) && !"ragepowder".equals(nome)) {
      return false;
    }
    Pokemon pokemon = execucao.getPokemon();
    long agora = atacante.getServer() == null ? 0 : atacante.getServer().getTickCount();
    CENTROS.put(pokemon.getUuid(), new Centro("ragepowder".equals(nome), agora + TICKS_DO_CENTRO));
    Feedback.particulas(atacante, ParticleTypes.NOTE, PARTICULAS);
    CobblemonCombatZA.LOGGER.info("REDIRECIONAMENTO: {} é o centro das atenções ({})", pokemon.getSpecies().getName(),
        nome);
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.singleturn." + nome, pokemon.getDisplayName(false)));
    return true;
  }

  // 01/11: o Holofote (GolpesDeAliado): o alvo vira o centro das atenções do lado dele
  public static void centro(Pokemon pokemon, long agora) {
    CENTROS.put(pokemon.getUuid(), new Centro(false, agora + TICKS_DO_CENTRO));
  }

  // Execucao: a mira do golpe, ou a mira num aliado do alvo que puxou o golpe para si
  public static HitResult mira(Pokemon atacante, Move golpe, HitResult mirada) {
    if (!(mirada instanceof EntityHitResult acerto) || !(acerto.getEntity() instanceof PokemonEntity alvo)
        || FormaDoGolpe.de(golpe) != FormaDoGolpe.UNICO || ignora(atacante, golpe)) {
      return mirada;
    }
    PokemonEntity novo = quemPuxa(atacante, golpe, alvo);
    if (novo == null) {
      return mirada;
    }
    CobblemonCombatZA.LOGGER.info("REDIRECIONAMENTO: {} de {} foi de {} para {}", golpe.getName(),
        atacante.getSpecies().getName(), alvo.getPokemon().getSpecies().getName(), novo.getPokemon().getSpecies().getName());
    return new EntityHitResult(novo, novo.getBoundingBox().getCenter());
  }

  private static boolean ignora(Pokemon atacante, Move golpe) {
    return "snipeshot".equals(golpe.getName()) || Habilidades.tem(atacante, "propellertail")
        || Habilidades.tem(atacante, "stalwart");
  }

  // O aliado do alvo que puxa o golpe: primeiro o centro das atenções, depois o Para-Raios ou a Drenagem. null = ninguém
  private static PokemonEntity quemPuxa(Pokemon atacante, Move golpe, PokemonEntity alvo) {
    PokemonEntity entidade = atacante.getEntity();
    if (entidade == null || CENTROS.isEmpty() && !puxaPeloTipo(atacante, golpe)) {
      return null;
    }
    long agora = entidade.getServer() == null ? 0 : entidade.getServer().getTickCount();
    PokemonEntity pelaHabilidade = null;
    for (Pokemon aliado : HabilidadesDaDupla.aliados(alvo.getPokemon())) {
      PokemonEntity doAliado = aliado.getEntity();
      if (doAliado == null || doAliado == entidade || doAliado.distanceTo(entidade) > Mira.ALCANCE) {
        continue;
      }
      Centro centro = CENTROS.get(aliado.getUuid());
      if (centro != null && agora < centro.ateOTick() && !(centro.doPo() && AplicarStatus.imuneAoPo(atacante))) {
        return doAliado;
      }
      if (pelaHabilidade == null && temOPuxador(aliado, ClimaNosGolpes.tipo(atacante, golpe))) {
        pelaHabilidade = doAliado;
      }
    }
    return pelaHabilidade;
  }

  private static boolean puxaPeloTipo(Pokemon atacante, Move golpe) {
    // O tipo que vale de fato no golpe (o do golpe, ou o que um item, uma habilidade ou o clima deu a ele)
    ElementalType tipo = ClimaNosGolpes.tipo(atacante, golpe);
    return tipo == ElementalTypes.ELECTRIC || tipo == ElementalTypes.WATER;
  }

  private static boolean temOPuxador(Pokemon aliado, ElementalType tipo) {
    return tipo == ElementalTypes.ELECTRIC && Habilidades.tem(aliado, "lightningrod")
        || tipo == ElementalTypes.WATER && Habilidades.tem(aliado, "stormdrain");
  }

  // A cada segundo, os centros que acabaram saem
  public static void atualizar(long agora) {
    if (agora % SharedConstants.TICKS_PER_SECOND == 0) {
      CENTROS.values().removeIf(centro -> agora >= centro.ateOTick());
    }
  }

  public static void esquecer(Pokemon pokemon) {
    CENTROS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    CENTROS.clear();
  }
}
