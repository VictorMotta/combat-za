package dev.victor.combatza.combate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.rede.PainelDaBatalhaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// O painel de quem está na batalha (pedido dele, 18/10: "quero um hud mostrando quem tá na batalha", cada pessoa com a
// foto e os Pokémon dela em jogo, contra os selvagens). A cada 5 ticks, monta o painel de cada batalha e manda a quem
// está nela, só quando ele muda. Quem saiu da batalha recebe o painel vazio uma vez
public class PainelDaBatalha {
  private static final int A_CADA_TICKS = 5;
  // A vida vai arredondada de 2 em 2%, para um arranhão não reenviar o painel
  private static final float PASSOS_DA_VIDA = 50f;
  private static final PainelDaBatalhaPayload VAZIO = new PainelDaBatalhaPayload(List.of(), List.of());
  private static final Map<UUID, PainelDaBatalhaPayload> ULTIMO_ENVIO = new HashMap<>();

  private PainelDaBatalha() {
  }

  public static void atualizar(MinecraftServer servidor) {
    if (servidor.getTickCount() % A_CADA_TICKS != 0) {
      return;
    }
    Set<UUID> emBatalha = new HashSet<>();
    for (Combate combate : GerenciarCombates.todos()) {
      PainelDaBatalhaPayload painel = montar(combate);
      for (ServerPlayer jogador : combate.getJogadores()) {
        emBatalha.add(jogador.getUUID());
        if (!painel.equals(ULTIMO_ENVIO.get(jogador.getUUID()))
            && ServerPlayNetworking.canSend(jogador, PainelDaBatalhaPayload.TIPO)) {
          ULTIMO_ENVIO.put(jogador.getUUID(), painel);
          ServerPlayNetworking.send(jogador, painel);
        }
      }
    }
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      if (!emBatalha.contains(jogador.getUUID()) && ULTIMO_ENVIO.remove(jogador.getUUID()) != null
          && ServerPlayNetworking.canSend(jogador, PainelDaBatalhaPayload.TIPO)) {
        ServerPlayNetworking.send(jogador, VAZIO);
      }
    }
  }

  // Cada jogador com o lutador dele; depois, os selvagens. 7.8 (dele, 20/10: "meus pokemons guarda não deveriam aparecer
  // dentro da batalha, nem faz sentido"): os guardas não lutam a batalha, então ficam fora do painel; quem está de guarda
  // aparece sempre na fileirinha deles, em cima do painel do time (client.FileiraDosGuardas)
  private static PainelDaBatalhaPayload montar(Combate combate) {
    List<PainelDaBatalhaPayload.Lado> lados = new ArrayList<>();
    for (ServerPlayer jogador : combate.getJogadores()) {
      List<PainelDaBatalhaPayload.Foto> fotos = new ArrayList<>();
      Pokemon lutador = Lutadores.de(jogador);
      if (lutador != null) {
        fotos.add(foto(lutador, true));
      }
      lados.add(new PainelDaBatalhaPayload.Lado(jogador.getUUID(), jogador.getGameProfile().getName(), fotos));
    }
    List<PainelDaBatalhaPayload.Foto> selvagens = new ArrayList<>();
    for (PokemonEntity selvagem : combate.getSelvagens()) {
      selvagens.add(foto(selvagem.getPokemon(), true));
    }
    return new PainelDaBatalhaPayload(lados, selvagens);
  }

  private static PainelDaBatalhaPayload.Foto foto(Pokemon pokemon, boolean grande) {
    float vida = Vida.pvAtual(pokemon) / (float) Math.max(1, pokemon.getMaxHealth());
    return new PainelDaBatalhaPayload.Foto(pokemon.getSpecies().getResourceIdentifier().toString(),
        List.copyOf(pokemon.getAspects()), Math.round(vida * PASSOS_DA_VIDA) / PASSOS_DA_VIDA, grande,
        pokemon.getEntity() == null ? -1 : pokemon.getEntity().getId(), pokemon.getUuid());
  }

  public static void esquecer(ServerPlayer jogador) {
    ULTIMO_ENVIO.remove(jogador.getUUID());
  }

  public static void limpar() {
    ULTIMO_ENVIO.clear();
  }
}
