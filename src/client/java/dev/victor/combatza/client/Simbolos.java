package dev.victor.combatza.client;

import com.cobblemon.mod.common.pokemon.Gender;

import net.minecraft.client.gui.GuiGraphics;

// Os símbolos que o painel do time e as placas dos selvagens usam, feitos com as Formas. As medidas foram desenhadas
// para um tamanho (o raio de referência); para outro tamanho, tudo é multiplicado pela mesma proporção.
// "alfa" é a opacidade (1 = como desenhado, 0 = invisível), para a placa poder aparecer e sumir aos poucos
public class Simbolos {
  private static final float RAIO_DO_GENERO = 5.5f;
  private static final float TRACO_DO_GENERO = 0.85f;
  private static final float RAIO_DA_BOLA = 2.6f;

  private static final int BRANCO = 0xFFFFFFFF;
  private static final int MACHO = 0xF25F70C8;
  private static final int FEMEA = 0xF2E66496;
  private static final int VERMELHO_DA_BOLA = 0xFFE04040;
  private static final int BRANCO_DA_BOLA = 0xFFF4F4F4;
  private static final int CONTORNO_DA_BOLA = 0xFF202020;

  private Simbolos() {
  }

  // O ♂ e o ♀ do Z-A: um círculo azul ou rosa com o símbolo em branco, feito de anel e linhas. Sem gênero, nada
  public static void genero(GuiGraphics tela, float cx, float cy, float raio, Gender genero, float alfa) {
    if (genero == Gender.GENDERLESS) {
      return;
    }
    float k = raio / RAIO_DO_GENERO;
    float traco = TRACO_DO_GENERO * k;
    int branco = Formas.comAlfa(BRANCO, alfa);
    Formas.circulo(tela, cx, cy, raio, Formas.comAlfa(genero == Gender.MALE ? MACHO : FEMEA, alfa));
    if (genero == Gender.MALE) {
      Formas.anel(tela, cx - 0.9f * k, cy + 0.9f * k, 2.1f * k, traco, branco);
      Formas.linha(tela, cx + 0.3f * k, cy - 0.3f * k, cx + 2.5f * k, cy - 2.5f * k, traco, branco);
      Formas.linha(tela, cx + 0.8f * k, cy - 2.5f * k, cx + 2.9f * k, cy - 2.5f * k, traco, branco);
      Formas.linha(tela, cx + 2.5f * k, cy - 2.9f * k, cx + 2.5f * k, cy - 0.8f * k, traco, branco);
    } else {
      Formas.anel(tela, cx, cy - 1f * k, 2.1f * k, traco, branco);
      Formas.linha(tela, cx, cy + 1.1f * k, cx, cy + 4f * k, traco, branco);
      Formas.linha(tela, cx - 1.4f * k, cy + 2.6f * k, cx + 1.4f * k, cy + 2.6f * k, traco, branco);
    }
  }

  // Uma pokébola: contorno escuro, metade de cima vermelha, a de baixo branca, a faixa e o botão
  public static void pokebola(GuiGraphics tela, float cx, float cy, float raio, float alfa) {
    float k = raio / RAIO_DA_BOLA;
    int contorno = Formas.comAlfa(CONTORNO_DA_BOLA, alfa);
    int branco = Formas.comAlfa(BRANCO_DA_BOLA, alfa);
    Formas.circulo(tela, cx, cy, raio + 0.5f * k, contorno);
    Formas.circulo(tela, cx, cy, raio, branco);
    float[][] metadeDeCima = new float[13][];
    for (int i = 0; i <= 12; i++) {
      double angulo = Math.PI + Math.PI * i / 12;
      metadeDeCima[i] = new float[] { (float) (cx + raio * Math.cos(angulo)), (float) (cy + raio * Math.sin(angulo)) };
    }
    Formas.poligono(tela, metadeDeCima, Formas.comAlfa(VERMELHO_DA_BOLA, alfa));
    Formas.linha(tela, cx - raio, cy, cx + raio, cy, 0.6f * k, contorno);
    Formas.circulo(tela, cx, cy, 1.1f * k, contorno);
    Formas.circulo(tela, cx, cy, 0.6f * k, branco);
  }

  // 7.9: o escudinho dos guardas: um escudo de borda escura, reto em cima e com a ponta embaixo. "tamanho" é a largura; a
  // altura é 1,1 vez ela. O contorno de pontos é desenhado para a largura 1, em volta do meio
  private static final float[][] ESCUDO = { { -0.5f, -0.5f }, { 0.5f, -0.5f }, { 0.5f, 0f }, { 0.42f, 0.25f },
      { 0.25f, 0.45f }, { 0f, 0.6f }, { -0.25f, 0.45f }, { -0.42f, 0.25f }, { -0.5f, 0f } };
  private static final float MIOLO_DO_ESCUDO = 0.72f;
  private static final int CONTORNO_DO_ESCUDO = 0xE6202838;

  public static void escudo(GuiGraphics tela, float cx, float cy, float tamanho, int cor, float alfa) {
    Formas.poligono(tela, escudo(cx, cy, tamanho), Formas.comAlfa(CONTORNO_DO_ESCUDO, alfa));
    Formas.poligono(tela, escudo(cx, cy, tamanho * MIOLO_DO_ESCUDO), Formas.comAlfa(cor, alfa));
  }

  private static float[][] escudo(float cx, float cy, float tamanho) {
    float[][] pontos = new float[ESCUDO.length][];
    for (int i = 0; i < ESCUDO.length; i++) {
      pontos[i] = new float[] { cx + ESCUDO[i][0] * tamanho, cy + ESCUDO[i][1] * tamanho };
    }
    return pontos;
  }
}
