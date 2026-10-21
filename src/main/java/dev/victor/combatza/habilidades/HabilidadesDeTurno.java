package dev.victor.combatza.habilidades;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Recargas;
import net.minecraft.network.chat.Component;

// 21/10 (habilidades, lote das formas: as que "dependem de turnos"). No tempo real, 1 turno = 3,5 s (o mesmo dos status):
// - Preguiça (Truant, Slaking): nos jogos ele age um turno sim, outro não. Aqui: depois de cada golpe, ele "vadia" um turno
//   (3,5 s) sem poder usar NENHUM golpe; a espera aparece como recarga nos 4 golpes (e a IA do selvagem espera junto);
// - Começo Lento (Slow Start, Regigigas): o Ataque e a Velocidade pela metade nos primeiros 5 turnos (17,5 s) depois de
//   entrar na batalha; voltou para a bola, começa de novo;
// - Adiar (Stall): nos jogos ele age por último. Aqui, como a Cauda Pesada (ItensDeCarga): a carga do golpe demora 50% mais;
// - Comatoso (Comatose, Komala): dorme para sempre, mas age normalmente: nenhum status pega nele, e ele conta como dormindo
//   para o Comedor de Sonhos (DanoEspecial). O Descansar falha, como nos jogos;
// - Incorporar Aspecto (Embody Aspect, Ogerpon com a máscara terastalizada): ao entrar, +1 na Velocidade (Turquesa), no
//   Ataque (Fornalha), na Defesa Especial (Nascente) ou na Defesa (Pedra Fundamental). Só existe na forma Tera dela, que a
//   nossa batalha não faz: vale se ela já entrar com essa habilidade
public class HabilidadesDeTurno {
  private static final String PREGUICA = "truant";
  private static final String COMECO_LENTO = "slowstart";
  private static final String ADIAR = "stall";
  private static final String COMATOSO = "comatose";
  private static final int TURNOS_DO_COMECO_LENTO = 5;
  private static final double METADE = 0.5;
  private static final double CARGA_DO_ADIAR = 1.5;
  private static final Map<String, String> INCORPORAR_ASPECTO = Map.of(
      "embodyaspectteal", "spe",
      "embodyaspecthearthflame", "atk",
      "embodyaspectwellspring", "spd",
      "embodyaspectcornerstone", "def");
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  private record Lento(Pokemon pokemon, long ate) {
  }

  // Quem está no Começo Lento, e até que tick
  private static final Map<UUID, Lento> LENTOS = new HashMap<>();

  private HabilidadesDeTurno() {
  }

  // Entrou na batalha (HabilidadesDeForma confere quem chegou)
  public static void aoEntrar(PokemonEntity entidade, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    if (Habilidades.tem(pokemon, COMECO_LENTO)) {
      LENTOS.put(pokemon.getUuid(), new Lento(pokemon, agora + TURNOS_DO_COMECO_LENTO * EfeitosDeStatus.ticksPorTurno()));
      CobblemonCombatZA.LOGGER.info("COMEÇO LENTO: {} com Ataque e Velocidade pela metade por {} turnos",
          pokemon.getSpecies().getName(), TURNOS_DO_COMECO_LENTO);
      HabilidadesDeForma.avisar(pokemon, Component.translatable(FRASE + "comeco_lento", pokemon.getDisplayName(false)));
    }
    if (Habilidades.tem(pokemon, COMATOSO)) {
      HabilidadesDeForma.avisar(pokemon, Component.translatable(FRASE + "comatoso", pokemon.getDisplayName(false)));
    }
    String atributo = INCORPORAR_ASPECTO.get(Habilidades.atual(pokemon));
    if (atributo != null) {
      HabilidadesDeForma.avisar(pokemon, Component.translatable("cobblemon.battle.ability.generic",
          pokemon.getDisplayName(false), Habilidades.nome(pokemon)));
      HabilidadesDeForma.subir(pokemon, Map.of(atributo, 1), agora);
    }
  }

  // A cada conferida na batalha: o Começo Lento acabou? (calado pelo Gás Neutralizador, acaba em silêncio, como no Showdown)
  public static void atualizar(Pokemon pokemon, long agora) {
    Lento lento = LENTOS.get(pokemon.getUuid());
    if (lento == null) {
      return;
    }
    if (!Habilidades.tem(pokemon, COMECO_LENTO)) {
      LENTOS.remove(pokemon.getUuid());
      return;
    }
    if (agora >= lento.ate()) {
      LENTOS.remove(pokemon.getUuid());
      CobblemonCombatZA.LOGGER.info("COMEÇO LENTO: {} engrenou", pokemon.getSpecies().getName());
      HabilidadesDeForma.avisar(pokemon, Component.translatable(FRASE + "comeco_lento_acabou",
          pokemon.getDisplayName(false)));
    }
  }

  // Showdown, onModifyAtk do Começo Lento (só o físico)
  public static double ataque(Pokemon pokemon, boolean fisico) {
    return fisico && lento(pokemon) ? METADE : 1.0;
  }

  // Showdown, onModifySpe do Começo Lento (a recarga e o ritmo da carga usam a Velocidade)
  public static double velocidade(Pokemon pokemon) {
    return lento(pokemon) ? METADE : 1.0;
  }

  private static boolean lento(Pokemon pokemon) {
    return LENTOS.containsKey(pokemon.getUuid()) && Habilidades.tem(pokemon, COMECO_LENTO);
  }

  // O Adiar deixa a carga mais lenta, como a Cauda Pesada
  public static double ritmoDaCarga(Pokemon pokemon) {
    return Habilidades.tem(pokemon, ADIAR) ? CARGA_DO_ADIAR : 1.0;
  }

  // Comatoso: para as regras que olham "está dormindo" (o Comedor de Sonhos)
  public static boolean contaComoDormindo(Pokemon pokemon) {
    return Habilidades.tem(pokemon, COMATOSO);
  }

  public static boolean comatoso(Pokemon pokemon) {
    return Habilidades.tem(pokemon, COMATOSO);
  }

  // A Preguiça: a recarga de um golpe começou (Recargas, só na batalha). Nenhum golpe por 1 turno, a partir de agora. Como
  // no Showdown, conta toda tentativa (também o golpe que a paralisia ou a confusão estragaram); recolhido no meio, não
  public static void depoisDoGolpe(Pokemon pokemon, long agora) {
    if (!Habilidades.tem(pokemon, PREGUICA) || pokemon.getEntity() == null || !GerenciarCombates.estaEmCombate(pokemon)) {
      return;
    }
    Recargas.esperar(pokemon, agora, EfeitosDeStatus.ticksPorTurno());
    CobblemonCombatZA.LOGGER.info("PREGUIÇA: {} vadia por 1 turno", pokemon.getSpecies().getName());
    HabilidadesDeForma.avisar(pokemon, Component.translatable("cobblemon.battle.cant.truant",
        pokemon.getDisplayName(false)));
  }

  // Saiu da batalha ou voltou para a bola
  public static void esquecer(Pokemon pokemon) {
    LENTOS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    LENTOS.clear();
  }
}
