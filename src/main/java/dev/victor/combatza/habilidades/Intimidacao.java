package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.itens.ItensDeEstagio;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class Intimidacao {
  private static final String HABILIDADE = "intimidate";
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

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
    // 21/10 (habilidades, lote da entrada e das imunidades): o Cão de Guarda (sobe) e a Armadura Espelhada (devolve)
    if (HabilidadesDeEstagio.reagiuAIntimidacao(quem, vitima)) {
      return;
    }
    if (Habilidades.ignoraIntimidacao(vitima) || Habilidades.naoDeixaCair(vitima, "atk")) {
      CobblemonCombatZA.LOGGER.info("INTIMIDAÇÃO BLOQUEADA: {} tem {}", vitima.getSpecies().getName(),
          Habilidades.atual(vitima));
      avisar(jogador, quem, Component.translatable(FRASE + "intimidou_nao_ligou", quem.getDisplayName(false),
          vitima.getDisplayName(false), Habilidades.nome(vitima)));
      return;
    }
    // Lote 3 dos itens: o Amuleto Purificador
    if (ItensDeEstagio.amuletoSegura(vitima)) {
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
    // 21/10: com o Contrário, a Intimidação SOBE o Ataque
    Component frase = mudou > 0
        ? Component.translatable(Estagios.FRASE + "subiu", Estagios.nomeDoAtributo("atk", vitima)).append(" (")
            .append(Habilidades.nome(vitima)).append(")")
        : Component.translatable(FRASE + (mudou < 0 ? "intimidou" : "intimidou_no_minimo"), quem.getDisplayName(false),
            vitima.getDisplayName(false));
    avisar(jogador, quem, frase);
    // Lote 3 dos itens: a Erva Branca e a Mochila Ejetora na queda; o Orbe de Adrenalina sobe a Velocidade
    if (mudou < 0) {
      ItensDeEstagio.mudaram(vitima, Map.of("atk", mudou));
      // 21/10: o Competitivo e o Desafiador
      HabilidadesDeEstagio.desafiar(vitima, Map.of("atk", mudou));
    }
    ItensDeEstagio.orbeDeAdrenalina(vitima, mudou);
    // 21/10 (habilidades, lote das reações): a Covardia sobe a Velocidade de quem foi intimidado
    HabilidadesQueReagem.intimidado(vitima);
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
