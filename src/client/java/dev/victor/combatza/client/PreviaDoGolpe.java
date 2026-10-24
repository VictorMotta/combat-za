package dev.victor.combatza.client;

import java.util.Optional;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;

import dev.victor.combatza.golpes.Marcador;
import dev.victor.combatza.golpes.Mira;
import dev.victor.combatza.rede.UsarGolpePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 6.11 (decidido por ele em 19/10, "estilo MOBA"; feito na sequência de 20/10): SEGURAR a tecla do golpe mostra a prévia (o
// que ele vai pegar, DesenhoDaPrevia); SOLTAR lança; o botão direito (a tecla "Usar item" dos Controles) CANCELA, sem gastar a
// recarga. Um TOQUE rápido (menos de 0,2 s) sai como sempre: o golpe agora sai ao soltar, que é o único jeito de separar o
// toque do segurar (no toque, o atraso é de no máximo 0,2 s).
// Abrir uma tela (chat, inventário, Esc) no meio cancela: o Minecraft solta todas as teclas ao abrir uma tela, e isso não pode
// virar um golpe.
// 6.11c, o MARCADOR: com o foco travado (G) num golpe que vai a algum lugar (Marcador.precisa), o mouse move um círculo azul
// no chão em volta do alvo, o lugar de onde o seu Pokémon vai soltar o golpe (a câmera continua seguindo o alvo, e o WASD
// continua andando). Ele começa onde o Pokémon já está: soltar sem mexer o mouse é o golpe de sempre
public class PreviaDoGolpe {
  private static final long PARA_MOSTRAR = 200;
  private static final int NENHUM = -1;
  // Quanto o marcador anda por unidade do mouse (~1 pixel com a sensibilidade padrão)
  private static final double BLOCOS_POR_UNIDADE_DO_MOUSE = 0.03;

  private static int segurando = NENHUM;
  private static long desde = 0;
  private static boolean cancelado = false;
  // O marcador: um deslocamento no chão a partir dos pés do alvo do foco (null = sem marcador)
  private static Vec3 marcador = null;

  private PreviaDoGolpe() {
  }

  public static void registrar() {
    // Antes de o Minecraft ler as teclas do tick (o "Usar item"): o clique de cancelar é nosso, e não usa o item da mão
    ClientTickEvents.START_CLIENT_TICK.register(PreviaDoGolpe::conferirOCancelar);
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> esquecer());
  }

  // TeclasDeGolpe: a tecla deste golpe foi apertada agora. Outro golpe apertado no meio troca a prévia (o primeiro não sai)
  static void apertou(int slot) {
    segurando = slot;
    desde = Util.getMillis();
    cancelado = false;
    marcador = null;
  }

  // TeclasDeGolpe, a cada tick, depois dos apertos: soltou → lança (cancelado: só esquece)
  static void atualizar(Minecraft cliente) {
    if (segurando == NENHUM) {
      return;
    }
    if (cliente.screen != null || cliente.player == null || LutadorNoCliente.vagaDoComandado() < 0) {
      esquecer();
      return;
    }
    if (TeclasDeGolpe.tecla(segurando).isDown()) {
      conferirOMarcador(cliente);
      return;
    }
    if (!cancelado) {
      enviar(segurando);
    }
    esquecer();
  }

  private static void conferirOCancelar(Minecraft cliente) {
    if (segurando == NENHUM || cancelado) {
      return;
    }
    boolean clicou = false;
    while (cliente.options.keyUse.consumeClick()) {
      clicou = true;
    }
    if (clicou) {
      cancelado = true;
      cliente.options.keyUse.setDown(false);
    }
  }

  private static void enviar(int slot) {
    int slotPokemon = LutadorNoCliente.vagaDoComandado();
    if (slotPokemon >= 0 && ClientPlayNetworking.canSend(UsarGolpePayload.TIPO)) {
      ClientPlayNetworking.send(new UsarGolpePayload(slot, slotPokemon, Foco.idDoAlvo(), marcadorParaEnviar()));
    }
  }

  // 6.11c: o marcador vai junto (o servidor confere tudo de novo); sem ele, vazio
  private static Optional<Vector3f> marcadorParaEnviar() {
    return marcador == null ? Optional.empty()
        : Optional.of(new Vector3f((float) marcador.x, 0f, (float) marcador.z));
  }

  // A cada tick segurando: com a prévia na tela e o foco, o marcador começa (onde o Pokémon está); sem foco, some
  private static void conferirOMarcador(Minecraft cliente) {
    PokemonEntity entidade = comandado();
    Move golpe = golpeSegurado();
    PokemonEntity alvo = Foco.alvo();
    boolean pode = mostrando() != NENHUM && alvo != null && entidade != null && golpe != null && Marcador.precisa(golpe)
        && cliente.options.getCameraType() != CameraType.THIRD_PERSON_FRONT && cliente.player.getVehicle() == null
        && !entidade.isVehicle();
    if (!pode) {
      marcador = null;
    } else if (marcador == null) {
      marcador = Marcador.inicial(golpe, entidade.position(), alvo.position());
    }
  }

  // TravarACameraMixin: com o marcador valendo, o mouse move ele (e não troca de alvo)
  public static boolean moveOMarcador() {
    return marcador != null && mostrando() != NENHUM;
  }

  // O mouse em relação à tela: para a direita = para a direita; para cima = para a frente. Os números são os do giro do
  // Minecraft: positivo = para a direita e para BAIXO (como a tela conta as linhas)
  public static void moverMarcador(double paraADireita, double paraBaixo) {
    Move golpe = golpeSegurado();
    if (marcador == null || golpe == null) {
      return;
    }
    double giro = Math.toRadians(Minecraft.getInstance().gameRenderer.getMainCamera().getYRot());
    Vec3 frente = new Vec3(-Math.sin(giro), 0, Math.cos(giro));
    Vec3 direita = new Vec3(-Math.cos(giro), 0, -Math.sin(giro));
    Vec3 andou = direita.scale(paraADireita * BLOCOS_POR_UNIDADE_DO_MOUSE)
        .add(frente.scale(-paraBaixo * BLOCOS_POR_UNIDADE_DO_MOUSE));
    marcador = Marcador.limitar(golpe, marcador.add(andou));
  }

  // O marcador no mundo agora (no chão, perto do alvo que anda), ou null
  static Vec3 marcadorNoMundo(float parcial) {
    PokemonEntity alvo = Foco.alvo();
    Minecraft cliente = Minecraft.getInstance();
    if (marcador == null || alvo == null || cliente.level == null) {
      return null;
    }
    return Mira.noChao(cliente.level, alvo.getPosition(parcial).add(marcador));
  }

  static void esquecer() {
    segurando = NENHUM;
    cancelado = false;
    marcador = null;
  }

  // De onde o golpe vai sair: onde o Pokémon está, ou o marcador
  static Vec3 ondeOPokemonVai(Entity pokemon, float parcial) {
    Vec3 noMarcador = marcadorNoMundo(parcial);
    return noMarcador != null ? noMarcador : pokemon.getPosition(parcial);
  }

  // O Pokémon que recebe as suas teclas (o lutador, ou o da setinha) fora da bola, ou null
  static PokemonEntity comandado() {
    int vaga = LutadorNoCliente.vagaDoComandado();
    Pokemon pokemon = vaga < 0 ? null : CobblemonClient.INSTANCE.getStorage().getParty().get(vaga);
    return pokemon != null && pokemon.getState() instanceof SentOutState fora ? fora.getEntity() : null;
  }

  // O golpe da tecla segurada, ou null
  static Move golpeSegurado() {
    int vaga = LutadorNoCliente.vagaDoComandado();
    Pokemon pokemon = vaga < 0 ? null : CobblemonClient.INSTANCE.getStorage().getParty().get(vaga);
    return pokemon == null || segurando == NENHUM ? null : pokemon.getMoveSet().get(segurando);
  }

  // O golpe com a prévia na tela agora (segurado há 0,2 s ou mais, sem cancelar); NENHUM = nenhum
  public static int mostrando() {
    return segurando != NENHUM && !cancelado && Util.getMillis() - desde >= PARA_MOSTRAR ? segurando : NENHUM;
  }
}
