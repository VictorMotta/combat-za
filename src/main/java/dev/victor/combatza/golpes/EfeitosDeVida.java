package dev.victor.combatza.golpes;

import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.status.Statuses;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDeProtecao;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class EfeitosDeVida {
  private static final String DESCANSAR = "rest";
  private static final String CLOROFILEISER = "chloroblast";
  private static final String GAMBITO_FINAL = "finalgambit";
  private static final Set<String> CURAM_METADE = Set.of("synthesis", "moonlight", "morningsun", "shoreup");
  private static final String FRASE = "cobblemon_combat_za.golpe.";

  private EfeitosDeVida() {
  }

  public static void pagarComVida(Execucao execucao, PokemonEntity atacante) {
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(execucao.getGolpe());
    if (efeito == null || !efeito.mindBlownRecoil()) {
      return;
    }
    Pokemon pokemon = atacante.getPokemon();
    int custo = (int) Math.round(pokemon.getMaxHealth() / 2.0);
    Vida.ferir(atacante, custo);
    CobblemonCombatZA.LOGGER.info("CUSTO DE VIDA: {} pagou {} PV para usar {}", pokemon.getSpecies().getName(), custo,
        execucao.getGolpe().getName());
    Feedback.historico(execucao.getJogador(), pokemon, Component.translatable(FRASE + "gastou_metade_da_vida",
        pokemon.getDisplayName(false), execucao.getGolpe().getDisplayName()));
  }

  public static void depoisDoDano(Execucao execucao, PokemonEntity atacante, Entity alvo, double pvTirados) {
    if (pvTirados <= 0) {
      return;
    }
    Move golpe = execucao.getGolpe();
    if (CLOROFILEISER.equals(golpe.getName())) {
      recuar(execucao, atacante, (int) Math.round(atacante.getPokemon().getMaxHealth() / 2.0));
    }
    if (GAMBITO_FINAL.equals(golpe.getName())) {
      Vida.desmaiar(atacante);
      CobblemonCombatZA.LOGGER.info("GAMBITO FINAL: {} deu toda a vida e desmaiou",
          atacante.getPokemon().getSpecies().getName());
      Feedback.historico(execucao.getJogador(), atacante,
          Component.translatable(FRASE + "gambito_final", atacante.getPokemon().getDisplayName(false)));
    }
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(golpe);
    if (efeito == null) {
      return;
    }
    if (efeito.drain() != null) {
      drenar(execucao, atacante, alvo, pvTirados * EfeitosDosGolpes.fracao(efeito.drain()));
    }
    if (efeito.recoil() != null) {
      recuar(execucao, atacante, (int) Math.max(1, Math.round(pvTirados * EfeitosDosGolpes.fracao(efeito.recoil()))));
    }
  }

  // 7.2: para a IA do selvagem guardar a cura para quando a vida cair (IaDoSelvagem)
  public static boolean ehDeCura(Move golpe) {
    return DESCANSAR.equals(golpe.getName()) || fracaoDaCura(golpe) > 0;
  }

  public static void curarComGolpe(Execucao execucao, PokemonEntity quem) {
    Move golpe = execucao.getGolpe();
    if (DESCANSAR.equals(golpe.getName())) {
      descansar(execucao, quem);
      return;
    }
    // 5b.2: Luar, Síntese e Sol da Manhã mudam com o clima; o Remendar Areia, na Areia
    double fracao = ClimaNosGolpes.cura(quem.getPokemon(), golpe, fracaoDaCura(golpe));
    if (fracao > 0) {
      curar(execucao, quem, quem.getPokemon().getMaxHealth() * fracao);
    }
  }

  private static double fracaoDaCura(Move golpe) {
    if (CURAM_METADE.contains(golpe.getName())) {
      return 0.5;
    }
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(golpe);
    return efeito != null && efeito.heal() != null ? EfeitosDosGolpes.fracao(efeito.heal()) : 0;
  }

  private static void drenar(Execucao execucao, PokemonEntity atacante, Entity alvo, double pv) {
    // 21/10 (habilidades, lote da entrada e das imunidades): o Lodo Líquido fere quem drena
    if (HabilidadesDeProtecao.lodoLiquido(execucao, atacante, alvo, pv)) {
      return;
    }
    int curou = Vida.curar(atacante, pv);
    if (curou <= 0) {
      return;
    }
    CobblemonCombatZA.LOGGER.info("DRENOU: {} recuperou {} PV de {}", atacante.getPokemon().getSpecies().getName(),
        curou, alvo.getName().getString());
    Feedback.historico(execucao.getJogador(), alvo,
        Component.translatable("cobblemon.battle.heal.drain", alvo.getName())
            .append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
  }

  private static void recuar(Execucao execucao, PokemonEntity atacante, int pv) {
    Pokemon pokemon = atacante.getPokemon();
    if (Habilidades.tem(pokemon, Habilidades.CABECA_DE_PEDRA)) {
      CobblemonCombatZA.LOGGER.info("RECUO BLOQUEADO: {} tem Cabeça de Pedra", pokemon.getSpecies().getName());
      Feedback.historico(execucao.getJogador(), pokemon,
          Component.translatable(FRASE + "sem_recuo", pokemon.getDisplayName(false), Habilidades.nome(pokemon)));
      return;
    }
    Vida.ferir(atacante, pv);
    CobblemonCombatZA.LOGGER.info("RECUO: {} perdeu {} PV com {}", pokemon.getSpecies().getName(), pv,
        execucao.getGolpe().getName());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.damage.recoil", pokemon.getDisplayName(false))
            .append(Component.translatable("cobblemon_combat_za.pv.perdeu", pv)));
  }

  private static void curar(Execucao execucao, PokemonEntity quem, double pv) {
    Pokemon pokemon = quem.getPokemon();
    if (Vida.pvAtual(pokemon) >= pokemon.getMaxHealth()) {
      vidaCheia(execucao, pokemon);
      return;
    }
    int curou = Vida.curar(quem, pv);
    CobblemonCombatZA.LOGGER.info("CUROU: {} recuperou {} PV com {}", pokemon.getSpecies().getName(), curou,
        execucao.getGolpe().getName());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.heal.generic", pokemon.getDisplayName(false))
            .append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
  }

  private static void descansar(Execucao execucao, PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if ("slp".equals(EfeitosDeStatus.siglaDoStatus(pokemon))) {
      Feedback.historico(execucao.getJogador(), pokemon,
          Component.translatable("cobblemon.battle.fail.slp", pokemon.getDisplayName(false)));
      return;
    }
    if (Vida.pvAtual(pokemon) >= pokemon.getMaxHealth()) {
      vidaCheia(execucao, pokemon);
      return;
    }
    // 5b.3: no Campo Enevoado ou no Elétrico, ninguém dorme
    Component doCampo = CampoEBarreiras.bloqueiaStatus(null, pokemon, "slp");
    if (doCampo != null) {
      CobblemonCombatZA.LOGGER.info("DESCANSO FALHOU: {} ({})", pokemon.getSpecies().getName(), doCampo.getString());
      Feedback.historico(execucao.getJogador(), pokemon, pokemon.getDisplayName(false).copy().append(" ").append(doCampo));
      return;
    }
    if (Habilidades.imuneAoStatus(pokemon, "slp")) {
      CobblemonCombatZA.LOGGER.info("DESCANSO FALHOU: {} não dorme ({})", pokemon.getSpecies().getName(),
          Habilidades.atual(pokemon));
      Feedback.historico(execucao.getJogador(), pokemon, Component.translatable(FRASE + "nao_consegue_dormir",
          pokemon.getDisplayName(false), Habilidades.nome(pokemon)));
      return;
    }
    pokemon.applyStatus(Statuses.SLEEP);
    StatusNossos.anotar(pokemon, "slp");
    SonoECongelado.comecar(entidade, "slp", Balanceamento.TURNOS_DO_DESCANSO,
        execucao.getJogador().server.getTickCount());
    int curou = Vida.curar(entidade, pokemon.getMaxHealth());
    CobblemonCombatZA.LOGGER.info("DESCANSOU: {} dormiu e recuperou {} PV", pokemon.getSpecies().getName(), curou);
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable(FRASE + "descansou", pokemon.getDisplayName(false), curou));
  }

  private static void vidaCheia(Execucao execucao, Pokemon pokemon) {
    CobblemonCombatZA.LOGGER.info("CURA FALHOU: {} já está com a vida cheia", pokemon.getSpecies().getName());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.fail.heal", pokemon.getDisplayName(false)));
  }
}
