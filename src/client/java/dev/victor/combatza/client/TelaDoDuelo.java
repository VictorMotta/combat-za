package dev.victor.combatza.client;

import java.util.ArrayList;
import java.util.List;

import dev.victor.combatza.rede.EscolhaDoDueloPayload;
import dev.victor.combatza.rede.QuantosNoDueloPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// 21/10 (PvP; dele: "quem desafia escolhe quantos Pokémon"): a tela do duelo. Abre sozinha para quem acabou de mandar o
// desafio de batalha pela roda do Cobblemon (o servidor manda: pvp.Duelos). "Quantos Pokémon cada um usa?": um botão para
// cada número, de 1 até o menor time com vida dos dois (os de cima ficam apagados); o marcado é o que já vale (o último que
// você usou). Clicou: vale esse, e o outro fica sabendo no chat. "Cancelar o desafio" desfaz o desafio do Cobblemon. Esc
// só fecha (vale o marcado). Ela fecha sozinha quando o desafio acaba (recusado, expirou) ou o duelo começa
public class TelaDoDuelo extends Screen {
  private static final String FRASE = "cobblemon_combat_za.duelo.tela.";
  private static final int MAXIMO_DE_POKEMON = 6;
  private static final int BOTAO = 24;
  private static final int ENTRE_BOTOES = 6;
  private static final int ALTURA_DO_BOTAO = 20;
  private static final int LARGURA_DO_CANCELAR = 140;
  private static final int LINHA = 14;
  private static final int CINZA = 0xFFB4B8C4;
  private static final int BRANCO = 0xFFFFFFFF;

  private record Rotulo(Component texto, int y, int cor) {
  }

  private final int maximo;
  private final int marcado;
  private final List<Rotulo> rotulos = new ArrayList<>();

  public TelaDoDuelo(String adversario, int maximo, int marcado) {
    super(Component.translatable(FRASE + "titulo", adversario));
    this.maximo = maximo;
    this.marcado = marcado;
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(EscolhaDoDueloPayload.TIPO, (pacote, contexto) -> chegou(pacote));
  }

  private static void chegou(EscolhaDoDueloPayload pacote) {
    Minecraft cliente = Minecraft.getInstance();
    if (pacote.maximo() == EscolhaDoDueloPayload.FECHAR) {
      if (cliente.screen instanceof TelaDoDuelo) {
        cliente.setScreen(null);
      }
      return;
    }
    cliente.setScreen(new TelaDoDuelo(pacote.adversario(), pacote.maximo(), pacote.sugerido()));
  }

  @Override
  protected void init() {
    rotulos.clear();
    int y = Math.max(8, height / 2 - 60);
    rotulos.add(new Rotulo(title.copy().withStyle(ChatFormatting.BOLD), y, BRANCO));
    y += LINHA + 4;
    rotulos.add(new Rotulo(Component.translatable(FRASE + "pergunta"), y, BRANCO));
    y += LINHA + 2;
    int largura = MAXIMO_DE_POKEMON * BOTAO + (MAXIMO_DE_POKEMON - 1) * ENTRE_BOTOES;
    int x = (width - largura) / 2;
    for (int quantos = 1; quantos <= MAXIMO_DE_POKEMON; quantos++) {
      int escolhido = quantos;
      Component texto = Component.literal(String.valueOf(quantos));
      if (quantos == marcado) {
        texto = texto.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
      }
      Button botao = Button.builder(texto, b -> escolher(escolhido))
          .bounds(x + (quantos - 1) * (BOTAO + ENTRE_BOTOES), y, BOTAO, ALTURA_DO_BOTAO).build();
      botao.active = quantos <= maximo;
      addRenderableWidget(botao);
    }
    y += ALTURA_DO_BOTAO + 8;
    rotulos.add(new Rotulo(Component.translatable(FRASE + "dica"), y, CINZA));
    y += LINHA;
    rotulos.add(new Rotulo(Component.translatable(FRASE + "dica_2"), y, CINZA));
    y += LINHA + 6;
    addRenderableWidget(Button.builder(Component.translatable(FRASE + "cancelar"), b -> escolher(
        QuantosNoDueloPayload.CANCELAR)).bounds((width - LARGURA_DO_CANCELAR) / 2, y, LARGURA_DO_CANCELAR, ALTURA_DO_BOTAO)
        .build());
  }

  private void escolher(int quantos) {
    if (ClientPlayNetworking.canSend(QuantosNoDueloPayload.TIPO)) {
      ClientPlayNetworking.send(new QuantosNoDueloPayload(quantos));
    }
    onClose();
  }

  // O duelo começou (o painel do duelo chegou) enquanto a tela estava aberta: ela sai da frente
  @Override
  public void tick() {
    super.tick();
    if (HistoricoDaBatalha.emDuelo()) {
      onClose();
    }
  }

  @Override
  public void render(GuiGraphics tela, int mouseX, int mouseY, float parcial) {
    super.render(tela, mouseX, mouseY, parcial);
    for (Rotulo rotulo : rotulos) {
      tela.drawCenteredString(font, rotulo.texto(), width / 2, rotulo.y(), rotulo.cor());
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
