package dev.victor.combatza.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.rede.UsarGolpePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

// 7.10 (dele, 20/10: "quero que no início tenha um tutorial de como jogar, quais botões fazer, como configurar qual botão faz
// o quê (...) temos muito botão para a pessoa aprender a jogar, e como funciona o combate"; "não é livro guia (...) igual o do
// Voice Chat, que a primeira vez que entra aparece esse tutorial com um botão de ir passando, e botão de marcar não mostrar
// novamente e fechar"; "sempre que adicionarmos coisa nova tem que adicionar aí bem explicado, e ter a opção de português e
// inglês"): ao entrar num mundo com o mod no servidor, a tela abre sozinha, até você marcar "Não mostrar de novo". As teclas
// aparecem como ESTÃO nos seus Controles. 7.10b (dele, com print: "deveria dar para configurar as teclas por aqui"; escolheu
// "em cada página + uma de todas"): embaixo do texto, um botão para cada tecla da página (clica e aperta a nova), e a última
// página tem todas as teclas do mod juntas. Para ver de novo: o botão no canto do menu de pausa (Esc).
// Os textos ficam nos arquivos de tradução (lang/pt_br.json e lang/en_us.json, as chaves "cobblemon_combat_za.tutorial."); o
// tutorial abre no idioma do seu Minecraft, e um botão troca entre português e inglês. As duas escolhas ficam no arquivo
// config/cobblemon_combat_za_cliente.json, do seu jogo
public class Tutorial {
  private static final String CHAVE_DE_MOSTRAR = "mostrarTutorial";
  private static final String CHAVE_DO_IDIOMA = "idiomaDoTutorial";
  private static final String PORTUGUES = "pt";
  private static final String INGLES = "en";
  private static final String TEXTOS = CobblemonCombatZA.MOD_ID + ".tutorial.";
  private static final Path ARQUIVO = FabricLoader.getInstance().getConfigDir()
      .resolve("cobblemon_combat_za_cliente.json");
  private static final int MARGEM_DO_BOTAO_DA_PAUSA = 4;
  private static final int LARGURA_DO_BOTAO_DA_PAUSA = 130;
  private static final int ALTURA_DO_BOTAO = 20;

  private static boolean mostrar = true;
  // null = o idioma do Minecraft
  private static String idioma = null;
  private static boolean abrirAoEntrar = false;
  private static Map<String, String> emPortugues;
  private static Map<String, String> emIngles;

  private Tutorial() {
  }

  public static void registrar() {
    carregar();
    ClientPlayConnectionEvents.JOIN.register((conexao, remetente, cliente) -> abrirAoEntrar = mostrar);
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> abrirAoEntrar = false);
    // Espera o mundo aparecer, sem outra tela aberta (a do Voice Chat, por exemplo, vem antes). Só com o mod no servidor:
    // num servidor sem ele, nada disto funciona
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      if (abrirAoEntrar && cliente.player != null && cliente.level != null && cliente.screen == null) {
        abrirAoEntrar = false;
        if (ClientPlayNetworking.canSend(UsarGolpePayload.TIPO)) {
          cliente.setScreen(new Tela(null));
        }
      }
    });
    ScreenEvents.AFTER_INIT.register((cliente, tela, largura, altura) -> {
      if (tela instanceof PauseScreen pausa && pausa.showsPauseMenu()) {
        Screens.getButtons(tela).add(Button.builder(Component.literal(frase("botao_da_pausa")),
            botao -> cliente.setScreen(new Tela(tela)))
            .bounds(MARGEM_DO_BOTAO_DA_PAUSA, MARGEM_DO_BOTAO_DA_PAUSA, LARGURA_DO_BOTAO_DA_PAUSA, ALTURA_DO_BOTAO)
            .build());
      }
    });
  }

  private static void carregar() {
    try {
      if (Files.exists(ARQUIVO)) {
        JsonObject json = JsonParser.parseString(Files.readString(ARQUIVO)).getAsJsonObject();
        mostrar = !json.has(CHAVE_DE_MOSTRAR) || json.get(CHAVE_DE_MOSTRAR).getAsBoolean();
        idioma = json.has(CHAVE_DO_IDIOMA) ? json.get(CHAVE_DO_IDIOMA).getAsString() : null;
      }
    } catch (IOException | RuntimeException erro) {
      CobblemonCombatZA.LOGGER.warn("TUTORIAL: não consegui ler {} ({}); ele vai aparecer", ARQUIVO, erro.getMessage());
    }
  }

  // O idioma escolhido no botão; sem escolha, o do Minecraft (português se ele começa com "pt", senão inglês)
  private static boolean emPortugues() {
    if (idioma != null) {
      return PORTUGUES.equals(idioma);
    }
    return Minecraft.getInstance().getLanguageManager().getSelected().startsWith(PORTUGUES);
  }

  // Uma frase do tutorial no idioma escolhido. Os dois arquivos de tradução são lidos direto do mod (o Minecraft só carrega
  // o idioma dele, e o botão pode pedir o outro). Faltou no português, vale o inglês; faltou nos dois, aparece a chave
  private static String frase(String chave) {
    if (emIngles == null) {
      emPortugues = traducoes("pt_br");
      emIngles = traducoes("en_us");
    }
    String completa = TEXTOS + chave;
    String frase = emPortugues() ? emPortugues.get(completa) : null;
    return frase != null ? frase : emIngles.getOrDefault(completa, completa);
  }

  private static Map<String, String> traducoes(String arquivo) {
    Map<String, String> frases = new HashMap<>();
    try (InputStream entrada = Tutorial.class.getResourceAsStream(
        "/assets/" + CobblemonCombatZA.MOD_ID + "/lang/" + arquivo + ".json")) {
      if (entrada != null) {
        JsonParser.parseReader(new InputStreamReader(entrada, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
            .forEach(par -> frases.put(par.getKey(), par.getValue().getAsString()));
      }
    } catch (IOException | RuntimeException erro) {
      CobblemonCombatZA.LOGGER.warn("TUTORIAL: não consegui ler as traduções {} ({})", arquivo, erro.getMessage());
    }
    return frases;
  }

  // Uma tecla com o nome curto que aparece no botão dela
  private record Tecla(String nome, KeyMapping mapeamento) {
  }

  private record Pagina(String titulo, List<Component> paragrafos, List<Tecla> teclas) {
  }

  // A tela: um painel no meio; em cima, o título e a página; no meio, o texto e os botões das teclas da página; embaixo,
  // Voltar, Todas as teclas e Próximo, e a caixinha, o idioma e Fechar. Esc fecha (ou cancela a troca de uma tecla)
  private static final class Tela extends Screen {
    private static final int LARGURA_MAXIMA = 340;
    private static final int ALTURA_MAXIMA = 260;
    private static final int BORDA_DA_TELA = 10;
    private static final int MARGEM = 12;
    private static final int ESPACO = 6;
    private static final int LARGURA_DO_FECHAR = 80;
    private static final int LARGURA_DO_IDIOMA = 70;
    // 7.10b (dele: "afasta um pouco isso do topo"): o título desce, e o texto começa bem abaixo da linha da página
    private static final int TOPO_DO_TITULO = 16;
    private static final int TOPO_DO_TEXTO = 50;
    private static final int ESPACO_ENTRE_PARAGRAFOS = 5;
    private static final int ALTURA_DA_TECLA = 20;
    private static final int ESPACO_ENTRE_TECLAS = 3;
    private static final int ACIMA_DOS_BOTOES = 8;
    // Com muitas teclas (a página de todas), elas vão em 3 colunas
    private static final int MUITAS_TECLAS = 6;
    private static final float MENOR_LETRA = 0.7f;
    private static final float[] CANTOS = Formas.raios(6f);
    private static final int FUNDO_DE_CIMA = 0xF01A2432;
    private static final int FUNDO_DE_BAIXO = 0xF0101418;
    private static final int BORDA = 0xFF5AA0FF;
    private static final int BRANCO = 0xFFFFFFFF;
    private static final int CINZA = 0xFFB4B4BE;
    private static final int TEXTO = 0xFFE6EAF0;

    private final Screen anterior;
    private List<Pagina> paginas;
    private int pagina = 0;
    // A tecla esperando você apertar a nova (null = nenhuma)
    private KeyMapping ouvindo = null;
    private int topoDasTeclas;

    private Tela(Screen anterior) {
      super(Component.literal(frase("botao_da_pausa")));
      this.anterior = anterior;
    }

    @Override
    protected void init() {
      paginas = paginas();
      int ultima = paginas.size() - 1;
      int largura = largura();
      int x = (width - largura) / 2;
      int y = (height - altura()) / 2;
      int yDeBaixo = y + altura() - MARGEM - ALTURA_DO_BOTAO;
      int yDeCima = yDeBaixo - ESPACO - ALTURA_DO_BOTAO;
      int larguraDoBotao = (largura - 2 * MARGEM - 2 * ESPACO) / 3;
      Button voltar = addRenderableWidget(Button.builder(Component.literal(frase("voltar")), botao -> mudar(pagina - 1))
          .bounds(x + MARGEM, yDeCima, larguraDoBotao, ALTURA_DO_BOTAO).build());
      voltar.active = pagina > 0;
      // No meio: "Todas as teclas" leva à última página; nela, os Controles do Minecraft (as teclas dos outros mods)
      Button doMeio = pagina == ultima
          ? Button.builder(Component.literal(frase("controles_do_minecraft")),
              botao -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))).build()
          : Button.builder(Component.literal(frase("todas_as_teclas")), botao -> mudar(ultima)).build();
      doMeio.setRectangle(larguraDoBotao, ALTURA_DO_BOTAO, x + MARGEM + larguraDoBotao + ESPACO, yDeCima);
      addRenderableWidget(doMeio);
      addRenderableWidget(Button.builder(Component.literal(frase(pagina == ultima ? "concluir" : "proximo")),
          botao -> avancar()).bounds(x + largura - MARGEM - larguraDoBotao, yDeCima, larguraDoBotao, ALTURA_DO_BOTAO)
          .build());
      addRenderableWidget(Checkbox.builder(Component.literal(frase("nao_mostrar")), font)
          .pos(x + MARGEM, yDeBaixo).selected(!mostrar)
          .onValueChange((caixa, marcada) -> {
            mostrar = !marcada;
            salvar();
          }).build());
      // O botão do idioma mostra o OUTRO: em português, "English"; em inglês, "Português"
      addRenderableWidget(Button.builder(Component.literal(frase("outro_idioma")), botao -> trocarOIdioma())
          .bounds(x + largura - MARGEM - LARGURA_DO_FECHAR - ESPACO - LARGURA_DO_IDIOMA, yDeBaixo, LARGURA_DO_IDIOMA,
              ALTURA_DO_BOTAO)
          .build());
      addRenderableWidget(Button.builder(Component.literal(frase("fechar")), botao -> onClose())
          .bounds(x + largura - MARGEM - LARGURA_DO_FECHAR, yDeBaixo, LARGURA_DO_FECHAR, ALTURA_DO_BOTAO).build());
      botoesDasTeclas(paginas.get(pagina).teclas(), x, yDeCima, largura);
    }

    // Os botões das teclas da página, em colunas, logo acima de Voltar / Próximo. Clicar = esperar a tecla nova
    private void botoesDasTeclas(List<Tecla> teclas, int x, int yDeCima, int largura) {
      int colunas = teclas.size() > MUITAS_TECLAS ? 3 : 2;
      int linhas = (teclas.size() + colunas - 1) / colunas;
      topoDasTeclas = yDeCima - ACIMA_DOS_BOTOES - linhas * (ALTURA_DA_TECLA + ESPACO_ENTRE_TECLAS) + ESPACO_ENTRE_TECLAS;
      int larguraDaTecla = (largura - 2 * MARGEM - (colunas - 1) * ESPACO) / colunas;
      for (int i = 0; i < teclas.size(); i++) {
        Tecla tecla = teclas.get(i);
        int coluna = i % colunas;
        int linha = i / colunas;
        Button botao = Button.builder(rotulo(tecla), b -> ouvir(tecla.mapeamento()))
            .bounds(x + MARGEM + coluna * (larguraDaTecla + ESPACO),
                topoDasTeclas + linha * (ALTURA_DA_TECLA + ESPACO_ENTRE_TECLAS), larguraDaTecla, ALTURA_DA_TECLA)
            .build();
        List<KeyMapping> outras = conflitos(tecla.mapeamento());
        if (!outras.isEmpty() && tecla.mapeamento() != ouvindo) {
          List<String> nomes = new ArrayList<>();
          outras.forEach(outra -> nomes.add(Component.translatable(outra.getName()).getString()));
          botao.setTooltip(Tooltip.create(Component.literal(String.format(frase("tecla.conflito"),
              String.join(", ", nomes)))));
        }
        addRenderableWidget(botao);
      }
    }

    // "Golpe 1: [Z]"; esperando a tecla nova, "Golpe 1: > aperte <"; presa a outra função também, a tecla fica vermelha
    private Component rotulo(Tecla tecla) {
      MutableComponent rotulo = Component.literal(tecla.nome() + ": ");
      if (tecla.mapeamento() == ouvindo) {
        return rotulo.append(Component.literal(frase("tecla.ouvindo")).withStyle(ChatFormatting.YELLOW));
      }
      boolean repetida = !conflitos(tecla.mapeamento()).isEmpty();
      return rotulo.append(tecla(tecla.mapeamento(), repetida ? ChatFormatting.RED : ChatFormatting.YELLOW));
    }

    private void ouvir(KeyMapping tecla) {
      ouvindo = tecla;
      rebuildWidgets();
    }

    // Como na tela de Controles do Minecraft (KeyBindsScreen): grava nas opções (que salvam o options.txt) e refaz o mapa
    // das teclas. Esc cancela (lá, Esc apaga a tecla); Delete ou Backspace deixam a ação SEM tecla (dele, 20/10: a tecla do
    // lado tinha ficado no Windows esquerdo, e aqui não dava para tirar)
    @Override
    public boolean keyPressed(int tecla, int codigo, int modificadores) {
      if (ouvindo != null) {
        if (tecla == GLFW.GLFW_KEY_DELETE || tecla == GLFW.GLFW_KEY_BACKSPACE) {
          gravar(InputConstants.UNKNOWN);
        } else if (tecla != GLFW.GLFW_KEY_ESCAPE) {
          gravar(InputConstants.getKey(tecla, codigo));
        }
        ouvindo = null;
        rebuildWidgets();
        return true;
      }
      return super.keyPressed(tecla, codigo, modificadores);
    }

    // Esperando a tecla: o botão do meio e os laterais do mouse também valem; o esquerdo e o direito cancelam
    @Override
    public boolean mouseClicked(double x, double y, int botao) {
      if (ouvindo != null) {
        if (botao > GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
          gravar(InputConstants.Type.MOUSE.getOrCreate(botao));
        }
        ouvindo = null;
        rebuildWidgets();
        return true;
      }
      return super.mouseClicked(x, y, botao);
    }

    private void gravar(InputConstants.Key nova) {
      minecraft.options.setKey(ouvindo, nova);
      KeyMapping.resetMapping();
    }

    private void trocarOIdioma() {
      alternarIdioma();
      salvar();
      rebuildWidgets();
    }

    private static void alternarIdioma() {
      idioma = emPortugues() ? INGLES : PORTUGUES;
    }

    private static void salvar() {
      JsonObject json = new JsonObject();
      json.addProperty(CHAVE_DE_MOSTRAR, mostrar);
      if (idioma != null) {
        json.addProperty(CHAVE_DO_IDIOMA, idioma);
      }
      try {
        Files.writeString(ARQUIVO, new GsonBuilder().setPrettyPrinting().create().toJson(json));
      } catch (IOException erro) {
        CobblemonCombatZA.LOGGER.warn("TUTORIAL: não consegui gravar {} ({})", ARQUIVO, erro.getMessage());
      }
    }

    private int largura() {
      return Math.min(LARGURA_MAXIMA, width - 2 * BORDA_DA_TELA);
    }

    private int altura() {
      return Math.min(ALTURA_MAXIMA, height - 2 * BORDA_DA_TELA);
    }

    private void mudar(int nova) {
      pagina = Math.clamp(nova, 0, paginas.size() - 1);
      ouvindo = null;
      rebuildWidgets();
    }

    private void avancar() {
      if (pagina == paginas.size() - 1) {
        onClose();
      } else {
        mudar(pagina + 1);
      }
    }

    @Override
    public void onClose() {
      minecraft.setScreen(anterior);
    }

    // O painel vem junto com o fundo, antes dos botões (que o Minecraft desenha por cima)
    @Override
    public void renderBackground(GuiGraphics tela, int mouseX, int mouseY, float parcial) {
      super.renderBackground(tela, mouseX, mouseY, parcial);
      int largura = largura();
      int altura = altura();
      int x = (width - largura) / 2;
      int y = (height - altura) / 2;
      Formas.retangulo(tela, x, y, largura, altura, CANTOS, FUNDO_DE_CIMA, FUNDO_DE_BAIXO);
      Formas.borda(tela, x, y, largura, altura, CANTOS, 1f, BORDA);
      tela.flush();
      Pagina atual = paginas.get(pagina);
      tela.drawCenteredString(font, Component.literal(atual.titulo()).withStyle(ChatFormatting.BOLD), width / 2,
          y + TOPO_DO_TITULO, BRANCO);
      tela.drawCenteredString(font, String.format(frase("pagina"), pagina + 1, paginas.size()), width / 2,
          y + TOPO_DO_TITULO + font.lineHeight + 4, CINZA);
      int topo = y + TOPO_DO_TEXTO;
      int fundo = atual.teclas().isEmpty()
          ? y + altura - MARGEM - 2 * ALTURA_DO_BOTAO - ESPACO - ACIMA_DOS_BOTOES
          : topoDasTeclas - ACIMA_DOS_BOTOES;
      paragrafos(tela, atual.paragrafos(), x + MARGEM, topo, largura - 2 * MARGEM, fundo - topo);
    }

    // O texto quebrado na largura do painel; se não couber na altura, ele encolhe (até 70%) e quebra de novo
    private void paragrafos(GuiGraphics tela, List<Component> paragrafos, int x, int y, int largura, int altura) {
      List<List<FormattedCharSequence>> linhas = quebrar(paragrafos, largura);
      float escala = 1f;
      float total = alturaDe(linhas);
      if (total > altura) {
        escala = Math.max(MENOR_LETRA, altura / total);
        linhas = quebrar(paragrafos, (int) (largura / escala));
      }
      tela.pose().pushPose();
      tela.pose().translate(x, y, 0);
      tela.pose().scale(escala, escala, 1f);
      int cursor = 0;
      for (List<FormattedCharSequence> paragrafo : linhas) {
        for (FormattedCharSequence linha : paragrafo) {
          tela.drawString(font, linha, 0, cursor, TEXTO, false);
          cursor += font.lineHeight + 1;
        }
        cursor += ESPACO_ENTRE_PARAGRAFOS;
      }
      tela.pose().popPose();
    }

    private List<List<FormattedCharSequence>> quebrar(List<Component> paragrafos, int largura) {
      List<List<FormattedCharSequence>> linhas = new ArrayList<>();
      for (Component paragrafo : paragrafos) {
        linhas.add(font.split(paragrafo, largura));
      }
      return linhas;
    }

    private float alturaDe(List<List<FormattedCharSequence>> linhas) {
      float altura = 0;
      for (List<FormattedCharSequence> paragrafo : linhas) {
        altura += paragrafo.size() * (font.lineHeight + 1f) + ESPACO_ENTRE_PARAGRAFOS;
      }
      return altura - ESPACO_ENTRE_PARAGRAFOS;
    }

    // As páginas, montadas na hora (as teclas já são as do momento). Ao mudar uma tecla ou uma regra do jogo, ou ao criar uma
    // funcionalidade nova, mudar aqui (o texto e as teclas da página) e nos DOIS arquivos de tradução (pedido dele)
    private static List<Pagina> paginas() {
      Minecraft cliente = Minecraft.getInstance();
      KeyMapping soltar = PartySendBinding.INSTANCE;
      KeyMapping lado = TeclasDoTime.lado();
      KeyMapping rolar = RolamentoNoCliente.tecla();
      KeyMapping guardas = RodaDosGuardas.tecla();
      KeyMapping mega = MegaNoCliente.tecla();
      KeyMapping party = TelaDaParty.tecla();
      Tecla anterior = new Tecla(frase("tecla.anterior"), TeclasDoTime.anterior());
      Tecla proximo = new Tecla(frase("tecla.proximo"), TeclasDoTime.proximo());
      Tecla aSoltar = new Tecla(frase("tecla.soltar"), soltar);
      Tecla oLado = new Tecla(frase("tecla.lado"), lado);
      List<Tecla> golpes = new ArrayList<>();
      for (int i = 0; i < TeclasDeGolpe.quantas(); i++) {
        golpes.add(new Tecla(String.format(frase("tecla.golpe"), i + 1), TeclasDeGolpe.tecla(i)));
      }
      Tecla oFoco = new Tecla(frase("tecla.foco"), Foco.tecla());
      Tecla aRolar = new Tecla(frase("tecla.rolar"), rolar);
      Tecla osGuardas = new Tecla(frase("tecla.guardas"), guardas);
      Tecla oZoom = new Tecla(frase("tecla.zoom"), ZoomDaCamera.tecla());
      Tecla aMega = new Tecla(frase("tecla.mega"), mega);
      Tecla aParty = new Tecla(frase("tecla.party"), party);
      List<Tecla> todas = new ArrayList<>(List.of(anterior, proximo, aSoltar, oLado));
      todas.addAll(golpes);
      // A Mega só existe com o Mega Showdown: sem ele, nem a página nem a tecla aparecem (como a do OPAC e a do Xaero)
      boolean comMega = FabricLoader.getInstance().isModLoaded("mega_showdown");
      todas.addAll(comMega ? List.of(oFoco, aRolar, osGuardas, aMega, oZoom, aParty) : List.of(oFoco, aRolar, osGuardas, oZoom, aParty));

      List<Pagina> paginas = new ArrayList<>();
      paginas.add(pagina(1, List.of(), texto("1.1"), texto("1.2")));
      paginas.add(pagina(2, List.of(anterior, proximo, aSoltar, oLado),
          texto("2.1", TeclasDoTime.anterior(), TeclasDoTime.proximo(), soltar), texto("2.2"),
          lado.isUnbound() ? texto("2.3_sem_tecla") : texto("2.3", lado)));
      paginas.add(pagina(3, List.of(aSoltar), texto("3.1", soltar), texto("3.2"), texto("3.3")));
      paginas.add(pagina(4, golpes, texto("4.1", TeclasDeGolpe.tecla(0), TeclasDeGolpe.tecla(1), TeclasDeGolpe.tecla(2),
          TeclasDeGolpe.tecla(3)), texto("4.2"), texto("4.3"), texto("4.4"),
          texto("4.5", cliente.options.keyUse)));
      paginas.add(pagina(5, List.of(oFoco, aRolar), texto("5.1"), texto("5.2", Foco.tecla(), Foco.tecla()),
          rolar.isUnbound() ? texto("5.3_sem_tecla", cliente.options.keyShift)
              : texto("5.3", cliente.options.keyShift, rolar),
          texto("5.4", Foco.tecla())));
      paginas.add(pagina(6, List.of(aSoltar), texto("6.1", soltar), texto("6.2"), texto("6.3", soltar)));
      paginas.add(pagina(7, List.of(osGuardas), texto("7.1"), texto("7.2", guardas), texto("7.3", guardas)));
      // 8.2: a página do XP (as chaves vão pelo número, não pela posição: a 10 vem antes da 8)
      paginas.add(pagina(10, List.of(), texto("10.1"), texto("10.2"), texto("10.3")));
      // 8.4: a Mega Evolução (a 11 vem depois da 10, antes da 8)
      if (comMega) {
        paginas.add(pagina(11, List.of(aMega), texto("11.1"),
            mega.isUnbound() ? texto("11.2_sem_tecla") : texto("11.2", mega), texto("11.3")));
      }
      // 8.7 e 8.8: as bolas especiais e a mochila na batalha
      paginas.add(pagina(12, List.of(), texto("12.1"), texto("12.2"), texto("12.3"), texto("12.4")));
      paginas.add(pagina(13, List.of(), texto("13.1"), texto("13.2"), texto("13.3"), texto("13.4")));
      // Lotes 2 e 3 dos itens segurados (dele, 20/10: "preciso que termine os lotes 2 e 3")
      paginas.add(pagina(19, List.of(), texto("19.1"), texto("19.2"), texto("19.3")));
      paginas.add(pagina(20, List.of(), texto("20.1"), texto("20.2"), texto("20.3")));
      paginas.add(pagina(21, List.of(), texto("21.1"), texto("21.2"), texto("21.3")));
      paginas.add(pagina(14, List.of(), texto("14.1"), texto("14.2"), texto("14.3")));
      paginas.add(pagina(15, List.of(), texto("15.1"), texto("15.2"), texto("15.3")));
      paginas.add(pagina(16, List.of(), texto("16.1"), texto("16.2")));
      // A frase do Open Parties and Claims só aparece quando a party do servidor é a dele (dele: "pra não trazer confusão")
      List<Component> daParty = new ArrayList<>(List.of(texto("17.1"),
          party.isUnbound() ? texto("17.4_sem_tecla") : texto("17.4", party), texto("17.2"), texto("17.3")));
      if (PartyNoCliente.doOpac()) {
        daParty.add(texto("17.5_opac"));
      }
      paginas.add(pagina(17, List.of(aParty), daParty.toArray(new Component[0])));
      // Os waypoints da party: só com o mapa do Xaero (sem ele, não há conjunto "Party" para dividir)
      if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
        paginas.add(pagina(18, List.of(), texto("18.1"), texto("18.2"), texto("18.3")));
      }
      paginas.add(pagina(8, List.of(oZoom), texto("8.1"), texto("8.2", ZoomDaCamera.tecla()), texto("8.3")));
      paginas.add(pagina(9, todas, texto("9.1")));
      return paginas;
    }

    private static Pagina pagina(int numero, List<Tecla> teclas, Component... paragrafos) {
      return new Pagina(frase(numero + ".titulo"), List.of(paragrafos), teclas);
    }

    // Um parágrafo: a frase da tradução, e cada "%s" dela vira a próxima tecla, em amarelo, com o nome que está nos Controles
    private static Component texto(String chave, KeyMapping... teclas) {
      String[] pedacos = frase(chave).split("%s", -1);
      MutableComponent paragrafo = Component.empty();
      for (int i = 0; i < pedacos.length; i++) {
        paragrafo.append(Component.literal(pedacos[i]));
        if (i < pedacos.length - 1 && i < teclas.length) {
          paragrafo.append(tecla(teclas[i], ChatFormatting.YELLOW));
        }
      }
      return paragrafo;
    }

    private static Component tecla(KeyMapping tecla, ChatFormatting cor) {
      return Component.literal("[").append(tecla.getTranslatedKeyMessage()).append("]")
          .withStyle(cor, ChatFormatting.BOLD);
    }

    // As outras funções do jogo (de qualquer mod) presas à mesma tecla
    private static List<KeyMapping> conflitos(KeyMapping tecla) {
      List<KeyMapping> outras = new ArrayList<>();
      if (tecla.isUnbound()) {
        return outras;
      }
      for (KeyMapping outra : Minecraft.getInstance().options.keyMappings) {
        if (outra != tecla && outra.same(tecla)) {
          outras.add(outra);
        }
      }
      return outras;
    }
  }
}
