package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 5.4a (dele: "o dano do revólver d'água não deveria ir abaixando enquanto tá jogando água?"; escolheu a lista de jatos e
// raios e "mesmo total, em pedaços"): nos golpes que são um jato ou um raio, o dano de um acerto se divide em pedaços, um a
// cada 2 ticks (5.4f: "mais pedaços, mais suave"), enquanto o jato encosta no alvo. O primeiro pedaço entra quando o
// projétil chega (5.2); os outros, se o alvo continua na linha do jato (de onde o Pokémon está, na direção do primeiro
// acerto). Quem sai da frente leva só uma parte. As frases de efetividade e os efeitos extras (a chance de queimar da
// Escaldada...) só no primeiro pedaço de cada alvo. Até o jato acabar, o Pokémon está ocupado.
// 5.6a (escolhas dele: "os raios" atravessam, "75% em todos"): os RAIOS pegam todos os inimigos na linha, e não só o
// primeiro; pegou mais de um, cada um leva 75% (como os golpes de vários alvos dos jogos, em dupla). Os jatos de água e
// fogo continuam parando no primeiro: luz atravessa, água bate
public class Jatos {
  private static final Set<String> JATOS = Set.of(
      "watergun", "hydropump", "hydrocannon", "scald", "steameruption", "bubblebeam", "flamethrower", "blastburn",
      "dragonbreath", "frostbreath");
  private static final Set<String> RAIOS = Set.of(
      "icebeam", "aurorabeam", "psybeam", "signalbeam", "chargebeam", "solarbeam", "hyperbeam", "flashcannon",
      "fleurcannon", "meteorbeam", "eternabeam", "prismaticlaser", "electroshot");
  private static final double VARIOS_ALVOS = 0.75;
  private static final int PEDACOS_DO_PROJETIL = 8;
  private static final int PEDACOS_DO_CANHAO = 12;
  private static final int TICKS_ENTRE_PEDACOS = 2;
  // O som, o efeito de impacto e a animação de apanhar: no primeiro pedaço (o estouro) e depois a cada 4 (0,4 s). A cada
  // 0,1 s, o som virava um zumbido e a animação recomeçava sem parar. As gotas saem em todos
  private static final int VISUAL_A_CADA = 4;
  // A grossura do jato: a caixa do alvo cresce isso para os lados
  private static final double GROSSURA_DO_JATO = 0.5;
  private static final double PASSO_DAS_GOTAS = 0.6;
  private static final double ESPALHAR_AS_GOTAS = 0.08;

  private static final class Jato {
    private final Execucao execucao;
    private final PokemonEntity atacante;
    private final List<Entity> alvos;
    private final Set<Entity> jaAcertados = new HashSet<>();
    private final double multiplicador;
    private final Vec3 direcao;
    private final int pedacos;
    private int faltam;
    private long proximoTick;

    private Jato(Execucao execucao, PokemonEntity atacante, List<Entity> alvos, Vec3 direcao, int pedacos, long agora) {
      this.execucao = execucao;
      this.atacante = atacante;
      this.alvos = alvos;
      this.multiplicador = alvos.size() > 1 ? VARIOS_ALVOS : 1.0;
      this.direcao = direcao;
      this.pedacos = pedacos;
      this.faltam = pedacos;
      this.proximoTick = agora;
    }
  }

  private static final Map<UUID, Jato> JORRANDO = new HashMap<>();

  private Jatos() {
  }

  public static boolean ehJato(Move golpe) {
    return JATOS.contains(golpe.getName()) || RAIOS.contains(golpe.getName());
  }

  public static boolean atravessa(Move golpe) {
    return RAIOS.contains(golpe.getName());
  }

  public static boolean estaJorrando(Pokemon pokemon) {
    return JORRANDO.containsKey(pokemon.getUuid());
  }

  // No lugar do acerto cheio: o projétil de um jato chegou em alguém. O primeiro pedaço sai agora
  public static void comecar(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    int pedacos = execucao.getArquetipo() == Arquetipo.CANHAO ? PEDACOS_DO_CANHAO : PEDACOS_DO_PROJETIL;
    Vec3 boca = atacante.getEyePosition();
    Vec3 direcao = alvo.getBoundingBox().getCenter().subtract(boca).normalize();
    List<Entity> alvos = atravessa(execucao.getGolpe()) ? naLinhaToda(execucao, atacante, alvo, boca, direcao)
        : new ArrayList<>(List.of(alvo));
    long agora = execucao.getJogador().server.getTickCount();
    Jato jato = new Jato(execucao, atacante, alvos, direcao, pedacos, agora);
    jorrar(jato, agora);
    if (jato.alvos.isEmpty()) {
      return;
    }
    JORRANDO.put(execucao.getPokemon().getUuid(), jato);
    CobblemonCombatZA.LOGGER.info("JATO: {} começou a jorrar {} em {} alvo(s) ({} pedaços)",
        execucao.getPokemon().getSpecies().getName(), execucao.getGolpe().getName(), alvos.size(), pedacos);
  }

  public static void atualizar(long agora) {
    for (Jato jato : new ArrayList<>(JORRANDO.values())) {
      if (agora >= jato.proximoTick) {
        jorrar(jato, agora);
      }
    }
  }

  public static void limpar() {
    JORRANDO.clear();
  }

  // Um pedaço em cada alvo que ainda está na linha. No primeiro pedaço de cada um: imune, absorvido ou desviou, ele sai da
  // lista (o jato não pega mais nele)
  private static void jorrar(Jato jato, long agora) {
    Pokemon pokemon = jato.execucao.getPokemon();
    // Quem lançou voltou para a bola ou desmaiou: o jato acaba
    if (pokemon.getEntity() != jato.atacante || !jato.atacante.isAlive()) {
      JORRANDO.remove(pokemon.getUuid());
      return;
    }
    jato.alvos.removeIf(alvo -> !alvo.isAlive());
    int pedaco = jato.pedacos - jato.faltam + 1;
    Vec3 boca = jato.atacante.getEyePosition();
    Vec3 maisLonge = null;
    for (Entity alvo : List.copyOf(jato.alvos)) {
      if (!naLinha(jato, alvo, boca)) {
        continue;
      }
      boolean primeiro = jato.jaAcertados.add(alvo);
      boolean comVisual = primeiro || (pedaco - 1) % VISUAL_A_CADA == 0;
      boolean pegou = ControleDeGolpes.acertarPedaco(jato.execucao, jato.atacante, alvo,
          jato.multiplicador / jato.pedacos, primeiro, comVisual);
      if (primeiro && !pegou) {
        jato.alvos.remove(alvo);
        continue;
      }
      Vec3 centro = alvo.getBoundingBox().getCenter();
      if (maisLonge == null || boca.distanceToSqr(centro) > boca.distanceToSqr(maisLonge)) {
        maisLonge = centro;
      }
      CobblemonCombatZA.LOGGER.info("JATO: pedaço {}/{} de {} em {}", pedaco, jato.pedacos,
          jato.execucao.getGolpe().getName(), alvo.getName().getString());
    }
    // As gotas vão até o último alvo que o jato pegou; sem ninguém na linha, até onde o primeiro estaria, e passam
    if (maisLonge == null && !jato.alvos.isEmpty()) {
      double distancia = boca.distanceTo(jato.alvos.get(0).getBoundingBox().getCenter());
      maisLonge = boca.add(jato.direcao.scale(distancia));
    }
    if (maisLonge != null) {
      gotas(jato.execucao.getGolpe(), jato.atacante, maisLonge);
    }
    jato.faltam--;
    if (jato.faltam <= 0 || jato.alvos.isEmpty()) {
      JORRANDO.remove(pokemon.getUuid());
    } else {
      jato.proximoTick = agora + TICKS_ENTRE_PEDACOS;
    }
  }

  // 5.6a: todos os inimigos que cruzam a linha do raio, do mais perto ao mais longe, até um bloco ou o fim do alcance (as
  // mesmas regras da bola: o alvo marcado sempre conta; os outros, se são inimigos e entram na luta). Quem já desviou da
  // bola fica de fora
  private static List<Entity> naLinhaToda(Execucao execucao, PokemonEntity atacante, Entity primeiro, Vec3 boca,
      Vec3 direcao) {
    Vec3 fim = fimDaLinha(atacante, boca, direcao);
    boolean emBatalha = GerenciarCombates.estaEmCombate(execucao.getPokemon());
    List<Entity> alvos = new ArrayList<>(atacante.level().getEntities(atacante,
        new AABB(boca, fim).inflate(GROSSURA_DO_JATO + 1.0),
        e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && !execucao.desviou(e)
            && (e == primeiro || Lados.pegaNoGolpe(execucao, atacante, e, emBatalha))
            && e.getBoundingBox().inflate(GROSSURA_DO_JATO).clip(boca, fim).isPresent()));
    if (!alvos.contains(primeiro)) {
      alvos.add(primeiro);
    }
    alvos.sort(Comparator.comparingDouble(e -> boca.distanceToSqr(e.getBoundingBox().getCenter())));
    return alvos;
  }

  // A linha do jato sai da boca, na direção do primeiro acerto, até um bloco ou o fim do alcance. O alvo está nela se a
  // caixa dele (com a grossura do jato) cruza a linha
  private static boolean naLinha(Jato jato, Entity alvo, Vec3 boca) {
    Vec3 fim = fimDaLinha(jato.atacante, boca, jato.direcao);
    return alvo.getBoundingBox().inflate(GROSSURA_DO_JATO).clip(boca, fim).isPresent();
  }

  private static Vec3 fimDaLinha(PokemonEntity atacante, Vec3 boca, Vec3 direcao) {
    Vec3 fim = boca.add(direcao.scale(Mira.ALCANCE));
    HitResult bloco = atacante.level().clip(
        new ClipContext(boca, fim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, atacante));
    return bloco.getType() == HitResult.Type.MISS ? fim : bloco.getLocation();
  }

  // O jato aparecendo: as partículas do tipo (as mesmas do rastro do projétil), da boca até o ponto
  private static void gotas(Move golpe, PokemonEntity atacante, Vec3 ate) {
    if (!(atacante.level() instanceof ServerLevel nivel)) {
      return;
    }
    ParticleOptions particula = Projetil.particulaDoTipo(golpe);
    Vec3 de = atacante.getEyePosition();
    int pontos = Math.max(1, (int) Math.ceil(de.distanceTo(ate) / PASSO_DAS_GOTAS));
    for (int p = 1; p <= pontos; p++) {
      Vec3 ponto = de.lerp(ate, (double) p / pontos);
      nivel.sendParticles(particula, ponto.x, ponto.y, ponto.z, 1, ESPALHAR_AS_GOTAS, ESPALHAR_AS_GOTAS,
          ESPALHAR_AS_GOTAS, 0);
    }
  }
}
