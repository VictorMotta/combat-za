package dev.victor.combatza;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

// 23/10 (dele: "estava enfrentando a Misty no servidor dedicado e o servidor crashou, derrubou todos na mesma hora"): um erro em
// qualquer parte do mod que roda a cada tick (a IA, os golpes, os itens, as habilidades...) derrubava o servidor inteiro. Aqui,
// cada parte (e cada ouvinte de evento) roda protegida: se ela der erro, só ela pula aquele tick, o erro vai para o
// latest.log (com a pilha na primeira vez, e depois um lembrete a cada 30 s, para não encher o log) e o jogo continua para todo
// mundo
public final class SemDerrubar {
  private static final long LEMBRETE_A_CADA_MS = 30_000;
  private static final Map<String, Long> ULTIMO_AVISO = new HashMap<>();

  private SemDerrubar() {
  }

  public static void rodar(String parte, Runnable acao) {
    try {
      acao.run();
    } catch (RuntimeException | StackOverflowError erro) {
      avisar(parte, erro);
    }
  }

  // Os ouvintes de eventos (o dano, a morte, os eventos do Cobblemon...) também rodam no meio do tick: com erro, vale o que o
  // jogo faria sem o mod (o dano entra, a morte acontece, o evento segue)
  public static <T> Consumer<T> evento(String parte, Consumer<T> ouvinte) {
    return evento -> rodar(parte, () -> ouvinte.accept(evento));
  }

  public static ServerLivingEntityEvents.AllowDamage podeLevarDano(String parte, ServerLivingEntityEvents.AllowDamage o) {
    return (entidade, fonte, dano) -> {
      try {
        return o.allowDamage(entidade, fonte, dano);
      } catch (RuntimeException | StackOverflowError erro) {
        avisar(parte, erro);
        return true;
      }
    };
  }

  public static ServerLivingEntityEvents.AllowDeath podeMorrer(String parte, ServerLivingEntityEvents.AllowDeath o) {
    return (entidade, fonte, dano) -> {
      try {
        return o.allowDeath(entidade, fonte, dano);
      } catch (RuntimeException | StackOverflowError erro) {
        avisar(parte, erro);
        return true;
      }
    };
  }

  public static ServerLivingEntityEvents.AfterDamage depoisDoDano(String parte, ServerLivingEntityEvents.AfterDamage o) {
    return (entidade, fonte, base, tirado, bloqueou) -> rodar(parte,
        () -> o.afterDamage(entidade, fonte, base, tirado, bloqueou));
  }

  public static ServerLivingEntityEvents.AfterDeath depoisDaMorte(String parte, ServerLivingEntityEvents.AfterDeath o) {
    return (entidade, fonte) -> rodar(parte, () -> o.afterDeath(entidade, fonte));
  }

  public static ServerEntityEvents.Load aoCarregar(String parte, ServerEntityEvents.Load o) {
    return (entidade, nivel) -> rodar(parte, () -> o.onLoad(entidade, nivel));
  }

  public static ServerPlayConnectionEvents.Join aoEntrar(String parte, ServerPlayConnectionEvents.Join o) {
    return (conexao, remetente, servidor) -> rodar(parte, () -> o.onPlayReady(conexao, remetente, servidor));
  }

  public static ServerPlayConnectionEvents.Disconnect aoSair(String parte, ServerPlayConnectionEvents.Disconnect o) {
    return (conexao, servidor) -> rodar(parte, () -> o.onPlayDisconnect(conexao, servidor));
  }

  private static void avisar(String parte, Throwable erro) {
    long agora = System.currentTimeMillis();
    Long antes = ULTIMO_AVISO.get(parte);
    if (antes == null) {
      CobblemonCombatZA.LOGGER.error("ERRO EM {}: esta parte pulou (o servidor continua)", parte, erro);
      ULTIMO_AVISO.put(parte, agora);
    } else if (agora - antes >= LEMBRETE_A_CADA_MS) {
      CobblemonCombatZA.LOGGER.error("ERRO EM {} de novo: {}", parte, erro.toString());
      ULTIMO_AVISO.put(parte, agora);
    }
  }
}
