package dev.victor.combatza.treinadores;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.SemDerrubar;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.rede.PerguntaDoTreinadorPayload;
import dev.victor.combatza.rede.RespostaDoTreinadorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

// 21/10 (treinadores, decisão dele: "ele escolhe na hora: uma telinha com 'Tempo real' / 'Por turnos' ao falar com o
// treinador"). O caminho:
// 1. falou com o treinador do RCT (ou o treinador te desafiou de longe): o RCT monta a batalha por turnos e o Cobblemon
//    avisa ANTES de começar (BATTLE_STARTED_PRE, que dá para cancelar). Sendo um duelo 1 contra 1 de um jogador com um
//    treinador do RCT, ela é cancelada aqui (com o "silêncio" de sempre: nenhuma mensagem de erro no chat), e o RCT não
//    anota nada: para ele, a batalha nem começou (o makeBattle devolve false e o treinador não muda);
// 2. o seu jogo abre a telinha (PerguntaDoTreinadorPayload → client.TelaDoTreinador);
// 3. "Por turnos": o RCT começa de novo, do jeito dele (TrainerMob.startBattleWith), e desta vez o aviso passa direto;
//    "Tempo real": a nossa batalha (BatalhasDeTreinador.comecar); fechou a tela (Esc): nada começa. As duplas do RCT, as
//    batalhas com nível ajustado e quem joga sem o nosso mod no cliente continuam por turnos, sem pergunta
final class EscolhaDoModo {
  // A escolha vale por 30 s; depois disso, a resposta é ignorada (fale com o treinador de novo)
  private static final int SEGUNDOS_PARA_ESCOLHER = 30;
  private static final String FRASE = "cobblemon_combat_za.treinador.";

  private record Pendente(LivingEntity treinador, List<Pokemon> time, long ateOTick) {
  }

  private static final Map<UUID, Pendente> PENDENTES = new HashMap<>();
  // "Por turnos": a próxima batalha do RCT deste jogador com este treinador passa pelo aviso sem pergunta
  private static final Map<UUID, UUID> PASSAGENS = new HashMap<>();
  // "Termine a batalha atual": no máximo um aviso a cada 10 s (o treinador que desafia de longe tenta a cada 3 s)
  private static final int TICKS_ENTRE_AVISOS = 10 * SharedConstants.TICKS_PER_SECOND;
  private static final Map<UUID, Long> ULTIMO_AVISO = new HashMap<>();

  private record Duelo(ServerPlayer jogador, LivingEntity treinador, List<Pokemon> time) {
  }

  private EscolhaDoModo() {
  }

  static void registrar() {
    CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, SemDerrubar.evento("EscolhaDoModo.BATTLE_STARTED_PRE", EscolhaDoModo::aoIniciar));
    ServerPlayNetworking.registerGlobalReceiver(RespostaDoTreinadorPayload.TIPO,
        (pacote, contexto) -> responder(contexto.player(), pacote.escolha()));
    ServerPlayConnectionEvents.DISCONNECT.register(SemDerrubar.aoSair("EscolhaDoModo.DISCONNECT", (conexao, servidor) -> {
      PENDENTES.remove(conexao.getPlayer().getUUID());
      PASSAGENS.remove(conexao.getPlayer().getUUID());
      ULTIMO_AVISO.remove(conexao.getPlayer().getUUID());
    }));
  }

  static void atualizar(long agora) {
    PENDENTES.values().removeIf(pendente -> agora > pendente.ateOTick());
  }

  static void limpar() {
    PENDENTES.clear();
    PASSAGENS.clear();
    ULTIMO_AVISO.clear();
  }

  private static void aoIniciar(BattleStartedEvent.Pre evento) {
    // Outro mod já cancelou a batalha: não há o que perguntar
    Duelo duelo = evento.isCanceled() ? null : dueloComTreinador(evento.getBattle());
    if (duelo == null) {
      return;
    }
    ServerPlayer jogador = duelo.jogador();
    LivingEntity treinador = duelo.treinador();
    UUID passagem = PASSAGENS.remove(jogador.getUUID());
    if (treinador.getUUID().equals(passagem)) {
      CobblemonCombatZA.LOGGER.info("TREINADOR: {} escolheu POR TURNOS contra {}", jogador.getGameProfile().getName(),
          PonteDoRct.id(treinador));
      return;
    }
    // Sem o nosso mod no cliente, não há telinha: a batalha por turnos segue como sempre
    if (!ServerPlayNetworking.canSend(jogador, PerguntaDoTreinadorPayload.TIPO)) {
      return;
    }
    evento.cancel();
    evento.setReason(Component.translatable(CobblemonCombatZA.CHAVE_SILENCIO));
    long agora = jogador.server.getTickCount();
    // Já na nossa batalha (um selvagem, ou outro treinador): uma batalha de cada vez
    if (GerenciarCombates.estaEmCombate(jogador) || BatalhasDeTreinador.estaEmBatalha(jogador)) {
      if (agora - ULTIMO_AVISO.getOrDefault(jogador.getUUID(), Long.MIN_VALUE / 2) >= TICKS_ENTRE_AVISOS) {
        ULTIMO_AVISO.put(jogador.getUUID(), agora);
        Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "ja_em_batalha"));
      }
      return;
    }
    // A telinha já está aberta: o treinador que te desafia de longe (o "forçar batalha" do RCT) tenta de novo a cada 3 s
    Pendente aberta = PENDENTES.get(jogador.getUUID());
    if (aberta != null && agora <= aberta.ateOTick()) {
      return;
    }
    PENDENTES.put(jogador.getUUID(), new Pendente(treinador, duelo.time(),
        agora + (long) SEGUNDOS_PARA_ESCOLHER * SharedConstants.TICKS_PER_SECOND));
    ServerPlayNetworking.send(jogador, new PerguntaDoTreinadorPayload(treinador.getDisplayName()));
    CobblemonCombatZA.LOGGER.info("TREINADOR: {} quer batalhar com {} ({} Pokémon); perguntando o modo",
        PonteDoRct.id(treinador), jogador.getGameProfile().getName(), duelo.time().size());
  }

  // Um duelo 1 contra 1, sem nível ajustado, entre um jogador e um treinador do RCT; null = qualquer outra batalha
  private static Duelo dueloComTreinador(PokemonBattle batalha) {
    BattleFormat formato = batalha.getFormat();
    if (formato.getBattleType().getPokemonPerSide() != 1 || formato.getAdjustLevel() > 0) {
      return null;
    }
    ServerPlayer jogador = null;
    LivingEntity treinador = null;
    List<Pokemon> time = new ArrayList<>();
    int atores = 0;
    for (BattleActor ator : batalha.getActors()) {
      atores++;
      if (ator instanceof PlayerBattleActor doJogador) {
        jogador = doJogador.getEntity();
      } else if (ator instanceof EntityBackedBattleActor<?> comCorpo && PonteDoRct.ehTreinador(comCorpo.getEntity())) {
        treinador = comCorpo.getEntity();
        // O original de cada Pokémon (o do time do treinador): o rctapi entrega uma cópia "de batalha" que o Cobblemon apaga
        // do mundo fora da batalha por turnos; a nossa cópia é feita do original (BatalhasDeTreinador)
        for (BattlePokemon pokemon : ator.getPokemonList()) {
          time.add(pokemon.getOriginalPokemon());
        }
      }
    }
    return atores == 2 && jogador != null && treinador != null && !time.isEmpty() ? new Duelo(jogador, treinador, time)
        : null;
  }

  private static void responder(ServerPlayer jogador, int escolha) {
    Pendente pendente = PENDENTES.remove(jogador.getUUID());
    if (pendente == null || jogador.server.getTickCount() > pendente.ateOTick()) {
      return;
    }
    LivingEntity treinador = pendente.treinador();
    if (escolha == RespostaDoTreinadorPayload.TEMPO_REAL) {
      CobblemonCombatZA.LOGGER.info("TREINADOR: {} escolheu TEMPO REAL contra {}", jogador.getGameProfile().getName(),
          PonteDoRct.id(treinador));
      BatalhasDeTreinador.comecar(jogador, treinador, pendente.time());
    } else if (escolha == RespostaDoTreinadorPayload.POR_TURNOS) {
      // A telinha não pausa o jogo: se nesse meio tempo começou outra batalha nossa (um selvagem, um duelo), nada de batalha
      // por turnos por cima dela
      if (GerenciarCombates.estaEmCombate(jogador) || BatalhasDeTreinador.estaEmBatalha(jogador)) {
        Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "ja_em_batalha"));
        return;
      }
      porTurnos(jogador, treinador);
    } else {
      CobblemonCombatZA.LOGGER.info("TREINADOR: {} fechou a escolha contra {}: nenhuma batalha",
          jogador.getGameProfile().getName(), PonteDoRct.id(treinador));
    }
  }

  // A batalha por turnos do RCT, do jeito dele (o RCT confere de novo se o treinador pode batalhar e explica se não pode)
  static void porTurnos(ServerPlayer jogador, LivingEntity treinador) {
    if (!treinador.isAlive() || treinador.isRemoved()) {
      return;
    }
    PASSAGENS.put(jogador.getUUID(), treinador.getUUID());
    boolean chamou = PonteDoRct.porTurnos(treinador, jogador);
    // A batalha começou (o aviso já passou e usou a passagem) ou o RCT recusou: a passagem não fica sobrando
    PASSAGENS.remove(jogador.getUUID());
    if (!chamou) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "nao_pode", treinador.getDisplayName()));
    }
  }
}
