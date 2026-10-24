package dev.victor.combatza.client;

import org.lwjgl.glfw.GLFW;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.InputConstants;

import dev.victor.combatza.combate.Captura;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// O foco (lock-on) do Z-A. Perto, o selvagem mais no meio da sua visão é o "candidato" (ganha os parênteses). G trava
// nele: a câmera passa a segui-lo e a placa completa aparece. G de novo solta. Solta sozinho se ele sumir, desmaiar,
// ficar a mais de 24 blocos ou passar 3 segundos fora de vista. Travado, um movimento rápido do mouse para o lado passa
// para o selvagem vizinho daquele lado (6.3f)
public class Foco {
  private static final double ALCANCE = 16.0;
  private static final double ALCANCE_TRAVADO = 24.0;
  private static final double ANGULO_MAXIMO = Math.toRadians(30);
  private static final int TICKS_FORA_DE_VISTA = 60;
  private static final double VELOCIDADE_DA_CAMERA = 10.0;
  private static final double SEGUNDOS_MAXIMOS_POR_QUADRO = 0.1;
  // O turn do Minecraft multiplica o giro do mouse por 0,15 para virar graus (Entity.turn)
  private static final double GRAUS_POR_UNIDADE_DO_MOUSE = 0.15;
  // O ombro: com foco, a câmera da 3ª pessoa vai 1 bloco para a direita e 0,2 para cima, deslizando em uns 0,3 segundo
  private static final float OMBRO_PARA_O_LADO = 1.0f;
  private static final float OMBRO_PARA_CIMA = 0.2f;
  private static final double VELOCIDADE_DO_OMBRO = 8.0;
  private static final int SEM_ALVO = -1;
  // A troca de alvo pelo mouse: o movimento vai somando (em graus que a câmera giraria) e se apaga sozinho em uns 0,12
  // segundo. Só um movimento rápido chega a 25 graus; devagar, a soma some antes. Depois de trocar, 0,35 segundo de
  // espera, para um movimento só não pular dois alvos. O vizinho fica entre 2 e 100 graus para o lado
  private static final double GRAUS_PARA_TROCAR = 25.0;
  private static final double MEMORIA_DO_MOUSE = 0.12;
  private static final long ESPERA_ENTRE_TROCAS = 350_000_000L;
  private static final double LADO_MINIMO = 2.0;
  private static final double LADO_MAXIMO = 100.0;

  private static KeyMapping tecla;
  private static ApertoDeTecla aperto;
  private static int alvo = SEM_ALVO;
  private static int ticksSemVer = 0;
  // 23/10: o travado caiu no meio da batalha; o foco espera o próximo Pokémon dela para travar sozinho
  private static boolean voltarSozinho = false;
  private static long ultimoGiro = 0;
  private static float ombro = 0f;
  private static long ultimoOmbro = 0;
  private static double somaDoMouse = 0;
  private static long ultimoMouse = 0;
  private static long trocaLiberadaEm = 0;

  private Foco() {
  }

  // 7.10: o tutorial mostra a tecla como está nos Controles
  static KeyMapping tecla() {
    return tecla;
  }

  public static void registrar() {
    tecla = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.focar",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, TeclasDeGolpe.CATEGORIA));
    // Segurar o G não fica travando e soltando: só o aperto conta
    aperto = new ApertoDeTecla(tecla);
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      boolean apertou = aperto.foiApertada();
      // 23/10: na batalha por turnos do Cobblemon, sem foco (BatalhaPorTurnos)
      if (BatalhaPorTurnos.agora()) {
        soltar();
        return;
      }
      if (apertou) {
        alternar(cliente);
      }
      conferirOAlvo(cliente);
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> soltar());
  }

  // O número da entidade travada, que vai junto quando você usa um golpe (o servidor mira nela); -1 = sem foco
  public static int idDoAlvo() {
    return alvo() == null ? SEM_ALVO : alvo;
  }

  public static PokemonEntity alvo() {
    Minecraft cliente = Minecraft.getInstance();
    if (alvo == SEM_ALVO || cliente.level == null) {
      return null;
    }
    return cliente.level.getEntity(alvo) instanceof PokemonEntity pokemon ? pokemon : null;
  }

  // No cliente o isWild() do Cobblemon não serve: ele pergunta se o Pokémon tem lugar num time ou numa caixa, e isso só o
  // servidor sabe (no cliente, todos pareceriam selvagens). O que chega ao cliente é o dono da entidade, que o pacote de
  // nascimento do Cobblemon grava (SpawnPokemonPacket): sem dono = selvagem
  public static boolean ehSelvagem(PokemonEntity entidade) {
    return entidade.getOwnerUUID() == null;
  }

  // Quem o foco, a placa e a prévia tratam como inimigo: o selvagem. 21/10 (PvP): no duelo, só o Pokémon do adversário (o
  // dono dele está do outro lado do painel da batalha: HistoricoDaBatalha.ehDoAdversario); os selvagens ficam de fora
  // 23/10 (dele: "numa batalha contra NPC e pessoa, não pode deixar o foco sair do Pokémon deles; toda hora tinha um selvagem
  // perto de mim e o foco ia para ele; de preferência, até contra selvagem, só foca em quem está na batalha; fora da batalha,
  // aí sim, livre"): na batalha, o foco só trava (e só troca, no G e no mouse de lado) entre os Pokémon dela: os selvagens que
  // lutam com você, o Pokémon do treinador, o do adversário no duelo (a marca "na sua batalha" das placas). Fora dela, como antes
  static boolean podeTravar(PokemonEntity entidade) {
    return ehAlvo(entidade) && (!HistoricoDaBatalha.emBatalha() || PlacasNaTela.naSuaBatalha(entidade.getId()));
  }

  public static boolean ehAlvo(PokemonEntity entidade) {
    return HistoricoDaBatalha.emDuelo() ? HistoricoDaBatalha.ehDoAdversario(entidade.getOwnerUUID())
        : ehSelvagem(entidade);
  }

  // O selvagem perto (até 16 blocos de você), à vista, mais perto do MEIO DA TELA (no máximo 30 graus de lado). O meio da
  // tela é o olhar da câmera: na 3ª pessoa (ainda mais com o Better Third Person) o personagem pode estar virado para outro lado
  public static PokemonEntity candidato(Minecraft cliente) {
    Camera camera = cliente.gameRenderer.getMainCamera();
    if (cliente.player == null || cliente.level == null || !camera.isInitialized()) {
      return null;
    }
    Vec3 olhos = camera.getPosition();
    Vec3 olhar = new Vec3(camera.getLookVector());
    PokemonEntity melhor = null;
    double menorAngulo = ANGULO_MAXIMO;
    for (PokemonEntity pokemon : cliente.level.getEntitiesOfClass(PokemonEntity.class,
        cliente.player.getBoundingBox().inflate(ALCANCE), Foco::podeTravar)) {
      Vec3 direcao = pokemon.getBoundingBox().getCenter().subtract(olhos);
      double distancia = cliente.player.distanceTo(pokemon);
      if (!pokemon.isAlive() || Captura.dentroDaBola(pokemon) || distancia > ALCANCE || direcao.lengthSqr() < 0.0001) {
        continue;
      }
      double angulo = Math.acos(Mth.clamp(direcao.normalize().dot(olhar), -1, 1));
      if (angulo < menorAngulo && estaVisivel(cliente, olhos, pokemon, 1f)) {
        menorAngulo = angulo;
        melhor = pokemon;
      }
    }
    return melhor;
  }

  // Um raio de onde você vê até o meio do Pokémon: se bater num bloco antes, ele está escondido. Folha conta como parede;
  // grama alta e flor não (não têm forma de colisão)
  public static boolean estaVisivel(Minecraft cliente, Vec3 de, PokemonEntity pokemon, float parcial) {
    Vec3 meio = pokemon.getPosition(parcial).add(0, pokemon.getBbHeight() * 0.6, 0);
    return cliente.level.clip(new ClipContext(de, meio, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
        cliente.player)).getType() == HitResult.Type.MISS;
  }

  // Com o foco travado, chamado a cada quadro no lugar do giro do mouse (TravarACameraMixin): devolve o giro que leva a
  // CÂMERA um pedaço na direção do alvo, na unidade do mouse. Vai pelo mesmo caminho do mouse (o turn do jogador), então
  // quem gira a câmera gira junto: o Minecraft (1ª pessoa e 3ª de costas) ou o Better Third Person, sem depender dele.
  // As posições são as do quadro (com o "parcial" entre dois ticks): com as do tick, a câmera pularia 20 vezes por segundo.
  // Devolve null sem foco, ou com a câmera de frente (F5 duas vezes): aí o mouse gira como sempre
  public static double[] giroParaOAlvo() {
    Minecraft cliente = Minecraft.getInstance();
    PokemonEntity pokemon = alvo();
    Camera camera = cliente.gameRenderer.getMainCamera();
    if (pokemon == null || !camera.isInitialized() || cliente.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
      ultimoGiro = 0;
      return null;
    }
    long agora = Util.getNanos();
    double segundos = ultimoGiro == 0 ? 0 : Math.min(SEGUNDOS_MAXIMOS_POR_QUADRO, (agora - ultimoGiro) / 1e9);
    ultimoGiro = agora;
    float parcial = cliente.getTimer().getGameTimeDeltaPartialTick(true);
    Vec3 meio = pokemon.getPosition(parcial).add(0, pokemon.getBbHeight() * 0.5, 0);
    Vec3 direcao = meio.subtract(camera.getPosition());
    float giroCerto = (float) Math.toDegrees(Math.atan2(direcao.z, direcao.x)) - 90f;
    float inclinacaoCerta = (float) -Math.toDegrees(Math.atan2(direcao.y, direcao.horizontalDistance()));
    double pedaco = 1 - Math.exp(-VELOCIDADE_DA_CAMERA * segundos);
    double giro = Mth.wrapDegrees(giroCerto - camera.getYRot()) * pedaco;
    double inclinacao = (inclinacaoCerta - camera.getXRot()) * pedaco;
    return new double[] { giro / GRAUS_POR_UNIDADE_DO_MOUSE, inclinacao / GRAUS_POR_UNIDADE_DO_MOUSE };
  }

  // Chamado a cada quadro pelo TravarACameraMixin com o giro que o mouse mandaria (na unidade do mouse). Com o foco
  // travado o mouse não gira a câmera; um movimento RÁPIDO para o lado troca o alvo para o vizinho daquele lado
  // (pedido dele, 18/10: "trocar entre os pokemons que estão próximos, igual num jogo que tem lock on")
  public static void mouseDeLado(double giroDoMouse) {
    Minecraft cliente = Minecraft.getInstance();
    long agora = Util.getNanos();
    double segundos = ultimoMouse == 0 ? 0 : Math.min(SEGUNDOS_MAXIMOS_POR_QUADRO, (agora - ultimoMouse) / 1e9);
    ultimoMouse = agora;
    PokemonEntity atual = alvo();
    if (atual == null || cliente.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
      somaDoMouse = 0;
      return;
    }
    somaDoMouse = somaDoMouse * Math.exp(-segundos / MEMORIA_DO_MOUSE) + giroDoMouse * GRAUS_POR_UNIDADE_DO_MOUSE;
    if (agora < trocaLiberadaEm || Math.abs(somaDoMouse) < GRAUS_PARA_TROCAR) {
      return;
    }
    PokemonEntity vizinho = vizinho(cliente, atual, somaDoMouse > 0);
    somaDoMouse = 0;
    trocaLiberadaEm = agora + ESPERA_ENTRE_TROCAS;
    if (vizinho != null) {
      alvo = vizinho.getId();
      ticksSemVer = 0;
    }
  }

  // O selvagem mais perto do atual, do lado pedido da tela: o de menor ângulo para aquele lado, a até 16 blocos de você
  // e à vista. Sem nenhum daquele lado, o foco fica onde está
  private static PokemonEntity vizinho(Minecraft cliente, PokemonEntity atual, boolean paraADireita) {
    Camera camera = cliente.gameRenderer.getMainCamera();
    Vec3 olhos = camera.getPosition();
    double referencia = anguloNaTela(camera, atual.getBoundingBox().getCenter());
    PokemonEntity melhor = null;
    double menor = LADO_MAXIMO;
    for (PokemonEntity pokemon : cliente.level.getEntitiesOfClass(PokemonEntity.class,
        cliente.player.getBoundingBox().inflate(ALCANCE), Foco::podeTravar)) {
      if (pokemon == atual || !pokemon.isAlive() || Captura.dentroDaBola(pokemon)
          || cliente.player.distanceTo(pokemon) > ALCANCE) {
        continue;
      }
      double lado = Mth.wrapDegrees(anguloNaTela(camera, pokemon.getBoundingBox().getCenter()) - referencia);
      double paraLa = paraADireita ? lado : -lado;
      if (paraLa > LADO_MINIMO && paraLa < menor && estaVisivel(cliente, olhos, pokemon, 1f)) {
        menor = paraLa;
        melhor = pokemon;
      }
    }
    return melhor;
  }

  // Quantos graus para a direita (positivo) ou para a esquerda (negativo) do meio da tela o ponto está. É a mesma conta
  // do giro do giroParaOAlvo: no Minecraft, o giro cresce quando você vira para a direita
  private static double anguloNaTela(Camera camera, Vec3 ponto) {
    Vec3 direcao = ponto.subtract(camera.getPosition());
    double giro = Math.toDegrees(Math.atan2(direcao.z, direcao.x)) - 90;
    return Mth.wrapDegrees(giro - camera.getYRot());
  }

  // Chamado a cada quadro pelo OmbroDaCameraMixin: quanto a câmera anda para a direita e para cima (em blocos). Vai para o
  // ombro com foco na 3ª pessoa de costas e volta ao meio sem foco, deslizando. Na 1ª pessoa ou de frente, fica no meio
  public static float[] deslocamentoDoOmbro(boolean terceiraPessoaDeCostas) {
    long agora = Util.getNanos();
    double segundos = ultimoOmbro == 0 ? 0 : Math.min(SEGUNDOS_MAXIMOS_POR_QUADRO, (agora - ultimoOmbro) / 1e9);
    ultimoOmbro = agora;
    if (!terceiraPessoaDeCostas) {
      ombro = 0f;
      return null;
    }
    float alvoDoOmbro = alvo() != null ? 1f : 0f;
    ombro += (float) ((alvoDoOmbro - ombro) * (1 - Math.exp(-VELOCIDADE_DO_OMBRO * segundos)));
    if (ombro < 0.001f) {
      return null;
    }
    return new float[] { OMBRO_PARA_O_LADO * ombro, OMBRO_PARA_CIMA * ombro };
  }

  private static void alternar(Minecraft cliente) {
    if (alvo != SEM_ALVO) {
      soltar();
      voltarSozinho = false;
      return;
    }
    PokemonEntity candidato = candidato(cliente);
    if (candidato != null) {
      alvo = candidato.getId();
      ticksSemVer = 0;
      ultimoGiro = 0;
    }
  }

  private static void conferirOAlvo(Minecraft cliente) {
    if (alvo == SEM_ALVO) {
      travarNoProximo(cliente);
      return;
    }
    PokemonEntity pokemon = alvo();
    if (pokemon == null || !pokemon.isAlive() || !ehAlvo(pokemon) || Captura.dentroDaBola(pokemon)
        || cliente.player == null
        || cliente.player.distanceTo(pokemon) > ALCANCE_TRAVADO) {
      soltar();
      // 23/10: o travado caiu (ou voltou para a bola) no meio da batalha: o foco passa sozinho para o próximo dela
      voltarSozinho = HistoricoDaBatalha.emBatalha();
      return;
    }
    ticksSemVer = estaVisivel(cliente, cliente.player.getEyePosition(), pokemon, 1f) ? 0 : ticksSemVer + 1;
    if (ticksSemVer > TICKS_FORA_DE_VISTA) {
      soltar();
    }
  }

  // 23/10: na batalha, depois que o travado caiu, o foco trava sozinho no próximo Pokémon dela que aparecer (o próximo do
  // treinador, o próximo do adversário, outro selvagem do bando). Acabou a batalha, ou você soltou no G: não volta
  private static void travarNoProximo(Minecraft cliente) {
    if (!voltarSozinho) {
      return;
    }
    if (!HistoricoDaBatalha.emBatalha()) {
      voltarSozinho = false;
      return;
    }
    PokemonEntity proximo = candidato(cliente);
    if (proximo != null) {
      alvo = proximo.getId();
      ticksSemVer = 0;
      ultimoGiro = 0;
      voltarSozinho = false;
    }
  }

  private static void soltar() {
    alvo = SEM_ALVO;
    ultimoGiro = 0;
    somaDoMouse = 0;
  }
}
