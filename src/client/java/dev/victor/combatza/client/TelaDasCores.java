package dev.victor.combatza.client;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;

import dev.victor.combatza.golpes.CoresDosTipos;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// A tela das cores da prévia do golpe (CoresDaPrevia), aberta pela tela do layout da HUD. Duas fileiras de quadradinhos: a cor
// de DENTRO (o primeiro, o arco-íris, é "a cor do tipo") e a do CONTORNO (o primeiro é o branco, o padrão). Embaixo, como fica
// em cinco chãos difíceis (gelo, neve, grama, água e areia), cada um com o golpe do tipo que mais some nele
public class TelaDasCores extends Screen {
  private static final String FRASE = "cobblemon_combat_za.cores.";
  private static final int LARGURA = 300;
  private static final int ALTURA = 228;
  private static final int MARGEM = 12;
  private static final int QUADRADO = 16;
  private static final int ESPACO = 4;
  private static final int ALTURA_DO_BOTAO = 20;
  private static final int LARGURA_DO_BOTAO = 100;
  private static final int TOPO_DO_TITULO = 10;
  private static final int TOPO_DA_PRIMEIRA = 40;
  private static final int ENTRE_FILEIRAS = 36;
  private static final int TOPO_DOS_EXEMPLOS = 122;
  private static final int EXEMPLO = 44;
  private static final float RAIO_DO_EXEMPLO = 14f;
  private static final float BORDA = 1.5f;
  private static final float[] CANTOS = Formas.raios(6f);
  private static final int FUNDO_DE_CIMA = 0xF01A2432;
  private static final int FUNDO_DE_BAIXO = 0xF0101418;
  private static final int BORDA_DO_PAINEL = 0xFF5AA0FF;
  private static final int BRANCO = 0xFFFFFFFF;
  private static final int CINZA = 0xFFB4B4BE;
  private static final int ESCOLHIDO = 0xFFFFE600;
  // O arco-íris do "cor do tipo"
  private static final int[] ARCO_IRIS = { 0xFFFF4040, 0xFFFFC040, 0xFF60E060, 0xFF40A0FF, 0xFFB060FF };
  // Os chãos dos exemplos e o tipo de golpe que mais some em cada um
  private record Chao(String nome, int cor, ElementalType tipo) {
  }

  private static final Chao[] CHAOS = { new Chao("gelo", 0xFF9EC9F5, ElementalTypes.ICE),
      new Chao("neve", 0xFFF2F6FA, ElementalTypes.ICE), new Chao("grama", 0xFF5E9B3A, ElementalTypes.GRASS),
      new Chao("agua", 0xFF2E5FD8, ElementalTypes.WATER), new Chao("areia", 0xFFE3D59A, ElementalTypes.GROUND) };

  private final Screen anterior;

  TelaDasCores(Screen anterior) {
    super(Component.translatable(FRASE + "titulo"));
    this.anterior = anterior;
  }

  private int x0() {
    return (width - LARGURA) / 2;
  }

  private int y0() {
    return (height - ALTURA) / 2;
  }

  @Override
  protected void init() {
    int y = y0() + ALTURA - MARGEM - ALTURA_DO_BOTAO;
    addRenderableWidget(Button.builder(Component.translatable(FRASE + "restaurar"), botao -> {
      CoresDaPrevia.escolherDentro(null);
      CoresDaPrevia.escolherContorno(null);
    }).bounds(x0() + MARGEM, y, LARGURA_DO_BOTAO, ALTURA_DO_BOTAO).build());
    addRenderableWidget(Button.builder(Component.translatable("cobblemon_combat_za.layout.concluir"), botao -> onClose())
        .bounds(x0() + LARGURA - MARGEM - LARGURA_DO_BOTAO, y, LARGURA_DO_BOTAO, ALTURA_DO_BOTAO).build());
  }

  @Override
  public void renderBackground(GuiGraphics tela, int mouseX, int mouseY, float parcial) {
    super.renderBackground(tela, mouseX, mouseY, parcial);
    int x = x0();
    int y = y0();
    Formas.retangulo(tela, x, y, LARGURA, ALTURA, CANTOS, FUNDO_DE_CIMA, FUNDO_DE_BAIXO);
    Formas.borda(tela, x, y, LARGURA, ALTURA, CANTOS, 1f, BORDA_DO_PAINEL);
    tela.flush();
    tela.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, y + TOPO_DO_TITULO, BRANCO);
    fileira(tela, Component.translatable(FRASE + "dentro"), y + TOPO_DA_PRIMEIRA, CoresDaPrevia.escolhidaDentro(), true,
        mouseX, mouseY);
    fileira(tela, Component.translatable(FRASE + "contorno"), y + TOPO_DA_PRIMEIRA + ENTRE_FILEIRAS,
        CoresDaPrevia.escolhidaContorno(), false, mouseX, mouseY);
    exemplos(tela, y + TOPO_DOS_EXEMPLOS);
  }

  // O nome da fileira e os quadradinhos: o primeiro é o padrão (dentro: a cor do tipo, em arco-íris; contorno: o branco)
  private void fileira(GuiGraphics tela, Component nome, int y, Integer escolhida, boolean deDentro, int mouseX, int mouseY) {
    int x = x0() + MARGEM;
    tela.drawString(font, nome, x, y, CINZA, false);
    int yDosQuadrados = y + font.lineHeight + 3;
    for (int i = 0; i <= CoresDaPrevia.PALETA.length; i++) {
      int xDoQuadrado = x + i * (QUADRADO + ESPACO);
      boolean padrao = i == 0;
      boolean marcado = padrao ? escolhida == null : escolhida != null && escolhida == CoresDaPrevia.PALETA[i - 1];
      if (marcado) {
        tela.fill(xDoQuadrado - 2, yDosQuadrados - 2, xDoQuadrado + QUADRADO + 2, yDosQuadrados + QUADRADO + 2, ESCOLHIDO);
      }
      if (padrao && deDentro) {
        int faixa = QUADRADO / ARCO_IRIS.length;
        for (int j = 0; j < ARCO_IRIS.length; j++) {
          tela.fill(xDoQuadrado, yDosQuadrados + j * faixa, xDoQuadrado + QUADRADO,
              j == ARCO_IRIS.length - 1 ? yDosQuadrados + QUADRADO : yDosQuadrados + (j + 1) * faixa, ARCO_IRIS[j]);
        }
      } else {
        tela.fill(xDoQuadrado, yDosQuadrados, xDoQuadrado + QUADRADO, yDosQuadrados + QUADRADO,
            padrao ? CoresDaPrevia.BRANCO : CoresDaPrevia.PALETA[i - 1]);
      }
      if (mouseX >= xDoQuadrado && mouseX < xDoQuadrado + QUADRADO && mouseY >= yDosQuadrados
          && mouseY < yDosQuadrados + QUADRADO && padrao) {
        tela.renderTooltip(font, Component.translatable(FRASE + (deDentro ? "do_tipo" : "padrao_contorno")), mouseX, mouseY);
      }
    }
  }

  // Cinco chãos, cada um com o golpe do tipo que mais some nele (com a sua cor de dentro, se escolheu uma)
  private void exemplos(GuiGraphics tela, int y) {
    tela.drawString(font, Component.translatable(FRASE + "como_fica"), x0() + MARGEM, y, CINZA, false);
    int yDoChao = y + font.lineHeight + 3;
    int total = CHAOS.length * EXEMPLO + (CHAOS.length - 1) * ESPACO;
    int x = (width - total) / 2;
    for (Chao chao : CHAOS) {
      tela.fill(x, yDoChao, x + EXEMPLO, yDoChao + EXEMPLO, chao.cor());
      float cx = x + EXEMPLO / 2f;
      float cy = yDoChao + EXEMPLO / 2f;
      int dentro = CoresDaPrevia.dentro(0xFF000000 | CoresDosTipos.de(chao.tipo()));
      Formas.circulo(tela, cx, cy, RAIO_DO_EXEMPLO, Formas.comAlfa(dentro, 0.35f));
      Formas.anel(tela, cx, cy, RAIO_DO_EXEMPLO + BORDA, BORDA, Formas.comAlfa(CoresDaPrevia.contornoDeFora(), 0.9f));
      Formas.anel(tela, cx, cy, RAIO_DO_EXEMPLO, BORDA, Formas.comAlfa(CoresDaPrevia.contornoDeDentro(), 0.9f));
      tela.flush();
      Component nome = Component.translatable(FRASE + chao.nome());
      tela.drawCenteredString(font, nome, (int) cx, yDoChao + EXEMPLO + 2, CINZA);
      x += EXEMPLO + ESPACO;
    }
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int botao) {
    if (super.mouseClicked(mouseX, mouseY, botao)) {
      return true;
    }
    int y = y0() + TOPO_DA_PRIMEIRA + font.lineHeight + 3;
    if (clicouNaFileira(mouseX, mouseY, y, true)) {
      return true;
    }
    return clicouNaFileira(mouseX, mouseY, y + ENTRE_FILEIRAS, false);
  }

  private boolean clicouNaFileira(double mouseX, double mouseY, int yDosQuadrados, boolean deDentro) {
    if (mouseY < yDosQuadrados || mouseY >= yDosQuadrados + QUADRADO) {
      return false;
    }
    int x = x0() + MARGEM;
    for (int i = 0; i <= CoresDaPrevia.PALETA.length; i++) {
      int xDoQuadrado = x + i * (QUADRADO + ESPACO);
      if (mouseX >= xDoQuadrado && mouseX < xDoQuadrado + QUADRADO) {
        Integer cor = i == 0 ? null : CoresDaPrevia.PALETA[i - 1];
        if (deDentro) {
          CoresDaPrevia.escolherDentro(cor);
        } else {
          CoresDaPrevia.escolherContorno(cor);
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void onClose() {
    minecraft.setScreen(anterior);
  }
}
