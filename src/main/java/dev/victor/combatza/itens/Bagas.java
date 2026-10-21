package dev.victor.combatza.itens;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Recargas;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;

// Lote 2 dos itens segurados: as BAGAS que faltavam (as do lote 1, de cura e de status, estão em ItensDeVida). Como no
// Showdown, cada baga é comida uma vez (e some):
// - as 18 de resistência (Occa, Passho, Wacan...): o golpe super efetivo do tipo dela tira a metade (a Chilan: qualquer golpe
//   Normal). A conta é do Dano; a baga é comida no acerto (errou, ela fica);
// - com 1/4 da vida ou menos (com Gula, metade), a cada tick: as de aperto (Liechi Ataque, Ganlon Defesa, Salac Velocidade,
//   Petaya Ataque Especial, Apicot Defesa Especial: +1), a Estrela (um atributo sorteado +2), a Lansat (crítico +2 até o fim da
//   batalha), a Micle (o próximo golpe acerta mais fácil), a Custap (o próximo golpe sai sem carregar) e as de cura que
//   confundem (Figy, Wiki, Mago, Aguav, Iapapa: +1/3 da vida, e confunde quem tem a natureza que não gosta do sabor dela);
// - depois de levar um golpe: Enigma (super efetivo: +1/4 da vida), Jaboca (físico) e Rowap (especial): quem bateu perde 1/8
//   da vida dele; Kee (físico: Defesa +1) e Maranga (especial: Defesa Especial +1);
// - Leppa (a baga do PP, que não existe no tempo real): o primeiro golpe que entra em recarga na batalha recarrega 2× mais
//   rápido por 17,5 s, como o Éter.
// As de sabor (Razz, Bluk, Nanab...) e as que baixam EV (Pomeg, Kelpsy...) não têm efeito de batalha nem no Showdown.
// 21/10 (habilidades, lote da conta do dano): com Amadurecer, o que a baga dá vale o dobro (a cura, os estágios, o dano da
// Jaboca e da Rowap; a de resistência deixa 1/4); a Lansat, a Micle, a Custap e a Leppa não mudam, como no Showdown
public class Bagas {
  private static final double METADE = 0.5;
  private static final int APERTO = 4;
  private static final int APERTO_COM_GULA = 2;
  private static final int CURA_QUE_CONFUNDE = 3;
  private static final int ENIGMA = 4;
  private static final int JABOCA_OU_ROWAP = 8;
  private static final int ESTRELA = 2;
  private static final double SEGUNDOS_DA_LEPPA = 17.5;
  private static final String GULA = "gluttony";
  private static final String FRASE = "cobblemon_combat_za.item.";

  private static final Map<String, ElementalType> RESISTEM = Map.ofEntries(
      Map.entry("occaberry", ElementalTypes.FIRE), Map.entry("passhoberry", ElementalTypes.WATER),
      Map.entry("wacanberry", ElementalTypes.ELECTRIC), Map.entry("rindoberry", ElementalTypes.GRASS),
      Map.entry("yacheberry", ElementalTypes.ICE), Map.entry("chopleberry", ElementalTypes.FIGHTING),
      Map.entry("kebiaberry", ElementalTypes.POISON), Map.entry("shucaberry", ElementalTypes.GROUND),
      Map.entry("cobaberry", ElementalTypes.FLYING), Map.entry("payapaberry", ElementalTypes.PSYCHIC),
      Map.entry("tangaberry", ElementalTypes.BUG), Map.entry("chartiberry", ElementalTypes.ROCK),
      Map.entry("kasibberry", ElementalTypes.GHOST), Map.entry("habanberry", ElementalTypes.DRAGON),
      Map.entry("colburberry", ElementalTypes.DARK), Map.entry("babiriberry", ElementalTypes.STEEL),
      Map.entry("roseliberry", ElementalTypes.FAIRY), Map.entry("chilanberry", ElementalTypes.NORMAL));

  private static final Map<String, String> DE_APERTO = Map.of("liechiberry", "atk", "ganlonberry", "def",
      "salacberry", "spe", "petayaberry", "spa", "apicotberry", "spd");

  // As de cura que confundem: a baga e o atributo que a natureza que não gosta do sabor dela baixa (Figy é apimentada: quem
  // não gosta são as naturezas que baixam o Ataque)
  private static final Map<String, Stat> CONFUNDEM = Map.of("figyberry", Stats.ATTACK, "wikiberry", Stats.SPECIAL_ATTACK,
      "magoberry", Stats.SPEED, "aguavberry", Stats.SPECIAL_DEFENCE, "iapapaberry", Stats.DEFENCE);

  private static final List<String> ATRIBUTOS_DA_ESTRELA = List.of("atk", "def", "spa", "spd", "spe");

  private Bagas() {
  }

  // O Dano pergunta: a baga de resistência entra nesta conta? (só o multiplicador; quem come é o acerto)
  public static double resistencia(Pokemon defensor, ElementalType tipoDoGolpe, double efetividade) {
    String item = ItensSegurados.id(defensor);
    ElementalType daBaga = RESISTEM.get(item);
    if (daBaga == null || daBaga != tipoDoGolpe || !ItensSegurados.disponivel(defensor, item)) {
      return 1.0;
    }
    return daBaga == ElementalTypes.NORMAL || efetividade > 1.0 ? HabilidadesDaConta.bagaDeResistencia(defensor, METADE)
        : 1.0;
  }

  // O golpe acertou com a baga na conta: ela é comida
  public static void comeuDeResistencia(Pokemon defensor) {
    String item = ItensSegurados.id(defensor);
    if (!RESISTEM.containsKey(item)) {
      return;
    }
    Component nome = ItensSegurados.gastarComNome(defensor, item);
    ItensSegurados.avisar(defensor, Component.translatable(FRASE + "baga_resistiu", nome, defensor.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("ITEM: {} comeu {} (o golpe tirou a metade)", defensor.getSpecies().getName(), item);
  }

  // A cada tick (ItensDeVida), com a vida baixa
  public static void aCadaTick(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    String item = ItensSegurados.id(pokemon);
    if (item.isEmpty() || !ItensSegurados.disponivel(pokemon, item) || !noAperto(pokemon)) {
      return;
    }
    if (DE_APERTO.containsKey(item)) {
      comer(pokemon, item);
      ItensDeEstagio.mudar(pokemon, DE_APERTO.get(item), HabilidadesDaConta.amadurecida(pokemon, 1));
    } else if ("starfberry".equals(item)) {
      estrela(entidade, pokemon);
    } else if ("lansatberry".equals(item)) {
      comer(pokemon, item);
      EstadoDosItens.focar(pokemon);
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.start.focusenergy",
          pokemon.getDisplayName(false)));
    } else if ("micleberry".equals(item)) {
      Component nome = ItensSegurados.gastarComNome(pokemon, item);
      EstadoDosItens.guardarMicle(pokemon);
      ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "micle", pokemon.getDisplayName(false), nome));
    } else if ("custapberry".equals(item)) {
      Component nome = ItensSegurados.gastarComNome(pokemon, item);
      EstadoDosItens.guardarCustap(pokemon);
      ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "custap", pokemon.getDisplayName(false), nome));
    } else if (CONFUNDEM.containsKey(item)) {
      curaQueConfunde(entidade, pokemon, item);
    } else {
      return;
    }
    CobblemonCombatZA.LOGGER.info("ITEM: {} comeu {} com {}/{} PV", pokemon.getSpecies().getName(), item,
        Vida.pvAtual(pokemon), pokemon.getMaxHealth());
  }

  // 1/4 da vida ou menos (com Gula, metade)
  private static boolean noAperto(Pokemon pokemon) {
    int parte = Habilidades.tem(pokemon, GULA) ? APERTO_COM_GULA : APERTO;
    return Vida.pvAtual(pokemon) * parte <= pokemon.getMaxHealth();
  }

  private static void comer(Pokemon pokemon, String item) {
    Component nome = ItensSegurados.gastarComNome(pokemon, item);
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.item.eat", pokemon.getDisplayName(false), nome));
  }

  // A Estrela: um atributo sorteado (dos que ainda podem subir) +2
  private static void estrela(PokemonEntity entidade, Pokemon pokemon) {
    List<String> podem = new ArrayList<>();
    for (String atributo : ATRIBUTOS_DA_ESTRELA) {
      if (Estagios.de(pokemon, atributo) < Estagios.MAXIMO) {
        podem.add(atributo);
      }
    }
    comer(pokemon, "starfberry");
    if (!podem.isEmpty()) {
      ItensDeEstagio.mudar(pokemon, podem.get(entidade.getRandom().nextInt(podem.size())),
          HabilidadesDaConta.amadurecida(pokemon, ESTRELA));
    }
  }

  // Cura 1/3 da vida; confunde quem tem a natureza que baixa o atributo ligado ao sabor dela
  private static void curaQueConfunde(PokemonEntity entidade, Pokemon pokemon, String item) {
    Component nome = ItensSegurados.gastarComNome(pokemon, item);
    int curou = Vida.curar(entidade,
        HabilidadesDaConta.amadurecida(pokemon, Math.max(1, pokemon.getMaxHealth() / CURA_QUE_CONFUNDE)));
    ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "comeu_cura", pokemon.getDisplayName(false), nome,
        curou));
    long agora = entidade.getServer() == null ? 0 : entidade.getServer().getTickCount();
    if (pokemon.getEffectiveNature().getDecreasedStat() == CONFUNDEM.get(item) && Confusao.confundirPorItem(entidade, agora)) {
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.start.confusion",
          pokemon.getDisplayName(false)));
    }
  }

  // Depois de levar um golpe que tirou vida (ItensDoAcerto)
  public static void depoisDeApanhar(PokemonEntity atingido, PokemonEntity atacante, Move golpe, double efetividade) {
    Pokemon pokemon = atingido.getPokemon();
    String item = ItensSegurados.id(pokemon);
    if (item.isEmpty() || !ItensSegurados.disponivel(pokemon, item)) {
      return;
    }
    boolean fisico = golpe.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
    boolean especial = golpe.getDamageCategory() == DamageCategories.INSTANCE.getSPECIAL();
    switch (item) {
      case "enigmaberry" -> {
        if (efetividade > 1.0 && atingido.isAlive()) {
          Component nome = ItensSegurados.gastarComNome(pokemon, item);
          int curou = Vida.curar(atingido,
              HabilidadesDaConta.amadurecida(pokemon, Math.max(1, pokemon.getMaxHealth() / ENIGMA)));
          ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "comeu_cura", pokemon.getDisplayName(false), nome,
              curou));
        }
      }
      case "jabocaberry", "rowapberry" -> {
        if ((fisico && "jabocaberry".equals(item) || especial && "rowapberry".equals(item)) && atacante.isAlive()) {
          ferirQuemBateu(pokemon, atacante, item);
        }
      }
      case "keeberry" -> {
        if (fisico) {
          comer(pokemon, item);
          ItensDeEstagio.mudar(pokemon, "def", HabilidadesDaConta.amadurecida(pokemon, 1));
        }
      }
      case "marangaberry" -> {
        if (especial) {
          comer(pokemon, item);
          ItensDeEstagio.mudar(pokemon, "spd", HabilidadesDaConta.amadurecida(pokemon, 1));
        }
      }
      default -> {
        // sem baga que reage ao golpe
      }
    }
  }

  private static void ferirQuemBateu(Pokemon dono, PokemonEntity atacante, String item) {
    Component nome = ItensSegurados.gastarComNome(dono, item);
    Pokemon doAtacante = atacante.getPokemon();
    int pv = HabilidadesDaConta.amadurecida(dono, Math.max(1, doAtacante.getMaxHealth() / JABOCA_OU_ROWAP));
    ItensSegurados.avisar(doAtacante, Component.translatable(FRASE + "baga_feriu", doAtacante.getDisplayName(false), nome,
        dono.getDisplayName(false), pv));
    Vida.ferir(atacante, pv);
    CobblemonCombatZA.LOGGER.info("ITEM: {} perdeu {} PV na {} de {}", doAtacante.getSpecies().getName(), pv, item,
        dono.getSpecies().getName());
  }

  // Leppa: o golpe entrou em recarga numa batalha (Recargas). Devolve se ela foi comida (a recarga já fica acelerada)
  public static boolean leppa(Pokemon pokemon, int slot, Move golpe, long agora) {
    if (!ItensSegurados.disponivel(pokemon, "leppaberry") || !GerenciarCombates.estaEmCombate(pokemon)) {
      return false;
    }
    Component nome = ItensSegurados.gastarComNome(pokemon, "leppaberry");
    Recargas.acelerar(pokemon, slot, agora + Math.round(SEGUNDOS_DA_LEPPA * SharedConstants.TICKS_PER_SECOND), agora);
    ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "leppa", pokemon.getDisplayName(false), nome,
        golpe.getDisplayName()));
    CobblemonCombatZA.LOGGER.info("ITEM: {} comeu a Leppa: {} recarrega 2× por {} s", pokemon.getSpecies().getName(),
        golpe.getName(), SEGUNDOS_DA_LEPPA);
    return true;
  }
}
