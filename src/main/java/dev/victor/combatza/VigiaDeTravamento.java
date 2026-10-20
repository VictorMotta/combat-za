package dev.victor.combatza;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

// Para os testes (dele, 18/10, depois de um travamento sem nenhum erro no log: "foi apenas o game"): um vigia, numa linha de
// execução separada, confere a cada segundo se o servidor (e, no seu jogo, a tela) continua andando. Parado por 10 segundos
// sem estar pausado, ele escreve no latest.log onde as linhas de execução principais estão presas (a do servidor e a da
// tela), e avisa de novo quando voltar a andar. Mesmo fechando o jogo à força, a resposta fica no log
public class VigiaDeTravamento {
  private static final long LIMITE_EM_MS = 10_000;
  private static final long INTERVALO_EM_MS = 1_000;
  private static final int PASSOS_DA_PILHA = 60;
  private static final List<String> LINHAS_PRINCIPAIS = List.of("Server thread", "Render thread");

  private final String nome;
  private final BooleanSupplier pausado;
  private volatile long ultimoSinal = System.currentTimeMillis();
  private volatile boolean ligado = true;

  private VigiaDeTravamento(String nome, BooleanSupplier pausado) {
    this.nome = nome;
    this.pausado = pausado;
  }

  // "pausado": quando parar é normal (o jogo pausado no Esc, ou sem mundo aberto) e não conta como travamento
  public static VigiaDeTravamento comecar(String nome, BooleanSupplier pausado) {
    VigiaDeTravamento vigia = new VigiaDeTravamento(nome, pausado);
    Thread linha = new Thread(vigia::vigiar, "Vigia de travamento (" + nome + ")");
    // "daemon": uma linha que não segura o jogo aberto quando ele fecha
    linha.setDaemon(true);
    linha.start();
    return vigia;
  }

  // Chamado a cada tick por quem é vigiado: "ainda estou andando"
  public void sinal() {
    ultimoSinal = System.currentTimeMillis();
  }

  public void parar() {
    ligado = false;
  }

  private void vigiar() {
    long travouEm = 0;
    while (ligado) {
      try {
        Thread.sleep(INTERVALO_EM_MS);
      } catch (InterruptedException interrompido) {
        Thread.currentThread().interrupt();
        return;
      }
      if (pausado.getAsBoolean()) {
        sinal();
        continue;
      }
      long parado = System.currentTimeMillis() - ultimoSinal;
      if (travouEm == 0 && parado >= LIMITE_EM_MS) {
        travouEm = ultimoSinal;
        contarOndeEsta(parado);
      } else if (travouEm != 0 && parado < LIMITE_EM_MS) {
        CobblemonCombatZA.LOGGER.warn("VIGIA: o {} voltou a andar depois de {} s parado", nome,
            (ultimoSinal - travouEm) / 1000);
        travouEm = 0;
      }
    }
  }

  // A pilha de chamadas de cada linha principal: o método em que ela está agora, quem chamou ele, e assim por diante
  private void contarOndeEsta(long parado) {
    CobblemonCombatZA.LOGGER.warn("VIGIA: o {} está parado há {} s. Onde as linhas principais estão:", nome,
        parado / 1000);
    for (Map.Entry<Thread, StackTraceElement[]> linha : Thread.getAllStackTraces().entrySet()) {
      if (!LINHAS_PRINCIPAIS.contains(linha.getKey().getName())) {
        continue;
      }
      StringBuilder pilha = new StringBuilder();
      StackTraceElement[] passos = linha.getValue();
      for (int i = 0; i < Math.min(PASSOS_DA_PILHA, passos.length); i++) {
        pilha.append("\n    at ").append(passos[i]);
      }
      CobblemonCombatZA.LOGGER.warn("VIGIA: \"{}\" ({}):{}", linha.getKey().getName(), linha.getKey().getState(),
          pilha);
    }
  }
}
