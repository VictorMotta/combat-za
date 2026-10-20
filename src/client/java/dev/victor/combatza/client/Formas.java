package dev.victor.combatza.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

// Formas lisas para a HUD. O fill do Minecraft só faz retângulos retos em pixels inteiros; aqui tudo vira triângulos
// com coordenadas quebradas (float), como o relógio dos golpes (6.1c). Na escala 4 da sua tela, um pixel da interface
// tem 4 pixels de verdade, então as curvas e as bordas finas ficam lisas
public class Formas {
  private static final int PEDACOS_DA_MEIA_LUA = 8;
  private static final int PONTOS_POR_CANTO = 6;
  private static final int PONTOS_DO_CIRCULO = 24;

  private Formas() {
  }

  // Os raios dos cantos, nesta ordem: em cima à esquerda, em cima à direita, embaixo à direita, embaixo à esquerda
  public static float[] raios(float emCimaEsquerda, float emCimaDireita, float emBaixoDireita, float emBaixoEsquerda) {
    return new float[] { emCimaEsquerda, emCimaDireita, emBaixoDireita, emBaixoEsquerda };
  }

  public static float[] raios(float todos) {
    return raios(todos, todos, todos, todos);
  }

  // Retângulo de cantos redondos, com degradê de cima para baixo
  public static void retangulo(GuiGraphics tela, float x, float y, float largura, float altura, float[] raios,
      int corDeCima, int corDeBaixo) {
    if (largura <= 0 || altura <= 0) {
      return;
    }
    leque(tela, contorno(x, y, largura, altura, raios, 0), x + largura / 2, y + altura / 2, y, altura, corDeCima,
        corDeBaixo);
  }

  // Retângulo reto com degradê da esquerda para a direita (7.1b: a faixa do olho, que some nas pontas)
  public static void degradeDeLado(GuiGraphics tela, float x, float y, float largura, float altura, int corDaEsquerda,
      int corDaDireita) {
    if (largura <= 0 || altura <= 0) {
      return;
    }
    VertexConsumer vertices = tela.bufferSource().getBuffer(RenderType.gui());
    Matrix4f matriz = tela.pose().last().pose();
    triangulo(vertices, matriz, x, y, corDaEsquerda, x + largura, y, corDaDireita, x + largura, y + altura, corDaDireita);
    triangulo(vertices, matriz, x, y, corDaEsquerda, x + largura, y + altura, corDaDireita, x, y + altura, corDaEsquerda);
  }

  // Só a borda do retângulo redondo, para dentro, com a espessura dada
  public static void borda(GuiGraphics tela, float x, float y, float largura, float altura, float[] raios,
      float espessura, int cor) {
    if (largura <= 0 || altura <= 0) {
      return;
    }
    faixa(tela, contorno(x, y, largura, altura, raios, 0), contorno(x, y, largura, altura, raios, espessura), cor);
  }

  public static void circulo(GuiGraphics tela, float cx, float cy, float raio, int cor) {
    leque(tela, volta(cx, cy, raio), cx, cy, cy - raio, 2 * raio, cor, cor);
  }

  // 8.4d: o círculo com degradê de cima para baixo (o disco de vidro do medidor da Mega)
  public static void circulo(GuiGraphics tela, float cx, float cy, float raio, int corDeCima, int corDeBaixo) {
    leque(tela, volta(cx, cy, raio), cx, cy, cy - raio, 2 * raio, corDeCima, corDeBaixo);
  }

  public static void anel(GuiGraphics tela, float cx, float cy, float raio, float espessura, int cor) {
    faixa(tela, volta(cx, cy, raio), volta(cx, cy, raio - espessura), cor);
  }

  // 8.4: um pedaço de anel (o medidor da Mega). Começa em cima (meio-dia) e anda no sentido do relógio até "fracao" da volta
  public static void arco(GuiGraphics tela, float cx, float cy, float raio, float espessura, float fracao, int cor) {
    arco(tela, cx, cy, raio, espessura, -90f, 360f * Math.clamp(fracao, 0f, 1f), cor);
  }

  // 8.4d: o mesmo, a partir de qualquer ângulo ("inicio", em graus: 0 = 3 horas, -90 = meio-dia), andando "varredura" graus
  // no sentido do relógio (os 3 pedaços do medidor da Mega do Z-A)
  public static void arco(GuiGraphics tela, float cx, float cy, float raio, float espessura, float inicio, float varredura,
      int cor) {
    if (varredura <= 0f) {
      return;
    }
    int pontos = Math.max(2, Math.round(PONTOS_DO_CIRCULO * 2 * varredura / 360f) + 1);
    VertexConsumer vertices = tela.bufferSource().getBuffer(RenderType.gui());
    Matrix4f matriz = tela.pose().last().pose();
    float[] foraAntes = null;
    float[] dentroAntes = null;
    for (int i = 0; i < pontos; i++) {
      double angulo = Math.toRadians(inicio + varredura * i / (pontos - 1));
      float cos = (float) Math.cos(angulo);
      float sen = (float) Math.sin(angulo);
      float[] fora = { cx + raio * cos, cy + raio * sen };
      float[] dentro = { cx + (raio - espessura) * cos, cy + (raio - espessura) * sen };
      if (foraAntes != null) {
        triangulo(vertices, matriz, foraAntes[0], foraAntes[1], cor, fora[0], fora[1], cor, dentro[0], dentro[1], cor);
        triangulo(vertices, matriz, foraAntes[0], foraAntes[1], cor, dentro[0], dentro[1], cor, dentroAntes[0],
            dentroAntes[1], cor);
      }
      foraAntes = fora;
      dentroAntes = dentro;
    }
  }

  // Um polígono convexo (triângulo, meia-lua...) de uma cor só
  public static void poligono(GuiGraphics tela, float[][] pontos, int cor) {
    float cx = 0;
    float cy = 0;
    for (float[] ponto : pontos) {
      cx += ponto[0];
      cy += ponto[1];
    }
    leque(tela, pontos, cx / pontos.length, cy / pontos.length, 0, 1, cor, cor);
  }

  // Uma linha com espessura: um retângulo deitado na direção da linha
  public static void linha(GuiGraphics tela, float x1, float y1, float x2, float y2, float espessura, int cor) {
    float dx = x2 - x1;
    float dy = y2 - y1;
    float tamanho = (float) Math.sqrt(dx * dx + dy * dy);
    if (tamanho == 0) {
      return;
    }
    float nx = -dy / tamanho * espessura / 2;
    float ny = dx / tamanho * espessura / 2;
    poligono(tela, new float[][] { { x1 + nx, y1 + ny }, { x2 + nx, y2 + ny }, { x2 - nx, y2 - ny },
        { x1 - nx, y1 - ny } }, cor);
  }

  // Uma linha curva com espessura e sem emendas: cada ponto do caminho é afastado para os dois lados pela perpendicular
  // do caminho, e a faixa entre os dois lados vira triângulos (linhas retas emendadas dobrariam a cor nas juntas)
  public static void caminho(GuiGraphics tela, float[][] pontos, float espessura, int cor) {
    int total = pontos.length;
    float[][] umLado = new float[total][];
    float[][] outroLado = new float[total][];
    for (int i = 0; i < total; i++) {
      float[] antes = pontos[Math.max(0, i - 1)];
      float[] depois = pontos[Math.min(total - 1, i + 1)];
      float dx = depois[0] - antes[0];
      float dy = depois[1] - antes[1];
      float tamanho = (float) Math.sqrt(dx * dx + dy * dy);
      float nx = tamanho == 0 ? 0 : -dy / tamanho * espessura / 2;
      float ny = tamanho == 0 ? 0 : dx / tamanho * espessura / 2;
      umLado[i] = new float[] { pontos[i][0] + nx, pontos[i][1] + ny };
      outroLado[i] = new float[] { pontos[i][0] - nx, pontos[i][1] - ny };
    }
    VertexConsumer vertices = tela.bufferSource().getBuffer(RenderType.gui());
    Matrix4f matriz = tela.pose().last().pose();
    for (int i = 0; i + 1 < total; i++) {
      triangulo(vertices, matriz, umLado[i][0], umLado[i][1], cor, umLado[i + 1][0], umLado[i + 1][1], cor,
          outroLado[i + 1][0], outroLado[i + 1][1], cor);
      triangulo(vertices, matriz, umLado[i][0], umLado[i][1], cor, outroLado[i + 1][0], outroLado[i + 1][1], cor,
          outroLado[i][0], outroLado[i][1], cor);
    }
  }

  // O mesmo caminho, com as pontas arredondadas (6.3g, pedido dele: "as pontas arredondadas"): uma meia-lua em cada ponta,
  // virada para fora. Ela começa exatamente onde a faixa acaba, sem sobrepor (sobrepor dobraria a opacidade na ponta)
  public static void caminhoArredondado(GuiGraphics tela, float[][] pontos, float espessura, int cor) {
    caminho(tela, pontos, espessura, cor);
    int ultimo = pontos.length - 1;
    if (ultimo < 1) {
      return;
    }
    VertexConsumer vertices = tela.bufferSource().getBuffer(RenderType.gui());
    Matrix4f matriz = tela.pose().last().pose();
    meiaLua(vertices, matriz, pontos[0], pontos[1], espessura / 2, cor);
    meiaLua(vertices, matriz, pontos[ultimo], pontos[ultimo - 1], espessura / 2, cor);
  }

  // Um leque de triângulos em volta da ponta, de −90 a +90 graus da direção "para fora" (de "vizinho" para "ponta")
  private static void meiaLua(VertexConsumer vertices, Matrix4f matriz, float[] ponta, float[] vizinho, float raio,
      int cor) {
    float dx = ponta[0] - vizinho[0];
    float dy = ponta[1] - vizinho[1];
    float tamanho = (float) Math.sqrt(dx * dx + dy * dy);
    if (tamanho == 0) {
      return;
    }
    dx /= tamanho;
    dy /= tamanho;
    float[] antes = null;
    for (int i = 0; i <= PEDACOS_DA_MEIA_LUA; i++) {
      double angulo = -Math.PI / 2 + Math.PI * i / PEDACOS_DA_MEIA_LUA;
      float cos = (float) Math.cos(angulo);
      float sen = (float) Math.sin(angulo);
      float[] agora = { ponta[0] + raio * (cos * dx - sen * dy), ponta[1] + raio * (cos * dy + sen * dx) };
      if (antes != null) {
        triangulo(vertices, matriz, ponta[0], ponta[1], cor, antes[0], antes[1], cor, agora[0], agora[1], cor);
      }
      antes = agora;
    }
  }

  // A mesma cor com a opacidade multiplicada (1 = como estava, 0 = invisível)
  public static int comAlfa(int cor, float alfa) {
    int opacidade = Math.round(((cor >>> 24) & 0xFF) * Math.clamp(alfa, 0f, 1f));
    return (opacidade << 24) | (cor & 0xFFFFFF);
  }

  // Mistura duas cores ARGB: t = 0 é a primeira, t = 1 é a segunda
  public static int misturar(int de, int para, float t) {
    float f = Math.clamp(t, 0f, 1f);
    int cor = 0;
    for (int deslocamento = 0; deslocamento <= 24; deslocamento += 8) {
      int a = (de >>> deslocamento) & 0xFF;
      int b = (para >>> deslocamento) & 0xFF;
      cor |= Math.round(a + (b - a) * f) << deslocamento;
    }
    return cor;
  }

  // Os pontos da volta do retângulo redondo, no sentido do relógio, a partir do canto de cima à esquerda. "recuo"
  // encolhe a volta para dentro (é assim que sai a borda). Um canto nunca passa da metade do lado
  private static float[][] contorno(float x, float y, float largura, float altura, float[] raios, float recuo) {
    float[][] pontos = new float[4 * (PONTOS_POR_CANTO + 1)][];
    int i = 0;
    for (int canto = 0; canto < 4; canto++) {
      float raio = Math.max(Math.min(raios[canto], Math.min(largura, altura) / 2), recuo);
      float cx = canto == 0 || canto == 3 ? x + raio : x + largura - raio;
      float cy = canto <= 1 ? y + raio : y + altura - raio;
      double inicio = Math.PI + canto * Math.PI / 2;
      for (int passo = 0; passo <= PONTOS_POR_CANTO; passo++) {
        double angulo = inicio + passo * (Math.PI / 2) / PONTOS_POR_CANTO;
        pontos[i++] = new float[] { (float) (cx + (raio - recuo) * Math.cos(angulo)),
            (float) (cy + (raio - recuo) * Math.sin(angulo)) };
      }
    }
    return pontos;
  }

  private static float[][] volta(float cx, float cy, float raio) {
    float[][] pontos = new float[PONTOS_DO_CIRCULO][];
    for (int i = 0; i < PONTOS_DO_CIRCULO; i++) {
      double angulo = 2 * Math.PI * i / PONTOS_DO_CIRCULO;
      pontos[i] = new float[] { (float) (cx + raio * Math.cos(angulo)), (float) (cy + raio * Math.sin(angulo)) };
    }
    return pontos;
  }

  // Triângulos do centro até cada lado da volta; a cor de cada ponto sai do degradê pela altura dele
  private static void leque(GuiGraphics tela, float[][] pontos, float cx, float cy, float yDeCima, float altura,
      int corDeCima, int corDeBaixo) {
    VertexConsumer vertices = tela.bufferSource().getBuffer(RenderType.gui());
    Matrix4f matriz = tela.pose().last().pose();
    int corDoCentro = misturar(corDeCima, corDeBaixo, (cy - yDeCima) / altura);
    for (int i = 0; i < pontos.length; i++) {
      float[] a = pontos[i];
      float[] b = pontos[(i + 1) % pontos.length];
      triangulo(vertices, matriz, cx, cy, corDoCentro, a[0], a[1], misturar(corDeCima, corDeBaixo, (a[1] - yDeCima) / altura),
          b[0], b[1], misturar(corDeCima, corDeBaixo, (b[1] - yDeCima) / altura));
    }
  }

  // A faixa entre duas voltas com o mesmo número de pontos (a de fora e a de dentro)
  private static void faixa(GuiGraphics tela, float[][] fora, float[][] dentro, int cor) {
    VertexConsumer vertices = tela.bufferSource().getBuffer(RenderType.gui());
    Matrix4f matriz = tela.pose().last().pose();
    for (int i = 0; i < fora.length; i++) {
      int j = (i + 1) % fora.length;
      triangulo(vertices, matriz, fora[i][0], fora[i][1], cor, fora[j][0], fora[j][1], cor, dentro[j][0], dentro[j][1], cor);
      triangulo(vertices, matriz, fora[i][0], fora[i][1], cor, dentro[j][0], dentro[j][1], cor, dentro[i][0], dentro[i][1],
          cor);
    }
  }

  // A tela desenha de 4 em 4 pontos e só a face virada para ela: cada triângulo vai com o último ponto repetido e nos
  // dois sentidos (um deles é descartado), igual ao relógio dos golpes
  private static void triangulo(VertexConsumer vertices, Matrix4f matriz, float ax, float ay, int corA, float bx, float by,
      int corB, float cx, float cy, int corC) {
    vertices.addVertex(matriz, ax, ay, 0).setColor(corA);
    vertices.addVertex(matriz, bx, by, 0).setColor(corB);
    vertices.addVertex(matriz, cx, cy, 0).setColor(corC);
    vertices.addVertex(matriz, cx, cy, 0).setColor(corC);
    vertices.addVertex(matriz, ax, ay, 0).setColor(corA);
    vertices.addVertex(matriz, cx, cy, 0).setColor(corC);
    vertices.addVertex(matriz, bx, by, 0).setColor(corB);
    vertices.addVertex(matriz, bx, by, 0).setColor(corB);
  }
}
