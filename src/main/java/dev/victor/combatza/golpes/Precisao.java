package dev.victor.combatza.golpes;

import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.itens.EstadoDosItens;
import dev.victor.combatza.itens.ItensDePrecisao;
import dev.victor.combatza.itens.ItensDoAcerto;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// 5.3 (escolhas dele, 18/10: "desvio visível", a precisão do golpe "entra no desvio", os que nunca erram "perseguem e
// ninguém desvia"): a precisão dos jogos vira a chance de o alvo DESVIAR. Quando um golpe vai acertar um Pokémon, sorteia,
// como no Showdown: a precisão do golpe (Hipnose 60%), vezes a tabela da Precisão de quem ataca e da Evasão de quem leva
// (estágio −1 = ×3/4, ..., −6 = ×1/3), vezes 1,3 com Olhos Compostos. Errou o sorteio: o alvo dá um pulinho para o lado,
// o golpe passa ("X desviou!") e nada dele acontece. Ninguém desvia dos golpes que nunca erram (no Cobblemon, precisão −1),
// de quem tem Indefeso, nem estando dormindo, congelado ou preso. Olhar Aguçado (e Iluminar, Olho da Mente) ignora a Evasão
// do alvo. Monstros e jogadores não desviam
public class Precisao {
  // Showdown, boostTable: o multiplicador de cada estágio, de 0 a 6
  private static final double[] TABELA = { 1.0, 4.0 / 3, 5.0 / 3, 2.0, 7.0 / 3, 8.0 / 3, 3.0 };
  private static final int ESTAGIO_MAXIMO = 6;
  private static final double OLHOS_COMPOSTOS = 1.3;
  private static final String INDEFESO = "noguard";
  private static final String COM_OLHOS_COMPOSTOS = "compoundeyes";
  private static final Set<String> IGNORAM_A_EVASAO = Set.of("keeneye", "illuminate", "mindseye");
  private static final double PULO_PARA_O_LADO = 0.55;
  private static final double PULO_PARA_CIMA = 0.35;
  private static final int NUVENS_DO_PULO = 6;

  private Precisao() {
  }

  // Precisão "true" nos dados do Showdown: o Cobblemon guarda como −1
  public static boolean nuncaErra(Move golpe) {
    return golpe.getTemplate().getAccuracy() < 0;
  }

  // De 0 a 1: a chance de o golpe acertar este Pokémon (o resto é a chance de ele desviar)
  public static double chanceDeAcertar(Pokemon atacante, Move golpe, Pokemon alvo) {
    if (nuncaErra(golpe) || Habilidades.tem(atacante, INDEFESO) || Habilidades.tem(alvo, INDEFESO)) {
      return 1.0;
    }
    // 5b.2: o Trovão na Chuva nunca erra, no Sol tem 50%...
    double precisao = ClimaNosGolpes.precisao(atacante, golpe, alvo, golpe.getTemplate().getAccuracy());
    if (precisao < 0) {
      return 1.0;
    }
    double chance = precisao / 100.0 * ClimaNosGolpes.evasao(alvo);
    // 21/10 (habilidades, lote da conta do dano): a Ignorância de um ignora os estágios do outro
    chance *= multiplicador(HabilidadesDaConta.estagioQueVale(atacante, alvo, "accuracy"));
    if (!IGNORAM_A_EVASAO.contains(Habilidades.atual(atacante))) {
      chance /= multiplicador(HabilidadesDaConta.estagioQueVale(alvo, atacante, "evasion"));
    }
    // 21/10: Pele Milagrosa, Pés Laçados e Entusiasmo
    chance = HabilidadesDaConta.precisao(atacante, golpe, alvo, chance);
    if (Habilidades.tem(atacante, COM_OLHOS_COMPOSTOS)) {
      chance *= OLHOS_COMPOSTOS;
    }
    // Lote 2 dos itens: Lentes Largas, Lentes de Aumento, Baga Micle e Pó Brilhante
    chance *= ItensDePrecisao.multiplicador(atacante, alvo);
    return Math.min(1.0, chance);
  }

  // O sorteio, uma vez por alvo em cada golpe (Execucao.sortearContra): o jato e a bola que já sortearam não sorteiam de
  // novo no acerto. Devolve se ele desviou (e já pulou e avisou)
  public static boolean desviou(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity entidade) || !entidade.isAlive() || !execucao.sortearContra(alvo)) {
      return false;
    }
    Pokemon defensor = entidade.getPokemon();
    if (SonoECongelado.estaParado(defensor) || Preso.estaPreso(defensor)) {
      return false;
    }
    double chance = chanceDeAcertar(execucao.getPokemon(), execucao.getGolpe(), defensor);
    // Lote 2 dos itens: a Baga Micle vale para um sorteio só
    EstadoDosItens.usouMicle(execucao.getPokemon());
    if (chance >= 1.0 || atacante.getRandom().nextDouble() < chance) {
      return false;
    }
    pular(atacante, entidade);
    execucao.marcarDesvio(alvo);
    // Lote 3 dos itens: a Política de Erro
    ItensDoAcerto.politicaDeErro(execucao);
    CobblemonCombatZA.LOGGER.info("DESVIOU: {} desviou de {} (chance de acertar {}%)", alvo.getName().getString(),
        execucao.getGolpe().getName(), Math.round(chance * 100));
    Feedback.historico(execucao.getJogador(), alvo,
        Component.translatable("cobblemon_combat_za.golpe.desviou", alvo.getName()));
    return true;
  }

  private static double multiplicador(int estagio) {
    int limitado = Math.clamp(estagio, -ESTAGIO_MAXIMO, ESTAGIO_MAXIMO);
    return limitado >= 0 ? TABELA[limitado] : 1.0 / TABELA[-limitado];
  }

  // Para o lado, de lado ao golpe (a perpendicular no chão da linha entre os dois), e um pouco para cima
  private static void pular(PokemonEntity atacante, PokemonEntity alvo) {
    Vec3 golpe = alvo.position().subtract(atacante.position());
    Vec3 lado = new Vec3(-golpe.z, 0, golpe.x);
    if (lado.lengthSqr() < 1.0E-4) {
      lado = new Vec3(1, 0, 0);
    }
    lado = lado.normalize().scale(alvo.getRandom().nextBoolean() ? PULO_PARA_O_LADO : -PULO_PARA_O_LADO);
    alvo.setDeltaMovement(lado.x, PULO_PARA_CIMA, lado.z);
    alvo.hurtMarked = true;
    Feedback.particulas(alvo, ParticleTypes.CLOUD, NUVENS_DO_PULO);
  }
}
