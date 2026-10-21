package dev.victor.combatza.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.victor.combatza.rede.AvisoDaBatalhaPayload;
import dev.victor.combatza.rede.HistoricoPayload;
import dev.victor.combatza.rede.PainelDaBatalhaPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// A HUD do Z-A, parte 4: o histórico da batalha, em cima, do lado SEM o minimapa do Xaero (na direita, como no Z-A, se
// ele estiver à esquerda ou não existir). Cada acontecimento vira um cartão com o rosto do Pokémon de quem a frase fala:
// vermelho se é selvagem (ou monstro), azul se é seu. No máximo 3 na tela: o mais velho sai quando chega o quarto, e cada
// um some sozinho depois de uns segundos. O cartão novo entra deslizando da borda. Acima dos cartões, o painel de quem
// está na batalha, a batalha inteira, com o resultado no fim (6.9b)
public class HistoricoDaBatalha {
  private record Cartao(Component texto, ResourceLocation especie, float escalaDaForma, boolean doSelvagem, long chegou,
      FloatingState estado) {
  }

  // 7.8: a fileirinha dos guardas (FileiraDosGuardas) desenha os rostos com este mesmo retrato
  record Rosto(ResourceLocation especie, float escalaDaForma, FloatingState estado) {
  }

  // "ganhos" (8.2b): uma linha por Pokémon, com o XP que ele ganhou na batalha
  private record Aviso(Component titulo, Component texto, int cor, List<Rosto> rostos, List<Component> ganhos,
      long chegou) {
  }

  private record FotoNoPainel(Rosto rosto, float vida, boolean grande, int entidade, UUID pokemon) {
  }

  private record LadoNoPainel(UUID jogador, String nome, List<FotoNoPainel> fotos) {
  }

  // 21/10 (PvP): "adversarios" = no duelo, o outro jogador (com o lutador dele), do lado direito no lugar dos selvagens
  private record Painel(List<LadoNoPainel> jogadores, List<FotoNoPainel> selvagens, List<LadoNoPainel> adversarios) {
  }

  // Uma foto já posicionada na tela (o painel desenha em três passadas: fundos, retratos e textos)
  private record Colocada(FotoNoPainel foto, float x, float y, float tamanho, boolean nossa) {
  }

  private static final int MAXIMO = 3;
  private static final long DURACAO = 4000;
  private static final long SUMINDO = 400;
  private static final long ENTRANDO = 220;

  // Medidas: os cartões encostam na borda, a partir de 22% da altura (acima do losango dos golpes)
  private static final float TOPO = 0.22f;
  private static final float ALTURA = 22f;
  private static final float ESPACO = 3f;
  private static final float ROSTO = 20f;
  private static final float TEXTO_DEPOIS_DO_ROSTO = 26f;
  private static final float SEM_ROSTO = 8f;
  private static final float FOLGA_DIREITA = 10f;
  private static final float LARGURA_MINIMA = 150f;
  private static final float LARGURA_MAXIMA = 240f;
  private static final float MENOR_LETRA = 0.7f;
  private static final float ALTURA_DA_LETRA = 7f;
  private static final float[] CANTOS_NA_DIREITA = Formas.raios(4f, 0f, 0f, 4f);
  private static final float[] CANTOS_NA_ESQUERDA = Formas.raios(0f, 4f, 4f, 0f);
  // O Xaero guarda onde está o minimapa em config/xaerohud.txt; a gente só LÊ, uma vez por segundo se o arquivo mudou
  private static final String MOD_DO_MINIMAPA = "xaerominimap";
  private static final String LINHA_DO_MINIMAPA = "id=xaerominimap:minimap";
  private static final long CONFERIR_O_MINIMAPA_A_CADA = 1000;
  // O rosto, com a conta do retrato do Cobblemon (caixa de 21, escala 13, 9,5 para a direita e 12 para cima)
  private static final float CAIXA_DO_COBBLEMON = 21f;
  private static final float ESCALA_DO_COBBLEMON = 13f;
  private static final float RETRATO_X_DO_COBBLEMON = 9.5f;
  private static final float RETRATO_Y_DO_COBBLEMON = -12f;
  // 7.9 (dele: "os rostinhos dos bichos não estão centralizados, só aparece a testa"): o drawPosablePortrait desce o desenho
  // 30 pixels FIXOS antes da escala (javap: translate(0, 30, 0)). Na caixa de 21 a conta acima já desconta; numa caixa
  // menor, os 30 não encolhem e o bicho desce demais. Agora a descida encolhe junto com a caixa
  private static final float DESCIDA_FIXA_DO_COBBLEMON = 30f;

  private static final int BRANCO = 0xFFFFFFFF;
  // O fundo dos cartões é 60% opaco (era 90%: atrapalhava a visão, ainda mais em tela pequena); o texto e o rosto ficam sólidos
  private static final int SELVAGEM_DE_CIMA = 0x99C24A72;
  private static final int SELVAGEM_DE_BAIXO = 0x99A63A5E;
  private static final int SEU_DE_CIMA = 0x994A90CC;
  private static final int SEU_DE_BAIXO = 0x993A74AE;
  private static final int BRILHO_DE_CIMA = 0x30FFFFFF;
  private static final int FUNDO_DO_ROSTO = 0x26FFFFFF;

  // O aviso da batalha: maior que um cartão, escuro, com uma faixa colorida em cima (dourada no começo; no fim, verde se
  // você venceu, vermelha se desmaiou, cinza se alguém fugiu)
  private static final long DURACAO_DO_AVISO = 5000;
  private static final float ALTURA_DO_AVISO = 28f;
  private static final float ACIMA_DOS_CARTOES = 4f;
  static final float MARGEM_DE_CIMA = 2f;
  private static final float ESPACO_ENTRE_ROSTOS = 2f;
  private static final float LARGURA_MAXIMA_DO_AVISO = 260f;
  private static final float LETRA_DO_TEXTO = 0.8f;
  private static final float FAIXA_DO_AVISO = 1.5f;
  private static final int AVISO_DE_CIMA = 0xB81A2230;
  private static final int AVISO_DE_BAIXO = 0xB8101620;
  private static final int TEXTO_DO_AVISO = 0xFFD8DCE6;
  private static final int COMECOU = 0xFFE8B84A;
  private static final int VENCEU = 0xFF5AD07A;
  private static final int PERDEU = 0xFFFF5A5A;
  private static final int ACABOU = 0xFFB4B4C4;
  // 8.2b: as linhas do XP, embaixo da frase, em letra pequena; com elas, o aviso fica mais tempo (dá para ler)
  private static final long DURACAO_COM_XP = 8000;
  private static final float LETRA_DO_XP = 0.75f;
  private static final float LINHA_DO_XP = ALTURA_DA_LETRA * LETRA_DO_XP + 2f;
  private static final float ANTES_DO_XP = 3f;

  // O painel de quem está na batalha: o rosto da skin e o nome em cima, as fotos embaixo (o lutador e os selvagens
  // grandes, os de guarda pequenos), cada uma com a barrinha de vida; VS no meio
  private static final float BORDA_DO_PAINEL = 5f;
  private static final float CABECA = 9f;
  private static final float FOTO_GRANDE = 22f;
  private static final float FOTO_PEQUENA = 15f;
  private static final float ESPACO_DAS_FOTOS = 3f;
  private static final float ENTRE_JOGADORES = 8f;
  private static final float ESPACO_DO_VS = 8f;
  private static final float BARRA_DA_FOTO = 2f;
  private static final float LETRA_PEQUENA = 0.75f;
  private static final float LETRA_DO_VS = 1.25f;
  private static final float LETRA_DO_RESULTADO = 0.9f;
  private static final float LARGURA_MAXIMA_DO_PAINEL = 300f;
  // O layout da HUD: a caixa tem a altura de um painel comum (com o resultado) mais os 3 cartões
  private static final float ALTURA_DE_UM_PAINEL = 60f;
  private static final float DESMAIADO = 0.35f;
  private static final int FUNDO_DA_NOSSA_FOTO = 0x404A90CC;
  private static final int FUNDO_DA_FOTO_SELVAGEM = 0x40C24A72;
  private static final int FUNDO_DA_BARRA = 0x99101A1A;
  private static final int VIDA_CHEIA = 0xFF5AD07A;
  private static final int VIDA_MEIA = 0xFFE8C04A;
  private static final int VIDA_POUCA = 0xFFFF5A5A;
  private static final int ROTULO = 0xFFB4B8C4;

  private static final Deque<Cartao> CARTOES = new ArrayDeque<>();
  private static Aviso aviso = null;
  private static Painel painel = null;
  private static long painelApareceu = 0;
  // O servidor mandou o painel vazio (a batalha acabou para você): ele fica só até o resultado sumir
  private static boolean painelAcabou = false;
  private static final Map<String, FloatingState> ESTADOS_DO_PAINEL = new HashMap<>();
  // 6.9e: a última vida vista na entidade de cada foto (pelo UUID do Pokémon), para quando ela some do seu jogo
  private static final Map<UUID, Float> VIDA_VISTA = new HashMap<>();
  private static boolean minimapaADireita = false;
  private static long minimapaLidoEm = -1;
  private static long minimapaConferidoEm = 0;

  private HistoricoDaBatalha() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(HistoricoPayload.TIPO, (pacote, contexto) -> chegou(pacote));
    ClientPlayNetworking.registerGlobalReceiver(AvisoDaBatalhaPayload.TIPO, (pacote, contexto) -> chegouAviso(pacote));
    ClientPlayNetworking.registerGlobalReceiver(PainelDaBatalhaPayload.TIPO, (pacote, contexto) -> chegouPainel(pacote));
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      CARTOES.clear();
      aviso = null;
      esquecerOPainel();
    });
    HudRenderCallback.EVENT.register(HistoricoDaBatalha::desenhar);
  }

  // A espécie chega como texto ("cobblemon:rattata"). O tamanho da forma (baseScale) entra no retrato, como na lista do
  // Cobblemon
  private static void chegou(HistoricoPayload pacote) {
    ResourceLocation especie = pacote.especie().isEmpty() ? null : ResourceLocation.tryParse(pacote.especie());
    Species dados = especie == null ? null : PokemonSpecies.getByIdentifier(especie);
    Set<String> aspectos = Set.copyOf(pacote.aspectos());
    FloatingState estado = new FloatingState();
    estado.setCurrentAspects(aspectos);
    float escalaDaForma = dados == null ? 1f : dados.getForm(aspectos).getBaseScale();
    CARTOES.addLast(new Cartao(pacote.texto(), dados == null ? null : especie, escalaDaForma, pacote.doSelvagem(),
        Util.getMillis(), estado));
    while (CARTOES.size() > MAXIMO) {
      CARTOES.removeFirst();
    }
  }

  // O resultado da batalha (o aviso de fim). Com o painel na tela, vira a última linha dele; sem painel, um aviso sozinho
  private static void chegouAviso(AvisoDaBatalhaPayload pacote) {
    List<Rosto> rostos = new ArrayList<>();
    for (AvisoDaBatalhaPayload.Rosto rosto : pacote.rostos()) {
      Rosto pronto = rosto(rosto.especie(), rosto.aspectos(), new FloatingState());
      if (pronto != null) {
        rostos.add(pronto);
      }
    }
    int cor = switch (pacote.tipo()) {
      case "comecou" -> COMECOU;
      case "venceu" -> VENCEU;
      case "perdeu" -> PERDEU;
      default -> ACABOU;
    };
    // 8.2b: um aviso novo depois do fim da batalha (o XP de uma captura sem batalha) já não é dela: o painel velho sai
    if (painelAcabou) {
      esquecerOPainel();
    }
    aviso = new Aviso(pacote.titulo(), pacote.texto(), cor, rostos, List.copyOf(pacote.ganhos()), Util.getMillis());
  }

  private static void esquecerOPainel() {
    painel = null;
    painelAcabou = false;
    ESTADOS_DO_PAINEL.clear();
    VIDA_VISTA.clear();
  }

  // 8.2b: com as linhas do XP, o aviso fica mais tempo na tela
  private static long duracao(Aviso aviso) {
    return aviso.ganhos().isEmpty() ? DURACAO_DO_AVISO : DURACAO_COM_XP;
  }

  // A linha mais larga do XP, já na letra pequena
  private static float larguraDosGanhos(Font fonte, Aviso aviso) {
    float largura = 0;
    for (Component linha : aviso.ganhos()) {
      largura = Math.max(largura, fonte.width(linha) * LETRA_DO_XP);
    }
    return largura;
  }

  private static float alturaDosGanhos(Aviso aviso) {
    return aviso.ganhos().isEmpty() ? 0 : ANTES_DO_XP + aviso.ganhos().size() * LINHA_DO_XP;
  }

  // O painel novo troca o antigo. Vazio = a batalha acabou para você: se o resultado está na tela, o painel fica até ele
  // sumir. Um painel cheio depois de um vazio é uma batalha nova: ele entra deslizando de novo
  // 5.5b: você está numa batalha agora? O painel chega cheio durante a batalha e vazio quando ela acaba para você (os 5
  // segundos do resultado já não contam). Os botões de trocar golpe do resumo do Pokémon usam (BotoesDosGolpesMixin)
  public static boolean emBatalha() {
    return painel != null && !painelAcabou;
  }

  // 21/10 (PvP): você está num duelo agora (o painel tem um adversário do outro lado)
  public static boolean emDuelo() {
    return emBatalha() && !painel.adversarios().isEmpty();
  }

  // 21/10 (PvP): o dono do Pokémon é o seu adversário no duelo de agora (Foco.ehAlvo: o foco, a placa e a prévia)
  public static boolean ehDoAdversario(UUID dono) {
    if (dono == null || !emDuelo()) {
      return false;
    }
    for (LadoNoPainel lado : painel.adversarios()) {
      if (dono.equals(lado.jogador())) {
        return true;
      }
    }
    return false;
  }

  private static void chegouPainel(PainelDaBatalhaPayload pacote) {
    if (pacote.jogadores().isEmpty()) {
      if (aviso == null) {
        esquecerOPainel();
      } else {
        painelAcabou = true;
      }
      return;
    }
    if (painel == null || painelAcabou) {
      painelApareceu = Util.getMillis();
      painelAcabou = false;
      aviso = null;
    }
    painel = new Painel(lados(pacote.jogadores()), fotos(pacote.selvagens()), lados(pacote.adversarios()));
  }

  private static List<LadoNoPainel> lados(List<PainelDaBatalhaPayload.Lado> lados) {
    return lados.stream().map(lado -> new LadoNoPainel(lado.jogador(), lado.nome(), fotos(lado.pokemons()))).toList();
  }

  private static List<FotoNoPainel> fotos(List<PainelDaBatalhaPayload.Foto> fotos) {
    List<FotoNoPainel> prontas = new ArrayList<>();
    for (PainelDaBatalhaPayload.Foto foto : fotos) {
      String chave = foto.especie() + foto.aspectos();
      Rosto rosto = rosto(foto.especie(), foto.aspectos(), ESTADOS_DO_PAINEL.computeIfAbsent(chave,
          qualquer -> new FloatingState()));
      if (rosto != null) {
        prontas.add(new FotoNoPainel(rosto, foto.vida(), foto.grande(), foto.entidade(), foto.pokemon()));
      }
    }
    return prontas;
  }

  // A espécie chega como texto ("cobblemon:rattata"); o tamanho da forma (baseScale) entra no retrato
  private static Rosto rosto(String especieComoTexto, List<String> aspectosDaLista, FloatingState estado) {
    ResourceLocation especie = ResourceLocation.tryParse(especieComoTexto);
    Species dados = especie == null ? null : PokemonSpecies.getByIdentifier(especie);
    if (dados == null) {
      return null;
    }
    Set<String> aspectos = Set.copyOf(aspectosDaLista);
    estado.setCurrentAspects(aspectos);
    return new Rosto(especie, dados.getForm(aspectos).getBaseScale(), estado);
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    Minecraft cliente = Minecraft.getInstance();
    long agora = Util.getMillis();
    CARTOES.removeIf(cartao -> agora - cartao.chegou() > DURACAO + SUMINDO);
    if (aviso != null && agora - aviso.chegou() > duracao(aviso) + SUMINDO) {
      aviso = null;
      if (painelAcabou) {
        esquecerOPainel();
      }
    }
    if (cliente.options.hideGui || cliente.getDebugOverlay().showDebugScreen() || cliente.player == null) {
      return;
    }
    int largura = tela.guiWidth();
    int altura = tela.guiHeight();
    // 7.9: a fileirinha dos guardas fica no alto, do mesmo lado; o painel da batalha começa embaixo dela. No layout da HUD
    // (LayoutDaHud), cada um pode ter o seu lugar e o seu tamanho
    LayoutDaHud.comecar(tela, LayoutDaHud.Bloco.GUARDAS);
    float fundoDosGuardas = FileiraDosGuardas.desenhar(tela, cliente,
        LayoutDaHud.naEsquerda(LayoutDaHud.Bloco.GUARDAS, largura, altura), MARGEM_DE_CIMA);
    LayoutDaHud.terminar(tela);
    if (CARTOES.isEmpty() && aviso == null && painel == null) {
      return;
    }
    boolean naEsquerda = LayoutDaHud.naEsquerda(LayoutDaHud.Bloco.HISTORICO, largura, altura);
    float topoMinimo = topoMinimo(largura, altura, naEsquerda, fundoDosGuardas);
    LayoutDaHud.comecar(tela, LayoutDaHud.Bloco.HISTORICO);
    float y = altura * TOPO;
    if (painel != null) {
      float fundoDoPainel = desenharPainel(tela, cliente, painel, y, agora, naEsquerda, topoMinimo);
      y = Math.max(y, fundoDoPainel + ACIMA_DOS_CARTOES);
    } else if (aviso != null) {
      // 8.2b: com as linhas do XP o aviso cresce; ele também começa embaixo da fileirinha, e os cartões embaixo dele
      float fundoDoAviso = desenharAviso(tela, cliente.font, aviso, y, agora, naEsquerda, topoMinimo);
      y = Math.max(y, fundoDoAviso + ACIMA_DOS_CARTOES);
    }
    for (Cartao cartao : CARTOES) {
      long idade = agora - cartao.chegou();
      float entrando = suave(Math.min(1f, (float) idade / ENTRANDO));
      float alfa = idade > DURACAO ? 1f - (float) (idade - DURACAO) / SUMINDO : 1f;
      desenharCartao(tela, cliente.font, cartao, y, entrando, Math.clamp(alfa, 0f, 1f), naEsquerda);
      y += ALTURA + ESPACO;
    }
    LayoutDaHud.terminar(tela);
  }

  // Até onde o painel da batalha pode subir: com o histórico num lugar seu (layout), até o topo da caixa dele; os dois no
  // lugar de sempre, até embaixo dos guardas; só os guardas noutro lugar, até o alto da tela
  private static float topoMinimo(int largura, int altura, boolean naEsquerda, float fundoDosGuardas) {
    if (LayoutDaHud.ajustado(LayoutDaHud.Bloco.HISTORICO)) {
      return caixa(largura, altura, naEsquerda).y();
    }
    return LayoutDaHud.ajustado(LayoutDaHud.Bloco.GUARDAS) ? MARGEM_DE_CIMA : fundoDosGuardas;
  }

  // O layout da HUD (LayoutDaHud): a caixa do bloco, encostada na borda do lado dele, da largura do maior painel; de um
  // painel comum acima dos cartões até o fim do terceiro cartão
  static LayoutDaHud.Caixa caixa(int largura, int altura, boolean naEsquerda) {
    float cartoes = altura * TOPO;
    float topo = Math.max(MARGEM_DE_CIMA, cartoes - ALTURA_DE_UM_PAINEL - ACIMA_DOS_CARTOES);
    float baixo = cartoes + MAXIMO * (ALTURA + ESPACO) - ESPACO;
    return new LayoutDaHud.Caixa(naEsquerda ? 0 : largura - LARGURA_MAXIMA_DO_PAINEL, topo, LARGURA_MAXIMA_DO_PAINEL,
        baixo - topo);
  }

  // O minimapa do Xaero está em cima à direita? ("fromRight=true" e não "fromBottom=true" na linha dele). O jogador pode
  // arrastar o minimapa no editor do Xaero: por isso o arquivo é lido de novo quando a data dele muda. Sem o Xaero, não
  private static boolean minimapaADireita(long agora) {
    if (agora - minimapaConferidoEm < CONFERIR_O_MINIMAPA_A_CADA) {
      return minimapaADireita;
    }
    minimapaConferidoEm = agora;
    if (!FabricLoader.getInstance().isModLoaded(MOD_DO_MINIMAPA)) {
      minimapaADireita = false;
      return false;
    }
    Path arquivo = FabricLoader.getInstance().getConfigDir().resolve("xaerohud.txt");
    try {
      long modificado = Files.getLastModifiedTime(arquivo).toMillis();
      if (modificado != minimapaLidoEm) {
        minimapaLidoEm = modificado;
        minimapaADireita = Files.readAllLines(arquivo).stream()
            .filter(linha -> linha.contains(LINHA_DO_MINIMAPA))
            .anyMatch(linha -> linha.contains("fromRight=true") && !linha.contains("fromBottom=true"));
      }
    } catch (IOException erro) {
      // Sem o arquivo, o Xaero usa o lugar padrão dele, em cima à direita
      minimapaADireita = true;
    }
    return minimapaADireita;
  }

  // Encosta na borda (direita, ou esquerda se o minimapa está à direita); "entrando" vai de 0 (ainda fora da tela) a 1
  // (no lugar): o cartão desliza da borda para dentro
  private static void desenharCartao(GuiGraphics tela, Font fonte, Cartao cartao, float y, float entrando, float alfa,
      boolean naEsquerda) {
    float inicioDoTexto = cartao.especie() != null ? TEXTO_DEPOIS_DO_ROSTO : SEM_ROSTO;
    float larguraDoTexto = (float) fonte.width(cartao.texto());
    float largura = Math.clamp(inicioDoTexto + larguraDoTexto + FOLGA_DIREITA, LARGURA_MINIMA, LARGURA_MAXIMA);
    float escala = Math.clamp((largura - inicioDoTexto - FOLGA_DIREITA) / Math.max(1f, larguraDoTexto), MENOR_LETRA, 1f);
    float x = naEsquerda ? -largura * (1f - entrando) : tela.guiWidth() - largura * entrando;
    int cima = Formas.comAlfa(cartao.doSelvagem() ? SELVAGEM_DE_CIMA : SEU_DE_CIMA, alfa);
    int baixo = Formas.comAlfa(cartao.doSelvagem() ? SELVAGEM_DE_BAIXO : SEU_DE_BAIXO, alfa);
    Formas.retangulo(tela, x, y, largura, ALTURA, naEsquerda ? CANTOS_NA_ESQUERDA : CANTOS_NA_DIREITA, cima, baixo);
    Formas.retangulo(tela, naEsquerda ? x : x + 2, y + 0.5f, largura - 2, 0.75f, Formas.raios(0f),
        Formas.comAlfa(BRILHO_DE_CIMA, alfa), Formas.comAlfa(BRILHO_DE_CIMA, alfa));
    if (cartao.especie() != null) {
      Formas.retangulo(tela, x + 1, y + 1, ROSTO, ROSTO, Formas.raios(3f), Formas.comAlfa(FUNDO_DO_ROSTO, alfa),
          Formas.comAlfa(FUNDO_DO_ROSTO, alfa));
    }
    tela.flush();
    if (cartao.especie() != null) {
      rosto(tela, cartao, x + 1, y + 1, alfa);
    }
    if (alfa > 0.03f) {
      tela.pose().pushPose();
      tela.pose().translate(x + inicioDoTexto, y + (ALTURA - ALTURA_DA_LETRA * escala) / 2, 0);
      tela.pose().scale(escala, escala, 1f);
      tela.drawString(fonte, cartao.texto(), 0, 0, Formas.comAlfa(BRANCO, alfa), true);
      tela.pose().popPose();
    }
  }

  // O aviso da batalha, colado na mesma borda dos cartões e logo acima deles: os rostos dos selvagens, o título e a frase
  // (e, embaixo, as linhas do XP). Entra deslizando e some como os cartões. Devolve onde ele termina
  private static float desenharAviso(GuiGraphics tela, Font fonte, Aviso aviso, float yDosCartoes, long agora,
      boolean naEsquerda, float topoMinimo) {
    long idade = agora - aviso.chegou();
    float entrando = suave(Math.min(1f, (float) idade / ENTRANDO));
    long duracao = duracao(aviso);
    float alfa = Math.clamp(idade > duracao ? 1f - (float) (idade - duracao) / SUMINDO : 1f, 0f, 1f);
    int rostos = aviso.rostos().size();
    float inicioDoTexto = rostos == 0 ? SEM_ROSTO : 1 + rostos * (ROSTO + ESPACO_ENTRE_ROSTOS) + 4;
    float larguraDoTexto = Math.max(Math.max((float) fonte.width(aviso.titulo()), fonte.width(aviso.texto()) * LETRA_DO_TEXTO),
        larguraDosGanhos(fonte, aviso));
    float largura = Math.clamp(inicioDoTexto + larguraDoTexto + FOLGA_DIREITA, LARGURA_MINIMA, LARGURA_MAXIMA_DO_AVISO);
    float escala = Math.clamp((largura - inicioDoTexto - FOLGA_DIREITA) / Math.max(1f, larguraDoTexto), MENOR_LETRA, 1f);
    float altura = ALTURA_DO_AVISO + alturaDosGanhos(aviso) * escala;
    float y = Math.max(topoMinimo, yDosCartoes - altura - ACIMA_DOS_CARTOES);
    float x = naEsquerda ? -largura * (1f - entrando) : tela.guiWidth() - largura * entrando;
    Formas.retangulo(tela, x, y, largura, altura, naEsquerda ? CANTOS_NA_ESQUERDA : CANTOS_NA_DIREITA,
        Formas.comAlfa(AVISO_DE_CIMA, alfa), Formas.comAlfa(AVISO_DE_BAIXO, alfa));
    Formas.retangulo(tela, naEsquerda ? x : x + 2, y, largura - 2, FAIXA_DO_AVISO, Formas.raios(0f),
        Formas.comAlfa(aviso.cor(), alfa), Formas.comAlfa(aviso.cor(), alfa));
    float yDoRosto = y + (ALTURA_DO_AVISO - ROSTO) / 2 + FAIXA_DO_AVISO / 2;
    for (int i = 0; i < rostos; i++) {
      Formas.retangulo(tela, x + 1 + i * (ROSTO + ESPACO_ENTRE_ROSTOS), yDoRosto, ROSTO, ROSTO, Formas.raios(3f),
          Formas.comAlfa(FUNDO_DO_ROSTO, alfa), Formas.comAlfa(FUNDO_DO_ROSTO, alfa));
    }
    tela.flush();
    for (int i = 0; i < rostos; i++) {
      Rosto rosto = aviso.rostos().get(i);
      rosto(tela, rosto, x + 1 + i * (ROSTO + ESPACO_ENTRE_ROSTOS), yDoRosto, ROSTO, 1f, alfa);
    }
    if (alfa > 0.03f) {
      tela.pose().pushPose();
      tela.pose().translate(x + inicioDoTexto, y + 5, 0);
      tela.pose().scale(escala, escala, 1f);
      tela.drawString(fonte, aviso.titulo(), 0, 0, Formas.comAlfa(aviso.cor(), alfa), true);
      tela.pose().translate(0, ALTURA_DA_LETRA + 4, 0);
      tela.pose().scale(LETRA_DO_TEXTO, LETRA_DO_TEXTO, 1f);
      tela.drawString(fonte, aviso.texto(), 0, 0, Formas.comAlfa(TEXTO_DO_AVISO, alfa), true);
      tela.pose().popPose();
      // As linhas do XP começam logo embaixo da frase (o título, 4 de espaço, a frase em letra 0,8)
      float yDaLinha = y + 5 + (ALTURA_DA_LETRA + 4 + ALTURA_DA_LETRA * LETRA_DO_TEXTO + ANTES_DO_XP) * escala;
      for (Component linha : aviso.ganhos()) {
        escrever(tela, fonte, linha, x + inicioDoTexto, yDaLinha, LETRA_DO_XP * escala, Formas.comAlfa(TEXTO_DO_AVISO, alfa));
        yDaLinha += LINHA_DO_XP * escala;
      }
    }
    return y + altura;
  }

  // O painel de quem está na batalha, colado na mesma borda dos cartões, logo acima deles. Devolve onde ele termina (os
  // cartões começam embaixo). Largo demais (muita gente), tudo encolhe junto: "k" multiplica todas as medidas
  private static float desenharPainel(GuiGraphics tela, Minecraft cliente, Painel painel, float yDosCartoes, long agora,
      boolean naEsquerda, float topoMinimo) {
    Font fonte = cliente.font;
    Component vs = Component.literal("VS").withStyle(ChatFormatting.BOLD);
    Component rotuloDosSelvagens = Component.translatable("cobblemon_combat_za.batalha.selvagens");
    Component resultado = aviso != null ? aviso.texto() : null;
    float[] larguras = new float[painel.jogadores().size()];
    float esquerda = medirJogadores(painel.jogadores(), fonte, larguras);
    // 21/10 (PvP): no duelo, a direita é do adversário, desenhado como os jogadores (o rosto, o nome e o lutador)
    boolean duelo = !painel.adversarios().isEmpty();
    float[] largurasDoOutroLado = new float[painel.adversarios().size()];
    float direita = duelo ? medirJogadores(painel.adversarios(), fonte, largurasDoOutroLado)
        : Math.max(larguraDasFotos(painel.selvagens()), fonte.width(rotuloDosSelvagens) * LETRA_PEQUENA);
    float larguraDoVs = fonte.width(vs) * LETRA_DO_VS + 2 * ESPACO_DO_VS;
    float largura = BORDA_DO_PAINEL + esquerda + larguraDoVs + direita + BORDA_DO_PAINEL;
    if (resultado != null) {
      largura = Math.max(largura, 2 * BORDA_DO_PAINEL + fonte.width(resultado) * LETRA_DO_RESULTADO);
      largura = Math.max(largura, 2 * BORDA_DO_PAINEL + larguraDosGanhos(fonte, aviso));
    }
    float altura = BORDA_DO_PAINEL + FAIXA_DO_AVISO + CABECA + 3 + FOTO_GRANDE + 1.5f + BARRA_DA_FOTO
        + (resultado != null ? 4 + ALTURA_DA_LETRA * LETRA_DO_RESULTADO + alturaDosGanhos(aviso) : 0) + BORDA_DO_PAINEL;
    float k = Math.min(1f, LARGURA_MAXIMA_DO_PAINEL / largura);
    float w = largura * k;
    float h = altura * k;
    float y = Math.max(topoMinimo, yDosCartoes - h - ACIMA_DOS_CARTOES);
    float entrando = suave(Math.min(1f, (float) (agora - painelApareceu) / ENTRANDO));
    float alfa = alfaDoPainel(agora);
    float x = naEsquerda ? -w * (1f - entrando) : tela.guiWidth() - w * entrando;
    int cor = aviso != null ? aviso.cor() : COMECOU;

    float yDosNomes = y + (FAIXA_DO_AVISO + BORDA_DO_PAINEL) * k;
    float yDasFotos = yDosNomes + (CABECA + 3) * k;
    List<Colocada> colocadas = new ArrayList<>();
    float cursor = x + BORDA_DO_PAINEL * k;
    float[] xDosJogadores = new float[larguras.length];
    for (int i = 0; i < larguras.length; i++) {
      xDosJogadores[i] = cursor;
      colocar(colocadas, painel.jogadores().get(i).fotos(), cursor, yDasFotos, k, true);
      cursor += (larguras[i] + ENTRE_JOGADORES) * k;
    }
    float xDoVs = x + (BORDA_DO_PAINEL + esquerda) * k;
    float xDosSelvagens = xDoVs + larguraDoVs * k;
    colocar(colocadas, painel.selvagens(), xDosSelvagens, yDasFotos, k, false);
    float[] xDosAdversarios = new float[largurasDoOutroLado.length];
    cursor = xDosSelvagens;
    for (int i = 0; i < largurasDoOutroLado.length; i++) {
      xDosAdversarios[i] = cursor;
      colocar(colocadas, painel.adversarios().get(i).fotos(), cursor, yDasFotos, k, false);
      cursor += (largurasDoOutroLado[i] + ENTRE_JOGADORES) * k;
    }

    // 1) o fundo, a faixa de cima, as caixinhas das fotos e as barrinhas de vida
    Formas.retangulo(tela, x, y, w, h, naEsquerda ? CANTOS_NA_ESQUERDA : CANTOS_NA_DIREITA,
        Formas.comAlfa(AVISO_DE_CIMA, alfa), Formas.comAlfa(AVISO_DE_BAIXO, alfa));
    Formas.retangulo(tela, naEsquerda ? x : x + 2, y, w - 2, FAIXA_DO_AVISO * k, Formas.raios(0f),
        Formas.comAlfa(cor, alfa), Formas.comAlfa(cor, alfa));
    Map<Colocada, Float> vidas = new HashMap<>();
    for (Colocada foto : colocadas) {
      vidas.put(foto, vidaAgora(cliente, foto.foto()));
      fundoEBarra(tela, foto, vidas.get(foto), k, alfa);
    }
    tela.flush();
    // 2) os retratos (desmaiado = cinza)
    for (Colocada foto : colocadas) {
      rosto(tela, foto.foto().rosto(), foto.x(), foto.y(), foto.tamanho(), vidas.get(foto) > 0 ? 1f : DESMAIADO, alfa);
    }
    if (alfa <= 0.03f) {
      return y + h;
    }
    // 3) os rostos das skins, os nomes, o VS e o resultado
    nomesDosJogadores(tela, cliente, painel.jogadores(), xDosJogadores, yDosNomes, k, alfa);
    if (duelo) {
      nomesDosJogadores(tela, cliente, painel.adversarios(), xDosAdversarios, yDosNomes, k, alfa);
    } else {
      escrever(tela, fonte, rotuloDosSelvagens, xDosSelvagens,
          yDosNomes + (CABECA - ALTURA_DA_LETRA * LETRA_PEQUENA) / 2 * k, LETRA_PEQUENA * k, Formas.comAlfa(ROTULO, alfa));
    }
    escrever(tela, fonte, vs, xDoVs + ESPACO_DO_VS * k, yDasFotos + (FOTO_GRANDE - ALTURA_DA_LETRA * LETRA_DO_VS) / 2 * k,
        LETRA_DO_VS * k, Formas.comAlfa(COMECOU, alfa));
    if (resultado != null) {
      float larguraDoResultado = fonte.width(resultado) * LETRA_DO_RESULTADO * k;
      float yDoResultado = yDasFotos + (FOTO_GRANDE + 1.5f + BARRA_DA_FOTO + 4) * k;
      escrever(tela, fonte, resultado, x + (w - larguraDoResultado) / 2, yDoResultado, LETRA_DO_RESULTADO * k,
          Formas.comAlfa(cor, alfa));
      // 8.2b: embaixo do resultado, uma linha por Pokémon com o XP que ele ganhou, centralizadas como ele
      float yDaLinha = yDoResultado + (ALTURA_DA_LETRA * LETRA_DO_RESULTADO + ANTES_DO_XP) * k;
      for (Component linha : aviso.ganhos()) {
        float larguraDaLinha = fonte.width(linha) * LETRA_DO_XP * k;
        escrever(tela, fonte, linha, x + (w - larguraDaLinha) / 2, yDaLinha, LETRA_DO_XP * k,
            Formas.comAlfa(TEXTO_DO_AVISO, alfa));
        yDaLinha += LINHA_DO_XP * k;
      }
    }
    return y + h;
  }

  // A largura de cada jogador (o maior entre as fotos e o rosto com o nome) vai em "larguras"; devolve o lado inteiro
  private static float medirJogadores(List<LadoNoPainel> lados, Font fonte, float[] larguras) {
    float esquerda = 0;
    for (int i = 0; i < larguras.length; i++) {
      LadoNoPainel lado = lados.get(i);
      larguras[i] = Math.max(larguraDasFotos(lado.fotos()), CABECA + 3 + fonte.width(lado.nome()) * LETRA_PEQUENA);
      esquerda += larguras[i] + (i > 0 ? ENTRE_JOGADORES : 0);
    }
    return esquerda;
  }

  // Inteiro, até a batalha acabar e o resultado passar do tempo: aí ele some junto com o resultado
  private static float alfaDoPainel(long agora) {
    if (aviso == null || !painelAcabou || agora - aviso.chegou() <= duracao(aviso)) {
      return 1f;
    }
    return Math.clamp(1f - (float) (agora - aviso.chegou() - duracao(aviso)) / SUMINDO, 0f, 1f);
  }

  // A caixinha da foto e a barrinha de vida embaixo dela
  private static void fundoEBarra(GuiGraphics tela, Colocada foto, float vida, float k, float alfa) {
    int fundo = Formas.comAlfa(foto.nossa() ? FUNDO_DA_NOSSA_FOTO : FUNDO_DA_FOTO_SELVAGEM, alfa);
    Formas.retangulo(tela, foto.x(), foto.y(), foto.tamanho(), foto.tamanho(), Formas.raios(3f * k), fundo, fundo);
    float yDaBarra = foto.y() + foto.tamanho() + 1.5f * k;
    int fundoDaBarra = Formas.comAlfa(FUNDO_DA_BARRA, alfa);
    Formas.retangulo(tela, foto.x(), yDaBarra, foto.tamanho(), BARRA_DA_FOTO * k, Formas.raios(k), fundoDaBarra,
        fundoDaBarra);
    if (vida > 0) {
      int corDaVida = Formas.comAlfa(corDaVida(vida), alfa);
      Formas.retangulo(tela, foto.x(), yDaBarra, foto.tamanho() * vida, BARRA_DA_FOTO * k, Formas.raios(k), corDaVida,
          corDaVida);
    }
  }

  // O rosto da skin e o nome de cada jogador, em cima das fotos dele
  private static void nomesDosJogadores(GuiGraphics tela, Minecraft cliente, List<LadoNoPainel> lados,
      float[] xDosJogadores, float yDosNomes, float k, float alfa) {
    for (int i = 0; i < xDosJogadores.length; i++) {
      LadoNoPainel lado = lados.get(i);
      RenderSystem.setShaderColor(1f, 1f, 1f, alfa);
      PlayerFaceRenderer.draw(tela, pele(cliente, lado.jogador()), Math.round(xDosJogadores[i]), Math.round(yDosNomes),
          Math.round(CABECA * k));
      RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
      escrever(tela, cliente.font, Component.literal(lado.nome()), xDosJogadores[i] + (CABECA + 3) * k,
          yDosNomes + (CABECA - ALTURA_DA_LETRA * LETRA_PEQUENA) / 2 * k, LETRA_PEQUENA * k, Formas.comAlfa(BRANCO, alfa));
    }
  }

  // A vida da foto na hora de desenhar, da MESMA fonte que o resto da HUD (pedido dele, 18/10: "tá dessincronizado o sangue
  // dele olhando pra ele e lá em cima"): o seu Pokémon, do time que o Cobblemon manda (o painel de baixo); os outros, da
  // entidade no mundo (a placa em cima dele). Só sem nenhum dos dois vale o número do servidor
  private static float vidaAgora(Minecraft cliente, FotoNoPainel foto) {
    for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
      if (pokemon != null && pokemon.getUuid().equals(foto.pokemon())) {
        return Math.clamp(pokemon.getCurrentHealth() / (float) Math.max(1, pokemon.getMaxHealth()), 0f, 1f);
      }
    }
    Entity entidade = cliente.level == null || foto.entidade() < 0 ? null : cliente.level.getEntity(foto.entidade());
    if (entidade instanceof LivingEntity vivo) {
      float vida = Math.clamp(vivo.getHealth() / Math.max(1f, vivo.getMaxHealth()), 0f, 1f);
      VIDA_VISTA.put(foto.pokemon(), vida);
      return vida;
    }
    // 6.9e (dele: "o bicho tinha desmaiado, tava com a vida pouca, aí do nada ele aumentou o sangue um pouquinho antes de
    // desaparecer"): a entidade sumiu do seu jogo (a animação do desmaio acabou, ou ficou longe). Vale a última vida vista
    // nela: o número do servidor pode ser de antes do golpe final, porque ele tira o selvagem do combate antes de mandar o
    // painel de novo
    Float vista = VIDA_VISTA.get(foto.pokemon());
    return vista != null ? vista : foto.vida();
  }

  // As fotos de um lado, da esquerda para a direita, alinhadas embaixo (a pequena fica na altura dos pés da grande)
  private static void colocar(List<Colocada> colocadas, List<FotoNoPainel> fotos, float x, float yDasFotos, float k,
      boolean nossa) {
    float cursor = x;
    for (FotoNoPainel foto : fotos) {
      float tamanho = (foto.grande() ? FOTO_GRANDE : FOTO_PEQUENA) * k;
      colocadas.add(new Colocada(foto, cursor, yDasFotos + FOTO_GRANDE * k - tamanho, tamanho, nossa));
      cursor += tamanho + ESPACO_DAS_FOTOS * k;
    }
  }

  private static float larguraDasFotos(List<FotoNoPainel> fotos) {
    float largura = 0;
    for (FotoNoPainel foto : fotos) {
      largura += (foto.grande() ? FOTO_GRANDE : FOTO_PEQUENA) + ESPACO_DAS_FOTOS;
    }
    return Math.max(0, largura - ESPACO_DAS_FOTOS);
  }

  // A skin do jogador, a mesma da lista do Tab; sem ela (ainda carregando), a skin padrão
  // Party: o quadrinho da party (PartyNaTela) usa o mesmo rosto e fica do lado oposto do histórico
  static boolean historicoNaEsquerda() {
    return minimapaADireita(Util.getMillis());
  }

  static PlayerSkin pele(Minecraft cliente, UUID jogador) {
    PlayerInfo info = cliente.getConnection() == null ? null : cliente.getConnection().getPlayerInfo(jogador);
    return info != null ? info.getSkin() : DefaultPlayerSkin.get(jogador);
  }

  static int corDaVida(float vida) {
    if (vida > 0.5f) {
      return VIDA_CHEIA;
    }
    return vida > 0.2f ? VIDA_MEIA : VIDA_POUCA;
  }

  private static void escrever(GuiGraphics tela, Font fonte, Component texto, float x, float y, float escala, int cor) {
    tela.pose().pushPose();
    tela.pose().translate(x, y, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.drawString(fonte, texto, 0, 0, cor, true);
    tela.pose().popPose();
  }

  private static void rosto(GuiGraphics tela, Cartao cartao, float x, float y, float alfa) {
    rosto(tela, new Rosto(cartao.especie(), cartao.escalaDaForma(), cartao.estado()), x, y, ROSTO, 1f, alfa);
  }

  // O rosto, o mesmo desenho dos retratos da lista do Cobblemon (GuiUtilsKt.drawPosablePortrait), recortado na caixinha
  // de "tamanho" pixels (o recorte acompanha o bloco no layout da HUD). "brilho" escurece o retrato (o desmaiado fica cinza)
  static void rosto(GuiGraphics tela, Rosto rosto, float x, float y, float tamanho, float brilho, float alfa) {
    float proporcao = tamanho / CAIXA_DO_COBBLEMON;
    LayoutDaHud.recortar(tela, x, y, tamanho, tamanho);
    tela.pose().pushPose();
    tela.pose().translate(x + RETRATO_X_DO_COBBLEMON * proporcao,
        y + (RETRATO_Y_DO_COBBLEMON + DESCIDA_FIXA_DO_COBBLEMON) * proporcao - DESCIDA_FIXA_DO_COBBLEMON, 0);
    GuiUtilsKt.drawPosablePortrait(rosto.especie(), tela.pose(), ESCALA_DO_COBBLEMON * proporcao, rosto.escalaDaForma(),
        false, rosto.estado(), 0f, 0f, 0f, 0f, 0f, 0f, false, brilho, brilho, brilho, alfa);
    tela.pose().popPose();
    tela.disableScissor();
  }

  // Começa rápido e chega devagar (a curva "ease out"), para o cartão "frear" ao chegar no lugar
  private static float suave(float t) {
    float falta = 1f - t;
    return 1f - falta * falta * falta;
  }
}
