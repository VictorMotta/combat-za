package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.Captura;
import dev.victor.combatza.rede.PlacasPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// A HUD do Z-A, parte 3 (itens 3, 4 e 5 da imagem), em cima dos Pokémon selvagens:
// - o candidato ao foco (o que a tecla G travaria agora) ganha os parênteses "( )";
// - o FOCADO ganha os parênteses e a placa ("Nv. 11", gênero, pokébola se já capturou, nome, vida e status);
// - quem está em batalha com você, sem foco, ganha só uma barrinha de vida pequena;
// - o resto, nada. O rótulo do Cobblemon (nome e nível) não aparece em selvagem nenhum
public class PlacasNaTela {
  private static final double DISTANCIA = 32.0;
  private static final double ACIMA_DA_CABECA = 0.35;
  private static final float VELOCIDADE_DO_SURGIR = 0.25f;
  private static final float INVISIVEL = 0.03f;
  // Etapa 8: o sinal de captura (irritado / caído) acima da placa, da barrinha ou da cabeça
  private static final float ACIMA_DA_PLACA = 40f;
  private static final float ACIMA_DA_BARRINHA = 20f;
  private static final float ACIMA_DA_CABECA_NA_TELA = 10f;
  private static final float VEIA = 3.2f;
  private static final float LETRA_DO_SINAL = 0.8f;
  private static final int COR_DA_VEIA = 0xFFE8333A;
  private static final int CONTORNO_DA_VEIA = 0xC0300000;
  private static final int COR_DO_CAIDO = 0xFFFFE36A;
  private static final float NA_TELA = 1.2f;
  private static final float EM_QUALQUER_LUGAR = Float.MAX_VALUE;
  // As três coisas que cada selvagem pode ter em cima, cada uma com a sua opacidade
  private static final int PLACA = 0;
  private static final int PARENTESES = 1;
  private static final int BARRINHA = 2;

  // Medidas da placa, de baixo para cima, a partir do ponto em cima da cabeça
  private static final float LARGURA_DA_VIDA = 56f;
  private static final float ALTURA_DA_VIDA = 3.5f;
  private static final float NOME = 1.25f;
  private static final float TEXTO_PEQUENO = 0.75f;
  private static final float ALTURA_DA_LETRA = 7f;
  private static final float ESPACO = 2.5f;
  private static final float ALTURA_DA_FAIXA = 11f;
  private static final float FOLGA_DA_FAIXA = 4f;
  private static final float RAIO_DO_GENERO = 4f;
  private static final float RAIO_DA_POKEBOLA = 3f;
  private static final float ALTURA_DO_STATUS = 8f;
  // Os estágios embaixo da barra de vida: uma plaquinha por atributo, com uma seta por estágio
  private static final float ALTURA_DO_ESTAGIO = 8f;
  private static final float LARGURA_DA_SETA = 3f;
  private static final float ALTURA_DA_SETA = 3.5f;
  private static final float ESPACO_DA_SETA = 0.75f;
  private static final float ESPACO_ENTRE_ESTAGIOS = 2f;
  private static final float ESTAGIOS_NA_BARRINHA = 0.8f;
  // Os parênteses e a barrinha
  private static final float ALTURA_MINIMA_DOS_PARENTESES = 14f;
  private static final float ALTURA_MAXIMA_DOS_PARENTESES = 140f;
  private static final float PARENTESES_NA_ALTURA = 0.9f;
  // A folga entre o Pokémon e cada parêntese: um mínimo, mais um pedaço da largura dele na tela
  private static final float FOLGA_DOS_PARENTESES = 7f;
  private static final float FOLGA_PELA_LARGURA = 0.15f;
  private static final float CURVA_DOS_PARENTESES = 0.18f;
  private static final int PEDACOS_DO_PARENTESE = 12;
  // 6.3g (dele, com print: "as pontas arredondadas, com menos opacidade, algo bem prime, igual os menus do iPhone, como se
  // fosse vidro, mas visível claro"; depois da 1ª versão grossa: "quero ele fino" e "pode deixar só fino e com opacidade
  // baixa"): o traço FINO da 6.3e, branco com pouca opacidade, e as pontas redondas. No focado, um pouco mais forte
  private static final float ESPESSURA_SOLTO = 0.9f;
  private static final float ESPESSURA_TRAVADO = 1.2f;
  // 8.1: as setas da chance de captura, ao lado do ")"
  private static final float LONGE_DO_PARENTESE = 6f;
  private static final float MEIA_LARGURA_DA_SETA_DE_CAPTURA = 3.2f;
  private static final float ALTURA_DA_SETA_DE_CAPTURA = 2.4f;
  private static final float ENTRE_SETAS_DE_CAPTURA = 3.6f;
  private static final float TRACO_DA_SETA_DE_CAPTURA = 1.4f;
  private static final float CONTORNO_DA_SETA_DE_CAPTURA = 2.6f;
  private static final int AMARELO_DA_CAPTURA = 0xFFFFD84A;
  private static final int SOMBRA_DA_SETA_DE_CAPTURA = 0x90302408;
  // 8.1d: de 99% a 100%, um halo amarelo pulsando atrás das setas e o traço clareando, no ritmo do "?" (1,5 por segundo)
  private static final float HALO_DA_SETA_DE_CAPTURA = 5.5f;
  private static final int HALO_DA_CAPTURA = 0x80FFE36A;
  private static final int AMARELO_CLARO_DA_CAPTURA = 0xFFFFF8D8;
  private static final double BRILHOS_POR_SEGUNDO = 1.5;
  private static final float HALO_MINIMO = 0.35f;
  private static final float LARGURA_DA_BARRINHA = 26f;
  private static final float ALTURA_DA_BARRINHA = 2.5f;

  private static final int BRANCO = 0xFFFFFFFF;
  private static final int FAIXA = 0xB8121E2A;
  private static final int FUNDO_DA_VIDA = 0x99101A1A;
  private static final int CINZA = 0xF08C8C9C;
  private static final int PARENTESE_SOLTO = 0x80FFFFFF;
  private static final int PARENTESE_TRAVADO = 0xB3FFFFFF;
  // As mesmas cores das partículas de estágio (Estagios): azul subiu, vermelho caiu
  private static final int SUBIU = 0xFF5AA0FF;
  private static final int CAIU = 0xFFFF5A5A;
  private static final Map<String, Integer> CORES_DOS_STATUS = Map.of(
      "brn", 0xF0E0703A, "par", 0xF0D8B020, "psn", 0xF0A33EA1, "tox", 0xF07A2A79,
      "slp", 0xF08C8C9C, "frz", 0xF05CB8D8);

  // O que o servidor contou (PlacasPayload), pelo número da entidade
  private static final Map<Integer, PlacasPayload.Placa> DO_SERVIDOR = new HashMap<>();
  // As opacidades de cada selvagem (placa, parênteses e barrinha), para tudo surgir e sumir suavemente
  private static final Map<Integer, float[]> OPACIDADES = new HashMap<>();
  // Os rótulos do Cobblemon que NÓS escondemos (os que o servidor já escondia ficam sem nada: alguém quis assim)
  private static final Set<Integer> ROTULOS_QUE_ESCONDEMOS = new HashSet<>();
  // As matrizes da câmera do último quadro do mundo: com elas um ponto do mundo vira um ponto da tela
  private static Matrix4f visao;
  private static Matrix4f projecao;

  // 23/10: o servidor contou que esta entidade está na SUA batalha (o foco, Foco.podeTravar)
  public static boolean naSuaBatalha(int entidade) {
    PlacasPayload.Placa placa = DO_SERVIDOR.get(entidade);
    return placa != null && placa.emCombate();
  }

  private PlacasNaTela() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(PlacasPayload.TIPO, (pacote, contexto) -> {
      DO_SERVIDOR.clear();
      for (PlacasPayload.Placa placa : pacote.placas()) {
        DO_SERVIDOR.put(placa.entidade(), placa);
      }
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      DO_SERVIDOR.clear();
      OPACIDADES.clear();
      ROTULOS_QUE_ESCONDEMOS.clear();
      visao = null;
    });
    WorldRenderEvents.LAST.register(contexto -> {
      visao = new Matrix4f(contexto.positionMatrix());
      projecao = new Matrix4f(contexto.projectionMatrix());
    });
    HudRenderCallback.EVENT.register(PlacasNaTela::desenhar);
  }

  private static void desenhar(GuiGraphics tela, DeltaTracker tempo) {
    // 23/10: na batalha por turnos do Cobblemon, só a tela dele (BatalhaPorTurnos)
    if (BatalhaPorTurnos.agora()) {
      return;
    }
    Minecraft cliente = Minecraft.getInstance();
    if (cliente.level == null || cliente.player == null || visao == null) {
      return;
    }
    ROTULOS_QUE_ESCONDEMOS.removeIf(id -> cliente.level.getEntity(id) == null);
    OPACIDADES.keySet().removeIf(id -> cliente.level.getEntity(id) == null);
    boolean semHud = cliente.options.hideGui || cliente.getDebugOverlay().showDebugScreen()
        || CobblemonClient.INSTANCE.getPokedexUsageContext().getScanningGuiOpen();
    float parcial = tempo.getGameTimeDeltaPartialTick(true);
    Vec3 camera = cliente.gameRenderer.getMainCamera().getPosition();
    PokemonEntity focado = Foco.alvo();
    PokemonEntity candidato = focado == null ? Foco.candidato(cliente) : null;
    for (PokemonEntity selvagem : cliente.level.getEntitiesOfClass(PokemonEntity.class,
        cliente.player.getBoundingBox().inflate(DISTANCIA), Foco::ehAlvo)) {
      esconderORotuloDoCobblemon(selvagem);
      Vec3 cabeca = selvagem.getPosition(parcial).add(0, selvagem.getBbHeight() + ACIMA_DA_CABECA, 0);
      float[] naTela = projetar(cabeca.subtract(camera), tela, NA_TELA);
      boolean eFocado = selvagem == focado;
      boolean eCandidato = selvagem == candidato;
      PlacasPayload.Placa placa = DO_SERVIDOR.get(selvagem.getId());
      boolean emBatalha = !eFocado && placa != null && placa.emCombate();
      boolean aparece = !semHud && naTela != null && selvagem.isAlive() && !Captura.dentroDaBola(selvagem)
          && !foiEscondidoPeloServidor(selvagem)
          && (eFocado || eCandidato || emBatalha) && Foco.estaVisivel(cliente, camera, selvagem, parcial);
      float[] alfas = opacidades(selvagem.getId(),
          new boolean[] { aparece && eFocado, aparece && (eFocado || eCandidato), aparece && emBatalha },
          tempo.getRealtimeDeltaTicks());
      // Etapa 8: irritado (a veia de raiva) e caído no chão (os segundos para a bola) aparecem sem precisar de foco
      if (!semHud && naTela != null && placa != null && (placa.irritado() || placa.desmaio() >= 0) && selvagem.isAlive()
          && !Captura.dentroDaBola(selvagem) && Foco.estaVisivel(cliente, camera, selvagem, parcial)) {
        boolean comPlaca = alfas != null && alfas[PLACA] > INVISIVEL;
        boolean comBarrinha = alfas != null && alfas[BARRINHA] > INVISIVEL;
        sinalDaCaptura(tela, cliente.font, placa, naTela[0],
            naTela[1] - (comPlaca ? ACIMA_DA_PLACA : comBarrinha ? ACIMA_DA_BARRINHA : ACIMA_DA_CABECA_NA_TELA));
      }
      if (alfas == null || naTela == null) {
        continue;
      }
      if (alfas[PARENTESES] > INVISIVEL) {
        parenteses(tela, selvagem, camera, parcial, alfas[PARENTESES], eFocado, placa);
      }
      tela.pose().pushPose();
      tela.pose().translate(naTela[0], naTela[1], 0);
      if (alfas[PLACA] > INVISIVEL) {
        desenharPlaca(tela, cliente.font, selvagem, alfas[PLACA]);
      }
      if (alfas[BARRINHA] > INVISIVEL) {
        barrinha(tela, selvagem, alfas[BARRINHA]);
      }
      tela.pose().popPose();
    }
  }

  // Etapa 8: a veia de raiva do irritado (as quatro curvinhas vermelhas dos animes, pulsando) ou o "Desmaiado: 7 s" do caído
  private static void sinalDaCaptura(GuiGraphics tela, Font fonte, PlacasPayload.Placa placa, float x, float y) {
    if (placa.desmaio() >= 0) {
      Component texto = Component.translatable("cobblemon_combat_za.captura.caido", placa.desmaio());
      float largura = fonte.width(texto) * LETRA_DO_SINAL;
      tela.pose().pushPose();
      tela.pose().translate(x - largura / 2, y - ALTURA_DA_LETRA * LETRA_DO_SINAL, 0);
      tela.pose().scale(LETRA_DO_SINAL, LETRA_DO_SINAL, 1f);
      tela.drawString(fonte, texto, 0, 0, COR_DO_CAIDO, true);
      tela.pose().popPose();
      return;
    }
    float pulso = 1f + 0.12f * (float) Math.sin(Util.getMillis() / 150.0);
    float d = VEIA * pulso;
    float[][] centros = { { x - d, y - d }, { x + d, y - d }, { x + d, y + d }, { x - d, y + d } };
    float[] inicios = { 0f, 90f, 180f, 270f };
    for (int vez = 0; vez < 2; vez++) {
      for (int i = 0; i < centros.length; i++) {
        Formas.arco(tela, centros[i][0], centros[i][1], d + (vez == 0 ? 0.6f : 0f), vez == 0 ? 2.8f : 1.6f, inicios[i], 90f,
            vez == 0 ? CONTORNO_DA_VEIA : COR_DA_VEIA);
      }
    }
    tela.flush();
  }

  // O rótulo do Cobblemon (nome e nível) aparece quando você olha para o Pokémon, a não ser que a marca HIDE_LABEL esteja
  // ligada. Ligamos a marca só aqui no cliente, para os selvagens ficarem limpos como no Z-A
  private static void esconderORotuloDoCobblemon(PokemonEntity selvagem) {
    EntityDataAccessor<Boolean> marca = PokemonEntity.Companion.getHIDE_LABEL();
    if (!selvagem.getEntityData().get(marca)) {
      selvagem.getEntityData().set(marca, true);
      ROTULOS_QUE_ESCONDEMOS.add(selvagem.getId());
    }
  }

  private static boolean foiEscondidoPeloServidor(PokemonEntity selvagem) {
    return !ROTULOS_QUE_ESCONDEMOS.contains(selvagem.getId());
  }

  // 7.1: um ponto do mundo na tela, com a mesma conta das placas (o sinal "?"/"!" do SinaisNaTela usa). Fora: null
  public static float[] naTela(Vec3 noMundo, GuiGraphics tela) {
    if (visao == null) {
      return null;
    }
    Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    return projetar(noMundo.subtract(camera), tela, NA_TELA);
  }

  // Do mundo para a tela: a câmera gira o ponto (visão), a lente faz a perspectiva (projeção) e o resultado vai de -1 a 1
  // nos dois eixos; aí é só esticar para o tamanho da tela. Atrás da câmera (w perto de 0 ou negativo) não aparece.
  // "limite" corta o que sai muito da tela (NA_TELA) ou deixa passar (EM_QUALQUER_LUGAR, para os cantos da caixa)
  private static float[] projetar(Vec3 relativo, GuiGraphics tela, float limite) {
    Vector4f ponto = new Vector4f((float) relativo.x, (float) relativo.y, (float) relativo.z, 1f);
    visao.transform(ponto);
    projecao.transform(ponto);
    if (ponto.w <= 0.05f) {
      return null;
    }
    float x = ponto.x / ponto.w;
    float y = ponto.y / ponto.w;
    if (Math.abs(x) > limite || Math.abs(y) > limite) {
      return null;
    }
    return new float[] { (x + 1) / 2 * tela.guiWidth(), (1 - y) / 2 * tela.guiHeight() };
  }

  // Cada uma das três sobe ou desce um pouco a cada quadro: uns 4 ticks (0,2 segundo) para aparecer ou sumir por
  // inteiro. Devolve null quando as três já sumiram
  private static float[] opacidades(int entidade, boolean[] mostrar, float passo) {
    float[] atuais = OPACIDADES.getOrDefault(entidade, new float[mostrar.length]);
    boolean alguma = false;
    for (int i = 0; i < mostrar.length; i++) {
      atuais[i] = mostrar[i] ? Math.min(1f, atuais[i] + passo * VELOCIDADE_DO_SURGIR)
          : Math.max(0f, atuais[i] - passo * VELOCIDADE_DO_SURGIR);
      alguma |= atuais[i] > 0f;
    }
    if (!alguma) {
      OPACIDADES.remove(entidade);
      return null;
    }
    OPACIDADES.put(entidade, atuais);
    return atuais;
  }

  // Os parênteses "( )" do Z-A dos dois lados do Pokémon: a caixa dele (a "bounding box") projetada na tela diz a altura e
  // onde fica cada lado. No focado ficam mais fortes
  private static void parenteses(GuiGraphics tela, PokemonEntity selvagem, Vec3 camera, float parcial, float alfa,
      boolean travado, PlacasPayload.Placa placa) {
    AABB caixa = selvagem.getBoundingBox().move(selvagem.getPosition(parcial).subtract(selvagem.position()));
    float esquerda = Float.MAX_VALUE;
    float direita = -Float.MAX_VALUE;
    float cima = Float.MAX_VALUE;
    float baixo = -Float.MAX_VALUE;
    for (int canto = 0; canto < 8; canto++) {
      Vec3 quina = new Vec3((canto & 1) == 0 ? caixa.minX : caixa.maxX, (canto & 2) == 0 ? caixa.minY : caixa.maxY,
          (canto & 4) == 0 ? caixa.minZ : caixa.maxZ);
      float[] ponto = projetar(quina.subtract(camera), tela, EM_QUALQUER_LUGAR);
      if (ponto == null) {
        return;
      }
      esquerda = Math.min(esquerda, ponto[0]);
      direita = Math.max(direita, ponto[0]);
      cima = Math.min(cima, ponto[1]);
      baixo = Math.max(baixo, ponto[1]);
    }
    float altura = Math.clamp((baixo - cima) * PARENTESES_NA_ALTURA, ALTURA_MINIMA_DOS_PARENTESES,
        ALTURA_MAXIMA_DOS_PARENTESES);
    float meio = (cima + baixo) / 2;
    float curva = altura * CURVA_DOS_PARENTESES;
    float folga = FOLGA_DOS_PARENTESES + (direita - esquerda) * FOLGA_PELA_LARGURA;
    float[][] daEsquerda = parentese(esquerda - folga, meio, altura, curva, 1);
    float[][] daDireita = parentese(direita + folga, meio, altura, curva, -1);
    float espessura = travado ? ESPESSURA_TRAVADO : ESPESSURA_SOLTO;
    int cor = Formas.comAlfa(travado ? PARENTESE_TRAVADO : PARENTESE_SOLTO, alfa);
    Formas.caminhoArredondado(tela, daEsquerda, espessura, cor);
    Formas.caminhoArredondado(tela, daDireita, espessura, cor);
    if (placa != null) {
      setasDaCaptura(tela, direita + folga + LONGE_DO_PARENTESE, meio + altura / 2, placa.setas(), placa.brilha(), alfa);
    }
    tela.flush();
  }

  // 8.1 (dele, com print do Z-A): a chance de captura em setas amarelas para cima, empilhadas de baixo para cima ao lado do
  // ")" (0 a 4: 80% ou mais, 50%, 20%, mais que zero; nenhuma = impossível). Cada seta é um "^" com um contorno escuro por
  // baixo, para aparecer no céu e na grama. 8.1d: "brilha" (99% ou mais) = o halo atrás, todo de uma vez antes das setas, e o
  // traço clareando no mesmo pulso
  private static void setasDaCaptura(GuiGraphics tela, float x, float embaixo, int setas, boolean brilha, float alfa) {
    float pulso = brilha ? 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 1000.0 * 2 * Math.PI * BRILHOS_POR_SEGUNDO) : 0;
    if (brilha) {
      int halo = Formas.comAlfa(HALO_DA_CAPTURA, alfa * (HALO_MINIMO + (1 - HALO_MINIMO) * pulso));
      for (int i = 0; i < setas; i++) {
        Formas.caminhoArredondado(tela, seta(x, embaixo, i), HALO_DA_SETA_DE_CAPTURA, halo);
      }
    }
    int amarelo = brilha ? Formas.misturar(AMARELO_DA_CAPTURA, AMARELO_CLARO_DA_CAPTURA, pulso) : AMARELO_DA_CAPTURA;
    for (int i = 0; i < setas; i++) {
      float[][] seta = seta(x, embaixo, i);
      Formas.caminhoArredondado(tela, seta, CONTORNO_DA_SETA_DE_CAPTURA, Formas.comAlfa(SOMBRA_DA_SETA_DE_CAPTURA, alfa));
      Formas.caminhoArredondado(tela, seta, TRACO_DA_SETA_DE_CAPTURA, Formas.comAlfa(amarelo, alfa));
    }
  }

  // Os 3 pontos do "^" número "i", de baixo para cima
  private static float[][] seta(float x, float embaixo, int i) {
    float ponta = embaixo - ALTURA_DA_SETA_DE_CAPTURA - i * ENTRE_SETAS_DE_CAPTURA;
    return new float[][] { { x - MEIA_LARGURA_DA_SETA_DE_CAPTURA, ponta + ALTURA_DA_SETA_DE_CAPTURA }, { x, ponta },
        { x + MEIA_LARGURA_DA_SETA_DE_CAPTURA, ponta + ALTURA_DA_SETA_DE_CAPTURA } };
  }

  // Os pontos de um parêntese: o meio fica em x e as pontas entram "curva" pixels para dentro. "para" = 1 entra para a
  // direita (o "(" da esquerda); -1 entra para a esquerda (o ")" da direita)
  private static float[][] parentese(float x, float meio, float altura, float curva, int para) {
    float[][] pontos = new float[PEDACOS_DO_PARENTESE + 1][];
    for (int i = 0; i <= PEDACOS_DO_PARENTESE; i++) {
      float t = -1 + 2f * i / PEDACOS_DO_PARENTESE;
      pontos[i] = new float[] { x + para * curva * t * t, meio + t * altura / 2 };
    }
    return pontos;
  }

  // Em batalha e sem foco: só uma barrinha de vida pequena em cima da cabeça
  private static void barrinha(GuiGraphics tela, PokemonEntity selvagem, float alfa) {
    float fracao = fracaoDaVida(selvagem);
    float[] cantos = Formas.raios(ALTURA_DA_BARRINHA / 2);
    float x = -LARGURA_DA_BARRINHA / 2;
    int fundo = Formas.comAlfa(FUNDO_DA_VIDA, alfa);
    int cor = Formas.comAlfa(corDaVida(fracao), alfa);
    Formas.retangulo(tela, x, -ALTURA_DA_BARRINHA, LARGURA_DA_BARRINHA, ALTURA_DA_BARRINHA, cantos, fundo, fundo);
    Formas.retangulo(tela, x, -ALTURA_DA_BARRINHA, LARGURA_DA_BARRINHA * fracao, ALTURA_DA_BARRINHA, cantos, cor, cor);
    tela.flush();
    // 7.2c: em batalha, o golpe que ele está carregando aparece mesmo sem o foco, em cima da barrinha
    FaixaDoGolpe.desenhar(tela, Minecraft.getInstance().font, selvagem.getId(), -ALTURA_DA_BARRINHA - 2, alfa);
    // Os estágios ao lado da barrinha, na mesma linha (embaixo dela, cobriam o Pokémon)
    PlacasPayload.Placa placa = DO_SERVIDOR.get(selvagem.getId());
    if (placa != null && !placa.estagios().isEmpty()) {
      tela.pose().pushPose();
      tela.pose().translate(LARGURA_DA_BARRINHA / 2 + 3, -ALTURA_DA_BARRINHA / 2, 0);
      tela.pose().scale(ESTAGIOS_NA_BARRINHA, ESTAGIOS_NA_BARRINHA, 1f);
      estagios(tela, Minecraft.getInstance().font, placa.estagios(), 0, 0, alfa);
      tela.pose().popPose();
    }
  }

  private static float fracaoDaVida(PokemonEntity selvagem) {
    return Math.clamp(selvagem.getHealth() / Math.max(1f, selvagem.getMaxHealth()), 0f, 1f);
  }

  // A placa, centrada no ponto em cima da cabeça: a barra de vida embaixo (com o status ao lado), o nome no meio e a
  // faixa escura com nível, gênero e pokébola em cima
  private static void desenharPlaca(GuiGraphics tela, Font fonte, PokemonEntity selvagem, float alfa) {
    PlacasPayload.Placa placa = DO_SERVIDOR.get(selvagem.getId());
    Pokemon pokemon = selvagem.getPokemon();
    float topoDaVida = -ALTURA_DA_VIDA;
    barraDeVida(tela, selvagem, topoDaVida, alfa);
    if (placa != null && !placa.status().isEmpty()) {
      status(tela, fonte, placa.status(), LARGURA_DA_VIDA / 2 + 3, topoDaVida + ALTURA_DA_VIDA / 2, alfa);
    }

    float topoDoNome = topoDaVida - ESPACO - ALTURA_DA_LETRA * NOME;
    Component nome = conhece(pokemon) ? pokemon.getDisplayName(false) : Component.literal("???");
    texto(tela, fonte, nome, -fonte.width(nome) * NOME / 2, topoDoNome, NOME, alfa, true);
    int nivel = placa != null ? placa.nivel() : selvagem.getEntityData().get(PokemonEntity.Companion.getLABEL_LEVEL());
    Gender genero = placa != null ? Gender.valueOf(placa.genero()) : Gender.GENDERLESS;
    float topoDaFaixa = topoDoNome - 2 - ALTURA_DA_FAIXA;
    float direitaDaFaixa = faixa(tela, fonte, nivel, genero, capturou(pokemon), topoDaFaixa, alfa);
    // 7.2c: o golpe que ele está carregando, em cima de tudo
    FaixaDoGolpe.desenhar(tela, fonte, selvagem.getId(), topoDaFaixa - 2, alfa);
    // Os estágios ao lado da faixa do nível (pedido dele, 18/10: embaixo da barra, cobriam o Pokémon e os parênteses)
    if (placa != null && !placa.estagios().isEmpty()) {
      estagios(tela, fonte, placa.estagios(), direitaDaFaixa + 3, topoDaFaixa + ALTURA_DA_FAIXA / 2, alfa);
    }
  }

  private static void barraDeVida(GuiGraphics tela, PokemonEntity selvagem, float topo, float alfa) {
    float fracao = fracaoDaVida(selvagem);
    float[] cantos = Formas.raios(ALTURA_DA_VIDA / 2);
    float x = -LARGURA_DA_VIDA / 2;
    Formas.retangulo(tela, x, topo, LARGURA_DA_VIDA, ALTURA_DA_VIDA, cantos, Formas.comAlfa(FUNDO_DA_VIDA, alfa),
        Formas.comAlfa(FUNDO_DA_VIDA, alfa));
    int cor = Formas.comAlfa(corDaVida(fracao), alfa);
    Formas.retangulo(tela, x, topo, LARGURA_DA_VIDA * fracao, ALTURA_DA_VIDA, cantos,
        Formas.misturar(cor, Formas.comAlfa(BRANCO, alfa), 0.25f), cor);
    tela.flush();
  }

  // A plaquinha do status, colorida, à direita da barra de vida (QMD, PAR, ENV, TOX, DRM, CGL)
  private static void status(GuiGraphics tela, Font fonte, String status, float x, float meio, float alfa) {
    Component texto = Component.translatable("cobblemon.ui.status." + status);
    float largura = 2 + fonte.width(texto) * TEXTO_PEQUENO + 2;
    int fundo = Formas.comAlfa(CORES_DOS_STATUS.getOrDefault(status, CINZA), alfa);
    Formas.retangulo(tela, x, meio - ALTURA_DO_STATUS / 2, largura, ALTURA_DO_STATUS, Formas.raios(1.5f), fundo, fundo);
    tela.flush();
    texto(tela, fonte, texto, x + 2, meio - ALTURA_DA_LETRA * TEXTO_PEQUENO / 2, TEXTO_PEQUENO, alfa, false);
  }

  // Os estágios (Ataque +1, Defesa -2...): uma plaquinha escura por atributo, com o nome curto e uma seta por estágio,
  // azul se subiu e vermelha se caiu, em fila a partir de "x", centradas na altura "meio"
  private static void estagios(GuiGraphics tela, Font fonte, List<PlacasPayload.Estagio> estagios, float x, float meio,
      float alfa) {
    float topo = meio - ALTURA_DO_ESTAGIO / 2;
    for (int i = 0; i < estagios.size(); i++) {
      PlacasPayload.Estagio estagio = estagios.get(i);
      Component nome = nomeCurto(estagio);
      float largura = 2 + fonte.width(nome) * TEXTO_PEQUENO + 1.5f
          + Math.abs(estagio.valor()) * (LARGURA_DA_SETA + ESPACO_DA_SETA) + 1.25f;
      int fundo = Formas.comAlfa(FAIXA, alfa);
      Formas.retangulo(tela, x, topo, largura, ALTURA_DO_ESTAGIO, Formas.raios(1.5f), fundo, fundo);
      float setaX = x + 2 + fonte.width(nome) * TEXTO_PEQUENO + 1.5f;
      int cor = Formas.comAlfa(estagio.valor() > 0 ? SUBIU : CAIU, alfa);
      for (int seta = 0; seta < Math.abs(estagio.valor()); seta++) {
        seta(tela, setaX + seta * (LARGURA_DA_SETA + ESPACO_DA_SETA), meio, estagio.valor() > 0, cor);
      }
      tela.flush();
      texto(tela, fonte, nome, x + 2, meio - ALTURA_DA_LETRA * TEXTO_PEQUENO / 2, TEXTO_PEQUENO, alfa, false);
      x += largura + ESPACO_ENTRE_ESTAGIOS;
    }
  }

  // Um triângulo pequeno, centrado na altura "meio": de ponta para cima (subiu) ou para baixo (caiu)
  private static void seta(GuiGraphics tela, float x, float meio, boolean subiu, int cor) {
    float cima = meio - ALTURA_DA_SETA / 2;
    float baixo = meio + ALTURA_DA_SETA / 2;
    float[][] pontos = subiu
        ? new float[][] { { x, baixo }, { x + LARGURA_DA_SETA / 2, cima }, { x + LARGURA_DA_SETA, baixo } }
        : new float[][] { { x, cima }, { x + LARGURA_DA_SETA, cima }, { x + LARGURA_DA_SETA / 2, baixo } };
    Formas.poligono(tela, pontos, cor);
  }

  // Atq, Def, AtqE, DefE, Vel, Prec, Eva (no nosso arquivo de idioma)
  private static Component nomeCurto(PlacasPayload.Estagio estagio) {
    return Component.translatable("cobblemon_combat_za.estagio." + estagio.atributo());
  }

  // "Nv." pequeno e o número, o gênero num círculo e a pokébola de "já capturou", numa faixa escura centrada. Devolve onde
  // ela termina, à direita (os estágios vêm logo depois)
  private static float faixa(GuiGraphics tela, Font fonte, int nivel, Gender genero, boolean capturou, float topo,
      float alfa) {
    Component rotulo = Component.translatable("cobblemon.ui.lv");
    String numero = nivel > 0 ? String.valueOf(nivel) : "?";
    float larguraDoRotulo = fonte.width(rotulo) * TEXTO_PEQUENO;
    float larguraDoNumero = fonte.width(numero);
    boolean temGenero = genero != Gender.GENDERLESS;
    float largura = FOLGA_DA_FAIXA + larguraDoRotulo + 2 + larguraDoNumero
        + (temGenero ? 3 + 2 * RAIO_DO_GENERO : 0) + (capturou ? 3 + 2 * RAIO_DA_POKEBOLA + 1 : 0) + FOLGA_DA_FAIXA;
    float x = -largura / 2;
    int fundo = Formas.comAlfa(FAIXA, alfa);
    Formas.retangulo(tela, x, topo, largura, ALTURA_DA_FAIXA, Formas.raios(2.5f), fundo, fundo);
    tela.flush();
    float topoDoNumero = topo + (ALTURA_DA_FAIXA - ALTURA_DA_LETRA) / 2;
    float cursor = x + FOLGA_DA_FAIXA;
    texto(tela, fonte, rotulo, cursor, topoDoNumero + ALTURA_DA_LETRA * (1 - TEXTO_PEQUENO), TEXTO_PEQUENO, alfa, false);
    cursor += larguraDoRotulo + 2;
    texto(tela, fonte, Component.literal(numero), cursor, topoDoNumero, 1f, alfa, false);
    cursor += larguraDoNumero;
    float meio = topo + ALTURA_DA_FAIXA / 2;
    if (temGenero) {
      Simbolos.genero(tela, cursor + 3 + RAIO_DO_GENERO, meio, RAIO_DO_GENERO, genero, alfa);
      cursor += 3 + 2 * RAIO_DO_GENERO;
    }
    if (capturou) {
      Simbolos.pokebola(tela, cursor + 3 + RAIO_DA_POKEBOLA + 0.5f, meio, RAIO_DA_POKEBOLA, alfa);
    }
    tela.flush();
    return x + largura;
  }

  private static void texto(GuiGraphics tela, Font fonte, Component texto, float x, float y, float escala, float alfa,
      boolean sombra) {
    tela.pose().pushPose();
    tela.pose().translate(x, y, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.drawString(fonte, texto, 0, 0, Formas.comAlfa(BRANCO, alfa), sombra);
    tela.pose().popPose();
  }

  // A pokébola da placa: a espécie já foi capturada por você (a Pokédex do Cobblemon, que o cliente recebe)
  private static boolean capturou(Pokemon pokemon) {
    return conhecimento(pokemon) == PokedexEntryProgress.CAUGHT;
  }

  // O modpack configurou o Cobblemon para não dar o nome de espécie que você nunca viu: a placa mostra "???" igual
  private static boolean conhece(Pokemon pokemon) {
    return Cobblemon.INSTANCE.getConfig().getDisplayNameForUnknownPokemon()
        || conhecimento(pokemon) != PokedexEntryProgress.NONE;
  }

  private static PokedexEntryProgress conhecimento(Pokemon pokemon) {
    return CobblemonClient.INSTANCE.getClientPokedexData()
        .getHighestKnowledgeForSpecies(pokemon.getSpecies().getResourceIdentifier());
  }

  // As mesmas cores da barra do painel do time: verde, amarela abaixo da metade, vermelha abaixo de 20%
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
