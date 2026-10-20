package dev.victor.combatza.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.server.SendOutPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.client.Foco;
import dev.victor.combatza.client.RNoSelvagem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

// O R do Cobblemon (PartySendBinding) nunca mais pede a batalha por turnos (pedido dele, 18/10: "o r usa para iniciar a
// luta no cobblemon, então preciso que tire isso de vez"). Lá, com a mira num selvagem, o R só pede batalha, e o Cobblemon
// ainda recusa quieto se o selvagem está a mais de 12 blocos ou se um Pokémon seu está ocupado. Agora, com um selvagem
// envolvido, o Pokémon da setinha, se está na bola, vai pelo NOSSO pacote (RNoSelvagem). Se ele já está fora, o R faz o
// de sempre do Cobblemon: recolhe. 7.7: na batalha, o R num Pokémon que já está fora (um guarda) faz dele o lutador, sem
// voltar para a bola. O resto do R (jogar no chão, montar, falar com outro jogador) continua do Cobblemon.
// remap = false: os nomes dos métodos são do Cobblemon, não do Minecraft
@Mixin(value = PartySendBinding.class, remap = false)
public class RDoCobblemonMixin {
  // Antes de o Cobblemon olhar a mira: na batalha, o guarda da setinha entra na luta (7.7); com o foco travado, vale o
  // selvagem travado, para onde quer que o corpo esteja virado
  @Inject(method = "checkForTargetInteractions", at = @At("HEAD"), cancellable = true)
  private void antesDaMira(LocalPlayer jogador, Pokemon pokemon, CallbackInfo info) {
    if (RNoSelvagem.entrarNaLuta(pokemon) || RNoSelvagem.jogar(pokemon, Foco.alvo())) {
      info.cancel();
    }
  }

  // Sem foco, a mira do Cobblemon achou alguém. Se for um selvagem: o nosso R, nunca a batalha por turnos
  @Inject(method = "processEntityTarget", at = @At("HEAD"), cancellable = true)
  private void naMira(LocalPlayer jogador, Pokemon pokemon, LivingEntity alvo, CallbackInfo info) {
    if (!(alvo instanceof PokemonEntity selvagem) || !Foco.ehSelvagem(selvagem)) {
      return;
    }
    if (!RNoSelvagem.jogar(pokemon, selvagem)) {
      CobblemonNetwork.sendToServer(new SendOutPokemonPacket(CobblemonClient.INSTANCE.getStorage().getSelectedSlot()));
    }
    info.cancel();
  }
}
