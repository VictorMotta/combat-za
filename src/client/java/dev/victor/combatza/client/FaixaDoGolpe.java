package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;

import dev.victor.combatza.golpes.CoresDosTipos;
import dev.victor.combatza.rede.GolpeDoSelvagemPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 7.2c (a placa do Z-A, ideia dele da 4.5: "o NOME do golpe que ele está usando numa faixa colorida + barra de carga"): o
// golpe que o selvagem está carregando, numa faixa da cor do tipo com o nome e uma barra que enche até o impacto. A barra
// fica cheia se ele ainda estiver correndo até o alvo; no impacto, a faixa some apagando. Quem desenha é a PlacasNaTela, em
// cima da placa (com o foco) e em cima da barrinha (em batalha, sem foco)
public class FaixaDoGolpe {
  private static final float ALTURA = 9f;
  private static final float ALTURA_DA_BARRA = 1.6f;
  private static final float FOLGA = 3f;
  private static final float LARGURA_MINIMA = 40f;
  private static final float LETRA = 0.8f;
  private static final float ALTURA_DA_LETRA = 7f;
  private static final int OPACIDADE_DO_FUNDO = 0xD8;
  private static final int TRILHO = 0x70000000;
  private static final int CHEIA = 0xF0FFFFFF;
  private static final int BRANCO = 0xFFFFFFFF;
  private static final long MILISSEGUNDOS_POR_TICK = 50;
  private static final long APAGANDO = 300;
  // Se o aviso do fim não chegar (o selvagem sumiu), a faixa vai embora sozinha depois disto além da carga
  private static final long SEGURANCA = 5000;

  private static final class Carga {
    private final String golpe;
    private final int cor;
    private final long comecouEm;
    private final long duracao;
    private long acabouEm = -1;

    private Carga(String golpe, int cor, long comecouEm, long duracao) {
      this.golpe = golpe;
      this.cor = cor;
      this.comecouEm = comecouEm;
      this.duracao = duracao;
    }
  }

  private static final Map<Integer, Carga> CARGAS = new HashMap<>();

  private FaixaDoGolpe() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(GolpeDoSelvagemPayload.TIPO, (aviso, contexto) -> {
      long agora = Util.getMillis();
      CARGAS.values().removeIf(carga -> acabou(carga, agora));
      if (aviso.ticks() > 0) {
        ElementalType tipo = ElementalTypes.get(aviso.tipo());
        int cor = tipo == null ? 0xFFFFFF : CoresDosTipos.de(tipo);
        CARGAS.put(aviso.entidade(), new Carga(aviso.golpe(), cor, agora, aviso.ticks() * MILISSEGUNDOS_POR_TICK));
        return;
      }
      Carga carga = CARGAS.get(aviso.entidade());
      if (carga != null && carga.acabouEm < 0) {
        carga.acabouEm = agora;
      }
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> CARGAS.clear());
  }

  // Já apagou, ou passou da segurança sem o aviso do fim. 7.4c: também vale para quem a tela nunca desenhou (um selvagem
  // longe, sem placa, ou capturado no meio da carga), que senão ficava guardado até você sair do mundo
  private static boolean acabou(Carga carga, long agora) {
    return carga.acabouEm >= 0 ? agora - carga.acabouEm >= APAGANDO : agora - carga.comecouEm > carga.duracao + SEGURANCA;
  }

  // A faixa centrada em x = 0, com a base em "baixo". Devolve a altura que ela ocupou (0 = sem golpe carregando)
  public static float desenhar(GuiGraphics tela, Font fonte, int entidade, float baixo, float alfa) {
    Carga carga = CARGAS.get(entidade);
    if (carga == null) {
      return 0;
    }
    long agora = Util.getMillis();
    if (acabou(carga, agora)) {
      CARGAS.remove(entidade);
      return 0;
    }
    float apagando = carga.acabouEm >= 0 ? 1f - (float) (agora - carga.acabouEm) / APAGANDO : 1f;
    float a = alfa * apagando;
    Component nome = Component.translatable("cobblemon.move." + carga.golpe);
    float largura = Math.max(LARGURA_MINIMA, fonte.width(nome) * LETRA + 2 * FOLGA);
    float x = -largura / 2;
    float topo = baixo - ALTURA - ALTURA_DA_BARRA;
    int fundo = Formas.comAlfa((OPACIDADE_DO_FUNDO << 24) | carga.cor, a);
    Formas.retangulo(tela, x, topo, largura, ALTURA + ALTURA_DA_BARRA, Formas.raios(2.5f), fundo, fundo);
    float progresso = carga.acabouEm >= 0 ? 1f
        : Math.min(1f, (float) (agora - carga.comecouEm) / Math.max(1, carga.duracao));
    float topoDaBarra = topo + ALTURA - 0.5f;
    Formas.retangulo(tela, x + FOLGA, topoDaBarra, largura - 2 * FOLGA, ALTURA_DA_BARRA, Formas.raios(0.8f),
        Formas.comAlfa(TRILHO, a), Formas.comAlfa(TRILHO, a));
    Formas.retangulo(tela, x + FOLGA, topoDaBarra, (largura - 2 * FOLGA) * progresso, ALTURA_DA_BARRA, Formas.raios(0.8f),
        Formas.comAlfa(CHEIA, a), Formas.comAlfa(CHEIA, a));
    tela.flush();
    tela.pose().pushPose();
    tela.pose().translate(-fonte.width(nome) * LETRA / 2, topo + (ALTURA - ALTURA_DA_LETRA * LETRA) / 2 - 0.5f, 0);
    tela.pose().scale(LETRA, LETRA, 1f);
    tela.drawString(fonte, nome, 0, 0, Formas.comAlfa(BRANCO, a), true);
    tela.pose().popPose();
    return ALTURA + ALTURA_DA_BARRA;
  }
}
