package dev.victor.combatza.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import dev.victor.combatza.client.LayoutDaHud.Bloco;
import dev.victor.combatza.client.LayoutDaHud.Caixa;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

// O editor do layout da HUD (LayoutDaHud), como a "transformação livre" do Photoshop (pedido dele, 20/10: "queria igual, e o
// mover também, e não com a rodinha do mouse"). Cada bloco da HUD vira uma caixa com o nome e o tamanho dele, por cima da HUD
// de verdade (a tela não escurece nem desfoca). Clique numa caixa para escolher; arraste por dentro para mover; puxe uma das 8
// alças (os quadradinhos brancos nos cantos e no meio das bordas) para aumentar ou diminuir, sempre na mesma proporção, com o
// lado oposto parado (com Alt, em volta do meio). Linhas-guia rosa aparecem, e o bloco gruda, nas bordas e no meio da tela e
// nas bordas e no meio dos outros blocos. As setas do teclado movem 1 pixel (com Shift, 10). Salva ao soltar o mouse e ao sair.
// Abre pelo botão "Layout da HUD" do menu de pausa, embaixo do botão do tutorial
public class TelaDoLayout extends Screen {
  private static final String FRASE = "cobblemon_combat_za.layout.";
  // O botão do menu de pausa, embaixo do "Tutorial do Combat ZA" (Tutorial: x 4, y 4, 130 x 20)
  private static final int X_DO_BOTAO_DA_PAUSA = 4;
  private static final int Y_DO_BOTAO_DA_PAUSA = 28;
  private static final int LARGURA_DO_BOTAO_DA_PAUSA = 130;
  private static final int ALTURA_DO_BOTAO = 20;
  // O botãozinho "–" de recolher a barra
  private static final int PEQUENO = 14;
  // A barra no meio da tela: o título, a dica e os 3 botões
  private static final int LARGURA_DO_BOTAO = 104;
  private static final int ESPACO = 4;
  private static final int MARGEM = 8;
  private static final int BORDA_DA_TELA = 10;
  private static final int LINHA = 10;
  private static final float[] CANTOS_DA_BARRA = Formas.raios(5f);
  private static final float[] SEM_CANTOS = Formas.raios(0f);
  // As 8 alças, como na transformação do Photoshop: onde cada uma fica na caixa (0 = esquerda / em cima, 1 = direita /
  // embaixo), a partir do canto de cima à esquerda, no sentido do relógio
  private static final float[][] ALCAS = { { 0, 0 }, { 0.5f, 0 }, { 1, 0 }, { 1, 0.5f }, { 1, 1 }, { 0.5f, 1 }, { 0, 1 },
      { 0, 0.5f } };
  // O cursor do sistema em cima de cada alça (as setas de redimensionar) e dentro da caixa (a de mover)
  private static final int[] CURSORES_DAS_ALCAS = { GLFW.GLFW_RESIZE_NWSE_CURSOR, GLFW.GLFW_RESIZE_NS_CURSOR,
      GLFW.GLFW_RESIZE_NESW_CURSOR, GLFW.GLFW_RESIZE_EW_CURSOR, GLFW.GLFW_RESIZE_NWSE_CURSOR, GLFW.GLFW_RESIZE_NS_CURSOR,
      GLFW.GLFW_RESIZE_NESW_CURSOR, GLFW.GLFW_RESIZE_EW_CURSOR };
  private static final int MOVER = -1;
  private static final int NENHUMA = -2;
  private static final float ALCA = 4f;
  private static final float ALCANCE_DA_ALCA = 4f;
  // A menos de 3 pixels de uma guia, o bloco gruda nela
  private static final float GRUDAR = 3f;
  private static final int PASSO = 1;
  private static final int PASSO_COM_SHIFT = 10;
  private static final float MENOR_LETRA = 0.35f;
  private static final float LETRA_DO_TAMANHO = 0.75f;
  // Cores em ARGB (o primeiro par é a opacidade)
  private static final int FUNDO = 0x30000000;
  private static final int FUNDO_DA_BARRA = 0xE0141A24;
  private static final int BORDA_DA_BARRA = 0xFF5AA0FF;
  private static final int CAIXA = 0x305AA0FF;
  private static final int CAIXA_EMBAIXO_DO_MOUSE = 0x505AA0FF;
  private static final int CAIXA_ESCOLHIDA = 0x40FFFFFF;
  private static final int BORDA = 0xC05AA0FF;
  private static final int BORDA_ESCOLHIDA = 0xFFFFFFFF;
  private static final int CONTORNO_DA_ALCA = 0xFF202020;
  private static final int GUIA = 0xFFFF3FD0;
  private static final int BRANCO = 0xFFFFFFFF;
  private static final int CINZA = 0xFFC8CCD4;
  private static final int FUNDO_DO_TAMANHO = 0xE0101418;

  // Os cursores do sistema, criados uma vez (0 = não existe neste sistema: fica a setinha normal)
  private static final Map<Integer, Long> CURSORES = new HashMap<>();

  // Uma guia que o bloco tocou: onde o bloco ficou e a linha a desenhar (null = nenhuma)
  private record Grude(float posicao, Float guia) {
  }

  private final Screen anterior;
  private Bloco escolhido = null;
  private Bloco arrastando = null;
  private int alca = MOVER;
  private double mouseNoInicioX;
  private double mouseNoInicioY;
  private Caixa caixaNoInicio;
  private Float guiaX = null;
  private Float guiaY = null;
  private boolean confirmandoTudo = false;
  private long cursorAtual = 0;
  private Button restaurarBloco;
  private Button restaurarTudo;
  // A barra arrastável e recolhível: os botões dela, o "–" que recolhe, o "☰" que abre, e o arrasto pelo fundo dela
  private final List<Button> daBarra = new ArrayList<>();
  private Button recolher;
  private Button abrir;
  private boolean arrastandoABarra = false;
  private double pegouABarraEmX;
  private double pegouABarraEmY;
  private List<FormattedCharSequence> linhasDaDica = List.of();

  private TelaDoLayout(Screen anterior) {
    super(Component.translatable(FRASE + "titulo"));
    this.anterior = anterior;
  }

  public static void registrar() {
    ScreenEvents.AFTER_INIT.register((cliente, tela, largura, altura) -> {
      if (tela instanceof PauseScreen pausa && pausa.showsPauseMenu()) {
        Screens.getButtons(tela).add(Button.builder(Component.translatable(FRASE + "botao_da_pausa"),
            botao -> cliente.setScreen(new TelaDoLayout(tela)))
            .bounds(X_DO_BOTAO_DA_PAUSA, Y_DO_BOTAO_DA_PAUSA, LARGURA_DO_BOTAO_DA_PAUSA, ALTURA_DO_BOTAO).build());
      }
    });
  }

  @Override
  protected void init() {
    int larguraDaBarra = larguraDaBarra();
    int larguraDoBotao = (larguraDaBarra - 2 * MARGEM - ESPACO) / 2;
    linhasDaDica = font.split(Component.translatable(FRASE + "dica"), larguraDaBarra - 2 * MARGEM);
    daBarra.clear();
    restaurarBloco = addRenderableWidget(Button.builder(Component.translatable(FRASE + "restaurar_bloco"),
        botao -> restaurarOEscolhido()).size(larguraDoBotao, ALTURA_DO_BOTAO).build());
    restaurarTudo = addRenderableWidget(Button.builder(Component.translatable(FRASE + "restaurar_tudo"),
        botao -> restaurarTudo()).size(larguraDoBotao, ALTURA_DO_BOTAO).build());
    Button cores = addRenderableWidget(Button.builder(Component.translatable(FRASE + "cores"),
        botao -> minecraft.setScreen(new TelaDasCores(this))).size(larguraDoBotao, ALTURA_DO_BOTAO).build());
    Button concluir = addRenderableWidget(Button.builder(Component.translatable(FRASE + "concluir"), botao -> onClose())
        .size(larguraDoBotao, ALTURA_DO_BOTAO).build());
    daBarra.addAll(List.of(restaurarBloco, restaurarTudo, cores, concluir));
    recolher = addRenderableWidget(Button.builder(Component.literal("–"), botao -> recolherABarra(true))
        .size(PEQUENO, PEQUENO).tooltip(Tooltip.create(Component.translatable(FRASE + "recolher"))).build());
    Component textoDoAbrir = Component.literal("☰ ").append(title);
    abrir = addRenderableWidget(Button.builder(textoDoAbrir, botao -> recolherABarra(false))
        .size(font.width(textoDoAbrir) + 2 * MARGEM, ALTURA_DO_BOTAO).build());
    posicionar();
    atualizarBotoes();
  }

  // Os botões acompanham a barra (onde ela está, aberta ou recolhida)
  private void posicionar() {
    int x = xDaBarra();
    int y = topoDaBarra();
    int larguraDoBotao = restaurarBloco.getWidth();
    int yDosBotoes = y + alturaDaBarra() - MARGEM - 2 * ALTURA_DO_BOTAO - ESPACO;
    for (int i = 0; i < daBarra.size(); i++) {
      daBarra.get(i).setPosition(x + MARGEM + (i % 2) * (larguraDoBotao + ESPACO),
          yDosBotoes + (i / 2) * (ALTURA_DO_BOTAO + ESPACO));
      daBarra.get(i).visible = !LayoutDaHud.barraRecolhida;
    }
    recolher.setPosition(x + larguraDaBarra() - ESPACO - PEQUENO, y + ESPACO);
    recolher.visible = !LayoutDaHud.barraRecolhida;
    abrir.setPosition(Math.min(x, width - abrir.getWidth()), y);
    abrir.visible = LayoutDaHud.barraRecolhida;
  }

  private void recolherABarra(boolean recolhida) {
    LayoutDaHud.barraRecolhida = recolhida;
    LayoutDaHud.salvar();
    posicionar();
  }

  private int larguraDaBarra() {
    return Math.min(3 * LARGURA_DO_BOTAO + 2 * ESPACO + 2 * MARGEM, width - 2 * BORDA_DA_TELA);
  }

  private int alturaDaBarra() {
    return MARGEM + font.lineHeight + ESPACO + linhasDaDica.size() * LINHA + ESPACO + 2 * ALTURA_DO_BOTAO + ESPACO + MARGEM;
  }

  // Onde a barra está: a fração guardada do espaço livre (0,5 = no meio), para valer em qualquer tamanho de janela
  private int xDaBarra() {
    return Math.round(LayoutDaHud.barraX * Math.max(0, width - larguraDaBarra()));
  }

  private int topoDaBarra() {
    return Math.round(LayoutDaHud.barraY * Math.max(0, height - alturaDaBarra()));
  }

  private boolean naBarra(double x, double y) {
    return !LayoutDaHud.barraRecolhida && x >= xDaBarra() && x <= xDaBarra() + larguraDaBarra() && y >= topoDaBarra()
        && y <= topoDaBarra() + alturaDaBarra();
  }

  private void atualizarBotoes() {
    restaurarBloco.active = escolhido != null && LayoutDaHud.ajustado(escolhido);
    restaurarTudo.active = LayoutDaHud.algumAjustado();
    restaurarTudo.setMessage(Component.translatable(FRASE + (confirmandoTudo ? "confirmar" : "restaurar_tudo")));
  }

  private void restaurarOEscolhido() {
    if (escolhido != null) {
      LayoutDaHud.restaurar(escolhido);
      LayoutDaHud.salvar();
    }
    confirmandoTudo = false;
    atualizarBotoes();
  }

  // "Restaurar tudo" pede um segundo clique ("Restaurar tudo? Clique de novo")
  private void restaurarTudo() {
    if (confirmandoTudo) {
      LayoutDaHud.restaurarTudo();
      LayoutDaHud.salvar();
    }
    confirmandoTudo = !confirmandoTudo;
    atualizarBotoes();
  }

  // Sem o desfoque nem o escurecido do menu: a HUD de verdade aparece atrás. Aqui vão a barra do meio e as caixas (os botões
  // vêm por cima delas)
  @Override
  public void renderBackground(GuiGraphics tela, int mouseX, int mouseY, float parcial) {
    tela.fill(0, 0, width, height, FUNDO);
    barra(tela);
    caixas(tela, mouseX, mouseY);
  }

  @Override
  public void render(GuiGraphics tela, int mouseX, int mouseY, float parcial) {
    super.render(tela, mouseX, mouseY, parcial);
    guias(tela);
    tamanhoNoMouse(tela, mouseX, mouseY);
    atualizarCursor(mouseX, mouseY);
  }

  private void barra(GuiGraphics tela) {
    if (LayoutDaHud.barraRecolhida) {
      return;
    }
    int largura = larguraDaBarra();
    int x = xDaBarra();
    int y = topoDaBarra();
    Formas.retangulo(tela, x, y, largura, alturaDaBarra(), CANTOS_DA_BARRA, FUNDO_DA_BARRA, FUNDO_DA_BARRA);
    Formas.borda(tela, x, y, largura, alturaDaBarra(), CANTOS_DA_BARRA, 1f, BORDA_DA_BARRA);
    tela.flush();
    tela.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), x + largura / 2, y + MARGEM, BRANCO);
    int yDaLinha = y + MARGEM + font.lineHeight + ESPACO;
    for (FormattedCharSequence linha : linhasDaDica) {
      tela.drawString(font, linha, x + MARGEM, yDaLinha, CINZA, false);
      yDaLinha += LINHA;
    }
  }

  // As caixas, da maior para a menor (a pequena fica por cima e dá para pegar), e a escolhida por último
  private void caixas(GuiGraphics tela, int mouseX, int mouseY) {
    Map<Bloco, Caixa> caixas = caixasNaTela();
    Bloco embaixoDoMouse = arrastando != null ? arrastando : blocoEm(caixas, mouseX, mouseY);
    List<Bloco> ordem = new ArrayList<>(caixas.keySet());
    ordem.sort(Comparator.comparingDouble((Bloco bloco) -> area(caixas.get(bloco))).reversed());
    if (escolhido != null && ordem.remove(escolhido)) {
      ordem.add(escolhido);
    }
    for (Bloco bloco : ordem) {
      caixa(tela, bloco, caixas.get(bloco), bloco == embaixoDoMouse);
    }
  }

  private void caixa(GuiGraphics tela, Bloco bloco, Caixa caixa, boolean embaixoDoMouse) {
    boolean escolhida = bloco == escolhido;
    int fundo = escolhida ? CAIXA_ESCOLHIDA : embaixoDoMouse ? CAIXA_EMBAIXO_DO_MOUSE : CAIXA;
    Formas.retangulo(tela, caixa.x(), caixa.y(), caixa.largura(), caixa.altura(), SEM_CANTOS, fundo, fundo);
    Formas.borda(tela, caixa.x(), caixa.y(), caixa.largura(), caixa.altura(), SEM_CANTOS, escolhida ? 1f : 0.75f,
        escolhida ? BORDA_ESCOLHIDA : BORDA);
    if (escolhida) {
      for (float[] ponto : ALCAS) {
        float x = caixa.x() + ponto[0] * caixa.largura() - ALCA / 2;
        float y = caixa.y() + ponto[1] * caixa.altura() - ALCA / 2;
        Formas.retangulo(tela, x - 1, y - 1, ALCA + 2, ALCA + 2, SEM_CANTOS, CONTORNO_DA_ALCA, CONTORNO_DA_ALCA);
        Formas.retangulo(tela, x, y, ALCA, ALCA, SEM_CANTOS, BRANCO, BRANCO);
      }
    }
    tela.flush();
    nomeNaCaixa(tela, bloco, caixa);
  }

  // O nome do bloco e o tamanho ("80%", ou "60% · automático" no lugar de sempre), no meio da caixa, encolhendo para caber
  private void nomeNaCaixa(GuiGraphics tela, Bloco bloco, Caixa caixa) {
    Component nome = Component.translatable(FRASE + "bloco." + bloco.nome);
    MutableComponent tamanho = Component.literal(porcento(bloco));
    if (!LayoutDaHud.ajustado(bloco)) {
      tamanho.append(" · ").append(Component.translatable(FRASE + "automatico"));
    }
    float larguraDoTexto = Math.max(font.width(nome), font.width(tamanho) * LETRA_DO_TAMANHO);
    float alturaDoTexto = font.lineHeight + 1 + font.lineHeight * LETRA_DO_TAMANHO;
    float escala = Math.clamp(Math.min((caixa.largura() - 4) / larguraDoTexto, (caixa.altura() - 2) / alturaDoTexto),
        MENOR_LETRA, 1f);
    tela.pose().pushPose();
    tela.pose().translate(caixa.centroX(), caixa.centroY() - alturaDoTexto * escala / 2, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.drawString(font, nome, -font.width(nome) / 2, 0, BRANCO, true);
    tela.pose().translate(0, font.lineHeight + 1, 0);
    tela.pose().scale(LETRA_DO_TAMANHO, LETRA_DO_TAMANHO, 1f);
    tela.drawString(font, tamanho, -font.width(tamanho) / 2, 0, CINZA, true);
    tela.pose().popPose();
  }

  private String porcento(Bloco bloco) {
    return Math.round(LayoutDaHud.transformacao(bloco, width, height).escala() * 100) + "%";
  }

  // As linhas rosa das guias em que o bloco grudou, de ponta a ponta da tela
  private void guias(GuiGraphics tela) {
    if (arrastando == null) {
      return;
    }
    if (guiaX != null) {
      Formas.retangulo(tela, guiaX - 0.5f, 0, 1, height, SEM_CANTOS, GUIA, GUIA);
    }
    if (guiaY != null) {
      Formas.retangulo(tela, 0, guiaY - 0.5f, width, 1, SEM_CANTOS, GUIA, GUIA);
    }
    tela.flush();
  }

  // Mudando o tamanho, o tamanho novo ao lado do mouse (como o Photoshop mostra a largura e a altura)
  private void tamanhoNoMouse(GuiGraphics tela, int mouseX, int mouseY) {
    if (arrastando == null || alca == MOVER) {
      return;
    }
    String texto = porcento(arrastando);
    int x = Math.min(mouseX + 10, width - font.width(texto) - 4);
    int y = Math.min(mouseY + 10, height - font.lineHeight - 4);
    tela.fill(x - 3, y - 2, x + font.width(texto) + 3, y + font.lineHeight + 1, FUNDO_DO_TAMANHO);
    tela.drawString(font, texto, x, y, BRANCO, false);
  }

  @Override
  public boolean mouseClicked(double x, double y, int botao) {
    if (super.mouseClicked(x, y, botao)) {
      return true;
    }
    if (botao != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
      return false;
    }
    // O fundo da barra (fora dos botões): pega a barra para arrastar
    if (naBarra(x, y)) {
      arrastandoABarra = true;
      pegouABarraEmX = x - xDaBarra();
      pegouABarraEmY = y - topoDaBarra();
      return true;
    }
    Map<Bloco, Caixa> caixas = caixasNaTela();
    int alcaClicada = escolhido == null ? NENHUMA : alcaEm(caixas.get(escolhido), x, y);
    if (alcaClicada >= 0) {
      comecarArrasto(escolhido, alcaClicada, caixas, x, y);
    } else {
      escolhido = blocoEm(caixas, x, y);
      if (escolhido != null) {
        comecarArrasto(escolhido, MOVER, caixas, x, y);
      }
    }
    confirmandoTudo = false;
    atualizarBotoes();
    return true;
  }

  private void comecarArrasto(Bloco bloco, int qualAlca, Map<Bloco, Caixa> caixas, double x, double y) {
    arrastando = bloco;
    alca = qualAlca;
    mouseNoInicioX = x;
    mouseNoInicioY = y;
    caixaNoInicio = caixas.get(bloco);
  }

  @Override
  public boolean mouseDragged(double x, double y, int botao, double dx, double dy) {
    if (arrastandoABarra) {
      double livreX = Math.max(1, width - larguraDaBarra());
      double livreY = Math.max(1, height - alturaDaBarra());
      LayoutDaHud.barraX = (float) Math.clamp((x - pegouABarraEmX) / livreX, 0, 1);
      LayoutDaHud.barraY = (float) Math.clamp((y - pegouABarraEmY) / livreY, 0, 1);
      posicionar();
      return true;
    }
    if (arrastando == null) {
      return super.mouseDragged(x, y, botao, dx, dy);
    }
    Caixa nova = alca == MOVER ? mover(x, y) : redimensionar(x, y);
    LayoutDaHud.ajustar(arrastando, nova, width, height);
    atualizarBotoes();
    return true;
  }

  @Override
  public boolean mouseReleased(double x, double y, int botao) {
    boolean dosBotoes = super.mouseReleased(x, y, botao);
    if (arrastandoABarra) {
      arrastandoABarra = false;
      LayoutDaHud.salvar();
      return true;
    }
    if (arrastando == null) {
      return dosBotoes;
    }
    arrastando = null;
    guiaX = null;
    guiaY = null;
    LayoutDaHud.salvar();
    atualizarBotoes();
    return true;
  }

  // Mover: a caixa anda junto com o mouse e gruda nas guias (as bordas e o meio da tela e dos outros blocos)
  private Caixa mover(double mouseX, double mouseY) {
    Caixa inicio = caixaNoInicio;
    List<Float> guiasX = new ArrayList<>(List.of(0f, width / 2f, (float) width));
    List<Float> guiasY = new ArrayList<>(List.of(0f, height / 2f, (float) height));
    caixasNaTela().forEach((bloco, outra) -> {
      if (bloco != arrastando) {
        guiasX.addAll(List.of(outra.x(), outra.centroX(), outra.direita()));
        guiasY.addAll(List.of(outra.y(), outra.centroY(), outra.baixo()));
      }
    });
    Grude emX = grudar(inicio.x() + (float) (mouseX - mouseNoInicioX), inicio.largura(), guiasX);
    Grude emY = grudar(inicio.y() + (float) (mouseY - mouseNoInicioY), inicio.altura(), guiasY);
    guiaX = emX.guia();
    guiaY = emY.guia();
    return new Caixa(LayoutDaHud.dentro(emX.posicao(), inicio.largura(), width), LayoutDaHud.dentro(emY.posicao(),
        inicio.altura(), height), inicio.largura(), inicio.altura());
  }

  // A borda do começo, o meio ou a borda do fim do bloco: a que estiver mais perto de uma guia (a menos de 3 pixels) gruda
  private static Grude grudar(float inicio, float tamanho, List<Float> guias) {
    float melhor = GRUDAR;
    float posicao = inicio;
    Float guia = null;
    for (float parte : new float[] { 0, tamanho / 2, tamanho }) {
      for (float alvo : guias) {
        float distancia = Math.abs(inicio + parte - alvo);
        if (distancia <= melhor) {
          melhor = distancia;
          posicao = alvo - parte;
          guia = alvo;
        }
      }
    }
    return new Grude(posicao, guia);
  }

  // Mudar o tamanho pela alça: o ponto oposto (ou o meio, com Alt) fica parado. O quanto cresceu é a projeção do mouse na
  // linha que vai do ponto parado até a alça: num canto, a diagonal; no meio de uma borda, só a largura ou a altura
  private Caixa redimensionar(double mouseX, double mouseY) {
    Caixa inicio = caixaNoInicio;
    float[] ponto = ALCAS[alca];
    boolean peloMeio = hasAltDown();
    float paradoX = peloMeio ? 0.5f : 1 - ponto[0];
    float paradoY = peloMeio ? 0.5f : 1 - ponto[1];
    float fixoX = inicio.x() + paradoX * inicio.largura();
    float fixoY = inicio.y() + paradoY * inicio.altura();
    float alcaX = inicio.x() + ponto[0] * inicio.largura() - fixoX;
    float alcaY = inicio.y() + ponto[1] * inicio.altura() - fixoY;
    float fator = (((float) mouseX - fixoX) * alcaX + ((float) mouseY - fixoY) * alcaY)
        / Math.max(0.0001f, alcaX * alcaX + alcaY * alcaY);
    Caixa doBloco = LayoutDaHud.caixa(arrastando, width, height);
    float maior = Math.min(LayoutDaHud.MAIOR_ESCALA, Math.min(width / doBloco.largura(), height / doBloco.altura()));
    float escala = Math.clamp(inicio.largura() / doBloco.largura() * fator, LayoutDaHud.MENOR_ESCALA,
        Math.max(LayoutDaHud.MENOR_ESCALA, maior));
    float largura = doBloco.largura() * escala;
    float altura = doBloco.altura() * escala;
    return new Caixa(LayoutDaHud.dentro(fixoX - paradoX * largura, largura, width),
        LayoutDaHud.dentro(fixoY - paradoY * altura, altura, height), largura, altura);
  }

  // As setas do teclado empurram o bloco escolhido: 1 pixel, ou 10 com Shift
  @Override
  public boolean keyPressed(int tecla, int codigo, int modificadores) {
    int dx = tecla == GLFW.GLFW_KEY_LEFT ? -1 : tecla == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
    int dy = tecla == GLFW.GLFW_KEY_UP ? -1 : tecla == GLFW.GLFW_KEY_DOWN ? 1 : 0;
    if (escolhido == null || arrastando != null || (dx == 0 && dy == 0)) {
      return super.keyPressed(tecla, codigo, modificadores);
    }
    int passo = hasShiftDown() ? PASSO_COM_SHIFT : PASSO;
    Caixa agora = LayoutDaHud.naTela(escolhido, width, height);
    LayoutDaHud.ajustar(escolhido, new Caixa(LayoutDaHud.dentro(agora.x() + dx * passo, agora.largura(), width),
        LayoutDaHud.dentro(agora.y() + dy * passo, agora.altura(), height), agora.largura(), agora.altura()), width,
        height);
    LayoutDaHud.salvar();
    atualizarBotoes();
    return true;
  }

  private Map<Bloco, Caixa> caixasNaTela() {
    Map<Bloco, Caixa> caixas = new HashMap<>();
    for (Bloco bloco : LayoutDaHud.blocos()) {
      caixas.put(bloco, LayoutDaHud.naTela(bloco, width, height));
    }
    return caixas;
  }

  // O bloco embaixo do mouse: o menor que tem o ponto (para dar para pegar um bloco pequeno em cima de um grande)
  private static Bloco blocoEm(Map<Bloco, Caixa> caixas, double x, double y) {
    Bloco achado = null;
    for (Map.Entry<Bloco, Caixa> par : caixas.entrySet()) {
      if (par.getValue().contem(x, y) && (achado == null || area(par.getValue()) < area(caixas.get(achado)))) {
        achado = par.getKey();
      }
    }
    return achado;
  }

  private static int alcaEm(Caixa caixa, double x, double y) {
    for (int i = 0; i < ALCAS.length; i++) {
      float alcaX = caixa.x() + ALCAS[i][0] * caixa.largura();
      float alcaY = caixa.y() + ALCAS[i][1] * caixa.altura();
      if (Math.abs(x - alcaX) <= ALCANCE_DA_ALCA && Math.abs(y - alcaY) <= ALCANCE_DA_ALCA) {
        return i;
      }
    }
    return NENHUMA;
  }

  private static float area(Caixa caixa) {
    return caixa.largura() * caixa.altura();
  }

  // O cursor do sistema: as setas de redimensionar em cima de uma alça, a cruz de mover em cima de um bloco
  private void atualizarCursor(double x, double y) {
    int forma = 0;
    if (arrastandoABarra || naBarra(x, y) && children().stream().noneMatch(filho -> filho.isMouseOver(x, y))) {
      forma = GLFW.GLFW_RESIZE_ALL_CURSOR;
    } else if (arrastando != null) {
      forma = alca == MOVER ? GLFW.GLFW_RESIZE_ALL_CURSOR : CURSORES_DAS_ALCAS[alca];
    } else if (children().stream().noneMatch(filho -> filho.isMouseOver(x, y))) {
      Map<Bloco, Caixa> caixas = caixasNaTela();
      int emCima = escolhido == null ? NENHUMA : alcaEm(caixas.get(escolhido), x, y);
      if (emCima >= 0) {
        forma = CURSORES_DAS_ALCAS[emCima];
      } else if (blocoEm(caixas, x, y) != null) {
        forma = GLFW.GLFW_RESIZE_ALL_CURSOR;
      }
    }
    long cursor = forma == 0 ? 0 : CURSORES.computeIfAbsent(forma, GLFW::glfwCreateStandardCursor);
    if (cursor != cursorAtual) {
      GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), cursor);
      cursorAtual = cursor;
    }
  }

  @Override
  public void onClose() {
    minecraft.setScreen(anterior);
  }

  // Saindo da tela (Concluir, Esc ou outra tela): salva e devolve o cursor normal
  @Override
  public void removed() {
    LayoutDaHud.salvar();
    if (cursorAtual != 0) {
      GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), 0);
      cursorAtual = 0;
    }
  }
}
