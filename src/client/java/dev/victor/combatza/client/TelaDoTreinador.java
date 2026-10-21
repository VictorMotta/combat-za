package dev.victor.combatza.client;

import dev.victor.combatza.rede.PerguntaDoTreinadorPayload;
import dev.victor.combatza.rede.RespostaDoTreinadorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// 21/10 (treinadores, decisão dele: "uma telinha com 'Tempo real' / 'Por turnos' ao falar com o treinador"). O servidor
// segurou a batalha do RCT e pergunta (PerguntaDoTreinadorPayload); a resposta volta (RespostaDoTreinadorPayload). Fechar a
// tela (Esc) = nenhuma batalha começa. Ela não pausa o jogo: o mundo segue, como na tela de batalha do Cobblemon
public class TelaDoTreinador extends Screen {
  private static final String FRASE = "cobblemon_combat_za.treinador.tela.";
  private static final int LARGURA = 280;
  private static final int ALTURA = 150;
  private static final int MARGEM = 12;
  private static final int ALTURA_DO_BOTAO = 20;
  private static final int LARGURA_DO_BOTAO = 120;
  private static final int TOPO_DO_TITULO = 12;
  private static final int TOPO_DA_PERGUNTA = 30;
  private static final int TOPO_DOS_BOTOES = 52;
  private static final int TOPO_DAS_DICAS = 78;
  private static final float[] CANTOS = Formas.raios(6f);
  private static final int FUNDO_DE_CIMA = 0xF01A2432;
  private static final int FUNDO_DE_BAIXO = 0xF0101418;
  private static final int BORDA_DO_PAINEL = 0xFF5AA0FF;
  private static final int BRANCO = 0xFFFFFFFF;
  private static final int CINZA = 0xFFB4B4BE;

  // A resposta já foi (um dos botões): fechar depois disso não manda o "fechou"
  private boolean respondeu;

  TelaDoTreinador(Component treinador) {
    super(Component.translatable(FRASE + "titulo", treinador));
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(PerguntaDoTreinadorPayload.TIPO,
        (pacote, contexto) -> contexto.client().setScreen(new TelaDoTreinador(pacote.treinador())));
  }

  private int x0() {
    return (width - LARGURA) / 2;
  }

  private int y0() {
    return (height - ALTURA) / 2;
  }

  @Override
  protected void init() {
    int y = y0() + TOPO_DOS_BOTOES;
    int entre = LARGURA - 2 * MARGEM - 2 * LARGURA_DO_BOTAO;
    addRenderableWidget(Button.builder(Component.translatable(FRASE + "tempo_real").withStyle(ChatFormatting.GOLD),
        botao -> responder(RespostaDoTreinadorPayload.TEMPO_REAL))
        .bounds(x0() + MARGEM, y, LARGURA_DO_BOTAO, ALTURA_DO_BOTAO).build());
    addRenderableWidget(Button.builder(Component.translatable(FRASE + "por_turnos"),
        botao -> responder(RespostaDoTreinadorPayload.POR_TURNOS))
        .bounds(x0() + MARGEM + LARGURA_DO_BOTAO + entre, y, LARGURA_DO_BOTAO, ALTURA_DO_BOTAO).build());
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
    tela.drawCenteredString(font, Component.translatable(FRASE + "pergunta"), width / 2, y + TOPO_DA_PERGUNTA, CINZA);
    int largura = LARGURA_DO_BOTAO;
    int entre = LARGURA - 2 * MARGEM - 2 * largura;
    tela.drawWordWrap(font, Component.translatable(FRASE + "tempo_real.dica"), x + MARGEM, y + TOPO_DAS_DICAS, largura,
        CINZA);
    tela.drawWordWrap(font, Component.translatable(FRASE + "por_turnos.dica"), x + MARGEM + largura + entre,
        y + TOPO_DAS_DICAS, largura, CINZA);
    tela.drawCenteredString(font, Component.translatable(FRASE + "fechar"), width / 2,
        y + ALTURA - MARGEM - font.lineHeight, CINZA);
  }

  private void responder(int escolha) {
    respondeu = true;
    ClientPlayNetworking.send(new RespostaDoTreinadorPayload(escolha));
    onClose();
  }

  // Esc, ou outra tela abriu por cima: nenhuma batalha (o servidor esquece a pergunta)
  @Override
  public void removed() {
    if (!respondeu) {
      respondeu = true;
      ClientPlayNetworking.send(new RespostaDoTreinadorPayload(RespostaDoTreinadorPayload.FECHOU));
    }
    super.removed();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
