package dev.victor.combatza.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.victor.combatza.CobblemonCombatZA;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;

// O layout da HUD (dele, 20/10, com o print de uma tela menor: "precisamos deixar esse layout dinâmico (...) uma forma dele
// redimensionar cada bloco (...) e ficar sempre salvo no perfil do mine dela daquela instância"; escolheu "mover e
// redimensionar", "botão no menu de pausa" e "encolher sozinho + ajuste"; e depois: "já viu um redimensionar do Photoshop?
// queria igual, e o mover também, e não com a rodinha do mouse"). Onde fica e de que tamanho é cada bloco da HUD.
// Cada bloco continua se desenhando onde sempre se desenhou (as "coordenadas do bloco"). Antes de desenhar, ele pede aqui uma
// transformação (mover + mudar o tamanho, na pose da tela) que leva a CAIXA dele (o retângulo que ele ocupa no maior tamanho)
// para o lugar certo da tela:
// - sem ajuste do jogador: o lugar de sempre, encolhendo sozinho em telas pequenas, em volta do canto ou da borda dele;
// - com ajuste (TelaDoLayout): o lugar e o tamanho que o jogador escolheu, preso ao canto mais perto (numa janela de outro
//   tamanho, o bloco continua perto do mesmo canto).
// Os ajustes ficam em config/cobblemon_combat_za_hud.json, no jogo (na instância) de cada um
public class LayoutDaHud {
  public enum Bloco {
    TIME("time"), SELECIONADO("selecionado"), PASSIVAS("passivas"), GOLPES("golpes"), MEGA("mega"), HISTORICO("historico"), GUARDAS("guardas"),
    PARTY("party"), OLHO("olho");

    // O nome no arquivo e nas traduções ("cobblemon_combat_za.layout.bloco.golpes")
    final String nome;

    Bloco(String nome) {
      this.nome = nome;
    }
  }

  // Um retângulo, na tela (pixels da interface) ou nas coordenadas de um bloco
  public record Caixa(float x, float y, float largura, float altura) {
    float direita() {
      return x + largura;
    }

    float baixo() {
      return y + altura;
    }

    float centroX() {
      return x + largura / 2;
    }

    float centroY() {
      return y + altura / 2;
    }

    boolean contem(double pontoX, double pontoY) {
      return pontoX >= x && pontoX <= direita() && pontoY >= y && pontoY <= baixo();
    }
  }

  // Das coordenadas do bloco para a tela: (x, y) + ponto × escala
  public record Transformacao(float x, float y, float escala) {
    Caixa aplicar(Caixa caixa) {
      return new Caixa(x + caixa.x() * escala, y + caixa.y() * escala, caixa.largura() * escala, caixa.altura() * escala);
    }
  }

  // Onde o bloco fica preso, em cada eixo: no começo (esquerda / em cima), no meio ou no fim (direita / embaixo)
  private enum Ancora {
    COMECO, MEIO, FIM
  }

  // O ajuste do jogador: em cada eixo, a âncora e a distância da borda do bloco até a borda da tela (no meio: do meio do bloco
  // até o meio da tela); e a escala (1 = o tamanho de sempre)
  private record Ajuste(Ancora ancoraX, float x, Ancora ancoraY, float y, float escala) {
  }

  static final float MENOR_ESCALA = 0.35f;
  static final float MAIOR_ESCALA = 2.5f;
  // "Encolher sozinho": o tamanho de sempre foi feito para uma tela de 640 x 360 (pixels da interface; 1920 x 1080 com a
  // escala da interface em 3). Menor que isso, os blocos encolhem junto, até 60%
  private static final float LARGURA_DE_REFERENCIA = 640f;
  private static final float ALTURA_DE_REFERENCIA = 360f;
  private static final float MENOR_AUTOMATICA = 0.6f;
  // O losango dos golpes e a Mega, embaixo à direita, encolhem para o pedaço de baixo do losango (o golpe 4 e o nome dele,
  // 185 pixels da borda) caber à direita da barra de itens (91 pixels de cada lado do meio, mais 4 de folga)
  private static final float METADE_DA_BARRA_DE_ITENS = 95f;
  private static final float BAIXO_DO_LOSANGO = 185f;
  private static final float ALTURA_DO_LOSANGO = 330f;
  // Menos de meio pixel do meio da tela = preso no meio (a TelaDoLayout gruda o bloco no meio exato)
  private static final float NO_MEIO = 0.5f;
  private static final Path ARQUIVO = FabricLoader.getInstance().getConfigDir().resolve("cobblemon_combat_za_hud.json");

  private static final Map<Bloco, Ajuste> AJUSTES = new EnumMap<>(Bloco.class);

  private LayoutDaHud() {
  }

  public static void registrar() {
    carregar();
  }

  // Os blocos que existem neste jogo (a Mega só com o Mega Showdown)
  static List<Bloco> blocos() {
    List<Bloco> blocos = new ArrayList<>(List.of(Bloco.values()));
    if (!FabricLoader.getInstance().isModLoaded("mega_showdown")) {
      blocos.remove(Bloco.MEGA);
    }
    return blocos;
  }

  // Antes de desenhar o bloco: a pose passa a levar as coordenadas dele para o lugar da tela. Depois, terminar()
  public static void comecar(GuiGraphics tela, Bloco bloco) {
    Transformacao transformacao = transformacao(bloco, tela.guiWidth(), tela.guiHeight());
    tela.pose().pushPose();
    tela.pose().translate(transformacao.x(), transformacao.y(), 0);
    tela.pose().scale(transformacao.escala(), transformacao.escala(), 1f);
  }

  public static void terminar(GuiGraphics tela) {
    tela.pose().popPose();
  }

  // A tesoura (scissor) do Minecraft corta em pixels da tela e NÃO acompanha a pose: os cantos do recorte passam pela pose
  // antes. Assim, com o bloco movido ou encolhido, o recorte dos retratos vai junto
  public static void recortar(GuiGraphics tela, float x, float y, float largura, float altura) {
    Matrix4f pose = tela.pose().last().pose();
    Vector4f a = pose.transform(new Vector4f(x, y, 0, 1));
    Vector4f b = pose.transform(new Vector4f(x + largura, y + altura, 0, 1));
    tela.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
        (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
  }

  static boolean ajustado(Bloco bloco) {
    return AJUSTES.containsKey(bloco);
  }

  static boolean algumAjustado() {
    return !AJUSTES.isEmpty();
  }

  static Transformacao transformacao(Bloco bloco, int largura, int altura) {
    Ajuste ajuste = AJUSTES.get(bloco);
    if (ajuste == null) {
      return padrao(bloco, largura, altura);
    }
    Caixa caixa = caixa(bloco, largura, altura);
    Caixa naTela = naTela(ajuste, caixa, largura, altura);
    return new Transformacao(naTela.x() - caixa.x() * ajuste.escala(), naTela.y() - caixa.y() * ajuste.escala(),
        ajuste.escala());
  }

  // O retângulo que o bloco ocupa na tela agora
  static Caixa naTela(Bloco bloco, int largura, int altura) {
    return transformacao(bloco, largura, altura).aplicar(caixa(bloco, largura, altura));
  }

  // A caixa do bloco, nas coordenadas dele (cada bloco sabe a sua)
  static Caixa caixa(Bloco bloco, int largura, int altura) {
    return caixa(bloco, largura, altura, naEsquerda(bloco, largura, altura));
  }

  private static Caixa caixa(Bloco bloco, int largura, int altura, boolean naEsquerda) {
    return switch (bloco) {
      case TIME -> PainelDoTime.caixaDaFileira();
      case SELECIONADO -> PainelDoTime.caixaDoSelecionado();
      case PASSIVAS -> PassivasNaTela.caixa();
      case GOLPES -> HudDeCombate.caixa(largura, altura);
      case MEGA -> MegaNoCliente.caixa(largura, altura);
      case HISTORICO -> HistoricoDaBatalha.caixa(largura, altura, naEsquerda);
      case GUARDAS -> FileiraDosGuardas.caixa(largura, naEsquerda);
      case PARTY -> PartyNaTela.caixa(largura, altura, naEsquerda);
      case OLHO -> SinaisNaTela.caixaDaFaixa(largura);
    };
  }

  // O histórico, os guardas e a party encostam numa borda. No lugar de sempre: o histórico e os guardas do lado sem o
  // minimapa do Xaero, a party do outro lado. Com ajuste: o lado da tela em que o meio do bloco está
  static boolean naEsquerda(Bloco bloco, int largura, int altura) {
    Ajuste ajuste = AJUSTES.get(bloco);
    if (bloco != Bloco.HISTORICO && bloco != Bloco.GUARDAS && bloco != Bloco.PARTY) {
      return true;
    }
    if (ajuste != null) {
      // A caixa tem o mesmo tamanho dos dois lados: qualquer um serve para achar o meio
      return naTela(ajuste, caixa(bloco, largura, altura, true), largura, altura).centroX() < largura / 2f;
    }
    if (bloco == Bloco.PARTY) {
      return !naEsquerda(Bloco.HISTORICO, largura, altura);
    }
    return HistoricoDaBatalha.historicoNaEsquerda();
  }

  // O lugar de sempre, encolhendo sozinho em volta do canto ou da borda do bloco. A fileira do time já encolhe sozinha para
  // caber ao lado da barra de itens; sem ajuste, o painel do selecionado vai junto com ela (em cima), e as passivas junto
  // com o painel (em cima dele)
  private static Transformacao padrao(Bloco bloco, int largura, int altura) {
    return switch (bloco) {
      case TIME -> PainelDoTime.padrao(largura, altura);
      case SELECIONADO -> transformacao(Bloco.TIME, largura, altura);
      case PASSIVAS -> transformacao(Bloco.SELECIONADO, largura, altura);
      case GOLPES, MEGA -> emVoltaDe(largura, altura, doLosango(largura, altura));
      case HISTORICO, GUARDAS -> emVoltaDe(naEsquerda(bloco, largura, altura) ? 0 : largura, 0, automatica(largura, altura));
      case PARTY -> emVoltaDe(naEsquerda(bloco, largura, altura) ? 0 : largura, caixa(bloco, largura, altura).y(),
          automatica(largura, altura));
      case OLHO -> emVoltaDe(largura / 2f, 0, automatica(largura, altura));
    };
  }

  private static float automatica(int largura, int altura) {
    return Math.clamp(Math.min(largura / LARGURA_DE_REFERENCIA, altura / ALTURA_DE_REFERENCIA), MENOR_AUTOMATICA, 1f);
  }

  private static float doLosango(int largura, int altura) {
    float aoLadoDaBarra = (largura / 2f - METADE_DA_BARRA_DE_ITENS) / BAIXO_DO_LOSANGO;
    return Math.clamp(Math.min(aoLadoDaBarra, altura / ALTURA_DO_LOSANGO), MENOR_AUTOMATICA, 1f);
  }

  // Encolher "fator" em volta do ponto (x, y): ele fica parado, e o resto se aproxima dele
  private static Transformacao emVoltaDe(float x, float y, float fator) {
    return new Transformacao(x * (1 - fator), y * (1 - fator), fator);
  }

  // O ajuste vira um retângulo na tela, sempre dentro dela (maior que a tela: no meio)
  private static Caixa naTela(Ajuste ajuste, Caixa caixa, int largura, int altura) {
    float w = caixa.largura() * ajuste.escala();
    float h = caixa.altura() * ajuste.escala();
    float x = dentro(daAncora(ajuste.ancoraX(), ajuste.x(), w, largura), w, largura);
    float y = dentro(daAncora(ajuste.ancoraY(), ajuste.y(), h, altura), h, altura);
    return new Caixa(x, y, w, h);
  }

  private static float daAncora(Ancora ancora, float distancia, float tamanho, float tela) {
    return switch (ancora) {
      case COMECO -> distancia;
      case MEIO -> tela / 2 + distancia - tamanho / 2;
      case FIM -> tela - distancia - tamanho;
    };
  }

  static float dentro(float inicio, float tamanho, float tela) {
    return tamanho >= tela ? (tela - tamanho) / 2 : Math.clamp(inicio, 0, tela - tamanho);
  }

  // A TelaDoLayout pôs o bloco em "naTela" (o retângulo dele na tela): vira um ajuste preso ao canto mais perto
  static void ajustar(Bloco bloco, Caixa naTela, int largura, int altura) {
    float escala = Math.clamp(naTela.largura() / caixa(bloco, largura, altura).largura(), MENOR_ESCALA, MAIOR_ESCALA);
    Ancora ancoraX = ancora(naTela.centroX(), largura);
    Ancora ancoraY = ancora(naTela.centroY(), altura);
    AJUSTES.put(bloco, new Ajuste(ancoraX, distancia(ancoraX, naTela.x(), naTela.largura(), largura), ancoraY,
        distancia(ancoraY, naTela.y(), naTela.altura(), altura), escala));
  }

  private static Ancora ancora(float centro, float tela) {
    if (Math.abs(centro - tela / 2) < NO_MEIO) {
      return Ancora.MEIO;
    }
    return centro < tela / 2 ? Ancora.COMECO : Ancora.FIM;
  }

  private static float distancia(Ancora ancora, float inicio, float tamanho, float tela) {
    return switch (ancora) {
      case COMECO -> inicio;
      case MEIO -> inicio + tamanho / 2 - tela / 2;
      case FIM -> tela - inicio - tamanho;
    };
  }

  static void restaurar(Bloco bloco) {
    AJUSTES.remove(bloco);
  }

  static void restaurarTudo() {
    AJUSTES.clear();
  }

  private static void carregar() {
    AJUSTES.clear();
    if (!Files.exists(ARQUIVO)) {
      return;
    }
    try {
      JsonObject json = JsonParser.parseString(Files.readString(ARQUIVO)).getAsJsonObject();
      for (Bloco bloco : Bloco.values()) {
        if (json.has(bloco.nome)) {
          JsonObject ajuste = json.getAsJsonObject(bloco.nome);
          AJUSTES.put(bloco, new Ajuste(Ancora.valueOf(ajuste.get("ancoraX").getAsString()), ajuste.get("x").getAsFloat(),
              Ancora.valueOf(ajuste.get("ancoraY").getAsString()), ajuste.get("y").getAsFloat(),
              Math.clamp(ajuste.get("escala").getAsFloat(), MENOR_ESCALA, MAIOR_ESCALA)));
        }
      }
    } catch (IOException | RuntimeException erro) {
      AJUSTES.clear();
      CobblemonCombatZA.LOGGER.warn("LAYOUT: não consegui ler {} ({}); a HUD fica no lugar de sempre", ARQUIVO,
          erro.getMessage());
    }
  }

  static void salvar() {
    JsonObject json = new JsonObject();
    AJUSTES.forEach((bloco, ajuste) -> {
      JsonObject deste = new JsonObject();
      deste.addProperty("ancoraX", ajuste.ancoraX().name());
      deste.addProperty("x", ajuste.x());
      deste.addProperty("ancoraY", ajuste.ancoraY().name());
      deste.addProperty("y", ajuste.y());
      deste.addProperty("escala", ajuste.escala());
      json.add(bloco.nome, deste);
    });
    try {
      Files.writeString(ARQUIVO, new GsonBuilder().setPrettyPrinting().create().toJson(json));
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.warn("LAYOUT: não consegui gravar {} ({})", ARQUIVO, erro.getMessage());
    }
  }
}
