package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

// 5.4c (dele: "o inimigo não consegue usar habilidade até acabar a animação e o dano, porém tem que ser mais difícil de
// acertar"; escolheu "preso 3 s, o dano continua", projétil lento e alcance curto): os golpes de prender (Chama Furacão,
// Redemoinho, Embrulho, Enlaçar, Tumba de Areia...) deixam o alvo PARADO e sem atacar por 3 segundos, como um atordoamento
// do LoL. O dano dos jogos (1/8 da vida a cada turno, por 4 ou 5 turnos) continua depois, como uma queimadura, mas ele já
// pode agir. Em troca, o golpe é difícil de acertar: o projétil voa na metade da velocidade e só chega a 8 blocos
public class Preso {
  public static final double ALCANCE = 8.0;
  public static final double MULTIPLICADOR_DA_VELOCIDADE = 0.5;
  private static final double SEGUNDOS_PRESO = 3.0;
  private static final int TURNOS_MINIMO = 4;
  private static final int TURNOS_MAXIMO = 5;
  private static final double FRACAO_POR_TURNO = 1.0 / 8;
  private static final int TICKS_ENTRE_PARTICULAS = 10;
  private static final int PARTICULAS = 8;
  private static final ResourceLocation ID_PRESO = ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID,
      "preso");

  private static final class Prisao {
    private final LivingEntity alvo;
    private final Move golpe;
    private final ServerPlayer jogador;
    private final long soltaNoTick;
    private long proximoTurno;
    private int turnosFaltam;
    private boolean travado = true;

    private Prisao(LivingEntity alvo, Move golpe, ServerPlayer jogador, long agora, int turnos) {
      this.alvo = alvo;
      this.golpe = golpe;
      this.jogador = jogador;
      this.soltaNoTick = agora + Math.round(SEGUNDOS_PRESO * SharedConstants.TICKS_PER_SECOND);
      this.proximoTurno = agora + EfeitosDeStatus.ticksPorTurno();
      this.turnosFaltam = turnos;
    }
  }

  private static final Map<UUID, Prisao> PRESOS = new HashMap<>();

  private Preso() {
  }

  public static boolean ehDePrender(Move golpe) {
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(golpe);
    return efeito != null && "partiallytrapped".equals(efeito.volatileStatus());
  }

  // Depois do acerto (AplicarEfeitos). Quem já está preso não fica preso de novo, como nos jogos
  public static void prender(Execucao execucao, Entity quem) {
    // 7.2: o treinador não fica preso (o selvagem pode mirar em você)
    if (!(quem instanceof LivingEntity alvo) || quem instanceof Player || !alvo.isAlive()
        || PRESOS.containsKey(alvo.getUUID())) {
      return;
    }
    long agora = execucao.getJogador().server.getTickCount();
    int turnos = alvo.getRandom().nextIntBetweenInclusive(TURNOS_MINIMO, TURNOS_MAXIMO);
    PRESOS.put(alvo.getUUID(), new Prisao(alvo, execucao.getGolpe(), execucao.getJogador(), agora, turnos));
    travar(alvo);
    CobblemonCombatZA.LOGGER.info("PRESO: {} preso por {} (3 s parado, {} turnos de dano)", alvo.getName().getString(),
        execucao.getGolpe().getName(), turnos);
    Feedback.historico(execucao.getJogador(), alvo, Component.literal("🔒 ").append(alvo.getName())
        .append(" ficou preso: ").append(execucao.getGolpe().getDisplayName()).append("!"));
  }

  // Só nos 3 segundos parado (depois, ele age, mesmo levando o dano)
  public static boolean estaPreso(Pokemon pokemon) {
    Entity entidade = pokemon.getEntity();
    Prisao prisao = entidade == null ? null : PRESOS.get(entidade.getUUID());
    return prisao != null && prisao.travado;
  }

  public static void atualizar(long agora) {
    for (UUID uuid : List.copyOf(PRESOS.keySet())) {
      Prisao prisao = PRESOS.get(uuid);
      LivingEntity alvo = prisao.alvo;
      if (!alvo.isAlive() || alvo.isRemoved()) {
        PRESOS.remove(uuid);
        continue;
      }
      if (prisao.travado) {
        if (agora >= prisao.soltaNoTick) {
          soltar(prisao);
        } else {
          travar(alvo);
          if (agora % TICKS_ENTRE_PARTICULAS == 0) {
            Feedback.particulas(alvo, Projetil.particulaDoTipo(prisao.golpe), PARTICULAS);
          }
        }
      }
      if (agora >= prisao.proximoTurno) {
        turnoDeDano(prisao, agora);
      }
    }
  }

  public static void limpar() {
    PRESOS.values().stream().filter(prisao -> prisao.travado).forEach(Preso::soltar);
    PRESOS.clear();
  }

  // 1/8 da vida, com a fonte do status (sem atacante: o rolamento não desvia, como da queimadura)
  private static void turnoDeDano(Prisao prisao, long agora) {
    LivingEntity alvo = prisao.alvo;
    float dano = (float) (alvo.getMaxHealth() * FRACAO_POR_TURNO);
    boolean pegou = alvo.hurt(EfeitosDeStatus.fonteDoStatus(alvo), dano);
    Feedback.particulas(alvo, Projetil.particulaDoTipo(prisao.golpe), PARTICULAS);
    CobblemonCombatZA.LOGGER.info("PRESO: {} perdeu 1/8 da vida no {} ({} de dano, aplicado? {})",
        alvo.getName().getString(), prisao.golpe.getName(), String.format("%.1f", dano), pegou);
    prisao.turnosFaltam--;
    if (prisao.turnosFaltam > 0) {
      prisao.proximoTurno = agora + EfeitosDeStatus.ticksPorTurno();
      return;
    }
    PRESOS.remove(alvo.getUUID());
    Feedback.historico(prisao.jogador, alvo, alvo.getName().copy().append(" se livrou: ")
        .append(prisao.golpe.getDisplayName()).append("!"));
  }

  // Parado: sem andar (velocidade zerada, como no sono da 4.5b-3) e sem alvo (não ataca)
  private static void travar(LivingEntity alvo) {
    AttributeInstance velocidade = alvo.getAttribute(Attributes.MOVEMENT_SPEED);
    if (velocidade != null) {
      velocidade.addOrUpdateTransientModifier(
          new AttributeModifier(ID_PRESO, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    if (alvo instanceof Mob monstro) {
      monstro.getNavigation().stop();
      monstro.setTarget(null);
    }
  }

  private static void soltar(Prisao prisao) {
    prisao.travado = false;
    AttributeInstance velocidade = prisao.alvo.getAttribute(Attributes.MOVEMENT_SPEED);
    if (velocidade != null) {
      velocidade.removeModifier(ID_PRESO);
    }
  }
}
