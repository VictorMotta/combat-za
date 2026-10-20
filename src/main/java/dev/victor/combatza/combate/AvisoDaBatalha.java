package dev.victor.combatza.combate;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.rede.AvisoDaBatalhaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

// Como a batalha acabou (pedido dele, 18/10: "em algum lugar tem que mostrar quem está no combate quando inicia e quando
// terminou"; e onde: "acima dos históricos"). Quem está nela, do começo ao fim, é o PainelDaBatalha; aqui ficam o
// resultado no fim e o cartão de quem entra no meio. As frases ficam no nosso arquivo de idioma
public class AvisoDaBatalha {
  // Por que a batalha acabou para o jogador
  public enum Fim {
    VENCEU, FUGIU, SELVAGEM_FUGIU, ACABOU, DESMAIOU, TIME_DESMAIOU
  }

  private static final String FRASE = "cobblemon_combat_za.batalha.";
  private static final int MAXIMO_DE_ROSTOS = 3;

  private AvisoDaBatalha() {
  }

  // Um selvagem entrou numa batalha que já estava valendo: um cartão no histórico, com o rosto dele
  public static void entrou(ServerPlayer jogador, PokemonEntity selvagem) {
    Feedback.historico(jogador, selvagem.getPokemon(),
        Component.translatable(FRASE + "entrou", selvagem.getPokemon().getDisplayName(false)));
  }

  // "rostos": de quem o aviso fala (os derrotados, os que fugiram, ou os que ainda estavam na luta)
  public static void terminou(ServerPlayer jogador, Fim fim, List<Pokemon> rostos) {
    Component texto = switch (fim) {
      case VENCEU -> Component.translatable(FRASE + "venceu");
      case FUGIU -> Component.translatable(FRASE + "fugiu");
      case SELVAGEM_FUGIU -> Component.translatable(FRASE + "selvagem_fugiu", nomes(rostos));
      case ACABOU -> Component.translatable(FRASE + "acabou");
      case DESMAIOU -> Component.translatable(FRASE + "desmaiou");
      case TIME_DESMAIOU -> Component.translatable(FRASE + "time_desmaiou");
    };
    String tipo = switch (fim) {
      case VENCEU -> "venceu";
      case DESMAIOU, TIME_DESMAIOU -> "perdeu";
      default -> "acabou";
    };
    // 8.2b: embaixo do resultado, quanto cada Pokémon ganhou de XP na batalha (Experiencia)
    enviar(jogador, Component.translatable(FRASE + "fim"), texto, tipo, rostos, Experiencia.tirarOsGanhos(jogador));
  }

  // 8.2b: o XP chegou sem batalha nenhuma para terminar (a captura pelas costas, ou o selvagem de uma batalha que já
  // acabou desmaiou depois): o mesmo aviso, com o que cada um ganhou
  public static void semBatalha(ServerPlayer jogador, Pokemon selvagem, boolean capturou, List<Component> ganhos) {
    if (ganhos.isEmpty()) {
      return;
    }
    Component titulo = Component.translatable(FRASE + (capturou ? "captura" : "vitoria"));
    Component texto = Component.translatable(FRASE + (capturou ? "capturado" : "derrotado"),
        selvagem.getDisplayName(false));
    enviar(jogador, titulo, texto, "venceu", List.of(selvagem), ganhos);
  }

  // "Watchog", "Watchog e Mudbray", "Watchog, Mudbray e Zigzagoon"
  private static Component nomes(List<Pokemon> pokemons) {
    MutableComponent nomes = Component.empty();
    for (int i = 0; i < pokemons.size(); i++) {
      if (i > 0) {
        nomes.append(i == pokemons.size() - 1 ? Component.translatable(FRASE + "e") : Component.literal(", "));
      }
      nomes.append(pokemons.get(i).getDisplayName(false));
    }
    return nomes;
  }

  // Sem o nosso mod no cliente, o aviso cai na barra de ação (sem o XP: o Cobblemon já conta no chat)
  private static void enviar(ServerPlayer jogador, Component titulo, Component texto, String tipo, List<Pokemon> quem,
      List<Component> ganhos) {
    if (!ServerPlayNetworking.canSend(jogador, AvisoDaBatalhaPayload.TIPO)) {
      Feedback.barraDeAcao(jogador, titulo.copy().append(" ").append(texto));
      return;
    }
    List<AvisoDaBatalhaPayload.Rosto> rostos = new ArrayList<>();
    for (Pokemon pokemon : quem.subList(0, Math.min(MAXIMO_DE_ROSTOS, quem.size()))) {
      rostos.add(new AvisoDaBatalhaPayload.Rosto(pokemon.getSpecies().getResourceIdentifier().toString(),
          List.copyOf(pokemon.getAspects())));
    }
    ServerPlayNetworking.send(jogador, new AvisoDaBatalhaPayload(titulo, texto, tipo, rostos, ganhos));
  }
}
