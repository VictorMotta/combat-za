package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Cargas;
import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.golpes.DanoEspecial;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 21/10 (habilidades, lote da conta do dano): o LAÇO FAMILIAR (a Kangaskhan Mega): o golpe de dano bate DUAS vezes, e a
// segunda tem 1/4 da força (Showdown, da Geração 7 em diante). No tempo real, a segunda pancada vem 0,3 s depois da
// primeira, no mesmo alvo e sem novo sorteio de desvio (no Showdown o 2º acerto não erra), com os efeitos extras de novo (a
// Mãozada de Força sobe o Ataque duas vezes) e "Acertou 2 vezes!". Ficam de fora, como no Showdown: os golpes de status, os
// de vários acertos (já batem várias vezes), os de carregar (Raio Solar, Voar...), os marcados "noparentalbond" (Explosão,
// Esforço, Arremesso, Rolagem...) e o golpe em área que pegou mais de um (o golpe "espalhado"); e, nosso, os jatos (que já
// acertam em pedaços). O golpe de dano fixo (Movimento Sísmico, Super Presa) bate a segunda vez inteiro, também como no
// Showdown
public class LacoFamiliar {
  private static final String LACO = "parentalbond";
  private static final double FORCA_DO_SEGUNDO = 0.25;
  private static final int TICKS_ATE_O_SEGUNDO = 6;
  private static final int ACERTOS = 2;

  private record Segundo(Execucao execucao, PokemonEntity atacante, Entity alvo, double multiplicadorDeArea, long noTick) {
  }

  private static final List<Segundo> AGENDADOS = new ArrayList<>();

  private LacoFamiliar() {
  }

  // O primeiro acerto pegou (ControleDeGolpes): marca o segundo para daqui a pouco
  public static void agendar(Execucao execucao, PokemonEntity atacante, Entity alvo, double multiplicadorDeArea) {
    Move golpe = execucao.getGolpe();
    if (!Habilidades.tem(execucao.getPokemon(), LACO)
        || golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS() || Cargas.ehDeVariosAcertos(golpe)
        || EfeitosDosGolpes.temMarca(golpe, "charge") || EfeitosDosGolpes.temMarca(golpe, "noparentalbond")
        || EfeitosDosGolpes.temMarca(golpe, "futuremove") || atacante.getServer() == null) {
      return;
    }
    AGENDADOS.add(new Segundo(execucao, atacante, alvo, multiplicadorDeArea,
        atacante.getServer().getTickCount() + TICKS_ATE_O_SEGUNDO));
  }

  // A cada tick (AgendaDeGolpes): as segundas pancadas que chegaram na hora
  public static void atualizar(long agora) {
    if (AGENDADOS.isEmpty()) {
      return;
    }
    List<Segundo> prontos = new ArrayList<>();
    // (marcado para longe demais = de um mundo que fechou e abriu de novo, com o relógio do servidor zerado: some)
    AGENDADOS.removeIf(segundo -> segundo.noTick() <= agora && prontos.add(segundo)
        || segundo.noTick() - agora > TICKS_ATE_O_SEGUNDO);
    // O golpe em área que pegou mais de um alvo (todos marcados no mesmo tick) não ganha o segundo acerto
    Map<Execucao, Integer> alvosPorGolpe = new IdentityHashMap<>();
    for (Segundo segundo : prontos) {
      alvosPorGolpe.merge(segundo.execucao(), 1, Integer::sum);
    }
    for (Segundo segundo : prontos) {
      if (alvosPorGolpe.get(segundo.execucao()) == 1) {
        bater(segundo);
      }
    }
  }

  private static void bater(Segundo segundo) {
    Execucao execucao = segundo.execucao();
    PokemonEntity atacante = segundo.atacante();
    Entity alvo = segundo.alvo();
    // O alvo que a primeira pancada derrubou (caído no chão, desmaiado, voltando para a bola do treinador) não leva a segunda
    if (!atacante.isAlive() || execucao.foiRecolhido() || !alvo.isAlive()
        || alvo instanceof PokemonEntity caido && HabilidadesQueReagem.caiu(caido)) {
      return;
    }
    double fracao = DanoEspecial.temDanoFixo(execucao.getGolpe()) ? 1.0 : FORCA_DO_SEGUNDO;
    boolean pegou = QuebraMolde.duranteOAcerto(execucao, () -> ControleDeGolpes.acertarPedaco(execucao, atacante, alvo,
        fracao * segundo.multiplicadorDeArea(), true, true));
    if (pegou) {
      CobblemonCombatZA.LOGGER.info("LAÇO FAMILIAR: {} bateu de novo com {} ({}%)",
          execucao.getPokemon().getSpecies().getName(), execucao.getGolpe().getName(), Math.round(fracao * 100));
      Feedback.historico(execucao.getJogador(), alvo, Component.translatable("cobblemon.battle.hit_count", ACERTOS));
    }
  }
}
