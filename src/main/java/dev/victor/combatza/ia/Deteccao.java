package dev.victor.combatza.ia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.combate.Captura;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.config.Configuracao;
import dev.victor.combatza.golpes.StatusNossos;
import dev.victor.combatza.rede.SinaisPayload;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 7.1 (decisões dele, 19/10): o selvagem te NOTA pela VISÃO. Na frente dele (um cone de 120 graus), até 10 blocos de dia e
// 6 à noite, e sem nada no meio, o medidor vai enchendo: 3 segundos para encher. Agachado (Shift), demora o dobro. (Era 16 e
// 10 blocos e 1,5 s; dele, 20/10: "tá muito fácil do selvagem te ver". Os três ficam no arquivo de configuração.)
// Atrás dele, só nota de perto (5 blocos) e bem devagar. Saiu da vista antes de encher, o medidor esvazia (esquece em 2 s).
// Encheu: ele reage pelo temperamento (Temperamento): o AGRESSIVO começa a batalha; o PACÍFICO para e fica te olhando; o
// ARISCO foge. TODOS mostram os sinais no seu jogo (SinaisNaTela): o "?" em cima dele e o olho no alto da tela.
// 7.1d (dele: "um Pokémon dormindo está me enxergando, nem faz sentido"): DORMINDO, NÃO VÊ. O Cobblemon acorda o selvagem
// quando um jogador em pé chega a 8 blocos (agachado, ele continua dormindo); acordado, ele começa a te notar como sempre
public class Deteccao {
  private static final int A_CADA_TICKS = 5;
  private static final double CONE = Math.cos(Math.toRadians(60));
  private static final double POR_TRAS_SO_DE_PERTO = 5;
  private static final double POR_TRAS = 0.15;
  private static final double AGACHADO = 0.5;
  private static final double TICKS_PARA_ESQUECER = 40;
  private static final long TICKS_DO_SINAL_DO_AGRESSIVO = 2L * SharedConstants.TICKS_PER_SECOND;
  private static final long TICKS_OLHANDO_SEM_TE_VER = 8L * SharedConstants.TICKS_PER_SECOND;
  private static final long TICKS_FUGINDO = 5L * SharedConstants.TICKS_PER_SECOND;
  private static final double DISTANCIA_DA_FUGA = 12;
  private static final float VELOCIDADE_DA_FUGA = 1.5f;
  private static final int PASSOS_DO_PROGRESSO = 10;
  // 8.6 (dele, 20/10: escolheu "perde o interesse por 1 minuto"): depois que você foge, ou que todos os seus Pokémon
  // desmaiam, os selvagens daquela batalha não te notam por 1 minuto (o "?" nem enche): dá tempo de ir embora ou de curar.
  // Bater neles ou mandar um golpe começa a batalha do mesmo jeito: quem não começa é ele
  private static final long TICKS_SEM_INTERESSE = 60L * SharedConstants.TICKS_PER_SECOND;

  private static final class Atencao {
    private final PokemonEntity selvagem;
    private final ServerPlayer jogador;
    private double medidor;
    private Temperamento.Tipo tipo;
    private long notouNoTick;
    private long vistoNoTick;
    private long atualizadoNoTick;

    private Atencao(PokemonEntity selvagem, ServerPlayer jogador) {
      this.selvagem = selvagem;
      this.jogador = jogador;
    }
  }

  private static final Map<String, Atencao> ATENCOES = new HashMap<>();
  // Até que tick cada selvagem está sem interesse em cada jogador (a chave é a mesma das ATENCOES)
  private static final Map<String, Long> SEM_INTERESSE = new HashMap<>();
  private static final Map<UUID, List<SinaisPayload.Sinal>> ULTIMO_ENVIO = new HashMap<>();

  private Deteccao() {
  }

  public static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    for (Atencao atencao : ATENCOES.values()) {
      reagir(atencao, agora);
    }
    if (agora % A_CADA_TICKS != 0) {
      return;
    }
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      if (!jogador.isSpectator() && jogador.isAlive()) {
        olharEmVolta(jogador, agora);
      }
    }
    esquecer(agora);
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      enviarSinais(jogador);
    }
  }

  public static void esquecer(ServerPlayer jogador) {
    ATENCOES.values().removeIf(atencao -> atencao.jogador == jogador);
    ULTIMO_ENVIO.remove(jogador.getUUID());
  }

  // 8.1: ele já te notou (o "?" encheu e ele ainda lembra)? Sem isso, a Pokébola vale "pelas costas" (ChanceDeCaptura)
  public static boolean teNotou(PokemonEntity selvagem, ServerPlayer jogador) {
    Atencao atencao = ATENCOES.get(chave(selvagem, jogador));
    return atencao != null && atencao.tipo != null;
  }

  public static void limpar() {
    ATENCOES.clear();
    ULTIMO_ENVIO.clear();
    SEM_INTERESSE.clear();
  }

  // A batalha acabou com você fugindo ou com o seu time desmaiado (Combate): os selvagens dela te esquecem por 1 minuto
  public static void perderOInteresse(ServerPlayer jogador, List<PokemonEntity> selvagens) {
    long ate = jogador.server.getTickCount() + TICKS_SEM_INTERESSE;
    for (PokemonEntity selvagem : selvagens) {
      SEM_INTERESSE.put(chave(selvagem, jogador), ate);
      ATENCOES.remove(chave(selvagem, jogador));
    }
  }

  private static boolean semInteresse(PokemonEntity selvagem, ServerPlayer jogador, long agora) {
    Long ate = SEM_INTERESSE.get(chave(selvagem, jogador));
    return ate != null && agora < ate;
  }

  // Os selvagens em volta que podem te notar: fora de qualquer batalha (a nossa e a do Cobblemon)
  private static void olharEmVolta(ServerPlayer jogador, long agora) {
    double alcance = jogador.level().isDay() ? Configuracao.alcanceDaVisaoDeDia() : Configuracao.alcanceDaVisaoDeNoite();
    for (PokemonEntity selvagem : jogador.level().getEntitiesOfClass(PokemonEntity.class,
        jogador.getBoundingBox().inflate(alcance), e -> e.getPokemon().isWild() && e.isAlive() && !e.isBattling()
            && !Captura.dentroDaBola(e) && !Desmaio.esta(e) && !BatalhasDeTreinador.ehDoTreinador(e)
            && !GerenciarCombates.estaEmCombate(e.getPokemon()) && !semInteresse(e, jogador, agora))) {
      Atencao atencao = ATENCOES.computeIfAbsent(chave(selvagem, jogador), k -> new Atencao(selvagem, jogador));
      double taxa = taxa(selvagem, jogador, alcance);
      atencao.atualizadoNoTick = agora;
      if (taxa > 0) {
        atencao.vistoNoTick = agora;
      }
      if (atencao.tipo == null) {
        atencao.medidor = Math.clamp(atencao.medidor + (taxa > 0 ? taxa : -A_CADA_TICKS / TICKS_PARA_ESQUECER), 0, 1);
        if (atencao.medidor >= 1) {
          notar(atencao, agora);
        }
      }
    }
  }

  // Quanto o "?" enche nesta olhada (0 = não te vê). Na frente: inteiro. Atrás: só de perto, e bem pouco
  private static double taxa(PokemonEntity selvagem, ServerPlayer jogador, double alcance) {
    if (StatusNossos.estaDormindo(selvagem.getPokemon())) {
      return 0;
    }
    double distancia = selvagem.distanceTo(jogador);
    if (distancia > alcance || !selvagem.hasLineOfSight(jogador)) {
      return 0;
    }
    Vec3 olhar = Vec3.directionFromRotation(0, selvagem.getYHeadRot());
    Vec3 paraOJogador = new Vec3(jogador.getX() - selvagem.getX(), 0, jogador.getZ() - selvagem.getZ()).normalize();
    double quanto = olhar.dot(paraOJogador) >= CONE ? 1 : (distancia <= POR_TRAS_SO_DE_PERTO ? POR_TRAS : 0);
    if (jogador.isCrouching()) {
      quanto *= AGACHADO;
    }
    return quanto * A_CADA_TICKS / (Configuracao.segundosParaNotar() * SharedConstants.TICKS_PER_SECOND);
  }

  private static void notar(Atencao atencao, long agora) {
    Temperamento.Decisao decisao = Temperamento.decidir(atencao.selvagem);
    atencao.tipo = decisao.tipo();
    atencao.notouNoTick = agora;
    CobblemonCombatZA.LOGGER.info("DETECÇÃO: {} (nv {}, {}) notou {}: raiva {} → {} ({})",
        atencao.selvagem.getPokemon().getSpecies().getName(), atencao.selvagem.getPokemon().getLevel(),
        atencao.selvagem.getPokemon().getNature().getName().getPath(), atencao.jogador.getGameProfile().getName(),
        Math.round(decisao.raiva()), atencao.tipo, decisao.porque());
    // 8.6: com o seu time todo desmaiado, o agressivo não começa batalha (ela acabaria na hora, e de novo a cada minuto)
    if (atencao.tipo == Temperamento.Tipo.AGRESSIVO && !Combate.timeTodoDesmaiado(atencao.jogador)) {
      GerenciarCombates.entrarEmCombate(atencao.jogador, atencao.selvagem, null);
    }
  }

  // A cada tick: o pacífico fica virado para você e parado; o arisco corre para o lado oposto
  private static void reagir(Atencao atencao, long agora) {
    PokemonEntity selvagem = atencao.selvagem;
    if (atencao.tipo == null || !selvagem.isAlive() || GerenciarCombates.estaEmCombate(selvagem.getPokemon())
        || StatusNossos.estaDormindo(selvagem.getPokemon())) {
      return;
    }
    if (atencao.tipo == Temperamento.Tipo.PACIFICO) {
      double dx = atencao.jogador.getX() - selvagem.getX();
      double dz = atencao.jogador.getZ() - selvagem.getZ();
      float giro = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
      selvagem.setYRot(giro);
      selvagem.setYBodyRot(giro);
      selvagem.setYHeadRot(giro);
      selvagem.getNavigation().stop();
    } else if (atencao.tipo == Temperamento.Tipo.ARISCO && agora - atencao.notouNoTick <= TICKS_FUGINDO
        && agora % SharedConstants.TICKS_PER_SECOND == 0) {
      fugir(selvagem, atencao.jogador);
    }
  }

  // 7.5: o bando dele entrou na batalha (Bandos) e ele, arisco, te nota na hora (sem o "?" enchendo) e foge, como na 7.1
  public static void fugirComOBando(PokemonEntity selvagem, ServerPlayer jogador, long agora) {
    Atencao atencao = ATENCOES.computeIfAbsent(chave(selvagem, jogador), k -> new Atencao(selvagem, jogador));
    atencao.medidor = 1;
    atencao.tipo = Temperamento.Tipo.ARISCO;
    atencao.notouNoTick = agora;
    atencao.vistoNoTick = agora;
    atencao.atualizadoNoTick = agora;
    fugir(selvagem, jogador);
  }

  // Um ponto 12 blocos para longe de você (Andar)
  private static void fugir(PokemonEntity selvagem, ServerPlayer jogador) {
    Andar.longeDe(selvagem, jogador, DISTANCIA_DA_FUGA, VELOCIDADE_DA_FUGA);
  }

  // O medidor que esvaziou some; quem notou fica: o agressivo, 2 s; o pacífico (te olhando), até passar 8 s sem te ver; o
  // arisco, a fuga. E quem saiu da batalha, morreu ou sumiu também sai daqui
  private static void esquecer(long agora) {
    SEM_INTERESSE.values().removeIf(ate -> ate <= agora);
    ATENCOES.values().removeIf(atencao -> {
      PokemonEntity selvagem = atencao.selvagem;
      if (!selvagem.isAlive() || selvagem.isRemoved() || atencao.jogador.isRemoved()) {
        return true;
      }
      if (atencao.tipo == null) {
        // Saiu do alcance (ninguém olhou por ele nesta volta): esvazia aqui
        if (atencao.atualizadoNoTick != agora) {
          atencao.medidor -= A_CADA_TICKS / TICKS_PARA_ESQUECER;
        }
        return atencao.medidor <= 0;
      }
      return switch (atencao.tipo) {
        case AGRESSIVO -> agora - atencao.notouNoTick > TICKS_DO_SINAL_DO_AGRESSIVO;
        case PACIFICO -> GerenciarCombates.estaEmCombate(selvagem.getPokemon())
            || agora - atencao.vistoNoTick > TICKS_OLHANDO_SEM_TE_VER;
        case ARISCO -> agora - atencao.notouNoTick > TICKS_FUGINDO;
      };
    });
  }

  // Só quando muda: o progresso vai de 10% em 10%, para um "?" enchendo não mandar pacote a cada olhada
  private static void enviarSinais(ServerPlayer jogador) {
    List<SinaisPayload.Sinal> sinais = new ArrayList<>();
    for (Atencao atencao : ATENCOES.values()) {
      if (atencao.jogador == jogador && (atencao.tipo != null || atencao.medidor > 0)) {
        float progresso = (float) Math.round(atencao.medidor * PASSOS_DO_PROGRESSO) / PASSOS_DO_PROGRESSO;
        int tipo = atencao.tipo == null ? SinaisPayload.NOTANDO : atencao.tipo.ordinal();
        sinais.add(new SinaisPayload.Sinal(atencao.selvagem.getId(), progresso, tipo));
      }
    }
    if (!sinais.equals(ULTIMO_ENVIO.get(jogador.getUUID())) && ServerPlayNetworking.canSend(jogador, SinaisPayload.TIPO)) {
      ULTIMO_ENVIO.put(jogador.getUUID(), sinais);
      ServerPlayNetworking.send(jogador, new SinaisPayload(sinais));
    }
  }

  private static String chave(PokemonEntity selvagem, ServerPlayer jogador) {
    return selvagem.getUUID() + "/" + jogador.getUUID();
  }
}
