package dev.victor.combatza.client;

import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.victor.combatza.rede.PartyPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

// O quadrinho da party (dele, 20/10: "em algum lugar da tela mostra a party atual, igual um MMO"; e, testando: "deveria
// aparecer em algum lugar da tela quem está na party, pelo menos o nome"): o título "Party (2/4)", VOCÊ na primeira linha (o
// fundo mais claro) e os outros membros embaixo, no meio da altura da tela, do lado oposto ao histórico da batalha (que fica
// do lado sem o minimapa). Cada um: o rosto da skin, o nome (com a coroa do líder e as espadas de quem está numa batalha) e a
// vida. Noutro mundo: apagado. Chegou um convite: uma linha amarela com a tecla da tela da party
public class PartyNaTela {
  private static final float TOPO = 0.42f;
  private static final float MARGEM = 4f;
  private static final float LARGURA = 96f;
  private static final float ALTURA = 16f;
  private static final float ESPACO = 2f;
  private static final float ROSTO = 10f;
  private static final float LETRA = 0.75f;
  private static final float BARRA = 2.5f;
  private static final float ALTURA_DO_TITULO = 9f;
  private static final float[] CANTOS = Formas.raios(3f);
  private static final int MAXIMO = 4;
  private static final int FUNDO = 0x99101620;
  private static final int FUNDO_VOCE = 0x99283448;
  private static final int FUNDO_DA_BARRA = 0x99101A1A;
  private static final int BRANCO = 0xFFFFFFFF;
  private static final int APAGADO = 0xFF8C8C9C;
  private static final int COROA = 0xFFFFD24A;
  private static final int ESPADAS = 0xFFFF6A6A;
  private static final String FRASE = "cobblemon_combat_za.party_na_tela.";

  private PartyNaTela() {
  }

  public static void registrar() {
    HudRenderCallback.EVENT.register(PartyNaTela::desenhar);
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    Minecraft cliente = Minecraft.getInstance();
    List<PartyPayload.Membro> membros = PartyNoCliente.membros();
    String convite = PartyNoCliente.convite();
    if ((membros.isEmpty() && convite.isEmpty()) || cliente.options.hideGui || cliente.player == null
        || cliente.getDebugOverlay().showDebugScreen()) {
      return;
    }
    boolean naEsquerda = !HistoricoDaBatalha.historicoNaEsquerda();
    float x = naEsquerda ? MARGEM : tela.guiWidth() - MARGEM - LARGURA;
    float y = tela.guiHeight() * TOPO;
    if (!convite.isEmpty()) {
      texto(tela, cliente.font, aviso(convite), x, y, COROA);
      y += ALTURA_DO_TITULO + ESPACO;
    }
    if (membros.isEmpty()) {
      return;
    }
    texto(tela, cliente.font, Component.translatable(FRASE + "titulo", membros.size() + 1, MAXIMO), x, y, APAGADO);
    y += ALTURA_DO_TITULO;
    float vida = cliente.player.getHealth() / Math.max(1f, cliente.player.getMaxHealth());
    linha(tela, cliente, cliente.player.getUUID(), cliente.player.getGameProfile().getName(), vida,
        HistoricoDaBatalha.emBatalha(), PartyNoCliente.souLider(), true, FUNDO_VOCE, x, y);
    y += ALTURA + ESPACO;
    for (PartyPayload.Membro membro : membros) {
      boolean perto = membro.onde().mundo().equals(cliente.level.dimension().location().toString());
      linha(tela, cliente, membro.jogador(), membro.nome(), membro.vida(), membro.emBatalha(), membro.lider(), perto,
          FUNDO, x, y);
      y += ALTURA + ESPACO;
    }
  }

  private static void linha(GuiGraphics tela, Minecraft cliente, UUID jogador, String nomeDoJogador, float vida,
      boolean emBatalha, boolean lider, boolean perto, int fundo, float x, float y) {
    Formas.retangulo(tela, x, y, LARGURA, ALTURA, CANTOS, fundo, fundo);
    float xDaBarra = x + 3 + ROSTO + 3;
    float largura = LARGURA - (xDaBarra - x) - 3;
    Formas.retangulo(tela, xDaBarra, y + ALTURA - 3 - BARRA, largura, BARRA, Formas.raios(1f), FUNDO_DA_BARRA, FUNDO_DA_BARRA);
    int cor = perto ? HistoricoDaBatalha.corDaVida(vida) : APAGADO;
    Formas.retangulo(tela, xDaBarra, y + ALTURA - 3 - BARRA, largura * vida, BARRA, Formas.raios(1f), cor, cor);
    tela.flush();
    if (!perto) {
      RenderSystem.setShaderColor(0.5f, 0.5f, 0.5f, 1f);
    }
    PlayerFaceRenderer.draw(tela, HistoricoDaBatalha.pele(cliente, jogador), Math.round(x + 3),
        Math.round(y + (ALTURA - ROSTO) / 2), Math.round(ROSTO));
    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    Component nome = Component.literal(lider ? "♛ " : "").withColor(COROA & 0xFFFFFF)
        .append(Component.literal(nomeDoJogador).withColor((perto ? BRANCO : APAGADO) & 0xFFFFFF))
        .append(Component.literal(emBatalha ? " ⚔" : "").withColor(ESPADAS & 0xFFFFFF));
    texto(tela, cliente.font, nome, xDaBarra, y + 2.5f, BRANCO);
  }

  private static void texto(GuiGraphics tela, Font fonte, Component texto, float x, float y, int cor) {
    tela.pose().pushPose();
    tela.pose().translate(x, y, 0);
    tela.pose().scale(LETRA, LETRA, 1f);
    tela.drawString(fonte, texto, 0, 0, cor, true);
    tela.pose().popPose();
  }

  // "Convite de Fulano: aperte P" (a tecla como está nos Controles); sem tecla, o comando
  private static Component aviso(String quem) {
    if (TelaDaParty.tecla() == null || TelaDaParty.tecla().isUnbound()) {
      return Component.translatable(FRASE + "convite_sem_tecla", quem);
    }
    return Component.translatable(FRASE + "convite", quem, TelaDaParty.tecla().getTranslatedKeyMessage());
  }
}
