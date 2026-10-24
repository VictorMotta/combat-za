package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.ia.Andar;
import net.minecraft.SharedConstants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Golpe de contato com alvo marcado: o Pokémon corre até o alvo, e o golpe só sai quando ele chega no alcance.
// Chegou: dá o salto final (o bote, ou o avanço de quem tem animação) e o impacto vem logo depois.
// 5.6b (escolha dele: "ele se aproxima"): o golpe de longe com o alvo além do alcance (24 blocos; 8 nos de prender) também
// corre, até ficar 2 blocos dentro do alcance, e só então solta. Os mesmos 3 segundos para desistir ("não alcançou")
public class Corrida {
  private static final int TICKS_ENTRE_CAMINHOS = 4;
  private static final long TICKS_DO_SALTO = 4;
  private static final double FOLGA_DO_ALCANCE = 2.0;
  // 8.6 (dele: "correndo muito atrás da gente, deslizando"): a corrida do selvagem até VOCÊ vai no máximo a 85% da sua
  // corrida (Andar.contraVoce) e desiste na metade do tempo (1,5 s): o bote fica curto, e correndo você escapa
  private static final double SEGUNDOS_DA_CORRIDA_ATE_VOCE = 1.5;
  // 6.11c: chegou ao marcador a até 1 bloco; com o caminho acabado, a até 2; e no máximo 3 s indo (depois, solta de onde parou)
  private static final double CHEGOU_AO_MARCADOR = 1.0;
  private static final double PERTO_DO_MARCADOR = 2.0;
  // Dele, 21/10 ("o Gyarados voa; por que não tá indo no Castform voando atacar ele?"; escolheu "pula até 3 blocos" para quem não
  // voa): o golpe de contato num alvo NO AR (fora do chão e da água, mais de 1 bloco acima do chão). Quem voa (o Cobblemon diz que
  // voa, ou ele tem montaria pelo ar, como o Gyarados) vai reto pelo ar até ele; quem não voa corre até embaixo e dá o bote para
  // cima (VisualDosGolpes), se o alvo está a até 3 blocos do chão; mais alto, o golpe não sai ("alto demais")
  private static final double NO_AR = 1.0;
  public static final double PULO_MAXIMO = 3.0;
  private static final double OLHAR_O_CHAO_ATE = 8.0;

  private Corrida() {
  }

  public static boolean precisaCorrer(Execucao execucao) {
    return alvo(execucao) != null && (EfeitosDosGolpes.fazContato(execucao.getGolpe()) || execucao.precisaAproximar());
  }

  // 5.6b: decidido uma vez, no começo do golpe (Execucao). Só os golpes que vão até um alvo (único ou área nele).
  // 7b: montado, o golpe de longe sai da montaria do lugar dela, sem se aproximar (quem pilota é você)
  public static boolean longeDemais(Move golpe, PokemonEntity atacante, HitResult mirada) {
    FormaDoGolpe forma = FormaDoGolpe.de(golpe);
    return atacante != null && !atacante.isVehicle() && mirada instanceof EntityHitResult acerto
        && !EfeitosDosGolpes.fazContato(golpe)
        && (forma == FormaDoGolpe.UNICO || forma == FormaDoGolpe.AREA_INIMIGOS)
        && atacante.distanceTo(acerto.getEntity()) > Mira.alcanceDo(golpe) - FOLGA_DO_ALCANCE;
  }

  public static boolean desistiu(Execucao execucao, long agora) {
    double segundos = alvo(execucao) instanceof Player ? SEGUNDOS_DA_CORRIDA_ATE_VOCE
        : Balanceamento.SEGUNDOS_MAXIMOS_DA_CORRIDA;
    long limite = Math.round(segundos * SharedConstants.TICKS_PER_SECOND);
    return !execucao.jaChegou() && agora - execucao.getTickImpactoMarcado() > limite;
  }

  public static void atualizar(Execucao execucao, long agora) {
    if (execucao.jaChegou()) {
      return;
    }
    PokemonEntity atacante = execucao.getEntidade();
    Entity alvo = alvo(execucao);
    // 7b: com você em cima, a montaria assume a corrida (CorridaMontada); o controle volta na pancada
    boolean montada = CorridaMontada.assumir(execucao);
    if (!alvo.isAlive() || estaNoAlcance(execucao, atacante, alvo)) {
      chegar(execucao, atacante, agora);
      return;
    }
    boolean noAr = noAr(alvo);
    if (montada && CorridaMontada.voando(atacante) || !montada && noAr && voa(atacante)) {
      CorridaMontada.voarAte(atacante, alvo);
    } else if (agora % TICKS_ENTRE_CAMINHOS == 0) {
      float velocidade = (float) Balanceamento.VELOCIDADE_DA_CORRIDA;
      if (alvo instanceof Player jogador && execucao.getPokemon().isWild()) {
        velocidade = Andar.contraVoce(atacante, jogador, velocidade);
      }
      if (noAr) {
        // Quem não voa vai até o chão embaixo do alvo, para dar o bote para cima
        Vec3 embaixo = chaoEmbaixo(alvo);
        atacante.getNavigation().moveTo(embaixo.x, embaixo.y, embaixo.z, velocidade);
      } else {
        atacante.getNavigation().moveTo(alvo, velocidade);
      }
    }
    if (agora >= execucao.getTickImpacto()) {
      execucao.adiar(1);
    }
  }

  // 6.11c: o caminho até o marcador, com o andar de sempre (de frente para onde anda). Chegou, ou o caminho acabou perto, ou
  // passaram 3 s: dali ele vira para o alvo (o alinhar do seu jogo) e o golpe segue como sempre. Enquanto isso, o impacto espera
  public static void irAoMarcador(Execucao execucao, long agora) {
    PokemonEntity atacante = execucao.getEntidade();
    Vec3 marcador = execucao.getMarcador();
    double distancia = Math.hypot(atacante.getX() - marcador.x, atacante.getZ() - marcador.z);
    long andando = agora - execucao.getTickInicio();
    boolean caminhoAcabou = andando > TICKS_ENTRE_CAMINHOS && atacante.getNavigation().isDone();
    long limite = Math.round(Balanceamento.SEGUNDOS_MAXIMOS_DA_CORRIDA * SharedConstants.TICKS_PER_SECOND);
    if (distancia <= CHEGOU_AO_MARCADOR || caminhoAcabou && distancia <= PERTO_DO_MARCADOR || andando > limite) {
      Andar.parar(atacante);
      execucao.chegouAoMarcador();
      VisualDosGolpes.alinharNoCliente(execucao, agora);
      CobblemonCombatZA.LOGGER.info("{} NO MARCADOR: {} para {} ({} bloco(s) dele, {} ticks)",
          andando > limite ? "DESISTIU DO MARCADOR" : "CHEGOU", execucao.getPokemon().getSpecies().getName(),
          execucao.getGolpe().getName(), String.format("%.1f", distancia), andando);
      return;
    }
    if (agora % TICKS_ENTRE_CAMINHOS == 0 || andando <= 1) {
      Andar.ate(atacante, marcador, (float) Balanceamento.VELOCIDADE_DA_CORRIDA);
    }
    if (agora >= execucao.getTickImpacto()) {
      execucao.adiar(1);
    }
  }

  private static void chegar(Execucao execucao, PokemonEntity atacante, long agora) {
    execucao.marcarChegada();
    atacante.getNavigation().stop();
    VisualDosGolpes.irAteOAlvo(execucao);
    long impactoMaisCedo = agora + TICKS_DO_SALTO;
    if (execucao.getTickImpacto() < impactoMaisCedo) {
      execucao.adiar(impactoMaisCedo - execucao.getTickImpacto());
    }
    CobblemonCombatZA.LOGGER.info("CHEGOU: {} chegou no alvo para {} ({} ticks depois da hora marcada)",
        execucao.getPokemon().getSpecies().getName(), execucao.getGolpe().getName(),
        Math.max(0, agora - execucao.getTickImpactoMarcado()));
  }

  // Contato: a distância entre as "cascas" dos dois (a largura de cada um conta). De longe: 2 blocos dentro do alcance. Com o alvo
  // no ar e quem não voa: embaixo dele (só a distância no chão), e o bote sobe até ele
  private static boolean estaNoAlcance(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!EfeitosDosGolpes.fazContato(execucao.getGolpe())) {
      return atacante.distanceTo(alvo) <= Mira.alcanceDo(execucao.getGolpe()) - FOLGA_DO_ALCANCE;
    }
    double larguras = (atacante.getBbWidth() + alvo.getBbWidth()) / 2;
    if (noAr(alvo) && !voa(atacante) && !atacante.isVehicle()) {
      double noChao = Math.hypot(alvo.getX() - atacante.getX(), alvo.getZ() - atacante.getZ());
      return noChao - larguras <= Balanceamento.ALCANCE_DO_CONTATO && alturaAcima(atacante, alvo) <= PULO_MAXIMO;
    }
    return atacante.distanceTo(alvo) - larguras <= Balanceamento.ALCANCE_DO_CONTATO;
  }

  // Quem voa: o Cobblemon diz que ele voa, ou ele tem montaria pelo ar (o Gyarados)
  public static boolean voa(PokemonEntity entidade) {
    FormData forma = entidade.getPokemon().getForm();
    if (forma.getBehaviour().getMoving().getFly().getCanFly()) {
      return true;
    }
    // 23/10: quem não tem montaria nenhuma vem sem a lista (null) no Cobblemon (o Psyduck da Misty derrubou o servidor aqui)
    var montarias = forma.getRiding().getBehaviours();
    return montarias != null && montarias.containsKey(RidingStyle.AIR);
  }

  // No ar: fora do chão e da água, e mais de 1 bloco acima do chão embaixo dele
  static boolean noAr(Entity alvo) {
    return !alvo.onGround() && !alvo.isInWater() && !alvo.isPassenger()
        && alvo.getBoundingBox().minY - chaoEmbaixo(alvo).y > NO_AR;
  }

  // O golpe de contato de quem não voa num alvo a mais de 3 blocos do chão: não sai (AgendaDeGolpes: "alto demais")
  public static boolean altoDemais(Execucao execucao) {
    PokemonEntity atacante = execucao.getEntidade();
    Entity alvo = alvo(execucao);
    return atacante != null && alvo != null && EfeitosDosGolpes.fazContato(execucao.getGolpe()) && !atacante.isVehicle()
        && noAr(alvo) && !voa(atacante) && alvo.getBoundingBox().minY - chaoEmbaixo(alvo).y > PULO_MAXIMO;
  }

  static double alturaAcima(PokemonEntity atacante, Entity alvo) {
    return alvo.getBoundingBox().minY - atacante.getY();
  }

  // O chão embaixo do alvo (até 8 blocos abaixo; sem chão, 8 blocos abaixo)
  private static Vec3 chaoEmbaixo(Entity alvo) {
    Vec3 pes = alvo.position();
    HitResult chao = alvo.level().clip(new ClipContext(pes, pes.subtract(0, OLHAR_O_CHAO_ATE, 0),
        ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, alvo));
    return chao.getType() == HitResult.Type.MISS ? pes.subtract(0, OLHAR_O_CHAO_ATE, 0) : chao.getLocation();
  }

  private static Entity alvo(Execucao execucao) {
    return execucao.getMirada() instanceof EntityHitResult acerto ? acerto.getEntity() : null;
  }
}
