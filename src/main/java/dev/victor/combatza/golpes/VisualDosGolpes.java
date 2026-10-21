package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.rede.AlinharPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

// O visual da batalha do Cobblemon para os golpes comuns (action effect "moves/generic_move"), e o nosso para quem
// não tem animação de ataque: o bote (contato) e o raiozinho (sem contato), ideia do Victor vinda do Fight or Flight
public class VisualDosGolpes {
  private static final String APANHAR = "recoil";
  private static final String PONTO_DO_ALVO = "target";
  private static final int FAISCAS_NO_MOB = 15;

  // O bote, como o LeapAtTargetGoal do lobo e da aranha (sobe 0,4); o ar freia: velocidade 1 anda uns 6,6 blocos
  private static final double ALTURA_DO_BOTE = 0.4;
  private static final double BLOCOS_POR_VELOCIDADE_NO_BOTE = 6.6;
  private static final double BOTE_PARA_ANTES_DO_ALVO = 0.5;
  // O avanço de quem tem animação: rente ao chão (o chão freia mais: velocidade 1 anda uns 4,3 blocos)
  private static final double ALTURA_DO_AVANCO = 0.15;
  private static final double BLOCOS_POR_VELOCIDADE_NO_AVANCO = 4.3;
  private static final double AVANCO_PARA_ANTES_DO_ALVO = 1.5;
  private static final double DISTANCIA_MINIMA = 1.0;
  private static final double DISTANCIA_MAXIMA = 6.0;

  private static final double PASSO_DO_RAIO = 0.3;
  private static final float TAMANHO_DO_RAIO = 1.2f;
  private VisualDosGolpes() {
  }

  // A animação de ataque, com as alternativas da batalha do Cobblemon (a Brasa: "ember", senão "fire", "spray",
  // "special"); sem animação de batalha, o nome do golpe. E, no fim, a da categoria (physical, special ou status)
  public static void aoCarregar(Execucao execucao) {
    Move golpe = execucao.getGolpe();
    List<String> nomes = new ArrayList<>(AnimacoesDeBatalha.animacao(golpe));
    if (nomes.isEmpty()) {
      nomes.add(golpe.getName());
    }
    String categoria = golpe.getDamageCategory().getName();
    if (!nomes.contains(categoria)) {
      nomes.add(categoria);
    }
    Feedback.animacao(execucao.getEntidade(), nomes);
  }

  // 5.4g: o visual da batalha do Cobblemon sai para onde o CORPO de quem ataca está virado no jogo de quem vê, e o servidor
  // não manda o giro do corpo. Então, num golpe com alvo, quem vê o atacante recebe um aviso para virar o corpo dele para o
  // alvo até o golpe acabar (CorpoAlinhado, no cliente). A folga cobre a corrida até o alvo, que adia o fim do golpe
  public static void alinharNoCliente(Execucao execucao, long agora) {
    PokemonEntity atacante = execucao.getEntidade();
    if (atacante == null || !(execucao.getMirada() instanceof EntityHitResult acerto)) {
      return;
    }
    long folga = Math.round(Balanceamento.SEGUNDOS_MAXIMOS_DA_CORRIDA * SharedConstants.TICKS_PER_SECOND);
    AlinharPayload pacote = new AlinharPayload(atacante.getId(), acerto.getEntity().getId(),
        (int) (execucao.getTickFim() - agora + folga));
    for (ServerPlayer quemVe : PlayerLookup.tracking(atacante)) {
      if (ServerPlayNetworking.canSend(quemVe, AlinharPayload.TIPO)) {
        ServerPlayNetworking.send(quemVe, pacote);
      }
    }
  }

  // O Pokémon vira para onde o golpe vai, a cada tick do golpe (senão a IA de seguir o dono faz ele olhar para você).
  // O giro é o ângulo no chão (atan2 de z e x); a inclinação, o ângulo para cima ou para baixo
  public static void olharParaOAlvo(Execucao execucao) {
    PokemonEntity atacante = execucao.getEntidade();
    if (atacante == null || atacante.isRemoved()) {
      return;
    }
    Vec3 ponto = execucao.getMirada() instanceof EntityHitResult acerto ? acerto.getEntity().getEyePosition()
        : execucao.getMirada().getLocation();
    double dx = ponto.x - atacante.getX();
    double dz = ponto.z - atacante.getZ();
    double noChao = Math.sqrt(dx * dx + dz * dz);
    if (noChao < 0.01) {
      return;
    }
    float giro = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    float inclinacao = (float) -Math.toDegrees(Math.atan2(ponto.y - atacante.getEyeY(), noChao));
    atacante.setYRot(giro);
    atacante.setYBodyRot(giro);
    atacante.setYHeadRot(giro);
    atacante.setXRot(inclinacao);
  }

  // Golpe de contato: quem tem animação avança rente ao chão e anima perto do alvo; quem não tem dá o bote
  public static void irAteOAlvo(Execucao execucao) {
    PokemonEntity atacante = execucao.getEntidade();
    if (atacante == null || !EfeitosDosGolpes.fazContato(execucao.getGolpe())) {
      return;
    }
    if (AnimacoesDeAtaque.tem(execucao.getPokemon(), execucao.getGolpe())) {
      lancar(execucao, atacante, AVANCO_PARA_ANTES_DO_ALVO, ALTURA_DO_AVANCO, BLOCOS_POR_VELOCIDADE_NO_AVANCO, false);
    } else {
      lancar(execucao, atacante, BOTE_PARA_ANTES_DO_ALVO, ALTURA_DO_BOTE, BLOCOS_POR_VELOCIDADE_NO_BOTE, true);
    }
  }

  // Golpe sem contato de quem não tem animação: um raiozinho da cor do tipo, do Pokémon até o alvo
  public static void raio(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    Move golpe = execucao.getGolpe();
    if (EfeitosDosGolpes.fazContato(golpe) || AnimacoesDeAtaque.tem(execucao.getPokemon(), golpe)
        || !(atacante.level() instanceof ServerLevel nivel)) {
      return;
    }
    int cor = CoresDosTipos.de(golpe.getType());
    DustParticleOptions particula = new DustParticleOptions(
        new Vector3f(((cor >> 16) & 0xFF) / 255f, ((cor >> 8) & 0xFF) / 255f, (cor & 0xFF) / 255f), TAMANHO_DO_RAIO);
    Vec3 de = atacante.getBoundingBox().getCenter();
    Vec3 ate = alvo.getBoundingBox().getCenter();
    Vec3 passo = ate.subtract(de).normalize().scale(PASSO_DO_RAIO);
    int passos = (int) (de.distanceTo(ate) / PASSO_DO_RAIO);
    Vec3 ponto = de;
    for (int i = 0; i < passos; i++) {
      nivel.sendParticles(particula, ponto.x, ponto.y, ponto.z, 1, 0, 0, 0, 0);
      ponto = ponto.add(passo);
    }
  }

  // A partícula e o som do tipo, o "hit" e o alvo apanhando. Mob não tem modelo do Cobblemon: ficam as faíscas
  public static void aoAcertar(Move golpe, Entity alvo) {
    String tipo = golpe.getType().getShowdownId();
    Feedback.som(alvo, ResourceLocation.fromNamespaceAndPath("cobblemon", "impact." + tipo));
    if (!(alvo instanceof PokemonEntity)) {
      Feedback.particulas(alvo, ParticleTypes.CRIT, FAISCAS_NO_MOB);
      return;
    }
    Feedback.efeitoDoCobblemon(alvo, "impact_" + tipo, PONTO_DO_ALVO);
    Feedback.efeitoDoCobblemon(alvo, "hit", PONTO_DO_ALVO);
    Feedback.animacao(alvo, List.of(APANHAR));
  }

  // A velocidade é a distância dividida por quanto o Pokémon anda com velocidade 1 (o ar e o chão freiam).
  // Colado no alvo: o avanço não acontece, mas o bote sim (um pulinho no lugar, batendo a cabeça)
  // 21/10: o alvo no alto (até 3 blocos, Corrida.PULO_MAXIMO): o pulo sobe até ele (v = raiz de 2 × gravidade × altura, com meio
  // bloco de folga), no máximo o pulo de 3 blocos
  private static final double GRAVIDADE = 0.08;
  private static final double FOLGA_DO_PULO = 0.5;

  private static double subidaAte(PokemonEntity atacante, Vec3 destino) {
    double altura = Math.min(destino.y - atacante.getY(), Corrida.PULO_MAXIMO);
    return altura <= 0 ? 0 : Math.sqrt(2 * GRAVIDADE * (altura + FOLGA_DO_PULO));
  }

  private static void lancar(Execucao execucao, PokemonEntity atacante, double paraAntes, double altura,
      double blocosPorVelocidade, boolean puloMesmoColado) {
    Vec3 destino = execucao.getMirada() instanceof EntityHitResult acerto ? acerto.getEntity().position()
        : execucao.getMirada().getLocation();
    Vec3 caminho = new Vec3(destino.x - atacante.getX(), 0, destino.z - atacante.getZ());
    double distancia = caminho.length() - paraAntes;
    if (distancia < DISTANCIA_MINIMA && !puloMesmoColado) {
      return;
    }
    double velocidade = Math.clamp(distancia, 0, DISTANCIA_MAXIMA) / blocosPorVelocidade;
    Vec3 impulso = caminho.lengthSqr() > 0 ? caminho.normalize().scale(velocidade) : Vec3.ZERO;
    atacante.getNavigation().stop();
    atacante.setDeltaMovement(impulso.x, Math.max(altura, subidaAte(atacante, destino)), impulso.z);
    atacante.hurtMarked = true;
  }
}
