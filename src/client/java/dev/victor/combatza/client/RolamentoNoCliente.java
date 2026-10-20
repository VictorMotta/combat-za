package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.victor.combatza.rede.RolarPayload;
import dev.victor.combatza.rede.RolouPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// O rolamento do treinador, do lado do cliente (escolhas dele, 18/10): rola uns 3 blocos para onde ele anda; parado, para
// trás da câmera. Um por segundo, em qualquer lugar. A tecla é a "Rolar" de Controles: sem tecla (o padrão), Shift duas
// vezes; com uma tecla ou um botão do mouse (a rodinha clicada, por exemplo), um toque nela (pedido dele, 6.7b).
// O empurrão é daqui (o movimento do jogador é do cliente); a invencibilidade é do servidor (combate.Rolamento).
// A cambalhota é desenhada aqui, para ele e para quem o servidor avisar (RolouPayload)
public class RolamentoNoCliente {
  private record Rolando(long comecou, float direcao) {
  }

  private static final long JANELA_DO_TOQUE_DUPLO = 300;
  private static final long RECARGA = 1000;
  // A cambalhota dura o mesmo que a invencibilidade (meio segundo)
  private static final long DURACAO = 500;
  private static final double DISTANCIA = 3.0;
  private static final double PASSO = 0.5;
  // Uma queda maior que isto na frente é um buraco: o rolamento para antes, como a beirada segura do agachar
  private static final double DEGRAU = 1.01;
  // No chão, o Minecraft multiplica a velocidade pelo atrito do bloco × 0,91 a cada tick
  private static final float AR_DO_MINECRAFT = 0.91f;
  private static final float PARADO = 0.01f;

  private static final Map<Integer, Rolando> ROLANDO = new HashMap<>();
  private static KeyMapping tecla;
  private static boolean teclaEstavaApertada = false;
  private static long primeiroToque = 0;
  private static long prontoEm = 0;

  private RolamentoNoCliente() {
  }

  // 7.10: o tutorial mostra a tecla como está nos Controles
  static KeyMapping tecla() {
    return tecla;
  }

  public static void registrar() {
    tecla = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.rolar",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), TeclasDeGolpe.CATEGORIA));
    ClientPlayNetworking.registerGlobalReceiver(RolouPayload.TIPO, (pacote, contexto) -> ROLANDO.put(pacote.entidade(),
        new Rolando(Util.getMillis(), pacote.direcao())));
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> ROLANDO.clear());
    ClientTickEvents.END_CLIENT_TICK.register(RolamentoNoCliente::conferirOShift);
    WorldRenderEvents.END.register(contexto -> conferirATecla());
  }

  // Sem tecla em Controles: cada aperto da tecla de agachar conta um "clique" (o consumeClick pega até os toques rápidos,
  // que começam e acabam entre dois ticks). Dois cliques em até 0,3 segundo = rolar. Com tecla, os cliques do Shift são
  // jogados fora: guardados, virariam um toque duplo na hora em que a tecla fosse tirada
  private static void conferirOShift(Minecraft cliente) {
    while (cliente.options.keyShift.consumeClick()) {
      if (!tecla.isUnbound()) {
        continue;
      }
      long agora = Util.getMillis();
      if (agora - primeiroToque <= JANELA_DO_TOQUE_DUPLO) {
        primeiroToque = 0;
        rolar(cliente, agora);
      } else {
        primeiroToque = agora;
      }
    }
  }

  // Com uma tecla em Controles: um toque nela rola. Ela é lida direto do teclado e do mouse (TeclaFisica), a cada quadro,
  // para pegar até o clique mais rápido. Direto porque o Minecraft só avisa UMA função por tecla, e a rodinha clicada,
  // por exemplo, já tem várias no modpack
  private static void conferirATecla() {
    Minecraft cliente = Minecraft.getInstance();
    boolean apertada = cliente.screen == null && TeclaFisica.estaApertada(tecla);
    if (apertada && !teclaEstavaApertada) {
      rolar(cliente, Util.getMillis());
    }
    teclaEstavaApertada = apertada;
  }

  private static void rolar(Minecraft cliente, long agora) {
    LocalPlayer jogador = cliente.player;
    if (jogador == null || agora < prontoEm || !jogador.onGround() || jogador.isPassenger() || jogador.isFallFlying()
        || jogador.isSwimming() || jogador.isSpectator() || jogador.getAbilities().flying
        || !ClientPlayNetworking.canSend(RolarPayload.TIPO)) {
      return;
    }
    Vec3 direcao = direcao(cliente, jogador);
    double distancia = semCair(jogador, direcao);
    if (distancia < PASSO) {
      return;
    }
    // A velocidade que, freada pelo atrito do chão a cada tick, anda exatamente a distância (no gelo, escorrega devagar)
    float atrito = jogador.getBlockStateOn().getBlock().getFriction() * AR_DO_MINECRAFT;
    double impulso = distancia * (1 - atrito);
    jogador.setDeltaMovement(direcao.x * impulso, jogador.getDeltaMovement().y, direcao.z * impulso);
    float giro = (float) Math.toDegrees(Math.atan2(-direcao.x, direcao.z));
    ROLANDO.put(jogador.getId(), new Rolando(agora, giro));
    prontoEm = agora + RECARGA;
    ClientPlayNetworking.send(new RolarPayload(giro));
  }

  // Andando: a mesma conta com que o Minecraft move o jogador (Entity.getInputVector: as teclas de andar giradas pelo
  // giro do jogador), por isso vale com ou sem o Better Third Person. Parado: para trás de onde a câmera olha (com o
  // foco, para longe do selvagem)
  private static Vec3 direcao(Minecraft cliente, LocalPlayer jogador) {
    float lado = jogador.input.leftImpulse;
    float frente = jogador.input.forwardImpulse;
    if (Math.abs(lado) < PARADO && Math.abs(frente) < PARADO) {
      float costas = (cliente.gameRenderer.getMainCamera().getYRot() + 180f) * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.sin(costas), 0, Mth.cos(costas));
    }
    float seno = Mth.sin(jogador.getYRot() * Mth.DEG_TO_RAD);
    float cosseno = Mth.cos(jogador.getYRot() * Mth.DEG_TO_RAD);
    return new Vec3(lado * cosseno - frente * seno, 0, frente * cosseno + lado * seno).normalize();
  }

  // Quanto dá para rolar sem cair num buraco: de meio em meio bloco, a caixa do jogador naquele ponto precisa ter chão
  // até 1 bloco abaixo (noCollision = nada para pisar)
  private static double semCair(LocalPlayer jogador, Vec3 direcao) {
    double seguro = 0;
    for (double passo = PASSO; passo <= DISTANCIA; passo += PASSO) {
      Vec3 andou = direcao.scale(passo);
      if (jogador.level().noCollision(jogador, jogador.getBoundingBox().move(andou).expandTowards(0, -DEGRAU, 0))) {
        break;
      }
      seguro = passo;
    }
    return seguro;
  }

  // Chamado pelo GiroDoRolamentoMixin antes de o Minecraft virar o corpo (setupRotations): uma cambalhota inteira na
  // direção do rolamento, em volta do meio do corpo, começando e terminando devagar. O eixo é o "de lado" da direção
  // (x, z): (z, 0, -x), o que faz a cabeça ir para a frente primeiro
  public static void girar(LivingEntity entidade, PoseStack pose, float escala) {
    Rolando rolando = ROLANDO.get(entidade.getId());
    if (rolando == null) {
      return;
    }
    float progresso = (Util.getMillis() - rolando.comecou()) / (float) DURACAO;
    if (progresso >= 1f) {
      ROLANDO.remove(entidade.getId());
      return;
    }
    float suave = progresso * progresso * (3f - 2f * progresso);
    float x = -Mth.sin(rolando.direcao() * Mth.DEG_TO_RAD);
    float z = Mth.cos(rolando.direcao() * Mth.DEG_TO_RAD);
    float meio = entidade.getBbHeight() / 2f / escala;
    pose.translate(0f, meio, 0f);
    pose.mulPose(new Quaternionf().rotateAxis(suave * Mth.TWO_PI, z, 0f, -x));
    pose.translate(0f, -meio, 0f);
  }
}
