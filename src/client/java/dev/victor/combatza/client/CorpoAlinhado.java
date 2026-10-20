package dev.victor.combatza.client;

import java.util.HashMap;
import java.util.Map;

import dev.victor.combatza.rede.AlinharPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// 5.4g (dele: "a animação em um momento saiu para o lado errado, porém deu o dano no bicho certo; isso não pode
// acontecer"): o Cobblemon calcula a direção do visual da batalha no espaço do CORPO de quem ataca, uma vez, quando o efeito
// nasce (ParticleStorm.distanceTo → emitterSpaceMatrix). O servidor só manda o giro da cabeça; o corpo de um mob, no seu
// jogo, acompanha devagar e só quando a cabeça passa do limite, e pode ficar dezenas de graus fora. Durante um golpe com
// alvo (AlinharPayload), o seu jogo vira o corpo, a cabeça e o giro do atacante exatamente para o alvo, a cada tick
public class CorpoAlinhado {
  private record Alinhamento(int alvo, long ateOTick) {
  }

  private static final Map<Integer, Alinhamento> ALINHANDO = new HashMap<>();

  private CorpoAlinhado() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(AlinharPayload.TIPO, (pacote, contexto) -> {
      Minecraft cliente = contexto.client();
      if (cliente.level != null) {
        ALINHANDO.put(pacote.atacante(), new Alinhamento(pacote.alvo(), cliente.level.getGameTime() + pacote.ticks()));
      }
    });
    ClientTickEvents.END_CLIENT_TICK.register(CorpoAlinhado::alinhar);
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> ALINHANDO.clear());
  }

  private static void alinhar(Minecraft cliente) {
    if (cliente.level == null) {
      ALINHANDO.clear();
      return;
    }
    long agora = cliente.level.getGameTime();
    ALINHANDO.values().removeIf(alinhamento -> agora > alinhamento.ateOTick());
    for (Map.Entry<Integer, Alinhamento> item : ALINHANDO.entrySet()) {
      Entity alvo = cliente.level.getEntity(item.getValue().alvo());
      if (cliente.level.getEntity(item.getKey()) instanceof LivingEntity atacante && alvo != null) {
        virar(atacante, alvo);
      }
    }
  }

  // O mesmo giro do servidor (VisualDosGolpes.olharParaOAlvo), com as posições do seu jogo. O "O" de cada campo é o valor
  // do quadro anterior: igualando, o desenho não passa pelo meio do caminho
  private static void virar(LivingEntity atacante, Entity alvo) {
    double dx = alvo.getX() - atacante.getX();
    double dz = alvo.getZ() - atacante.getZ();
    if (dx * dx + dz * dz < 1.0E-4) {
      return;
    }
    float giro = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    atacante.setYRot(giro);
    atacante.yRotO = giro;
    atacante.yBodyRot = giro;
    atacante.yBodyRotO = giro;
    atacante.yHeadRot = giro;
    atacante.yHeadRotO = giro;
  }
}
