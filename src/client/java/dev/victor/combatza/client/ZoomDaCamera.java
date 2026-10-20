package dev.victor.combatza.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

// O zoom da 3ª pessoa (pedido dele): segurando o Alt esquerdo, a rodinha aproxima e afasta a câmera, de 1,5 a 8 blocos (o
// Minecraft usa 4). Sem o Alt, a rodinha troca o item da barra, como sempre. A distância desliza até a escolhida e vale até
// fechar o jogo.
// 6.3i (dele, 20/10: "se eu segurar o Alt e rodar a rodinha para baixo, ele já vai saindo da primeira pessoa com o máximo
// de zoom na terceira e vai tirando o zoom; e na terceira faz o mesmo dando o zoom"): a 1ª pessoa é o fim do zoom. Da 1ª,
// rodinha para baixo vai para a 3ª com a câmera no mais perto; na 3ª, já no mais perto, mais um giro para cima volta para a 1ª
public class ZoomDaCamera {
  private static final double DISTANCIA_DO_MINECRAFT = 4.0;
  private static final double MAIS_PERTO = 1.5;
  private static final double MAIS_LONGE = 8.0;
  private static final double BLOCOS_POR_GIRO = 0.5;
  private static final double VELOCIDADE = 10.0;
  private static final double SEGUNDOS_MAXIMOS_POR_QUADRO = 0.1;
  // Para voltar à 1ª pessoa, um giro inteiro além do mais perto (o "clique" da rodinha do mouse; o trackpad do Mac manda
  // pedacinhos de giro, e assim um deslize curto não troca a câmera sem querer)
  private static final double UM_GIRO = 1.0;
  private static final double FOLGA = 0.001;

  private static KeyMapping tecla;
  private static double escolhida = DISTANCIA_DO_MINECRAFT;
  private static double atual = DISTANCIA_DO_MINECRAFT;
  private static double alemDoMaisPerto = 0;
  private static long ultimoQuadro = 0;

  private ZoomDaCamera() {
  }

  // 7.10: o tutorial mostra a tecla como está nos Controles
  static KeyMapping tecla() {
    return tecla;
  }

  public static void registrar() {
    tecla = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.zoom_da_camera",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, TeclasDeGolpe.CATEGORIA));
  }

  // Chamado pela RodinhaMixin a cada giro da rodinha: devolve true se o giro virou zoom (aí o Minecraft não troca o item).
  // Rodinha para cima (giro positivo) aproxima. Na 3ª pessoa de frente (a do F5 olhando para você), nada muda
  public static boolean rodinha(double giro) {
    Minecraft cliente = Minecraft.getInstance();
    if (cliente.screen != null || cliente.player == null || !estaApertada()) {
      return false;
    }
    CameraType camera = cliente.options.getCameraType();
    if (camera == CameraType.FIRST_PERSON) {
      if (giro < 0) {
        escolhida = MAIS_PERTO;
        atual = MAIS_PERTO;
        alemDoMaisPerto = 0;
        trocarACamera(cliente, CameraType.THIRD_PERSON_BACK);
      }
      // Com o Alt seguro, a rodinha é da câmera: na 1ª pessoa, para cima não faz nada (e não troca o item)
      return true;
    }
    if (camera != CameraType.THIRD_PERSON_BACK) {
      return false;
    }
    if (giro > 0 && escolhida <= MAIS_PERTO + FOLGA) {
      alemDoMaisPerto += giro;
      if (alemDoMaisPerto >= UM_GIRO) {
        alemDoMaisPerto = 0;
        trocarACamera(cliente, CameraType.FIRST_PERSON);
      }
      return true;
    }
    alemDoMaisPerto = 0;
    escolhida = Mth.clamp(escolhida - giro * BLOCOS_POR_GIRO, MAIS_PERTO, MAIS_LONGE);
    return true;
  }

  // O mesmo que o F5 faz (Minecraft.handleKeybinds): troca a câmera, avisa o efeito de tela da entidade (como a visão do
  // creeper no espectador) e redesenha o mundo
  private static void trocarACamera(Minecraft cliente, CameraType nova) {
    boolean estavaNaPrimeira = cliente.options.getCameraType().isFirstPerson();
    cliente.options.setCameraType(nova);
    if (estavaNaPrimeira != nova.isFirstPerson()) {
      cliente.gameRenderer.checkEntityPostEffect(nova.isFirstPerson() ? cliente.getCameraEntity() : null);
    }
    cliente.levelRenderer.needsUpdate();
  }

  // Chamado a cada quadro pela câmera: a distância desliza até a escolhida. Devolve -1 quando é a do Minecraft (não mexe)
  public static double distancia() {
    long agora = Util.getNanos();
    double segundos = ultimoQuadro == 0 ? 0 : Math.min(SEGUNDOS_MAXIMOS_POR_QUADRO, (agora - ultimoQuadro) / 1e9);
    ultimoQuadro = agora;
    atual += (escolhida - atual) * (1 - Math.exp(-VELOCIDADE * segundos));
    return Math.abs(atual - DISTANCIA_DO_MINECRAFT) < 0.01 ? -1 : atual;
  }

  // O Alt também é o "olhar livre" da montaria do Cobblemon, e o Minecraft só avisa UMA função por tecla. Por isso a tecla
  // é lida direto do teclado (TeclaFisica, a que estiver configurada em Controles): assim as duas funcionam
  private static boolean estaApertada() {
    return TeclaFisica.estaApertada(tecla);
  }
}
