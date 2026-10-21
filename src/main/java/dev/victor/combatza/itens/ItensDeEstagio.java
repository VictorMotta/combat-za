package dev.victor.combatza.itens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.golpes.AplicarEfeitos;
import dev.victor.combatza.golpes.Estagios;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// Lote 3 dos itens segurados: os que reagem quando um atributo SOBE ou CAI (AplicarEfeitos e Intimidacao avisam aqui):
// - Erva Branca: algum atributo caiu → as quedas somem (a erva some);
// - Mochila Ejetora: algum atributo caiu → o seu Pokémon volta para a bola, e você manda o próximo (a mochila some). No
//   selvagem, nada (numa batalha contra selvagem dos jogos, ele não tem para quem trocar);
// - Erva Imitadora: o inimigo subiu algum atributo → quem segura sobe o mesmo (a erva some);
// - Amuleto Purificador: os golpes (e a Intimidação) dos outros não baixam os atributos de quem segura;
// - Orbe de Adrenalina: intimidado → Velocidade +1 (o orbe some).
// E a conta de todos os itens que mexem num atributo (as bagas de aperto, a Política de Fraqueza...): mudar(), com a frase
public class ItensDeEstagio {
  // Fora da batalha, a mudança que veio de um item dura 30 s (na batalha, os estágios ficam até ela acabar)
  private static final long SEGUNDOS = 30;
  // A Erva Imitadora copiando: o que ela sobe não faz outra erva copiar de volta
  private static boolean copiando = false;
  private static final List<String> ORDEM = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");

  private ItensDeEstagio() {
  }

  // Um item mexeu num atributo: muda e avisa ("O Ataque de Pikachu subiu muito!"), como os golpes. Devolve quanto mudou
  public static int mudar(Pokemon pokemon, String atributo, int quanto) {
    return mudarVarios(pokemon, Map.of(atributo, quanto)).getOrDefault(atributo, 0);
  }

  // Vários de uma vez (a Política de Fraqueza: Ataque e Ataque Especial): as reações (Erva Imitadora...) veem tudo junto
  public static Map<String, Integer> mudarVarios(Pokemon pokemon, Map<String, Integer> pedidos) {
    long ate = agora(pokemon) + SEGUNDOS * SharedConstants.TICKS_PER_SECOND;
    Map<String, Integer> mudaram = new LinkedHashMap<>();
    for (String atributo : ORDEM) {
      Integer quanto = pedidos.get(atributo);
      if (quanto == null) {
        continue;
      }
      int mudou = Estagios.mudar(pokemon, atributo, quanto, ate);
      ItensSegurados.avisar(pokemon, Component.translatable(Estagios.FRASE + AplicarEfeitos.comoMudou(quanto, mudou),
          Estagios.nomeDoAtributo(atributo, pokemon)));
      CobblemonCombatZA.LOGGER.info("ITEM: {} {} {} (agora {})", pokemon.getSpecies().getName(), atributo,
          quanto > 0 ? "+" + quanto : quanto, Estagios.de(pokemon, atributo));
      if (mudou != 0) {
        mudaram.put(atributo, mudou);
      }
    }
    if (!mudaram.isEmpty()) {
      mudaram(pokemon, mudaram);
    }
    return mudaram;
  }

  // O Amuleto Purificador segura a queda que veio de outro? (avisa)
  public static boolean amuletoSegura(Pokemon pokemon) {
    if (!ItensSegurados.tem(pokemon, ItensSegurados.AMULETO_PURIFICADOR)) {
      return false;
    }
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon_combat_za.item.amuleto", ItensSegurados.nome(pokemon),
        pokemon.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("ITEM: {} não teve a queda (Amuleto Purificador)", pokemon.getSpecies().getName());
    return true;
  }

  // Os atributos de "pokemon" mudaram ("mudancas": quanto cada um mudou de verdade)
  public static void mudaram(Pokemon pokemon, Map<String, Integer> mudancas) {
    if (mudancas.values().stream().anyMatch(valor -> valor < 0)) {
      ervaBranca(pokemon);
      mochilaEjetora(pokemon);
    }
    Map<String, Integer> subidas = mudancas.entrySet().stream().filter(mudanca -> mudanca.getValue() > 0)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    if (!subidas.isEmpty() && !copiando) {
      ervaImitadora(pokemon, subidas);
    }
  }

  private static void ervaBranca(Pokemon pokemon) {
    if (!ItensSegurados.disponivel(pokemon, "whiteherb") || !Estagios.tirarQuedas(pokemon)) {
      return;
    }
    ItensSegurados.gastar(pokemon, "whiteherb");
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.whiteherb",
        pokemon.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("ITEM: {} desfez as quedas (Erva Branca)", pokemon.getSpecies().getName());
  }

  private static void mochilaEjetora(Pokemon pokemon) {
    if (!ItensSegurados.disponivel(pokemon, "ejectpack") || !podeVoltar(pokemon)) {
      return;
    }
    ItensSegurados.gastar(pokemon, "ejectpack");
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.ejectpack",
        pokemon.getDisplayName(false)));
    recolher(pokemon, "Mochila Ejetora");
  }

  // Um Pokémon do jogador, fora da bola e numa batalha (o selvagem não tem para quem trocar)
  public static boolean podeVoltar(Pokemon pokemon) {
    return !pokemon.isWild() && pokemon.getEntity() != null && pokemon.getOwnerPlayer() != null
        && GerenciarCombates.estaEmCombate(pokemon);
  }

  // A troca forçada dos jogos, no tempo real: ele volta para a bola, e o dono manda o próximo (a tecla de soltar)
  public static void recolher(Pokemon pokemon, String porque) {
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade != null) {
      entidade.recallWithAnimation();
      CobblemonCombatZA.LOGGER.info("ITEM: {} voltou para a bola ({})", pokemon.getSpecies().getName(), porque);
    }
  }

  private static void ervaImitadora(Pokemon quemSubiu, Map<String, Integer> subidas) {
    Combate combate = GerenciarCombates.combateDe(quemSubiu);
    if (combate == null) {
      return;
    }
    for (Pokemon inimigo : inimigos(combate, quemSubiu)) {
      if (!ItensSegurados.disponivel(inimigo, "mirrorherb")) {
        continue;
      }
      ItensSegurados.gastar(inimigo, "mirrorherb");
      ItensSegurados.avisar(inimigo, Component.translatable("cobblemon.battle.enditem.mirrorherb",
          inimigo.getDisplayName(false)));
      copiando = true;
      try {
        mudarVarios(inimigo, subidas);
      } finally {
        copiando = false;
      }
    }
  }

  // Do outro lado da batalha: para o selvagem, o lutador de cada jogador; para o seu, os selvagens
  private static List<Pokemon> inimigos(Combate combate, Pokemon pokemon) {
    List<Pokemon> inimigos = new ArrayList<>();
    if (pokemon.isWild()) {
      for (ServerPlayer jogador : combate.getJogadores()) {
        Pokemon lutador = Lutadores.de(jogador);
        if (lutador != null && lutador.getEntity() != null) {
          inimigos.add(lutador);
        }
      }
    } else {
      for (PokemonEntity selvagem : combate.getSelvagens()) {
        if (selvagem.isAlive()) {
          inimigos.add(selvagem.getPokemon());
        }
      }
    }
    return inimigos;
  }

  // Intimidado (o Ataque caiu de verdade): o Orbe de Adrenalina sobe a Velocidade, se ela ainda pode subir
  public static void orbeDeAdrenalina(Pokemon pokemon, int ataqueMudou) {
    if (ataqueMudou >= 0 || Estagios.de(pokemon, "spe") >= Estagios.MAXIMO
        || !ItensSegurados.disponivel(pokemon, "adrenalineorb")) {
      return;
    }
    Component nome = ItensSegurados.gastarComNome(pokemon, "adrenalineorb");
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.generic", pokemon.getDisplayName(false),
        nome));
    mudar(pokemon, "spe", 1);
  }

  private static long agora(Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    return entidade != null && entidade.getServer() != null ? entidade.getServer().getTickCount() : 0;
  }
}
