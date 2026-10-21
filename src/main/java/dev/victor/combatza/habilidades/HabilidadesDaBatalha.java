package dev.victor.combatza.habilidades;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 21/10 (habilidades, lote da conta do dano): as habilidades que dependem de QUEM ESTÁ NA BATALHA e de QUANDO cada um entrou
// ou agiu. No Showdown isso é contado em turnos; aqui, 1 turno = 3,5 s (EfeitosDeStatus.ticksPorTurno):
// - as Ruínas (Tábuas, Urna, Espada e Pérolas da Ruína): enquanto quem tem está na batalha, o Ataque, o Ataque Especial, a
//   Defesa ou a Defesa Especial de TODOS os outros fica ×0,75 (quem tem não sofre; duas iguais não somam);
// - as Auras (Aura Negra e Aura Feérica): enquanto quem tem está na batalha, os golpes Sombrios ou de Fada de todos (dos
//   dois lados) ficam ×1,33 (5448/4096); com o Rompe-Auras de alguém na batalha, ×0,75 (3072/4096);
// - Vigilância: o golpe em quem entrou trocando no meio da batalha há menos de 1 turno ("o alvo acabou de trocar"; quem
//   começa a batalha não conta, como no Showdown);
// - Analisar: o golpe em quem já agiu há menos de 1 turno (o golpe dele saiu nos últimos 3,5 s): é o "agir por último";
// - Senhor Supremo: ao entrar, conta os companheiros do time desmaiados (até 5);
// - as frases de quando entram na batalha (Quebra-Molde, Teravoltagem, Turbochama, as Auras, as Ruínas, o Senhor Supremo).
//   EfeitosDoCampo avisa quem entrou (a cada 5 ticks, junto do Chuvisco e das -Gêneses); AgendaDeGolpes avisa quem agiu
public class HabilidadesDaBatalha {
  private static final String FRASE = "cobblemon_combat_za.habilidade.";
  private static final String ROMPE_AURAS = "aurabreak";
  private static final Map<ElementalType, String> AURAS = Map.of(ElementalTypes.DARK, "darkaura",
      ElementalTypes.FAIRY, "fairyaura");
  private static final double AURA = 5448.0 / 4096.0;
  private static final double AURA_ROMPIDA = 3072.0 / 4096.0;
  private static final int MAXIMO_DE_CAIDOS = 5;
  // Quem entra até 0,5 s depois do começo da batalha entrou com ela (quem chega é conferido a cada 5 ticks)
  private static final long COMECO_DA_BATALHA = 10;
  // Quando cada Pokémon entrou na arena e quando o golpe dele saiu (tick); e os caídos que o Senhor Supremo contou ao entrar
  private static final Map<UUID, Long> ENTROU = new HashMap<>();
  private static final Map<UUID, Long> AGIU = new HashMap<>();
  private static final Map<UUID, Integer> CAIDOS = new HashMap<>();
  // Sem um "fim" para cada Pokémon, as anotações velhas (10 minutos) saem quando a lista passa de 256
  private static final int LISTA_GRANDE = 256;
  private static final long ANOTACAO_VELHA = 10L * 60 * 20;

  private HabilidadesDaBatalha() {
  }

  // Entrou numa arena (a batalha; fora dela, o campo pessoal: saiu da bola)
  public static void aoEntrar(Arena arena, PokemonEntity entidade, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    ENTROU.put(pokemon.getUuid(), agora);
    CAIDOS.put(pokemon.getUuid(), contarCaidos(pokemon));
    esquecerAsVelhas(agora);
    if (!arena.naBatalha()) {
      return;
    }
    Component frase = fraseDeEntrada(pokemon);
    if (frase != null) {
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} entrou com {}", pokemon.getSpecies().getName(),
          Habilidades.atual(pokemon));
      arena.avisar(pokemon, frase);
    }
  }

  // O golpe dele saiu agora (o impacto)
  public static void agiu(Pokemon pokemon, long agora) {
    AGIU.put(pokemon.getUuid(), agora);
    if (AGIU.size() > LISTA_GRANDE) {
      AGIU.values().removeIf(tick -> agora - tick > ANOTACAO_VELHA);
    }
  }

  // Vigilância: o alvo entrou TROCANDO, no meio da batalha, há menos de 1 turno? Como no Showdown, quem começa a batalha não
  // conta (lá, no 1º turno todos já têm um turno em campo); conta o lutador trocado e o selvagem que chegou depois
  public static boolean acabouDeTrocar(Pokemon alvo, long agora) {
    Combate combate = GerenciarCombates.combateDe(alvo);
    Long entrou = ENTROU.get(alvo.getUuid());
    return combate != null && entrou != null && entrou - combate.getComecouNoTick() > COMECO_DA_BATALHA
        && agora - entrou < EfeitosDeStatus.ticksPorTurno();
  }

  // Analisar: o golpe do alvo saiu há menos de 1 turno?
  public static boolean agiuHaPouco(Pokemon alvo, long agora) {
    Long agiu = AGIU.get(alvo.getUuid());
    return agiu != null && agora - agiu < EfeitosDeStatus.ticksPorTurno();
  }

  // Senhor Supremo: os caídos que ele contou ao entrar (de 0 a 5)
  public static int caidos(Pokemon pokemon) {
    Integer contados = CAIDOS.get(pokemon.getUuid());
    return contados != null ? contados : contarCaidos(pokemon);
  }

  // Os companheiros desmaiados do time do dono (o selvagem não tem time)
  private static int contarCaidos(Pokemon pokemon) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    if (dono == null) {
      return 0;
    }
    int caidos = 0;
    for (Pokemon companheiro : Cobblemon.INSTANCE.getStorage().getParty(dono)) {
      if (companheiro.isFainted() && !companheiro.getUuid().equals(pokemon.getUuid())) {
        caidos++;
      }
    }
    return Math.min(caidos, MAXIMO_DE_CAIDOS);
  }

  // A Ruína "habilidade" vale contra "quemSofre"? (alguém da batalha tem, e não é ele)
  public static boolean sobRuina(Pokemon quemSofre, Pokemon outro, String habilidade) {
    return !Habilidades.tem(quemSofre, habilidade) && alguemTem(quemSofre, outro, habilidade);
  }

  // A Aura do tipo do golpe: ×1,33 com a Aura na batalha, ×0,75 se também há Rompe-Auras
  public static double aura(Pokemon atacante, Pokemon defensor, ElementalType tipo) {
    String aura = AURAS.get(tipo);
    if (aura == null || !alguemTem(atacante, defensor, aura)) {
      return 1.0;
    }
    return alguemTem(atacante, defensor, ROMPE_AURAS) ? AURA_ROMPIDA : AURA;
  }

  // Um dos dois, ou alguém da arena deles (a batalha), tem a habilidade (e está de pé)
  private static boolean alguemTem(Pokemon um, Pokemon outro, String habilidade) {
    if (Habilidades.tem(um, habilidade) || outro != null && Habilidades.tem(outro, habilidade)) {
      return true;
    }
    Arena arena = Arenas.de(um);
    if (arena == null && outro != null) {
      arena = Arenas.de(outro);
    }
    if (arena == null) {
      return false;
    }
    for (PokemonEntity entidade : arena.pokemons()) {
      if (entidade.isAlive() && !entidade.getPokemon().isFainted() && Habilidades.tem(entidade.getPokemon(), habilidade)) {
        return true;
      }
    }
    return false;
  }

  // A frase da entrada (null = a habilidade dele não fala nada ao entrar)
  private static Component fraseDeEntrada(Pokemon pokemon) {
    Component nome = pokemon.getDisplayName(false);
    return switch (Habilidades.atual(pokemon)) {
      case "moldbreaker" -> Component.translatable(FRASE + "quebra_molde", nome);
      case "teravolt", "turboblaze", "darkaura", "fairyaura" ->
        Component.translatable(FRASE + Habilidades.atual(pokemon), nome);
      case "tabletsofruin" -> ruina(pokemon, "atk");
      case "vesselofruin" -> ruina(pokemon, "spa");
      case "swordofruin" -> ruina(pokemon, "def");
      case "beadsofruin" -> ruina(pokemon, "spd");
      case "supremeoverlord" -> caidos(pokemon) > 0
          ? Component.translatable(FRASE + "senhor_supremo", nome, caidos(pokemon))
          : null;
      default -> null;
    };
  }

  // "Tábuas da Ruína de Wo-Chien: o Ataque de todos em volta enfraqueceu!"
  private static Component ruina(Pokemon pokemon, String atributo) {
    return Component.translatable(FRASE + "ruina." + atributo, pokemon.getDisplayName(false), Habilidades.nome(pokemon));
  }

  private static void esquecerAsVelhas(long agora) {
    if (ENTROU.size() <= LISTA_GRANDE) {
      return;
    }
    ENTROU.values().removeIf(tick -> agora - tick > ANOTACAO_VELHA);
    CAIDOS.keySet().retainAll(ENTROU.keySet());
  }
}
