package dev.victor.combatza.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.keybind.keybinds.HidePartyBinding;
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// A HUD do Z-A, parte 2 (itens 6 e 7 da imagem), no lugar da lista de Pokémon do Cobblemon, embaixo à esquerda: a
// fileira do time em blocos de vidro e, em cima dela, só com o selecionado FORA da bola, o painel dele (retrato de
// corpo inteiro, nível, gênero, status, nome e vida). Medidas e cores tiradas da foto do Z-A
public class PainelDoTime {
  private record Vaga(int numero, Pokemon pokemon) {
  }

  // Como uma tela do Cobblemon enquadra o corpo inteiro: o tamanho da caixa dela, a escala do modelo e a altura da
  // origem do modelo (a partir do topo da caixa)
  private record Enquadramento(float caixa, float escala, float origemY) {
  }

  // Tela de resumo (Summary): caixa de 66, escala 2 x 20, origem 10 acima. PC (StorageSlot): caixa de 25, escala
  // 2,5 x 4,5, origem 1 abaixo. Os dois giram 13 graus para baixo e 35 para o lado (o PC vira para a esquerda, como no Z-A)
  private static final Enquadramento DO_RESUMO = new Enquadramento(66f, 40f, -10f);
  private static final Enquadramento DO_PC = new Enquadramento(25f, 11.25f, 1f);
  private static final float GIRO_PARA_BAIXO = 13f;
  private static final float GIRO_PARA_O_LADO = 35f;
  private static final float COR_DE_QUEM_DESMAIOU = 0.4f;

  // O desenho tem tamanho fixo (os blocos da fileira com 24 pixels) e encolhe até 60% se não couber antes da mão
  // secundária da barra de itens (Gui.renderItemHotbar: a barra começa 91 pixels à esquerda do meio; a mão, 29 antes)
  private static final float ENCOLHER_ATE = 0.6f;
  private static final int MARGEM_ESQUERDA = 8;
  private static final int MARGEM_DE_BAIXO = 6;
  private static final int METADE_DA_BARRA_DE_ITENS = 91;
  private static final int MAO_SECUNDARIA = 29;

  // O painel do selecionado (item 6)
  private static final float MOLDURA = 48f;
  private static final float[] CANTOS_DA_MOLDURA = Formas.raios(3f, 12f, 3f, 3f);
  private static final float ESPACO_ATE_O_TEXTO = 5f;
  private static final float ALTURA_DA_PLAQUINHA = 11f;
  private static final float RAIO_DO_GENERO = 5.5f;
  private static final float RAIO_DA_POKEBOLA = 2.6f;
  private static final float TOPO_DO_NOME = 15f;
  private static final float NOME_GRANDE = 1.5f;
  private static final float TEXTO_PEQUENO = 0.75f;
  private static final float ALTURA_DA_LETRA = 7f;
  private static final float TOPO_DA_VIDA = 31f;
  private static final float LARGURA_DA_VIDA = 112f;
  private static final float ALTURA_DA_VIDA = 9f;
  private static final float LARGURA_DA_ABA = 26f;
  private static final float ALTURA_DA_ABA = 4.5f;
  private static final float VELOCIDADE_DO_RASTRO = 0.12f;

  // A fileira do time (item 7)
  private static final int VAGAS_DO_TIME = 6;
  private static final float BLOCO = 24f;
  private static final float[] CANTOS_DO_BLOCO = Formas.raios(2.5f);
  private static final float ESPACO_ENTRE_BLOCOS = 5f;
  private static final float ESPACO_ATE_A_FILEIRA = 5f;
  private static final float ALTURA_DA_FILEIRA = 33f;
  private static final float BARRINHA = 2.5f;
  private static final float ALTURA_DA_TECLA = 7.5f;
  private static final float LARGURA_DA_SETA = 7f;
  private static final float ALTURA_DA_SETA = 12f;
  private static final float ESPACO_DA_SETA = 4f;
  private static final float[] BRILHO_POR_CAMADA = { 0.22f, 0.12f, 0.06f };
  // 7.9: o escudinho do bloco de "todos de guarda", em proporção ao bloco
  private static final float TAMANHO_DO_ESCUDO = 0.55f;
  private static final float PERIODO_DO_BRILHO = 1600f;
  // Desenhos por cima do retrato precisam ficar "na frente" dele (o retrato é 3D e tem profundidade)
  private static final float NA_FRENTE_DO_RETRATO = 200f;
  // O item segurado: metade do tamanho de um item (16), como na lista do Cobblemon, com meio pixel de folga do canto
  private static final float ITEM_NO_CANTO = 8f;
  private static final float TAMANHO_DE_UM_ITEM = 16f;
  private static final float FOLGA_DO_CANTO = 0.5f;
  // 8.5: o gênero e o nível na coluna da esquerda do quadrado; o status numa plaquinha no meio da borda de cima
  private static final float RAIO_DO_GENERO_NO_BLOCO = 2.6f;
  private static final float ALTURA_DO_GENERO_NO_BLOCO = 13f;
  private static final float LETRA_NO_BLOCO = 0.5f;
  private static final float ALTURA_DA_PLAQUINHA_NO_BLOCO = 6f;

  // Cores em ARGB (o primeiro par é a opacidade: FF = sólido)
  private static final int BRANCO = 0xFFFFFFFF;
  private static final int CINZA = 0xF08C8C9C;
  private static final int MOLDURA_DE_CIMA = 0xD957BDD8;
  private static final int MOLDURA_DE_BAIXO = 0x8C6EA5AA;
  private static final int BORDA_DA_MOLDURA = 0xD9B9F0F0;
  private static final int PLAQUINHA_DO_NIVEL = 0xE072ADB4;
  private static final int VIDA_VAZIA = 0xD914201E;
  private static final int RASTRO = 0xBFFFFFFF;
  private static final int BLOCO_DE_CIMA = 0x4DD7E4DE;
  private static final int BLOCO_DE_BAIXO = 0x42BECDC8;
  private static final int BORDA_DO_BLOCO = 0x80EBF5F0;
  private static final int SELECIONADO_DE_CIMA = 0x9E8CCDDC;
  private static final int SELECIONADO_DE_BAIXO = 0xB37DBEC8;
  private static final int BORDA_DO_SELECIONADO = 0xE6E1F8FC;
  private static final int DESMAIADO_DE_CIMA = 0x61D25050;
  private static final int DESMAIADO_DE_BAIXO = 0x61AA3C3C;
  private static final int FUNDO_DA_TECLA = 0xEBEEF5FA;
  private static final int TEXTO_DA_TECLA = 0xFF2D3E48;
  private static final int FUNDO_DA_BARRINHA = 0x8C0F1E14;
  private static final int SETA = 0xEB8CEBE6;
  private static final int COR_DO_BRILHO = 0x8CEBE6;
  private static final int VERDE_DA_EVOLUCAO = 0xFF6CF06C;
  private static final int CONTORNO_DA_EVOLUCAO = 0xC0204020;
  private static final Map<String, Integer> CORES_DOS_STATUS = Map.of(
      "brn", 0xF0E0703A, "par", 0xF0D8B020, "psn", 0xF0A33EA1, "tox", 0xF07A2A79,
      "slp", 0xF08C8C9C, "frz", 0xF05CB8D8, "fnt", 0xF0A03030);

  // O "estado" de cada retrato (a pose e o tempo da animação), um por Pokémon, como o Cobblemon guarda nas telas dele.
  // O grande anda (respira, mexe); os pequenos ficam parados. E o rastro da vida de cada um
  private static final Map<UUID, FloatingState> RETRATOS_GRANDES = new HashMap<>();
  private static final Map<UUID, FloatingState> RETRATOS_PEQUENOS = new HashMap<>();
  private static final Map<UUID, Float> RASTROS = new HashMap<>();
  private static boolean escondemosOdoCobblemon = false;
  private static boolean escondidoPeloJogador = false;

  private PainelDoTime() {
  }

  public static void registrar() {
    HudRenderCallback.EVENT.register(PainelDoTime::desenhar);
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> esconderOPainelDoCobblemon());
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      RETRATOS_GRANDES.clear();
      RETRATOS_PEQUENOS.clear();
      RASTROS.clear();
    });
  }

  // O Cobblemon não desenha a lista dele enquanto a chave da tecla "Esconder time" (HidePartyBinding) estiver ligada.
  // Deixamos a chave sempre ligada; se ela aparecer desligada, foi o jogador que apertou a tecla: aí some o nosso.
  // Com o time vazio a chave volta para o Cobblemon, que nesse caso mostra o aviso de escolher o inicial
  private static void esconderOPainelDoCobblemon() {
    if (time().isEmpty()) {
      if (escondemosOdoCobblemon) {
        HidePartyBinding.INSTANCE.setShouldHide(false);
        escondemosOdoCobblemon = false;
      }
      return;
    }
    if (!HidePartyBinding.INSTANCE.getShouldHide()) {
      if (escondemosOdoCobblemon) {
        escondidoPeloJogador = !escondidoPeloJogador;
      }
      HidePartyBinding.INSTANCE.setShouldHide(true);
      escondemosOdoCobblemon = true;
    }
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    // 23/10: na batalha por turnos do Cobblemon, só a tela dele (BatalhaPorTurnos)
    if (BatalhaPorTurnos.agora()) {
      return;
    }
    Minecraft cliente = Minecraft.getInstance();
    if (cliente.options.hideGui || cliente.player == null || escondidoPeloJogador
        || cliente.getDebugOverlay().showDebugScreen()) {
      return;
    }
    List<Vaga> time = time();
    if (time.isEmpty()) {
      return;
    }
    int selecionado = CobblemonClient.INSTANCE.getStorage().getSelectedSlot();
    // Em batalha, o painel grande é o do lutador (a setinha só escolhe quem entra no R); fora dela, o do selecionado.
    // 7.9: com "todos de guarda", ninguém é comandado, e o painel grande some
    int doPainelGrande = LutadorNoCliente.vagaDoComandado();
    Pokemon atual = null;
    for (Vaga vaga : time) {
      if (vaga.numero() == doPainelGrande) {
        atual = vaga.pokemon();
      }
    }
    boolean todosDeGuarda = LutadorNoCliente.todosDeGuarda();
    // O lugar e o tamanho de cada bloco (LayoutDaHud): o de sempre, ou o que você escolheu no layout da HUD. O painel do
    // selecionado e a fileira são blocos separados (dele, 20/10: "a vida do seu bicho selecionado pode ser separada também,
    // separada da barra de baixo"); sem ajuste, o painel fica em cima da fileira, onde ela estiver
    LayoutDaHud.comecar(tela, LayoutDaHud.Bloco.SELECIONADO);
    if (atual != null && atual.getState() instanceof SentOutState) {
      desenharSelecionado(tela, cliente.font, atual, tempo.getRealtimeDeltaTicks());
    }
    LayoutDaHud.terminar(tela);
    LayoutDaHud.comecar(tela, LayoutDaHud.Bloco.TIME);
    desenharFileira(tela, cliente.font, time, selecionado, todosDeGuarda, MOLDURA + ESPACO_ATE_A_FILEIRA);
    LayoutDaHud.terminar(tela);
    // P5-10: as passivas (o clima, o campo e as barreiras) logo acima do painel do selecionado, na mesma escala; com lugar
    // próprio no layout, lá
    LayoutDaHud.comecar(tela, LayoutDaHud.Bloco.PASSIVAS);
    PassivasNaTela.desenhar(tela, cliente.font);
    LayoutDaHud.terminar(tela);
  }

  // O layout da HUD (LayoutDaHud): a caixa da fileira no maior tamanho (7 blocos, com o de "todos de guarda"). As caixas do
  // painel, da fileira e das passivas usam as mesmas coordenadas: (0, 0) é o canto de cima do painel
  static LayoutDaHud.Caixa caixaDaFileira() {
    return new LayoutDaHud.Caixa(0, MOLDURA + ESPACO_ATE_A_FILEIRA, larguraDaFileira(VAGAS_DO_TIME + 1), ALTURA_DA_FILEIRA);
  }

  // A caixa do painel do selecionado: o retrato e, ao lado, a linha do nível, o nome e a barra de vida
  static LayoutDaHud.Caixa caixaDoSelecionado() {
    return new LayoutDaHud.Caixa(0, 0, MOLDURA + ESPACO_ATE_O_TEXTO + LARGURA_DA_VIDA, MOLDURA);
  }

  // O lugar de sempre: embaixo à esquerda, encolhido para caber antes da barra de itens
  static LayoutDaHud.Transformacao padrao(int largura, int altura) {
    float escala = escala(largura, VAGAS_DO_TIME + (LutadorNoCliente.todosDeGuarda() ? 1 : 0));
    float alturaDoBloco = MOLDURA + ESPACO_ATE_A_FILEIRA + ALTURA_DA_FILEIRA;
    return new LayoutDaHud.Transformacao(MARGEM_ESQUERDA, altura - MARGEM_DE_BAIXO - alturaDoBloco * escala, escala);
  }

  private static float escala(int largura, int blocos) {
    float espacoLivre = largura / 2f - METADE_DA_BARRA_DE_ITENS - MAO_SECUNDARIA - 2 * MARGEM_ESQUERDA;
    return Math.clamp(espacoLivre / larguraDaFileira(blocos), ENCOLHER_ATE, 1f);
  }

  private static float larguraDaFileira(int blocos) {
    return 2 * (LARGURA_DA_SETA + ESPACO_DA_SETA) + blocos * BLOCO + (blocos - 1) * ESPACO_ENTRE_BLOCOS;
  }

  // As 6 vagas do time podem ter buracos (vaga 1 e 3 ocupadas, por exemplo): guardamos o número de cada uma
  private static List<Vaga> time() {
    List<Vaga> vagas = new ArrayList<>();
    List<Pokemon> slots = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
    for (int numero = 0; numero < slots.size(); numero++) {
      if (slots.get(numero) != null) {
        vagas.add(new Vaga(numero, slots.get(numero)));
      }
    }
    return vagas;
  }

  // Item 6: o retrato de corpo inteiro numa moldura de vidro (o canto de cima à direita mais redondo) e, ao lado, a
  // plaquinha do nível, o gênero num círculo, o status, o nome grande e a barra de vida
  private static void desenharSelecionado(GuiGraphics tela, Font fonte, Pokemon pokemon, float tempo) {
    Formas.retangulo(tela, 0, 0, MOLDURA, MOLDURA, CANTOS_DA_MOLDURA, MOLDURA_DE_CIMA, MOLDURA_DE_BAIXO);
    tela.flush();
    corpoInteiro(tela, pokemon, RETRATOS_GRANDES, DO_RESUMO, 1, 1, MOLDURA - 2, tempo);
    naFrente(tela, () -> {
      Formas.borda(tela, 0, 0, MOLDURA, MOLDURA, CANTOS_DA_MOLDURA, 0.75f, BORDA_DA_MOLDURA);
      if (podeEvoluir(pokemon)) {
        setinhaDeEvolucao(tela, 3f, 3f);
      }
      tela.flush();
    });
    float x = MOLDURA + ESPACO_ATE_O_TEXTO;
    float xDaLinha = plaquinhaDoNivel(tela, fonte, pokemon, x);
    xDaLinha = genero(tela, pokemon, xDaLinha);
    String status = status(pokemon);
    if (status != null) {
      plaquinha(tela, fonte, Component.translatable("cobblemon.ui.status." + status), xDaLinha,
          CORES_DOS_STATUS.getOrDefault(status, CINZA));
    }
    nome(tela, fonte, pokemon, x);
    barraDeVida(tela, fonte, pokemon, x, tempo);
  }

  // Item 7: sem fundo, cada Pokémon num bloco de vidro com a barrinha de vida embaixo; o selecionado mais claro e mais
  // comprido, com a tecla de soltar embaixo; nas pontas, setas que brilham (as teclas ← e → trocam o selecionado).
  // 7.9: com "todos de guarda", um 7º bloco com o escudinho depois do último (só para mostrar que está ligado: as setas
  // não param nele)
  private static void desenharFileira(GuiGraphics tela, Font fonte, List<Vaga> time, int selecionado,
      boolean todosDeGuarda, float y) {
    float largura = larguraDaFileira(time.size() + (todosDeGuarda ? 1 : 0));
    seta(tela, 0, y + BLOCO / 2, 1);
    seta(tela, largura, y + BLOCO / 2, -1);
    for (int i = 0; i < time.size(); i++) {
      float x = LARGURA_DA_SETA + ESPACO_DA_SETA + i * (BLOCO + ESPACO_ENTRE_BLOCOS);
      bloco(tela, fonte, time.get(i), time.get(i).numero() == selecionado, x, y);
    }
    if (todosDeGuarda) {
      blocoDosGuardas(tela, LARGURA_DA_SETA + ESPACO_DA_SETA + time.size() * (BLOCO + ESPACO_ENTRE_BLOCOS), y);
    }
  }

  private static void blocoDosGuardas(GuiGraphics tela, float x, float y) {
    Formas.retangulo(tela, x, y, BLOCO, BLOCO, CANTOS_DO_BLOCO, BLOCO_DE_CIMA, BLOCO_DE_BAIXO);
    Formas.borda(tela, x, y, BLOCO, BLOCO, CANTOS_DO_BLOCO, 0.5f, BORDA_DO_BLOCO);
    Simbolos.escudo(tela, x + BLOCO / 2, y + BLOCO / 2 - 1f, BLOCO * TAMANHO_DO_ESCUDO, BRANCO, 1f);
  }

  private static void bloco(GuiGraphics tela, Font fonte, Vaga vaga, boolean eOSelecionado, float x,
      float y) {
    Pokemon pokemon = vaga.pokemon();
    float altura = eOSelecionado ? ALTURA_DA_FILEIRA : BLOCO;
    if (eOSelecionado) {
      Formas.retangulo(tela, x, y, BLOCO, altura, CANTOS_DO_BLOCO, SELECIONADO_DE_CIMA, SELECIONADO_DE_BAIXO);
    } else if (pokemon.isFainted()) {
      Formas.retangulo(tela, x, y, BLOCO, altura, CANTOS_DO_BLOCO, DESMAIADO_DE_CIMA, DESMAIADO_DE_BAIXO);
    } else {
      Formas.retangulo(tela, x, y, BLOCO, altura, CANTOS_DO_BLOCO, BLOCO_DE_CIMA, BLOCO_DE_BAIXO);
    }
    tela.flush();
    corpoInteiro(tela, pokemon, RETRATOS_PEQUENOS, DO_PC, x, y, BLOCO, 0f);
    naFrente(tela, () -> {
      Formas.borda(tela, x, y, BLOCO, altura, CANTOS_DO_BLOCO, eOSelecionado ? 0.75f : 0.5f,
          eOSelecionado ? BORDA_DO_SELECIONADO : BORDA_DO_BLOCO);
      if (pokemon.getState() instanceof SentOutState) {
        Simbolos.pokebola(tela, x + BLOCO - 4f, y + 4f, RAIO_DA_POKEBOLA, 1f);
      }
      if (podeEvoluir(pokemon)) {
        setinhaDeEvolucao(tela, x + 2.5f, y + 2.5f);
      }
      if (eOSelecionado) {
        teclaDeSoltar(tela, fonte, x + BLOCO / 2, y + ALTURA_DA_FILEIRA - 4f);
      }
      tela.flush();
    });
    itemNoCanto(tela, pokemon, x, y, BLOCO, ITEM_NO_CANTO);
    naFrente(tela, () -> informacoesNoBloco(tela, fonte, pokemon, x, y));
    if (!eOSelecionado) {
      barrinha(tela, x + 1.5f, y + BLOCO + BARRINHA, BLOCO - 3f, fracaoDaVida(pokemon));
    }
  }

  // 8.3 (dele, 20/10: "tem que aparecer que tá com item (...) igual é no Cobblemon", "no canto inferior direito de cada
  // quadrado"): o item que o Pokémon segura, pequeno, no canto de baixo à direita do quadrado do retrato. A lista do
  // Cobblemon faz o mesmo (PartyOverlay: o heldItem com metade do tamanho). Sem item, nada. Só na fileira do time (dele, em
  // seguida: "pode tirar na verdade dos guardas, deixa só na fileira")
  // 8.5 (dele, 20/10: "aqui também deveria ter mais informação do Pokémon, como o nível, é importante"; escolheu o nível,
  // o status e o gênero): na coluna da esquerda, embaixo da setinha de evoluir, o gênero e, no canto de baixo, o nível
  // ("Nv.30"); o status (QMD, ENV, DRM...), na cor dele, numa plaquinha no meio da borda de cima. A coluna da direita
  // continua com a pokébola (fora da bola) e o item
  private static void informacoesNoBloco(GuiGraphics tela, Font fonte, Pokemon pokemon, float x, float y) {
    if (pokemon.getGender() != Gender.GENDERLESS) {
      Simbolos.genero(tela, x + 1f + RAIO_DO_GENERO_NO_BLOCO, y + ALTURA_DO_GENERO_NO_BLOCO, RAIO_DO_GENERO_NO_BLOCO,
          pokemon.getGender(), 1f);
    }
    String status = status(pokemon);
    float larguraDoStatus = 0;
    Component textoDoStatus = null;
    if (status != null) {
      textoDoStatus = Component.translatable("cobblemon.ui.status." + status);
      larguraDoStatus = fonte.width(textoDoStatus) * LETRA_NO_BLOCO + 3;
      int fundo = CORES_DOS_STATUS.getOrDefault(status, CINZA);
      Formas.retangulo(tela, x + (BLOCO - larguraDoStatus) / 2, y - ALTURA_DA_PLAQUINHA_NO_BLOCO / 2, larguraDoStatus,
          ALTURA_DA_PLAQUINHA_NO_BLOCO, Formas.raios(1.5f), fundo, fundo);
    }
    tela.flush();
    if (textoDoStatus != null) {
      texto(tela, fonte, textoDoStatus, x + (BLOCO - larguraDoStatus) / 2 + 1.5f,
          y - ALTURA_DA_LETRA * LETRA_NO_BLOCO / 2, LETRA_NO_BLOCO, false);
    }
    Component nivel = Component.translatable("cobblemon.ui.lv").append(String.valueOf(pokemon.getLevel()));
    texto(tela, fonte, nivel, x + 1.5f, y + BLOCO - 1.5f - ALTURA_DA_LETRA * LETRA_NO_BLOCO, LETRA_NO_BLOCO, true);
  }

  private static void itemNoCanto(GuiGraphics tela, Pokemon pokemon, float x, float y, float quadrado, float tamanho) {
    ItemStack item = pokemon.heldItem();
    if (item.isEmpty()) {
      return;
    }
    float escala = tamanho / TAMANHO_DE_UM_ITEM;
    tela.pose().pushPose();
    tela.pose().translate(x + quadrado - tamanho - FOLGA_DO_CANTO, y + quadrado - tamanho - FOLGA_DO_CANTO,
        NA_FRENTE_DO_RETRATO);
    tela.pose().scale(escala, escala, 1f);
    tela.renderItem(item, 0, 0);
    tela.pose().popPose();
  }

  // O corpo inteiro com a mesma função das telas do Cobblemon (PokemonGuiUtilsKt.drawProfilePokemon), recortado na
  // caixa. Para outro tamanho de caixa, a escala e a origem crescem na mesma proporção. "tempo" = quanto a animação anda
  private static void corpoInteiro(GuiGraphics tela, Pokemon pokemon, Map<UUID, FloatingState> estados,
      Enquadramento enquadramento, float x, float y, float tamanho, float tempo) {
    FloatingState estado = estados.computeIfAbsent(pokemon.getUuid(), uuid -> new FloatingState());
    estado.setCurrentAspects(pokemon.getAspects());
    float proporcao = tamanho / enquadramento.caixa();
    float cor = pokemon.isFainted() ? COR_DE_QUEM_DESMAIOU : 1f;
    LayoutDaHud.recortar(tela, x, y, tamanho, tamanho);
    tela.pose().pushPose();
    tela.pose().translate(x + tamanho / 2, y + enquadramento.origemY() * proporcao, 0);
    PokemonGuiUtilsKt.drawProfilePokemon(pokemon.asRenderablePokemon(), tela.pose(),
        QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(GIRO_PARA_BAIXO, GIRO_PARA_O_LADO, 0f)),
        PoseType.PROFILE, estado, tempo, enquadramento.escala() * proporcao, true, false, cor, cor, cor, 1f, 0f, 0f);
    tela.pose().popPose();
    tela.disableScissor();
  }

  // "Nv." pequeno e o número normal, numa plaquinha, como o "Lv. 12" do Z-A
  private static float plaquinhaDoNivel(GuiGraphics tela, Font fonte, Pokemon pokemon, float x) {
    Component rotulo = Component.translatable("cobblemon.ui.lv");
    String numero = String.valueOf(pokemon.getLevel());
    float larguraDoRotulo = fonte.width(rotulo) * TEXTO_PEQUENO;
    float largura = 3 + larguraDoRotulo + 2 + fonte.width(numero) + 3;
    Formas.retangulo(tela, x, 0, largura, ALTURA_DA_PLAQUINHA, Formas.raios(2f), PLAQUINHA_DO_NIVEL, PLAQUINHA_DO_NIVEL);
    tela.flush();
    float topoDoNumero = (ALTURA_DA_PLAQUINHA - ALTURA_DA_LETRA) / 2;
    texto(tela, fonte, rotulo, x + 3, topoDoNumero + ALTURA_DA_LETRA * (1 - TEXTO_PEQUENO), TEXTO_PEQUENO, false);
    texto(tela, fonte, Component.literal(numero), x + 3 + larguraDoRotulo + 2, topoDoNumero, 1f, false);
    return x + largura + 3;
  }

  // O ♂ e o ♀ num círculo (Simbolos.genero); sem gênero, a linha continua de onde estava
  private static float genero(GuiGraphics tela, Pokemon pokemon, float x) {
    Gender genero = pokemon.getGender();
    if (genero == Gender.GENDERLESS) {
      return x;
    }
    Simbolos.genero(tela, x + RAIO_DO_GENERO, ALTURA_DA_PLAQUINHA / 2, RAIO_DO_GENERO, genero, 1f);
    tela.flush();
    return x + 2 * RAIO_DO_GENERO + 3;
  }

  // A plaquinha do status, colorida, depois do gênero
  private static void plaquinha(GuiGraphics tela, Font fonte, Component texto, float x, int fundo) {
    float largura = 3 + fonte.width(texto) * TEXTO_PEQUENO + 3;
    Formas.retangulo(tela, x, 0, largura, ALTURA_DA_PLAQUINHA, Formas.raios(2f), fundo, fundo);
    tela.flush();
    texto(tela, fonte, texto, x + 3, (ALTURA_DA_PLAQUINHA - ALTURA_DA_LETRA * TEXTO_PEQUENO) / 2, TEXTO_PEQUENO, false);
  }

  // O nome grande (1,5 vez); se não couber na largura da barra de vida, encolhe sem sair da linha de baixo
  private static void nome(GuiGraphics tela, Font fonte, Pokemon pokemon, float x) {
    Component nome = pokemon.getDisplayName(false);
    float escala = Math.min(NOME_GRANDE, LARGURA_DA_VIDA / Math.max(1, fonte.width(nome)));
    texto(tela, fonte, nome, x, TOPO_DO_NOME + ALTURA_DA_LETRA * (NOME_GRANDE - escala), escala, true);
  }

  // A barra do Z-A: grossa, com uma aba que desce do começo, o fundo escuro, um rastro claro do dano que desce
  // devagar, e os números por cima da ponta
  private static void barraDeVida(GuiGraphics tela, Font fonte, Pokemon pokemon, float x, float tempo) {
    float fracao = fracaoDaVida(pokemon);
    float rastro = rastro(pokemon.getUuid(), fracao, tempo);
    int cor = corDaVida(fracao);
    float[] cantos = Formas.raios(1f);
    float baixo = TOPO_DA_VIDA + ALTURA_DA_VIDA;
    Formas.retangulo(tela, x, TOPO_DA_VIDA, LARGURA_DA_VIDA, ALTURA_DA_VIDA, cantos, VIDA_VAZIA, VIDA_VAZIA);
    Formas.poligono(tela, new float[][] { { x, baixo - 0.01f }, { x + LARGURA_DA_ABA, baixo - 0.01f },
        { x + LARGURA_DA_ABA - ALTURA_DA_ABA, baixo + ALTURA_DA_ABA }, { x, baixo + ALTURA_DA_ABA } },
        fracao > 0 ? cor : VIDA_VAZIA);
    if (rastro > fracao) {
      Formas.retangulo(tela, x, TOPO_DA_VIDA, LARGURA_DA_VIDA * rastro, ALTURA_DA_VIDA, cantos, RASTRO, RASTRO);
    }
    Formas.retangulo(tela, x, TOPO_DA_VIDA, LARGURA_DA_VIDA * fracao, ALTURA_DA_VIDA, cantos,
        Formas.misturar(cor, BRANCO, 0.25f), cor);
    tela.flush();
    String numeros = pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth();
    texto(tela, fonte, Component.literal(numeros), x + LARGURA_DA_VIDA - 2 - fonte.width(numeros), baixo - 4, 1f, true);
  }

  // O rastro alcança a vida de verdade aos poucos (uns 0,4 segundo); se a vida subiu, ele pula junto
  private static float rastro(UUID pokemon, float fracao, float tempo) {
    float anterior = RASTROS.getOrDefault(pokemon, fracao);
    float rastro = anterior <= fracao ? fracao
        : anterior + (fracao - anterior) * Math.min(1f, tempo * VELOCIDADE_DO_RASTRO);
    RASTROS.put(pokemon, rastro);
    return rastro;
  }

  private static void barrinha(GuiGraphics tela, float x, float y, float largura, float fracao) {
    float[] cantos = Formas.raios(BARRINHA / 2);
    Formas.retangulo(tela, x, y, largura, BARRINHA, cantos, FUNDO_DA_BARRINHA, FUNDO_DA_BARRINHA);
    Formas.retangulo(tela, x, y, largura * fracao, BARRINHA, cantos, corDaVida(fracao), corDaVida(fracao));
    tela.flush();
  }

  // Triângulo ciano com brilho: três cópias maiores e bem transparentes atrás, que pulsam devagar.
  // "direcao" 1 = a ponta está em x e o triângulo abre para a direita (a seta da esquerda); -1 = o contrário
  private static void seta(GuiGraphics tela, float ponta, float meio, int direcao) {
    float base = ponta + direcao * LARGURA_DA_SETA;
    float[][] triangulo = { { ponta, meio }, { base, meio - ALTURA_DA_SETA / 2 }, { base, meio + ALTURA_DA_SETA / 2 } };
    float centro = (ponta + 2 * base) / 3;
    float pulso = 0.7f + 0.3f * (float) Math.sin(Util.getMillis() * 2 * Math.PI / PERIODO_DO_BRILHO);
    for (int camada = BRILHO_POR_CAMADA.length; camada >= 1; camada--) {
      int alfa = Math.round(255 * pulso * BRILHO_POR_CAMADA[camada - 1]);
      Formas.poligono(tela, ampliar(triangulo, centro, meio, 1 + 0.32f * camada), (alfa << 24) | COR_DO_BRILHO);
    }
    Formas.poligono(tela, triangulo, SETA);
    tela.flush();
  }

  private static float[][] ampliar(float[][] pontos, float cx, float cy, float fator) {
    float[][] maiores = new float[pontos.length][];
    for (int i = 0; i < pontos.length; i++) {
      maiores[i] = new float[] { cx + (pontos[i][0] - cx) * fator, cy + (pontos[i][1] - cy) * fator };
    }
    return maiores;
  }

  // No lugar do ▾ do Z-A, a tecla que solta o Pokémon (a do Cobblemon, PartySendBinding: "R" se você não trocou), escura
  // numa plaquinha clara. Um nome de tecla comprido encolhe para caber no bloco
  private static void teclaDeSoltar(GuiGraphics tela, Font fonte, float cx, float cy) {
    Component tecla = PartySendBinding.INSTANCE.getTranslatedKeyMessage();
    float escala = Math.min(TEXTO_PEQUENO, (BLOCO - 6) / Math.max(1, fonte.width(tecla)));
    float larguraDoTexto = fonte.width(tecla) * escala;
    float largura = Math.max(ALTURA_DA_TECLA, larguraDoTexto + 3);
    Formas.retangulo(tela, cx - largura / 2, cy - ALTURA_DA_TECLA / 2, largura, ALTURA_DA_TECLA,
        Formas.raios(ALTURA_DA_TECLA / 2), FUNDO_DA_TECLA, FUNDO_DA_TECLA);
    tela.flush();
    tela.pose().pushPose();
    tela.pose().translate(cx - larguraDoTexto / 2, cy - ALTURA_DA_LETRA * escala / 2, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.drawString(fonte, tecla, 0, 0, TEXTO_DA_TECLA, false);
    tela.pose().popPose();
  }

  // Um triângulo verde para cima, com contorno escuro: "pode evoluir"
  private static void setinhaDeEvolucao(GuiGraphics tela, float x, float y) {
    Formas.poligono(tela, new float[][] { { x + 2.5f, y - 0.9f }, { x + 5.9f, y + 3.9f }, { x - 0.9f, y + 3.9f } },
        CONTORNO_DA_EVOLUCAO);
    Formas.poligono(tela, new float[][] { { x + 2.5f, y }, { x + 5f, y + 3.3f }, { x, y + 3.3f } }, VERDE_DA_EVOLUCAO);
  }

  private static void naFrente(GuiGraphics tela, Runnable desenho) {
    tela.pose().pushPose();
    tela.pose().translate(0, 0, NA_FRENTE_DO_RETRATO);
    desenho.run();
    tela.pose().popPose();
  }

  private static void texto(GuiGraphics tela, Font fonte, Component texto, float x, float y, float escala,
      boolean sombra) {
    tela.pose().pushPose();
    tela.pose().translate(x, y, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.drawString(fonte, texto, 0, 0, BRANCO, sombra);
    tela.pose().popPose();
  }

  // A evolução que o servidor liberou chega ao cliente na lista "client()" (é dela que sai o botão Evoluir do resumo)
  private static boolean podeEvoluir(Pokemon pokemon) {
    return pokemon.getEvolutionProxy().isClient() && !pokemon.getEvolutionProxy().client().isEmpty();
  }

  // Quem desmaiou mostra "DSM"; senão, o status persistente (QMD, PAR, ENV, TOX, DRM, CGL), ou nada
  private static String status(Pokemon pokemon) {
    if (pokemon.isFainted()) {
      return "fnt";
    }
    PersistentStatusContainer status = pokemon.getStatus();
    return status == null ? null : status.getStatus().getShowdownName();
  }

  private static float fracaoDaVida(Pokemon pokemon) {
    return Math.clamp((float) pokemon.getCurrentHealth() / Math.max(1, pokemon.getMaxHealth()), 0f, 1f);
  }

  // As cores da barra de vida do Z-A: verde, amarela abaixo da metade, vermelha abaixo de 20%
  private static int corDaVida(float fracao) {
    if (fracao > 0.5f) {
      return 0xFF39BE69;
    }
    if (fracao > 0.2f) {
      return 0xFFE8C030;
    }
    return 0xFFE04848;
  }
}
