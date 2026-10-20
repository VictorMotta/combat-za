package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Feedback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class Intimidacao {
  private static final String HABILIDADE = "intimidate";

  private Intimidacao() {
  }

  // O jogador entrou no combate: os Pokémon dele que estão fora da bola intimidam os selvagens
  public static void doTime(ServerPlayer jogador, List<PokemonEntity> selvagens) {
    for (PokemonEntity entidade : foraDaBola(jogador)) {
      aoEntrar(entidade, selvagens);
    }
  }

  // O selvagem entrou no combate: ele intimida os Pokémon fora da bola de cada jogador
  public static void doSelvagem(PokemonEntity selvagem, List<ServerPlayer> jogadores) {
    List<PokemonEntity> inimigos = new ArrayList<>();
    for (ServerPlayer jogador : jogadores) {
      inimigos.addAll(foraDaBola(jogador));
    }
    aoEntrar(selvagem, inimigos);
  }

  // Showdown, onStart: quem tem Intimidação entra, e o Ataque de cada inimigo cai 1
  public static void aoEntrar(PokemonEntity quem, List<PokemonEntity> inimigos) {
    if (!Habilidades.tem(quem.getPokemon(), HABILIDADE) || !(quem.level() instanceof ServerLevel nivel)) {
      return;
    }
    long agora = nivel.getServer().getTickCount();
    for (PokemonEntity inimigo : inimigos) {
      intimidar(quem.getPokemon(), inimigo.getPokemon(), agora);
    }
  }

  private static void intimidar(Pokemon quem, Pokemon vitima, long agora) {
    ServerPlayer jogador = quem.getOwnerPlayer() != null ? quem.getOwnerPlayer() : vitima.getOwnerPlayer();
    Component inicio = quem.getDisplayName(false).copy().append(" intimidou ").append(vitima.getDisplayName(false));
    if (Habilidades.ignoraIntimidacao(vitima) || Habilidades.naoDeixaCair(vitima, "atk")) {
      CobblemonCombatZA.LOGGER.info("INTIMIDAÇÃO BLOQUEADA: {} tem {}", vitima.getSpecies().getName(),
          vitima.getAbility().getName());
      avisar(jogador, quem, inicio.copy().append(", que não ligou: ").append(Habilidades.nome(vitima)).append("!"));
      return;
    }
    // 5b.3: a Névoa do lado dele
    if (CampoEBarreiras.bloqueiaQueda(vitima)) {
      CobblemonCombatZA.LOGGER.info("INTIMIDAÇÃO BLOQUEADA: {} está na Névoa", vitima.getSpecies().getName());
      avisar(jogador, quem, Component.translatable("cobblemon.battle.activate.mist", vitima.getDisplayName(false)));
      return;
    }
    int mudou = Estagios.mudar(vitima, "atk", -1, agora);
    CobblemonCombatZA.LOGGER.info("INTIMIDAÇÃO: {} intimidou {} (Ataque agora {})", quem.getSpecies().getName(),
        vitima.getSpecies().getName(), Estagios.de(vitima, "atk"));
    avisar(jogador, quem, inicio.copy().append(mudou < 0 ? ": o Ataque caiu!" : ": o Ataque não pode cair mais!"));
  }

  // A frase vai para o histórico da batalha, com o rosto de quem intimidou
  private static void avisar(ServerPlayer jogador, Pokemon quem, Component mensagem) {
    Feedback.historico(jogador, quem, mensagem);
  }

  private static List<PokemonEntity> foraDaBola(ServerPlayer jogador) {
    List<PokemonEntity> entidades = new ArrayList<>();
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (pokemon.getEntity() != null) {
        entidades.add(pokemon.getEntity());
      }
    }
    return entidades;
  }
}
