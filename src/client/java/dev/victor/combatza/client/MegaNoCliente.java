package dev.victor.combatza.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.victor.combatza.rede.MedidorDaMegaPayload;
import dev.victor.combatza.rede.MegaPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// 8.4: a Mega no seu jogo. A tecla (nova, sem padrão, escolha dele) e o medidor do Z-A, embaixo à direita, ao lado do
// losango dos golpes. 8.4d (com dois prints do Z-A que ele mandou: "a primeira é ele usando, a segunda desativado; ele fica
// brilhando quando a Mega está ativa"): um disco magenta de vidro com o símbolo da Mega (a Pedra-Chave do Mega Showdown) e
// um anel rosa em 3 pedaços. 8.4e (dele, com print do nosso: "o layout de lá é uma meia lua quase completa, e não um círculo";
// "a barrinha brilha, não é estática, é como se fosse um glitter dentro da barra"): o anel é um arco de 300 graus aberto
// embaixo, que enche das 7 horas, por cima, até as 5, com um glitter andando dentro da parte cheia; a tecla fica na abertura
// de baixo. Desativado: calmo, o símbolo apagado. Na Mega: o anel rosa forte esvazia como um relógio, com o brilho em volta,
// os brilhinhos girando, o glitter mais forte e o símbolo aceso (cinza: o Mega está na bola e o relógio parou). O servidor
// manda o estado 4 vezes por segundo (mega.Mega), também fora da batalha; o anel anda suave entre um e outro
public class MegaNoCliente {
  private static final ResourceLocation PEDRA_CHAVE = ResourceLocation.fromNamespaceAndPath("mega_showdown", "keystone");
  private static final float RAIO = 16f;
  private static final float ESPESSURA = 3.5f;
  private static final float MARGEM_DIREITA = 30f;
  private static final float MARGEM_DE_BAIXO = 36f;
  private static final float ICONE = 14f;
  // O arco: começa nas 7 horas (0 grau = 3 horas, 90 = 6 horas) e anda 300 graus por cima, em 3 pedaços de 100 com uma
  // falhinha entre eles; embaixo (das 5 às 7) fica aberto
  private static final float INICIO_DO_ARCO = 120f;
  private static final int PEDACOS = 3;
  private static final float PEDACO = 100f;
  private static final float FALHA = 6f;
  // O glitter dentro da parte cheia: pontinhos que andam devagar ao longo do arco e piscam
  private static final int GLITTER = 16;
  private static final double VOLTA_DO_GLITTER = 5000.0;
  // Na Mega, os brilhinhos que giram em volta (uma volta a cada 3 s)
  private static final int BRILHINHOS = 5;
  private static final float VOLTA_DOS_BRILHINHOS = 3000f;
  private static final float TAMANHO_DO_BRILHINHO = 2.4f;
  private static final float TAMANHO_DE_UM_ITEM = 16f;
  private static final float SUAVIDADE = 0.25f;
  private static final float PERIODO_DO_BRILHO = 900f;
  // Desenhos por cima do ícone da pedra (o item é desenhado mais "para a frente")
  private static final float NA_FRENTE_DO_ITEM = 250f;
  private static final float ALTURA_DA_TECLA = 7.5f;
  private static final float LETRA_DA_TECLA = 0.75f;
  private static final float ALTURA_DA_LETRA = 7f;

  // Cores em ARGB (o primeiro par é a opacidade)
  private static final int BORDA = 0xC8301028;
  private static final int DISCO_DE_CIMA = 0xF0A84C9E;
  private static final int DISCO_DE_BAIXO = 0xF05C1F5E;
  private static final int DISCO_NA_MEGA_DE_CIMA = 0xF0D066C4;
  private static final int DISCO_NA_MEGA_DE_BAIXO = 0xF0802680;
  private static final int ANEL_VAZIO = 0x90703060;
  private static final int ANEL_CHEIO = 0xFFF2B4E2;
  private static final int ANEL_NA_MEGA = 0xFFFF78DE;
  private static final int ANEL_PAUSADO = 0xFFB89AB0;
  private static final int BRILHO = 0xFFE050D0;
  private static final int BRILHINHO = 0xFFFFF2FF;
  // Desativado, o símbolo fica apagado, rosado, como no print
  private static final int SIMBOLO_APAGADO = 0xA86A2A66;
  private static final int FUNDO_DA_TECLA = 0xEBEEF5FA;
  private static final int TEXTO_DA_TECLA = 0xFF2D3E48;

  private static KeyMapping tecla;
  private static MedidorDaMegaPayload estado = MedidorDaMegaPayload.ESCONDIDO;
  private static float mostrada = 0f;
  private static ItemStack pedraChave = null;

  private MegaNoCliente() {
  }

  // O tutorial mostra a tecla como está nos Controles
  static KeyMapping tecla() {
    return tecla;
  }

  public static void registrar() {
    tecla = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.mega",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), TeclasDeGolpe.CATEGORIA));
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      while (tecla.consumeClick()) {
        // 23/10: na batalha por turnos, a Mega é a do Cobblemon (BatalhaPorTurnos)
        if (!BatalhaPorTurnos.agora() && ClientPlayNetworking.canSend(MegaPayload.TIPO)) {
          ClientPlayNetworking.send(new MegaPayload());
        }
      }
    });
    ClientPlayNetworking.registerGlobalReceiver(MedidorDaMegaPayload.TIPO, (pacote, contexto) -> {
      // Aparecendo agora: o anel já começa onde está, sem "encher" do zero
      if (!estado.visivel()) {
        mostrada = pacote.carga();
      }
      estado = pacote;
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      estado = MedidorDaMegaPayload.ESCONDIDO;
      mostrada = 0f;
    });
    HudRenderCallback.EVENT.register(MegaNoCliente::desenhar);
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    // 23/10: na batalha por turnos do Cobblemon, só a tela dele (BatalhaPorTurnos)
    if (BatalhaPorTurnos.agora()) {
      return;
    }
    Minecraft cliente = Minecraft.getInstance();
    if (!estado.visivel() || cliente.options.hideGui || cliente.player == null
        || cliente.getDebugOverlay().showDebugScreen()) {
      return;
    }
    mostrada += (estado.carga() - mostrada) * Math.min(1f, tempo.getRealtimeDeltaTicks() * SUAVIDADE);
    // O lugar e o tamanho do bloco (LayoutDaHud): o de sempre, ou o que você escolheu no layout da HUD
    LayoutDaHud.comecar(tela, LayoutDaHud.Bloco.MEGA);
    desenharMedidor(tela, cliente);
    LayoutDaHud.terminar(tela);
  }

  // O layout da HUD (LayoutDaHud): a caixa do medidor, com o brilho e os brilhinhos em volta
  static LayoutDaHud.Caixa caixa(int largura, int altura) {
    float fora = RAIO + 6f;
    return new LayoutDaHud.Caixa(largura - MARGEM_DIREITA - fora, altura - MARGEM_DE_BAIXO - fora, 2 * fora, 2 * fora);
  }

  private static void desenharMedidor(GuiGraphics tela, Minecraft cliente) {
    float cx = tela.guiWidth() - MARGEM_DIREITA;
    float cy = tela.guiHeight() - MARGEM_DE_BAIXO;
    boolean ativa = estado.megando() && !estado.pausado();
    long agora = Util.getMillis();
    float pulso = 0.5f + 0.5f * (float) Math.sin(agora * 2 * Math.PI / PERIODO_DO_BRILHO);
    // Na Mega, o brilho em volta, pulsando
    if (ativa) {
      Formas.circulo(tela, cx, cy, RAIO + 3f + pulso * 1.5f, Formas.comAlfa(BRILHO, 0.18f + 0.15f * pulso));
      Formas.circulo(tela, cx, cy, RAIO + 1.5f, Formas.comAlfa(BRILHO, 0.35f));
    }
    // A borda escura, o disco de vidro e os 3 pedaços do anel (o vazio por baixo, o cheio por cima)
    Formas.circulo(tela, cx, cy, RAIO, BORDA);
    Formas.circulo(tela, cx, cy, RAIO - ESPESSURA - 1f, ativa ? DISCO_NA_MEGA_DE_CIMA : DISCO_DE_CIMA,
        ativa ? DISCO_NA_MEGA_DE_BAIXO : DISCO_DE_BAIXO);
    int corDoAnel = corDoAnel();
    for (int i = 0; i < PEDACOS; i++) {
      float inicio = INICIO_DO_ARCO + i * PEDACO + FALHA / 2;
      float tamanho = PEDACO - FALHA;
      float cheioDeste = Math.clamp(mostrada * PEDACOS - i, 0f, 1f);
      Formas.arco(tela, cx, cy, RAIO - 0.5f, ESPESSURA, inicio, tamanho, ANEL_VAZIO);
      Formas.arco(tela, cx, cy, RAIO - 0.5f, ESPESSURA, inicio, tamanho * cheioDeste, corDoAnel);
      // O brilho de vidro na beirada de fora da parte cheia
      Formas.arco(tela, cx, cy, RAIO - 0.5f, 0.8f, inicio, tamanho * cheioDeste, Formas.comAlfa(BRILHINHO, 0.3f));
    }
    glitter(tela, cx, cy, mostrada * PEDACOS * PEDACO, agora, ativa);
    tela.flush();
    pedraNoMeio(tela, cx, cy);
    tela.pose().pushPose();
    tela.pose().translate(0, 0, NA_FRENTE_DO_ITEM);
    // Desativada, o símbolo fica apagado; na Mega, aceso e com os brilhinhos girando
    if (ativa) {
      brilhinhos(tela, cx, cy, agora);
    } else {
      Formas.circulo(tela, cx, cy, RAIO - ESPESSURA - 1f, SIMBOLO_APAGADO);
    }
    tela.flush();
    // A tecla na abertura de baixo do arco
    if (estado.pronto() && !tecla.isUnbound()) {
      teclaEmbaixo(tela, cliente.font, cx, cy + RAIO - ALTURA_DA_TECLA * 0.75f);
    }
    tela.pose().popPose();
  }

  private static int corDoAnel() {
    if (estado.megando()) {
      return estado.pausado() ? ANEL_PAUSADO : ANEL_NA_MEGA;
    }
    return ANEL_CHEIO;
  }

  // O glitter: pontinhos brancos dentro da faixa cheia do anel, cada um andando devagar ao longo dela e piscando no seu tempo
  // (na Mega, mais e mais fortes). "cheio" = quantos graus do arco estão cheios
  private static void glitter(GuiGraphics tela, float cx, float cy, float cheio, long agora, boolean forte) {
    if (cheio <= 0f) {
      return;
    }
    for (int i = 0; i < GLITTER; i++) {
      double fase = (i * 0.618034) % 1.0;
      double velocidade = 0.6 + (i % 3) * 0.3;
      double onde = (fase + agora / VOLTA_DO_GLITTER * velocidade) % 1.0;
      float angulo = (float) Math.toRadians(INICIO_DO_ARCO + onde * cheio);
      float naFaixa = RAIO - 0.5f - ESPESSURA * (0.2f + 0.6f * (float) ((i * 0.377) % 1.0));
      float pisca = 0.5f + 0.5f * (float) Math.sin(agora / 140.0 + i * 2.1);
      if (!forte && pisca < 0.45f) {
        continue;
      }
      float x = cx + naFaixa * (float) Math.cos(angulo);
      float y = cy + naFaixa * (float) Math.sin(angulo);
      float tamanho = (forte ? 1.3f : 0.9f) * (0.5f + pisca);
      int cor = Formas.comAlfa(BRILHINHO, (forte ? 0.95f : 0.7f) * pisca);
      Formas.poligono(tela, new float[][] { { x, y - tamanho }, { x + tamanho * 0.35f, y }, { x, y + tamanho },
          { x - tamanho * 0.35f, y } }, cor);
      Formas.poligono(tela, new float[][] { { x - tamanho, y }, { x, y - tamanho * 0.35f }, { x + tamanho, y },
          { x, y + tamanho * 0.35f } }, cor);
    }
  }

  // Estrelinhas de 4 pontas girando em volta do anel, cada uma piscando no seu tempo
  private static void brilhinhos(GuiGraphics tela, float cx, float cy, long agora) {
    for (int i = 0; i < BRILHINHOS; i++) {
      double angulo = 2 * Math.PI * (agora / VOLTA_DOS_BRILHINHOS + (double) i / BRILHINHOS);
      float pisca = 0.5f + 0.5f * (float) Math.sin(agora / 180.0 + i * 1.7);
      float raio = RAIO + 1f + 1.5f * (float) Math.sin(agora / 400.0 + i);
      float x = cx + raio * (float) Math.cos(angulo);
      float y = cy + raio * (float) Math.sin(angulo);
      float tamanho = TAMANHO_DO_BRILHINHO * (0.6f + 0.6f * pisca);
      int cor = Formas.comAlfa(BRILHINHO, 0.4f + 0.6f * pisca);
      Formas.poligono(tela, new float[][] { { x, y - tamanho }, { x + tamanho * 0.3f, y }, { x, y + tamanho },
          { x - tamanho * 0.3f, y } }, cor);
      Formas.poligono(tela, new float[][] { { x - tamanho, y }, { x, y - tamanho * 0.3f }, { x + tamanho, y },
          { x, y + tamanho * 0.3f } }, cor);
    }
  }

  // A Pedra-Chave (o símbolo da Mega) do Mega Showdown, pequena, no meio do anel. Sem o mod, o anel fica sem ícone
  private static void pedraNoMeio(GuiGraphics tela, float cx, float cy) {
    if (pedraChave == null) {
      pedraChave = BuiltInRegistries.ITEM.getOptional(PEDRA_CHAVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
    if (pedraChave.isEmpty()) {
      return;
    }
    float escala = ICONE / TAMANHO_DE_UM_ITEM;
    tela.pose().pushPose();
    tela.pose().translate(cx - ICONE / 2, cy - ICONE / 2, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.renderItem(pedraChave, 0, 0);
    tela.pose().popPose();
  }

  // A tecla da Mega numa plaquinha clara, embaixo do anel (como a tecla de soltar no painel do time)
  private static void teclaEmbaixo(GuiGraphics tela, Font fonte, float cx, float y) {
    Component nome = tecla.getTranslatedKeyMessage();
    float larguraDoTexto = fonte.width(nome) * LETRA_DA_TECLA;
    float largura = Math.max(ALTURA_DA_TECLA, larguraDoTexto + 3);
    Formas.retangulo(tela, cx - largura / 2, y, largura, ALTURA_DA_TECLA, Formas.raios(ALTURA_DA_TECLA / 2),
        FUNDO_DA_TECLA, FUNDO_DA_TECLA);
    tela.flush();
    tela.pose().pushPose();
    tela.pose().translate(cx - larguraDoTexto / 2, y + (ALTURA_DA_TECLA - ALTURA_DA_LETRA * LETRA_DA_TECLA) / 2, 0);
    tela.pose().scale(LETRA_DA_TECLA, LETRA_DA_TECLA, 1f);
    tela.drawString(fonte, nome, 0, 0, TEXTO_DA_TECLA, false);
    tela.pose().popPose();
  }
}
