package dev.victor.combatza.client;

import com.bedrockk.molang.runtime.MoLangRuntime;
import com.cobblemon.mod.common.api.molang.MoLangFunctions;
import com.cobblemon.mod.common.api.snowstorm.BedrockParticleOptions;
import com.cobblemon.mod.common.client.ClientMoLangFunctions;
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository;
import com.cobblemon.mod.common.client.particle.ParticleStorm;
import com.cobblemon.mod.common.client.render.MatrixWrapper;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Projetil;
import kotlin.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

// 5.2b: o ENCAIXE dos projéteis feitos por nós no Snowstorm (snowstorm.app, o mesmo formato das partículas do Cobblemon).
// Para os golpes sem a parte que voa na batalha do Cobblemon, quando a bola aparece no seu jogo, procura (nesta ordem):
//   cobblemon_combat_za:projetil_<golpe>   (ex.: projetil_swift, o Meteoro)
//   cobblemon_combat_za:projetil_<tipo>    (ex.: projetil_normal)
// O arquivo vai em assets/cobblemon_combat_za/bedrock/particles/ (o Cobblemon lê essa pasta de todos os mods). Achou: a
// partícula nasce PRESA à bola (o emissor anda com ela) e a bola some. Não achou: fica a bola de sempre
public class ProjeteisNossos {
  private ProjeteisNossos() {
  }

  public static void aoAparecer(Projetil projetil) {
    if (!projetil.mostraABola()) {
      return;
    }
    BedrockParticleOptions efeito = procurar("projetil_" + projetil.golpe());
    if (efeito == null) {
      efeito = procurar("projetil_" + projetil.tipoDoGolpe());
    }
    Minecraft cliente = Minecraft.getInstance();
    if (efeito == null || cliente.level == null) {
      return;
    }
    // O ponto de onde a partícula sai: o Cobblemon pergunta "onde estou" (getOrigin) e esta função responde com a posição
    // da bola naquele quadro. Assim o emissor anda junto com ela
    MatrixWrapper meio = new MatrixWrapper();
    meio.setUpdateFunction(eu -> {
      float parcial = cliente.getTimer().getGameTimeDeltaPartialTick(true);
      Vec3 posicao = projetil.getPosition(parcial).add(0, projetil.getBbHeight() / 2, 0);
      eu.updatePosition(posicao);
      return Unit.INSTANCE;
    });
    MoLangRuntime molang = ClientMoLangFunctions.INSTANCE.setupClient(MoLangFunctions.INSTANCE.setup(new MoLangRuntime()));
    new ParticleStorm(efeito, meio, meio, cliente.level, projetil::getDeltaMovement, () -> !projetil.isRemoved(),
        () -> !projetil.isInvisible(), null, () -> Unit.INSTANCE, () -> null, molang, projetil).spawn();
    projetil.usarParticulaNossa();
  }

  private static BedrockParticleOptions procurar(String nome) {
    return BedrockParticleOptionsRepository.INSTANCE
        .getEffect(ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, nome));
  }
}
