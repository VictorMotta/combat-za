package dev.victor.combatza.itens;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.habilidades.HabilidadesDoTurno;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 9.1, lote 1: os itens que mexem na VIDA, só nos Pokémon da batalha (os selvagens e o seu lutador; fora da batalha não existe
// "turno", e curar andando tiraria a graça das Poções):
// - as frutas, conferidas a cada tick (o "onUpdate" do Showdown: comem na hora em que a vida passa do ponto): Oran (+10 PV) e
//   Sitrus (+1/4 da vida) com metade da vida ou menos (e o Suco de Baga, +20 PV, pedido dele em 20/10: "meu bicho tá com
//   suco de baga e não recuperou"); Lum (qualquer status e a confusão), Cheri (paralisia), Chesto (sono),
//   Pecha (veneno), Rawst (queimadura), Aspear (congelado), Persim (confusão). Comida, a fruta some;
// - Sobras (+1/16) e Lodo Escuro (+1/16 no tipo Veneno, −1/8 nos outros), a cada "turno" de 3,5 s, como o dano de status;
// - o recuo do Orbe da Vida: −1/10 da vida, uma vez por golpe (depois do golpe inteiro, como no Showdown)
public class ItensDeVida {
  private static final int ORAN = 10;
  private static final int SUCO_DE_BAGA = 20;
  private static final int METADE = 2;
  private static final int SITRUS = 4;
  private static final int SOBRAS = 16;
  private static final int LODO_FERE = 8;
  private static final int ORBE = 10;
  private static final String FRASE = "cobblemon_combat_za.item.";
  // As frutas de status: a fruta e os status que ela cura (a Lum, todos; a Persim, só a confusão)
  private static final Map<String, List<String>> CURAM = Map.of(
      ItensSegurados.CHERI, List.of("par"), ItensSegurados.CHESTO, List.of("slp"),
      ItensSegurados.PECHA, List.of("psn", "tox"), ItensSegurados.RAWST, List.of("brn"),
      ItensSegurados.ASPEAR, List.of("frz"), ItensSegurados.LUM, List.of("par", "slp", "psn", "tox", "brn", "frz"));

  private ItensDeVida() {
  }

  // A cada tick: as frutas; a cada turno de 3,5 s, as Sobras e o Lodo
  public static void atualizar(long agora) {
    boolean virouTurno = agora % EfeitosDeStatus.ticksPorTurno() == 0;
    for (Combate combate : List.copyOf(GerenciarCombates.todos())) {
      for (PokemonEntity entidade : daBatalha(combate)) {
        if (!entidade.isAlive() || entidade.getPokemon().isFainted()) {
          continue;
        }
        frutas(combate, entidade);
        // Lotes 2 e 3: as bagas da vida baixa e as Sementes, a cada tick; os Orbes e a Farpa, a cada turno
        Bagas.aCadaTick(entidade);
        ItensDoTurno.aCadaTick(entidade);
        if (virouTurno) {
          porTurno(combate, entidade);
          ItensDoTurno.porTurno(entidade, agora);
        }
      }
    }
    // 21/10 (habilidades, lote das reações): as habilidades de cada turno (Acelerador, Mau-Humor...) andam junto
    HabilidadesDoTurno.atualizar(agora);
  }

  // Os selvagens e o lutador de cada jogador
  private static List<PokemonEntity> daBatalha(Combate combate) {
    List<PokemonEntity> todos = new ArrayList<>(combate.getSelvagens());
    for (ServerPlayer jogador : combate.getJogadores()) {
      Pokemon lutador = Lutadores.de(jogador);
      if (lutador != null && lutador.getEntity() != null) {
        todos.add(lutador.getEntity());
      }
    }
    return todos;
  }

  private static void frutas(Combate combate, PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    String item = ItensSegurados.id(pokemon);
    if (item.isEmpty()) {
      return;
    }
    boolean naMetade = Vida.pvAtual(pokemon) * METADE <= pokemon.getMaxHealth();
    // 21/10 (habilidades, lote da conta do dano): com Amadurecer, as bagas curam o dobro (o Suco de Baga não é baga)
    if (ItensSegurados.ORAN.equals(item) && naMetade) {
      comerParaCurar(combate, entidade, item, HabilidadesDaConta.amadurecida(pokemon, ORAN));
    } else if (ItensSegurados.SITRUS.equals(item) && naMetade) {
      comerParaCurar(combate, entidade, item,
          HabilidadesDaConta.amadurecida(pokemon, Math.max(1, pokemon.getMaxHealth() / SITRUS)));
    } else if (ItensSegurados.SUCO_DE_BAGA.equals(item) && naMetade) {
      beberOSuco(combate, entidade);
    } else if (CURAM.containsKey(item) && CURAM.get(item).contains(EfeitosDeStatus.siglaDoStatus(pokemon))) {
      Component nome = ItensSegurados.nome(pokemon);
      String sigla = EfeitosDeStatus.siglaDoStatus(pokemon);
      pokemon.setStatus(null);
      if (ItensSegurados.LUM.equals(item)) {
        Confusao.esquecer(pokemon);
      }
      ItensSegurados.gastar(pokemon, item);
      avisar(combate, pokemon, Component.translatable(FRASE + "comeu_status", pokemon.getDisplayName(false), nome));
      CobblemonCombatZA.LOGGER.info("ITEM: {} curou {} ({})", pokemon.getSpecies().getName(), sigla, item);
    } else if ((ItensSegurados.PERSIM.equals(item) || ItensSegurados.LUM.equals(item)) && Confusao.estaConfuso(pokemon)) {
      Component nome = ItensSegurados.nome(pokemon);
      Confusao.esquecer(pokemon);
      ItensSegurados.gastar(pokemon, item);
      avisar(combate, pokemon, Component.translatable(FRASE + "comeu_status", pokemon.getDisplayName(false), nome));
      CobblemonCombatZA.LOGGER.info("ITEM: {} saiu da confusão ({})", pokemon.getSpecies().getName(), item);
    }
  }

  private static void comerParaCurar(Combate combate, PokemonEntity entidade, String item, int pv) {
    Pokemon pokemon = entidade.getPokemon();
    Component nome = ItensSegurados.nome(pokemon);
    int curou = Vida.curar(entidade, pv);
    ItensSegurados.gastar(pokemon, item);
    avisar(combate, pokemon, Component.translatable(FRASE + "comeu_cura", pokemon.getDisplayName(false), nome, curou));
    CobblemonCombatZA.LOGGER.info("ITEM: {} comeu {}: +{} PV ({}/{})", pokemon.getSpecies().getName(), item, curou,
        Vida.pvAtual(pokemon), pokemon.getMaxHealth());
  }

  // O Suco de Baga não é baga (a etiqueta do Cobblemon, consumed_in_wild_battle, só tem bagas, ervas e a bola de neve), mas é
  // bebido e some, como nos jogos (dele, 21/10: "não tá recuperando a vida se ficar abaixo da metade em uma luta, e consumindo").
  // A frase é a do Cobblemon ("bebeu o seu Suco de Baga!")
  private static void beberOSuco(Combate combate, PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if (ItensSegurados.usadoNestaBatalha(pokemon, ItensSegurados.SUCO_DE_BAGA)) {
      return;
    }
    int curou = Vida.curar(entidade, SUCO_DE_BAGA);
    ItensSegurados.gastar(pokemon, ItensSegurados.SUCO_DE_BAGA);
    pokemon.removeHeldItem();
    avisar(combate, pokemon, Component.translatable("cobblemon.battle.enditem.berryjuice", pokemon.getDisplayName(false))
        .append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
    CobblemonCombatZA.LOGGER.info("ITEM: {} bebeu o Suco de Baga: +{} PV ({}/{})", pokemon.getSpecies().getName(), curou,
        Vida.pvAtual(pokemon), pokemon.getMaxHealth());
  }

  private static void porTurno(Combate combate, PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    String item = ItensSegurados.id(pokemon);
    boolean sobras = ItensSegurados.SOBRAS.equals(item);
    boolean lodo = ItensSegurados.LODO_ESCURO.equals(item);
    if (!sobras && !lodo) {
      return;
    }
    // 21/10 (lote das formas): o tipo de agora (um Proteico que virou Venenoso)
    boolean venenoso = AplicarStatus.temTipo(pokemon, ElementalTypes.POISON);
    if (sobras || venenoso) {
      if (Vida.pvAtual(pokemon) >= pokemon.getMaxHealth()) {
        return;
      }
      int curou = Vida.curar(entidade, Math.max(1, pokemon.getMaxHealth() / SOBRAS));
      avisar(combate, pokemon, Component.translatable(FRASE + "sobras", pokemon.getDisplayName(false),
          ItensSegurados.nome(pokemon)));
      CobblemonCombatZA.LOGGER.info("ITEM: {} +{} PV ({})", pokemon.getSpecies().getName(), curou, item);
      return;
    }
    int pv = Math.max(1, pokemon.getMaxHealth() / LODO_FERE);
    avisar(combate, pokemon, Component.translatable(FRASE + "feriu", pokemon.getDisplayName(false),
        ItensSegurados.nome(pokemon), pv));
    Vida.ferir(entidade, pv);
    CobblemonCombatZA.LOGGER.info("ITEM: {} −{} PV (Lodo Escuro)", pokemon.getSpecies().getName(), pv);
  }

  // O recuo do Orbe da Vida, depois que o golpe tirou vida de alguém (uma vez por golpe: o jato e a área acertam várias)
  public static void orbeDaVida(Execucao execucao, PokemonEntity atacante, double pvTirados) {
    Pokemon pokemon = atacante.getPokemon();
    // 21/10 (habilidades, lote da conta do dano): no golpe que a Força Absoluta fortaleceu, sem recuo (como nos jogos)
    if (pvTirados <= 0 || !atacante.isAlive() || !ItensSegurados.tem(pokemon, ItensSegurados.ORBE_DA_VIDA)
        || HabilidadesDaConta.forcaAbsoluta(pokemon, execucao.getGolpe()) || !execucao.primeiroRecuoDoOrbe()) {
      return;
    }
    int pv = Math.max(1, pokemon.getMaxHealth() / ORBE);
    Feedback.historico(execucao.getJogador(), pokemon, Component.translatable(FRASE + "feriu",
        pokemon.getDisplayName(false), ItensSegurados.nome(pokemon), pv));
    Vida.ferir(atacante, pv);
    CobblemonCombatZA.LOGGER.info("ITEM: {} −{} PV (Orbe da Vida)", pokemon.getSpecies().getName(), pv);
  }

  private static void avisar(Combate combate, Pokemon pokemon, Component frase) {
    for (ServerPlayer jogador : combate.getJogadores()) {
      Feedback.historico(jogador, pokemon, frase);
    }
  }
}
