package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.rede.GolpeDoSelvagemPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

// 7.2c (a placa do Z-A, ideia dele da 4.5: "o NOME do golpe que ele está usando numa faixa colorida + barra de carga"): o
// golpe do selvagem avisa quem está vendo ele quando a carga começa e quando ela acaba. A barra de carga lá em cima é só do
// seu Pokémon (7.2a); a do selvagem fica na placa dele (FaixaDoGolpe, no seu jogo)
public class AvisoDoGolpeDoSelvagem {
  private AvisoDoGolpeDoSelvagem() {
  }

  public static void comecou(Execucao execucao, long agora) {
    PokemonEntity selvagem = execucao.getEntidade();
    if (!execucao.getPokemon().isWild() || selvagem == null) {
      return;
    }
    Move golpe = execucao.getGolpe();
    int ticks = (int) Math.max(1, execucao.getTickImpacto() - agora);
    enviar(selvagem, new GolpeDoSelvagemPayload(selvagem.getId(), golpe.getName(), golpe.getType().getShowdownId(), ticks));
  }

  // O impacto, ou ele perdeu o golpe (cancelado, não alcançou)
  public static void acabou(Execucao execucao) {
    PokemonEntity selvagem = execucao.getEntidade();
    if (!execucao.getPokemon().isWild() || selvagem == null) {
      return;
    }
    enviar(selvagem, new GolpeDoSelvagemPayload(selvagem.getId(), "", "", 0));
  }

  private static void enviar(PokemonEntity selvagem, GolpeDoSelvagemPayload aviso) {
    for (ServerPlayer quemVe : PlayerLookup.tracking(selvagem)) {
      if (ServerPlayNetworking.canSend(quemVe, GolpeDoSelvagemPayload.TIPO)) {
        ServerPlayNetworking.send(quemVe, aviso);
      }
    }
  }
}
