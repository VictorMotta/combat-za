package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.itens.EstadoDosItens;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;

// 21/10 (habilidades, lote da entrada e das imunidades): as habilidades que reagem a uma BAGA comida (Showdown, onEatItem).
// O momento de comer é o ItensSegurados.gastar de uma baga: toda baga comida passa por lá (as de cura, de status, de aperto,
// de resistência, a Jaboca...):
// - Bochechões: comeu uma baga → +1/3 da vida;
// - Ruminante: comeu uma baga → 2 turnos depois (7 s) come de novo, se ainda estiver na mesma batalha sem ter voltado
//   para a bola. O efeito de novo, como o "onEat" do Showdown: as de cura (Oran, Sitrus, Figy...), as de status (Lum,
//   Cheri...), as de aperto (+1), a Estrela (+2), a Lansat, a Micle, a Kee e a Maranga. As de resistência, a Jaboca, a
//   Rowap, a Custap e a Enigma não têm efeito ao comer (o efeito delas é na hora do golpe); a Leppa (o PP) não existe no
//   tempo real
public class HabilidadesDeBaga {
  private static final String BOCHECHOES = "cheekpouch";
  private static final String RUMINANTE = "cudchew";
  private static final String BAGA = "berry";
  private static final int TERCO = 3;
  private static final int TURNOS_PARA_RUMINAR = 2;
  private static final int ORAN = 10;
  private static final int SITRUS = 4;
  private static final int ESTRELA = 2;
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  // As bagas de status: a baga e os status que ela cura
  private static final Map<String, List<String>> CURAM = Map.of("cheriberry", List.of("par"), "chestoberry",
      List.of("slp"), "pechaberry", List.of("psn", "tox"), "rawstberry", List.of("brn"), "aspearberry", List.of("frz"),
      "lumberry", List.of("par", "slp", "psn", "tox", "brn", "frz"));
  private static final Map<String, String> SOBEM = Map.of("liechiberry", "atk", "ganlonberry", "def", "salacberry", "spe",
      "petayaberry", "spa", "apicotberry", "spd", "keeberry", "def", "marangaberry", "spd");
  // As de cura que confundem quem tem a natureza que não gosta do sabor (o atributo que essa natureza baixa)
  private static final Map<String, Stat> CONFUNDEM = Map.of("figyberry", Stats.ATTACK, "wikiberry", Stats.SPECIAL_ATTACK,
      "magoberry", Stats.SPEED, "aguavberry", Stats.SPECIAL_DEFENCE, "iapapaberry", Stats.DEFENCE);
  private static final List<String> ATRIBUTOS_DA_ESTRELA = List.of("atk", "def", "spa", "spd", "spe");

  // Quem vai ruminar: a baga, o nome dela (já não está mais na mão), quando e em que batalha
  private record Ruminando(Pokemon pokemon, String baga, Component nome, long noTick, Combate daBatalha) {
  }

  private static final Map<UUID, Ruminando> RUMINANDO = new HashMap<>();

  private HabilidadesDeBaga() {
  }

  // ItensSegurados.gastar: ele vai comer a baga agora (ela ainda está na mão)
  public static void comeu(Pokemon pokemon, String item) {
    PokemonEntity entidade = pokemon.getEntity();
    if (!item.endsWith(BAGA) || entidade == null || !entidade.isAlive() || pokemon.isFainted()) {
      return;
    }
    if (Habilidades.tem(pokemon, BOCHECHOES) && Vida.pvAtual(pokemon) < pokemon.getMaxHealth()) {
      int curou = Vida.curar(entidade, Math.max(1, pokemon.getMaxHealth() / TERCO));
      CobblemonCombatZA.LOGGER.info("BOCHECHÕES: {} comeu {} e recuperou {} PV", pokemon.getSpecies().getName(), item,
          curou);
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.heal.cheekpouch",
          pokemon.getDisplayName(false)).append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
    }
    Combate combate = GerenciarCombates.combateDe(pokemon);
    if (Habilidades.tem(pokemon, RUMINANTE) && combate != null && entidade.getServer() != null) {
      long quando = entidade.getServer().getTickCount() + TURNOS_PARA_RUMINAR * EfeitosDeStatus.ticksPorTurno();
      RUMINANDO.put(pokemon.getUuid(), new Ruminando(pokemon, item, ItensSegurados.nome(pokemon), quando, combate));
      CobblemonCombatZA.LOGGER.info("RUMINANTE: {} vai comer {} de novo em {} turnos", pokemon.getSpecies().getName(), item,
          TURNOS_PARA_RUMINAR);
    }
  }

  // A cada tick (HabilidadesDeEntrada): chegou a hora de ruminar
  public static void atualizar(long agora) {
    if (RUMINANDO.isEmpty()) {
      return;
    }
    for (Ruminando ruminando : List.copyOf(RUMINANDO.values())) {
      if (ruminando.noTick() > agora) {
        continue;
      }
      RUMINANDO.remove(ruminando.pokemon().getUuid());
      Pokemon pokemon = ruminando.pokemon();
      PokemonEntity entidade = pokemon.getEntity();
      if (entidade != null && entidade.isAlive() && !pokemon.isFainted()
          && GerenciarCombates.combateDe(pokemon) == ruminando.daBatalha()) {
        ruminar(entidade, ruminando, agora);
      }
    }
  }

  // Voltou para a bola: não rumina mais (como no Showdown, o efeito acaba na troca)
  public static void esquecer(Pokemon pokemon) {
    RUMINANDO.remove(pokemon.getUuid());
  }

  public static void limpar() {
    RUMINANDO.clear();
  }

  private static void ruminar(PokemonEntity entidade, Ruminando ruminando, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    String baga = ruminando.baga();
    CobblemonCombatZA.LOGGER.info("RUMINANTE: {} comeu {} de novo", pokemon.getSpecies().getName(), baga);
    ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "ruminou", pokemon.getDisplayName(false),
        ruminando.nome(), Habilidades.nome(pokemon)));
    if ("oranberry".equals(baga)) {
      curar(entidade, ORAN);
    } else if ("sitrusberry".equals(baga)) {
      curar(entidade, Math.max(1, pokemon.getMaxHealth() / SITRUS));
    } else if (CONFUNDEM.containsKey(baga)) {
      curar(entidade, Math.max(1, pokemon.getMaxHealth() / TERCO));
      if (pokemon.getEffectiveNature().getDecreasedStat() == CONFUNDEM.get(baga)
          && Confusao.confundirPorItem(entidade, agora)) {
        ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.start.confusion",
            pokemon.getDisplayName(false)));
      }
    } else if (CURAM.containsKey(baga) || "persimberry".equals(baga)) {
      curarStatus(pokemon, baga, ruminando.nome());
    } else if (SOBEM.containsKey(baga)) {
      HabilidadesDeEstagio.mudarProprio(pokemon, Map.of(SOBEM.get(baga), 1));
    } else if ("starfberry".equals(baga)) {
      estrela(entidade, pokemon);
    } else if ("lansatberry".equals(baga)) {
      EstadoDosItens.focar(pokemon);
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.start.focusenergy",
          pokemon.getDisplayName(false)));
    } else if ("micleberry".equals(baga)) {
      EstadoDosItens.guardarMicle(pokemon);
    }
  }

  private static void curar(PokemonEntity entidade, int pv) {
    Pokemon pokemon = entidade.getPokemon();
    if (Vida.pvAtual(pokemon) >= pokemon.getMaxHealth()) {
      return;
    }
    int curou = Vida.curar(entidade, pv);
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.heal.generic", pokemon.getDisplayName(false))
        .append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
  }

  // A Lum e a Persim também tiram a confusão
  private static void curarStatus(Pokemon pokemon, String baga, Component nome) {
    String sigla = EfeitosDeStatus.siglaDoStatus(pokemon);
    boolean curou = false;
    if (sigla != null && CURAM.getOrDefault(baga, List.of()).contains(sigla)) {
      pokemon.setStatus(null);
      curou = true;
    }
    if (("lumberry".equals(baga) || "persimberry".equals(baga)) && Confusao.estaConfuso(pokemon)) {
      Confusao.esquecer(pokemon);
      curou = true;
    }
    if (curou) {
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon_combat_za.item.comeu_status",
          pokemon.getDisplayName(false), nome));
    }
  }

  // A Estrela: um atributo sorteado (dos que ainda podem subir) +2
  private static void estrela(PokemonEntity entidade, Pokemon pokemon) {
    List<String> podem = new ArrayList<>();
    for (String atributo : ATRIBUTOS_DA_ESTRELA) {
      if (Estagios.de(pokemon, atributo) < Estagios.MAXIMO) {
        podem.add(atributo);
      }
    }
    if (!podem.isEmpty()) {
      HabilidadesDeEstagio.mudarProprio(pokemon, Map.of(podem.get(entidade.getRandom().nextInt(podem.size())), ESTRELA));
    }
  }
}
