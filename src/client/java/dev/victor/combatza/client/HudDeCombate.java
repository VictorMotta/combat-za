package dev.victor.combatza.client;

import org.joml.Matrix4f;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.TypeIcon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.golpes.CoresDosTipos;
import dev.victor.combatza.golpes.PrimeiroTurno;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

// A HUD do Z-A, parte 1: o losango dos 4 golpes (item 9 da imagem). O painel do time (itens 6 e 7) é o PainelDoTime.
// Só aparece com o Pokémon comandado FORA da bola (o estado SentOutState, que o Cobblemon manda para o cliente): em
// batalha o lutador, fora dela o da setinha (LutadorNoCliente)
public class HudDeCombate {
  private static final String[] TECLAS = { "Z", "X", "C", "V" };
  // O losango: golpe 1 em cima, 2 à esquerda, 3 à direita, 4 embaixo (como no controle do Z-A)
  private static final int[][] POSICOES = { { 0, -1 }, { -1, 0 }, { 1, 0 }, { 0, 1 } };
  private static final int DISTANCIA_ENTRE_GOLPES = 35;
  private static final int TAMANHO = 17;
  private static final int MARGEM_DIREITA = 145;
  private static final int MARGEM_DE_BAIXO = 78;
  private static final int METADE_DO_ICONE = 9;
  private static final float APAGADO_O_ICONE = 0.35f;
  private static final double PASSO_DO_RELOGIO = Math.toRadians(5);

  private static final int BRANCO = 0xFFFFFFFF;
  // 6.11: a dica da prévia fica em cima do nome do golpe de cima
  private static final int ALTURA_DA_DICA = 44;
  private static final int CINZA = 0xFFAAAAAA;
  private static final int SOMBRA = 0x70000000;
  private static final int BORDA = 0xFFF2F2F2;
  private static final int BRILHO = 0x40FFFFFF;
  private static final int APAGADO = 0xD0202020;
  private static final int FUNDO_DO_TEXTO = 0x90000000;
  private static final float TICKS_POR_SEGUNDO = 20f;
  private static final int RISCO = 0xE6FF5A5A;
  private static final float ESPESSURA_DO_RISCO = 2f;
  private static final float PONTA_DO_RISCO = 0.55f;
  // 5.4b: o número de cargas que sobram, num losango dourado no canto de cima, à direita
  private static final int CANTO_DAS_CARGAS = 13;
  private static final int TAMANHO_DAS_CARGAS = 7;
  private static final int FUNDO_DAS_CARGAS = 0xFFC8901E;
  private static final float ESCALA_DAS_CARGAS = 0.75f;
  // O layout da HUD: a caixa do losango tem lugar para nomes de golpe de até 70 pixels dos lados
  private static final int NOME_COMPRIDO = 70;

  private HudDeCombate() {
  }

  public static void registrar() {
    HudRenderCallback.EVENT.register(HudDeCombate::desenhar);
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    Minecraft cliente = Minecraft.getInstance();
    if (cliente.options.hideGui || cliente.player == null) {
      return;
    }
    // 7.9: com "todos de guarda", ninguém é comandado (-1) e o losango some
    int vaga = LutadorNoCliente.vagaDoComandado();
    Pokemon pokemon = vaga < 0 ? null : CobblemonClient.INSTANCE.getStorage().getParty().get(vaga);
    if (pokemon == null || !(pokemon.getState() instanceof SentOutState)) {
      return;
    }
    // O lugar e o tamanho do bloco (LayoutDaHud): o de sempre, ou o que você escolheu no layout da HUD
    LayoutDaHud.comecar(tela, LayoutDaHud.Bloco.GOLPES);
    desenharGolpes(tela, cliente.font, pokemon);
    LayoutDaHud.terminar(tela);
  }

  // O layout da HUD (LayoutDaHud): a caixa do losango, com os nomes dos lados e a dica da prévia em cima
  static LayoutDaHud.Caixa caixa(int largura, int altura) {
    float centroX = largura - MARGEM_DIREITA;
    float centroY = altura - MARGEM_DE_BAIXO;
    float folgaDoNome = TAMANHO + 7;
    float lado = DISTANCIA_ENTRE_GOLPES + folgaDoNome + NOME_COMPRIDO + 3;
    float cima = DISTANCIA_ENTRE_GOLPES + ALTURA_DA_DICA + 2;
    float baixo = DISTANCIA_ENTRE_GOLPES + folgaDoNome + 11;
    return new LayoutDaHud.Caixa(centroX - lado, centroY - cima, 2 * lado, cima + baixo);
  }

  private static void desenharGolpes(GuiGraphics tela, Font fonte, Pokemon pokemon) {
    int centroX = tela.guiWidth() - MARGEM_DIREITA;
    int centroY = tela.guiHeight() - MARGEM_DE_BAIXO;
    for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
      Move golpe = pokemon.getMoveSet().get(slot);
      if (golpe != null) {
        int x = centroX + POSICOES[slot][0] * DISTANCIA_ENTRE_GOLPES;
        int y = centroY + POSICOES[slot][1] * DISTANCIA_ENTRE_GOLPES;
        desenharGolpe(tela, fonte, pokemon, golpe, slot, x, y);
      }
    }
    // 6.11: segurando a tecla, a dica em cima do losango (acima do nome do golpe de cima)
    if (PreviaDoGolpe.mostrando() >= 0) {
      Component dica = Component.translatable(PreviaDoGolpe.moveOMarcador() ? "cobblemon_combat_za.previa.dica_marcador"
          : "cobblemon_combat_za.previa.dica", Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage());
      int largura = fonte.width(dica);
      int y = centroY - DISTANCIA_ENTRE_GOLPES - ALTURA_DA_DICA;
      tela.fill(centroX - largura / 2 - 3, y - 2, centroX + largura / 2 + 3, y + 10, FUNDO_DO_TEXTO);
      tela.drawString(fonte, dica, centroX - largura / 2, y, BRANCO, true);
    }
  }

  // Sombra, borda branca, a cor do tipo e o ícone do tipo do Cobblemon no meio. Na recarga, a cor volta como um
  // relógio: começa no meio-dia e gira no sentido do ponteiro até completar a volta
  private static void desenharGolpe(GuiGraphics tela, Font fonte, Pokemon pokemon, Move golpe, int slot, int x, int y) {
    int corDoTipo = 0xFF000000 | CoresDosTipos.de(golpe.getType());
    long falta = RecargasNoCliente.ticksQueFaltam(pokemon.getUuid(), slot);
    // O Fingimento (e os outros golpes que só saem logo que o Pokémon entra), depois da primeira ação: apagado e riscado
    // até o Pokémon voltar para a bola (PrimeiroTurno)
    boolean gasto = PrimeiroTurno.soNoPrimeiroTurno(golpe) && PrimeiroTurnoNoCliente.jaAgiu(pokemon.getUuid())
        // 9.1: preso em outro golpe pela Escolha, ou de status com o Colete de Ataque: riscado do mesmo jeito
        || TravaDaEscolhaNoCliente.desligado(pokemon, slot, golpe);
    // 6.11: o golpe segurado ganha um brilho em volta, pulsando
    if (PreviaDoGolpe.mostrando() == slot) {
      float pulso = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 120.0);
      losango(tela, x, y, TAMANHO + 5 + Math.round(pulso * 2), Formas.comAlfa(BRANCO, 0.35f + 0.35f * pulso));
    }
    losango(tela, x, y + 2, TAMANHO + 3, SOMBRA);
    losango(tela, x, y, TAMANHO + 2, BORDA);
    if (gasto) {
      losango(tela, x, y, TAMANHO, APAGADO);
      iconeDoTipo(tela, golpe, x, y, APAGADO_O_ICONE);
      riscado(tela, x, y);
    } else if (falta > 0) {
      losango(tela, x, y, TAMANHO, APAGADO);
      relogio(tela, x, y, TAMANHO, 1f - RecargasNoCliente.fracaoQueFalta(pokemon.getUuid(), slot), corDoTipo);
      iconeDoTipo(tela, golpe, x, y, APAGADO_O_ICONE);
      textoNoMeio(tela, fonte, String.format("%.1f", falta / TICKS_POR_SEGUNDO), x, y, 1f, BRANCO);
    } else {
      losango(tela, x, y, TAMANHO, corDoTipo);
      losango(tela, x, y - TAMANHO / 3, TAMANHO / 2, BRILHO);
      iconeDoTipo(tela, golpe, x, y, 1f);
    }
    textoNoMeio(tela, fonte, TECLAS[slot], x, y + TAMANHO - 5, 0.75f, BRANCO);
    int cargas = CargasNoCliente.restantes(pokemon.getUuid(), slot);
    if (cargas > 0) {
      contadorDeCargas(tela, fonte, cargas, x + CANTO_DAS_CARGAS, y - CANTO_DAS_CARGAS);
    }
    nomeDoGolpe(tela, fonte, golpe.getDisplayName(), slot, x, y, falta > 0 || gasto ? CINZA : BRANCO);
  }

  private static void contadorDeCargas(GuiGraphics tela, Font fonte, int cargas, int x, int y) {
    losango(tela, x, y, TAMANHO_DAS_CARGAS + 1, BORDA);
    losango(tela, x, y, TAMANHO_DAS_CARGAS, FUNDO_DAS_CARGAS);
    textoNoMeio(tela, fonte, String.valueOf(cargas), x, y + 1, ESCALA_DAS_CARGAS, BRANCO);
  }

  // Um risco vermelho de canto a canto, por cima do losango apagado
  private static void riscado(GuiGraphics tela, int x, int y) {
    float ponta = TAMANHO * PONTA_DO_RISCO;
    Formas.linha(tela, x - ponta, y + ponta, x + ponta, y - ponta, ESPESSURA_DO_RISCO, RISCO);
    tela.flush();
  }

  // O mesmo desenho do ícone de tipo que o Cobblemon usa na tela de resumo do Pokémon (textures/gui/types.png)
  private static void iconeDoTipo(GuiGraphics tela, Move golpe, int x, int y, float opacidade) {
    new TypeIcon(x, y - METADE_DO_ICONE, golpe.getType(), null, true, false, 0f, 0f, opacidade).render(tela);
  }

  // Fatias de 5 graus, do centro até a borda do losango. A borda do losango, num ângulo, fica a
  // tamanho / (|seno| + |cosseno|) do centro (é a "distância de Manhattan" que define um losango)
  private static void relogio(GuiGraphics tela, int x, int y, int tamanho, float pronto, int cor) {
    double ate = 2 * Math.PI * pronto;
    if (ate <= 0) {
      return;
    }
    Matrix4f matriz = tela.pose().last().pose();
    VertexConsumer vertices = tela.bufferSource().getBuffer(RenderType.gui());
    for (double angulo = 0; angulo < ate; angulo += PASSO_DO_RELOGIO) {
      double proximo = Math.min(angulo + PASSO_DO_RELOGIO, ate);
      float[] a = naBorda(angulo, tamanho);
      float[] b = naBorda(proximo, tamanho);
      fatia(vertices, matriz, x, y, a, b, cor);
      fatia(vertices, matriz, x, y, b, a, cor);
    }
    tela.flush();
  }

  // Um triângulo como um quadrilátero com o último ponto repetido (a tela do Minecraft desenha de 4 em 4)
  private static void fatia(VertexConsumer vertices, Matrix4f matriz, int x, int y, float[] a, float[] b, int cor) {
    vertices.addVertex(matriz, x, y, 0).setColor(cor);
    vertices.addVertex(matriz, x + a[0], y + a[1], 0).setColor(cor);
    vertices.addVertex(matriz, x + b[0], y + b[1], 0).setColor(cor);
    vertices.addVertex(matriz, x + b[0], y + b[1], 0).setColor(cor);
  }

  // Ângulo 0 = meio-dia; cresce no sentido do relógio (na tela, o y cresce para baixo)
  private static float[] naBorda(double angulo, int tamanho) {
    double seno = Math.sin(angulo);
    double cosseno = Math.cos(angulo);
    double raio = tamanho / (Math.abs(seno) + Math.abs(cosseno));
    return new float[] { (float) (seno * raio), (float) (-cosseno * raio) };
  }

  // O nome fica do lado de fora do losango (em cima, à esquerda, à direita ou embaixo), com um fundo escuro atrás
  private static void nomeDoGolpe(GuiGraphics tela, Font fonte, Component nome, int slot, int x, int y, int cor) {
    int largura = fonte.width(nome);
    int folga = TAMANHO + 7;
    int textoX = switch (slot) {
      case 1 -> x - folga - largura;
      case 2 -> x + folga;
      default -> x - largura / 2;
    };
    int textoY = switch (slot) {
      case 0 -> y - folga - 9;
      case 3 -> y + folga + 1;
      default -> y - 4;
    };
    tela.fill(textoX - 3, textoY - 2, textoX + largura + 3, textoY + 10, FUNDO_DO_TEXTO);
    tela.drawString(fonte, nome, textoX, textoY, cor, true);
  }

  private static void textoNoMeio(GuiGraphics tela, Font fonte, String texto, int x, int y, float escala, int cor) {
    tela.pose().pushPose();
    tela.pose().translate(x, y, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.drawString(fonte, texto, -fonte.width(texto) / 2, -4, cor, true);
    tela.pose().popPose();
  }

  // Um quadrado girado 45 graus em volta de (x, y); "tamanho" é a distância do centro até a ponta
  private static void losango(GuiGraphics tela, int x, int y, int tamanho, int cor) {
    if (tamanho <= 0) {
      return;
    }
    float metadeDoLado = (float) (tamanho / Math.sqrt(2));
    tela.pose().pushPose();
    tela.pose().translate(x, y, 0);
    tela.pose().mulPose(Axis.ZP.rotationDegrees(45));
    tela.pose().scale(metadeDoLado, metadeDoLado, 1f);
    tela.fill(-1, -1, 1, 1, cor);
    tela.pose().popPose();
  }
}
