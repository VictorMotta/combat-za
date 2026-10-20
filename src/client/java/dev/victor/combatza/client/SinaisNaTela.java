package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.combate.Captura;
import dev.victor.combatza.rede.SinaisPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// 7.1b (pedido dele, 19/10, com duas fotos do Z-A: "eu queria uma interrogação simples igual no Pokémon Z-A e não algo
// carregando"): os sinais do selvagem que está te notando (Deteccao, no servidor). NÃO TEM "!".
// - Em cima dele, só um "?" ciano brilhando, enquanto ele te nota. Quando ele te nota de vez, o "?" some.
// - No alto da tela, a FAIXA DO OLHO: é ela que diz que alguém está te vendo. Enquanto ele te nota, o olho acende e cresce;
//   quando ele te nota de vez, o olho dá um susto (cresce mais, aceso) e volta ao tamanho normal ("o olho cresce alertando
//   e acende mesmo, e volta ao tamanho normal", dele). As setas mostram o lado do bicho: à esquerda, só as da esquerda;
//   na sua frente, as dos dois lados, igual à foto
public class SinaisNaTela {
  // O "?" em cima do selvagem
  private static final float ACIMA_DA_CABECA = 0.5f;
  private static final float SUBIR_NA_TELA = 26f;
  private static final float TAMANHO_DA_INTERROGACAO = 2f;
  private static final float PULSO = 0.08f;
  private static final double PULSOS_POR_SEGUNDO = 1.5;
  // O brilho é o mesmo "?" desenhado 8 vezes em volta, um pouco afastado, num ciano meio transparente
  private static final float AFASTAR_O_BRILHO = 0.7f;
  private static final int CIANO_CLARO = 0xFFD2FDFF;
  private static final int BRILHO = 0x9030D8F0;

  // A faixa do olho, no alto da tela, no meio
  private static final float CENTRO_DA_FAIXA = 16f;
  private static final float LARGURA_DA_FAIXA = 190f;
  private static final float ALTURA_DA_FAIXA = 14f;
  private static final float LARGURA_DA_CAIXA = 30f;
  private static final float ALTURA_DA_CAIXA = 18f;
  private static final float CANTO_DA_CAIXA = 4f;
  private static final int VERMELHO = 0xFFFF4D6D;
  private static final int VERMELHO_DAS_SETAS = 0xFFFF7A8E;
  private static final int CAIXA_APAGADA = 0xC02A2630;
  private static final int CAIXA_ACESA = 0xF0FF5C7A;
  private static final int OLHO_APAGADO = 0xFF8C8C96;
  private static final int OLHO_ACESO = 0xFFFFFFFF;
  private static final float LARGURA_DO_OLHO = 7.5f;
  private static final float ALTURA_DO_OLHO = 4.5f;
  private static final float TRACO_DO_OLHO = 1.6f;
  private static final float MENINA_DO_OLHO = 2.4f;
  private static final int PONTOS_DA_PALPEBRA = 12;
  private static final int SETAS_POR_LADO = 3;
  private static final float PRIMEIRA_SETA = 7f;
  private static final float ENTRE_SETAS = 8f;
  private static final float ALTURA_DA_SETA = 9f;
  private static final float ESPESSURA_DA_SETA = 2.2f;
  // Um brilho que corre pelas setas, do olho para fora
  private static final double ONDAS_POR_SEGUNDO = 1.2;
  // Até 20 graus para cada lado, ele está "na sua frente": setas dos dois lados
  private static final float NA_FRENTE = 20f;
  // O olho cresce até 35% enquanto ele te nota; no susto, sobe a 70% em 0,2 s e volta ao normal até 0,6 s
  private static final float CRESCER_NOTANDO = 0.35f;
  private static final float CRESCER_NO_SUSTO = 0.7f;
  private static final float SUBIDA_DO_SUSTO = 0.2f;
  private static final float DURACAO_DO_SUSTO = 0.6f;
  private static final float ALERTA_ESPALHA = 14f;
  // Depois do susto, a faixa fica mais um pouco e some aos poucos
  private static final float FAIXA_DEPOIS_DE_NOTAR = 1.4f;
  private static final float VELOCIDADE_DO_APARECER = 8f;
  private static final float VELOCIDADE_DO_SUMIR = 5f;

  private static final Map<Integer, SinaisPayload.Sinal> SINAIS = new HashMap<>();
  // Quando cada um te notou de vez (em milissegundos), para o susto do olho
  private static final Map<Integer, Long> NOTOU_EM = new HashMap<>();
  // A faixa muda aos poucos entre um quadro e outro (o servidor manda o progresso de 10% em 10%)
  private static float alfaDaFaixa;
  private static float progressoDaFaixa;
  private static float escalaDoOlho = 1f;
  private static float sustoDaFaixa = -1f;
  private static float setasDaEsquerda;
  private static float setasDaDireita;
  private static long quadroAnterior;

  private SinaisNaTela() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(SinaisPayload.TIPO, (pacote, contexto) -> {
      long agora = Util.getMillis();
      SINAIS.clear();
      for (SinaisPayload.Sinal sinal : pacote.sinais()) {
        SINAIS.put(sinal.entidade(), sinal);
        if (sinal.tipo() != SinaisPayload.NOTANDO) {
          NOTOU_EM.putIfAbsent(sinal.entidade(), agora);
        }
      }
      NOTOU_EM.keySet().retainAll(SINAIS.keySet());
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      SINAIS.clear();
      NOTOU_EM.clear();
      alfaDaFaixa = 0;
    });
    HudRenderCallback.EVENT.register(SinaisNaTela::desenhar);
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    Minecraft cliente = Minecraft.getInstance();
    long agora = Util.getMillis();
    float passou = quadroAnterior == 0 ? 0 : Math.min(0.1f, (agora - quadroAnterior) / 1000f);
    quadroAnterior = agora;
    if (cliente.level == null || cliente.player == null || cliente.options.hideGui
        || cliente.getDebugOverlay().showDebugScreen()) {
      return;
    }
    float parcial = tempo.getGameTimeDeltaPartialTick(true);
    float pulso = 1f + PULSO * (float) Math.sin(agora / 1000.0 * 2 * Math.PI * PULSOS_POR_SEGUNDO);
    // A faixa segue quem acabou de te notar; se ninguém, quem está mais perto de te notar
    LivingEntity daFaixa = null;
    float progresso = 0;
    float desdeQueNotou = -1;
    for (SinaisPayload.Sinal sinal : SINAIS.values()) {
      Entity entidade = cliente.level.getEntity(sinal.entidade());
      if (!(entidade instanceof LivingEntity vivo) || !vivo.isAlive()
          || (vivo instanceof PokemonEntity pokemon && Captura.dentroDaBola(pokemon))) {
        continue;
      }
      Long notou = NOTOU_EM.get(sinal.entidade());
      if (notou == null) {
        desenharInterrogacao(tela, cliente.font, vivo, parcial, pulso);
        if (desdeQueNotou < 0 && (daFaixa == null || sinal.progresso() > progresso)) {
          daFaixa = vivo;
          progresso = sinal.progresso();
        }
        continue;
      }
      float segundos = (agora - notou) / 1000f;
      if (segundos < FAIXA_DEPOIS_DE_NOTAR && (desdeQueNotou < 0 || segundos < desdeQueNotou)) {
        daFaixa = vivo;
        progresso = 1;
        desdeQueNotou = segundos;
      }
    }
    tela.flush();
    atualizarFaixa(cliente, daFaixa, progresso, desdeQueNotou, parcial, passou);
    if (alfaDaFaixa > 0) {
      desenharFaixa(tela, agora);
      tela.flush();
    }
  }

  private static void desenharInterrogacao(GuiGraphics tela, Font fonte, LivingEntity vivo, float parcial, float pulso) {
    Vec3 cabeca = vivo.getPosition(parcial).add(0, vivo.getBbHeight() + ACIMA_DA_CABECA, 0);
    float[] naTela = PlacasNaTela.naTela(cabeca, tela);
    if (naTela == null) {
      return;
    }
    tela.pose().pushPose();
    tela.pose().translate(naTela[0], naTela[1] - SUBIR_NA_TELA, 0);
    float tamanho = TAMANHO_DA_INTERROGACAO * pulso;
    tela.pose().scale(tamanho, tamanho, 1f);
    // A letra tem 5 de largura (mais 1 de espaço) e 7 de altura: o meio dela fica em (2,5; 3,5)
    tela.pose().translate(-(fonte.width("?") - 1) / 2f, -3.5f, 0);
    for (int i = 0; i < 8; i++) {
      double angulo = i * Math.PI / 4;
      tela.pose().pushPose();
      tela.pose().translate((float) Math.cos(angulo) * AFASTAR_O_BRILHO, (float) Math.sin(angulo) * AFASTAR_O_BRILHO, 0);
      tela.drawString(fonte, "?", 0, 0, BRILHO, false);
      tela.pose().popPose();
    }
    tela.drawString(fonte, "?", 0, 0, CIANO_CLARO, false);
    tela.pose().popPose();
  }

  private static void atualizarFaixa(Minecraft cliente, LivingEntity daFaixa, float progresso, float desdeQueNotou,
      float parcial, float passou) {
    if (daFaixa == null) {
      alfaDaFaixa = alfaDaFaixa < 0.02f ? 0 : alfaDaFaixa - alfaDaFaixa * Math.min(1f, passou * VELOCIDADE_DO_SUMIR);
      sustoDaFaixa = -1;
      return;
    }
    float rapidez = Math.min(1f, passou * VELOCIDADE_DO_APARECER);
    if (alfaDaFaixa == 0) {
      progressoDaFaixa = progresso;
    }
    alfaDaFaixa += (1 - alfaDaFaixa) * rapidez;
    progressoDaFaixa = progresso >= 1 ? 1 : progressoDaFaixa + (progresso - progressoDaFaixa) * rapidez;
    escalaDoOlho = tamanhoDoOlho(progressoDaFaixa, desdeQueNotou);
    sustoDaFaixa = desdeQueNotou;
    // O giro até o bicho, comparado com o seu: negativo = ele está à sua esquerda; positivo, à direita
    Vec3 eu = cliente.player.getPosition(parcial);
    Vec3 ele = daFaixa.getPosition(parcial);
    float paraEle = (float) Math.toDegrees(Math.atan2(ele.z - eu.z, ele.x - eu.x)) - 90f;
    float diferenca = Mth.wrapDegrees(paraEle - cliente.player.getViewYRot(parcial));
    boolean naFrente = Math.abs(diferenca) <= NA_FRENTE;
    setasDaEsquerda += ((naFrente || diferenca < 0 ? 1 : 0) - setasDaEsquerda) * rapidez;
    setasDaDireita += ((naFrente || diferenca > 0 ? 1 : 0) - setasDaDireita) * rapidez;
  }

  // Notando: cresce com o progresso. Notou: sobe rápido até o susto e desce suave até o tamanho normal
  private static float tamanhoDoOlho(float progresso, float desdeQueNotou) {
    if (desdeQueNotou < 0) {
      return 1f + CRESCER_NOTANDO * progresso;
    }
    if (desdeQueNotou < SUBIDA_DO_SUSTO) {
      return 1f + Mth.lerp(desdeQueNotou / SUBIDA_DO_SUSTO, CRESCER_NOTANDO, CRESCER_NO_SUSTO);
    }
    float t = Math.min(1f, (desdeQueNotou - SUBIDA_DO_SUSTO) / (DURACAO_DO_SUSTO - SUBIDA_DO_SUSTO));
    return 1f + CRESCER_NO_SUSTO * (1 - t * t * (3 - 2 * t));
  }

  private static void desenharFaixa(GuiGraphics tela, long agora) {
    float cx = tela.guiWidth() / 2f;
    float cy = CENTRO_DA_FAIXA;
    float alfa = alfaDaFaixa;
    float p = progressoDaFaixa;
    // O fundo: vermelho meio transparente no meio, sumindo nas pontas
    int fundo = Formas.comAlfa(VERMELHO, alfa * (0.3f + 0.35f * p));
    float metade = LARGURA_DA_FAIXA / 2;
    float topo = cy - ALTURA_DA_FAIXA / 2;
    Formas.degradeDeLado(tela, cx - metade, topo, metade, ALTURA_DA_FAIXA, Formas.comAlfa(fundo, 0), fundo);
    Formas.degradeDeLado(tela, cx, topo, metade, ALTURA_DA_FAIXA, fundo, Formas.comAlfa(fundo, 0));
    for (int i = 0; i < SETAS_POR_LADO; i++) {
      float distancia = LARGURA_DA_CAIXA / 2 + PRIMEIRA_SETA + i * ENTRE_SETAS;
      double fase = agora / 1000.0 * ONDAS_POR_SEGUNDO - (double) i / SETAS_POR_LADO;
      float onda = 0.7f + 0.3f * (float) Math.sin(2 * Math.PI * fase);
      float alfaDaSeta = alfa * (1f - 0.25f * i) * onda;
      seta(tela, cx - distancia, cy, -1, Formas.comAlfa(VERMELHO_DAS_SETAS, alfaDaSeta * setasDaEsquerda));
      seta(tela, cx + distancia, cy, 1, Formas.comAlfa(VERMELHO_DAS_SETAS, alfaDaSeta * setasDaDireita));
    }
    float largura = LARGURA_DA_CAIXA * escalaDoOlho;
    float altura = ALTURA_DA_CAIXA * escalaDoOlho;
    int caixa = Formas.comAlfa(Formas.misturar(CAIXA_APAGADA, CAIXA_ACESA, p), alfa);
    Formas.retangulo(tela, cx - largura / 2, cy - altura / 2, largura, altura, Formas.raios(CANTO_DA_CAIXA * escalaDoOlho),
        caixa, caixa);
    // O alerta do susto: uma borda que sai da caixa e se espalha, sumindo
    if (sustoDaFaixa >= 0 && sustoDaFaixa < DURACAO_DO_SUSTO) {
      float t = sustoDaFaixa / DURACAO_DO_SUSTO;
      float espalhou = ALERTA_ESPALHA * t;
      Formas.borda(tela, cx - largura / 2 - espalhou, cy - altura / 2 - espalhou, largura + 2 * espalhou,
          altura + 2 * espalhou, Formas.raios(CANTO_DA_CAIXA * escalaDoOlho + espalhou), 1.5f,
          Formas.comAlfa(CAIXA_ACESA, alfa * (1 - t)));
    }
    olho(tela, cx, cy, escalaDoOlho, Formas.comAlfa(Formas.misturar(OLHO_APAGADO, OLHO_ACESO, p), alfa));
  }

  // Uma seta "<" (sentido −1) ou ">" (sentido 1), com a ponta em (x, y)
  private static void seta(GuiGraphics tela, float x, float y, int sentido, int cor) {
    if ((cor >>> 24) == 0) {
      return;
    }
    float meiaAltura = ALTURA_DA_SETA / 2;
    float recuo = meiaAltura * 0.7f;
    Formas.caminhoArredondado(tela, new float[][] { { x - sentido * recuo, y - meiaAltura }, { x, y },
        { x - sentido * recuo, y + meiaAltura } }, ESPESSURA_DA_SETA, cor);
  }

  // O olho: as duas pálpebras (curvas que se encontram nos cantos) e a menina do olho no meio
  private static void olho(GuiGraphics tela, float cx, float cy, float escala, int cor) {
    float largura = LARGURA_DO_OLHO * escala;
    float altura = ALTURA_DO_OLHO * escala;
    float[][] deCima = new float[PONTOS_DA_PALPEBRA + 1][];
    float[][] deBaixo = new float[PONTOS_DA_PALPEBRA + 1][];
    for (int i = 0; i <= PONTOS_DA_PALPEBRA; i++) {
      float t = -1f + 2f * i / PONTOS_DA_PALPEBRA;
      float abertura = altura * (1 - t * t);
      deCima[i] = new float[] { cx + largura * t, cy - abertura };
      deBaixo[i] = new float[] { cx + largura * t, cy + abertura };
    }
    Formas.caminhoArredondado(tela, deCima, TRACO_DO_OLHO * escala, cor);
    Formas.caminhoArredondado(tela, deBaixo, TRACO_DO_OLHO * escala, cor);
    Formas.circulo(tela, cx, cy, MENINA_DO_OLHO * escala, cor);
  }
}
