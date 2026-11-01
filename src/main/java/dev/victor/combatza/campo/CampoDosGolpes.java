package dev.victor.combatza.campo;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Execucao;
import net.minecraft.network.chat.Component;

// 01/11 (as pendências, "como nos jogos"): os golpes que mudam o CAMPO da batalha inteira (os dois lados), como o Truque de
// Agilidade (SalaTrick), guardados no CampoDaBatalha (vão do campo pessoal para a batalha e voltam). Um turno é 3,5 s:
// - Gravidade (5 turnos): todos no chão (a Terra pega no Voador, na Levitação, no Balão; a Levitação Magnética e a
//   Telecinese não seguram), precisão ×5/3, e os golpes de voo e pulo (a marca "gravity") ficam riscados;
// - Sala Mágica (5 turnos; usar de novo desfaz): os itens segurados não fazem nada;
// - Zona Estranha (5 turnos; usar de novo desfaz): a Defesa e a Def. Esp. trocam na conta do dano;
// - Esporte de Lama / Esporte Aquático (5 turnos): os golpes Elétricos / de Fogo com 1/3 da força;
// - Chuva de Plasma (1 turno): os golpes Normais viram Elétricos;
// - Trava de Fada (1 turno): ninguém foge (o selvagem arisco não foge, e você precisa ir a 24 blocos para sair);
// - Troca de Campo: as barreiras dos dois lados trocam de lado (Refletir, Tela de Luz, Vento de Cauda...; as armadilhas são
//   coisas no chão e ficam onde estão)
public final class CampoDosGolpes {
  public static final String GRAVIDADE = "gravity";
  public static final String SALA_MAGICA = "magicroom";
  public static final String ZONA_ESTRANHA = "wonderroom";
  public static final String ESPORTE_DE_LAMA = "mudsport";
  public static final String ESPORTE_AQUATICO = "watersport";
  public static final String CHUVA_DE_PLASMA = "iondeluge";
  public static final String TRAVA_DE_FADA = "fairylock";
  private static final Map<String, Integer> TURNOS = Map.of(GRAVIDADE, 5, SALA_MAGICA, 5, ZONA_ESTRANHA, 5,
      ESPORTE_DE_LAMA, 5, ESPORTE_AQUATICO, 5, CHUVA_DE_PLASMA, 1, TRAVA_DE_FADA, 1);
  // Os que desfazem ao usar de novo (as salas)
  private static final Set<String> DESFAZEM = Set.of(SALA_MAGICA, ZONA_ESTRANHA);
  private static final double PRECISAO_NA_GRAVIDADE = 5.0 / 3;
  private static final double ESPORTE = 1.0 / 3;

  private CampoDosGolpes() {
  }

  // O golpe sem alvo (golpes.GolpesDeStatus.semAlvo). true = era um destes
  public static boolean aoUsarGolpe(Execucao execucao, PokemonEntity atacante) {
    String golpe = execucao.getGolpe().getName();
    if (!TURNOS.containsKey(golpe) && !"courtchange".equals(golpe)) {
      return false;
    }
    Pokemon pokemon = execucao.getPokemon();
    Arena arena = Arenas.de(pokemon);
    if (arena == null) {
      return true;
    }
    CampoDaBatalha campo = arena.campo();
    if ("courtchange".equals(golpe)) {
      Map<Barreira, CampoDaBatalha.Prazo> meu = Map.copyOf(campo.barreiras(true));
      Map<Barreira, CampoDaBatalha.Prazo> dele = Map.copyOf(campo.barreiras(false));
      campo.barreiras(true).clear();
      campo.barreiras(true).putAll(dele);
      campo.barreiras(false).clear();
      campo.barreiras(false).putAll(meu);
      arena.avisar(pokemon, Component.translatable("cobblemon.battle.activate.courtchange", pokemon.getDisplayName(false)));
      return true;
    }
    long agora = execucao.getJogador().server.getTickCount();
    if (campo.efeitos().containsKey(golpe)) {
      if (DESFAZEM.contains(golpe)) {
        campo.efeitos().remove(golpe);
        arena.avisar(pokemon, Component.translatable("cobblemon.battle.fieldend." + golpe));
      } else {
        arena.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
      }
      return true;
    }
    long duracao = TURNOS.get(golpe) * EfeitosDeStatus.ticksPorTurno();
    campo.efeitos().put(golpe, new CampoDaBatalha.Prazo(agora + duracao, duracao));
    CobblemonCombatZA.LOGGER.info("CAMPO: {} começou ({} turnos)", golpe, TURNOS.get(golpe));
    arena.avisar(pokemon, Component.translatable(TURNOS.get(golpe) == 1
        ? "cobblemon.battle.fieldactivate." + golpe : "cobblemon.battle.fieldstart." + golpe));
    return true;
  }

  // A cada tick (CampoEBarreiras.atualizar): os que acabaram
  public static void atualizar(Arena arena, long agora) {
    Iterator<Map.Entry<String, CampoDaBatalha.Prazo>> efeitos = arena.campo().efeitos().entrySet().iterator();
    while (efeitos.hasNext()) {
      Map.Entry<String, CampoDaBatalha.Prazo> efeito = efeitos.next();
      if (agora < efeito.getValue().fim()) {
        continue;
      }
      efeitos.remove();
      CobblemonCombatZA.LOGGER.info("CAMPO: {} acabou", efeito.getKey());
      if (TURNOS.get(efeito.getKey()) > 1) {
        arena.avisar(null, Component.translatable("cobblemon.battle.fieldend." + efeito.getKey()));
      }
    }
  }

  public static boolean ativo(Pokemon pokemon, String efeito) {
    Arena arena = pokemon == null ? null : Arenas.de(pokemon);
    return arena != null && arena.campo().efeitos().containsKey(efeito);
  }

  public static boolean gravidade(Pokemon pokemon) {
    return ativo(pokemon, GRAVIDADE);
  }

  // ItensSegurados: na Sala Mágica, o item não faz nada
  public static boolean semItem(Pokemon pokemon) {
    return ativo(pokemon, SALA_MAGICA);
  }

  // Dano: na Zona Estranha, a Defesa e a Def. Esp. trocam
  public static boolean defesasTrocadas(Pokemon pokemon) {
    return ativo(pokemon, ZONA_ESTRANHA);
  }

  // Dano: os Esportes cortam a força
  public static double poder(Pokemon atacante, ElementalType tipo) {
    if (tipo == ElementalTypes.ELECTRIC && ativo(atacante, ESPORTE_DE_LAMA)
        || tipo == ElementalTypes.FIRE && ativo(atacante, ESPORTE_AQUATICO)) {
      return ESPORTE;
    }
    return 1.0;
  }

  // ClimaNosGolpes.tipo: na Chuva de Plasma, o golpe Normal vira Elétrico
  public static ElementalType plasma(Pokemon atacante, ElementalType tipo) {
    return tipo == ElementalTypes.NORMAL && ativo(atacante, CHUVA_DE_PLASMA) ? ElementalTypes.ELECTRIC : tipo;
  }

  // Precisao: na Gravidade, ×5/3
  public static double precisao(Pokemon atacante) {
    return gravidade(atacante) ? PRECISAO_NA_GRAVIDADE : 1.0;
  }

  // GolpesQueMarcam: na Gravidade, os golpes de voo e pulo ficam riscados
  public static Component bloqueia(Pokemon pokemon, Move golpe) {
    if (gravidade(pokemon) && EfeitosDosGolpes.temMarca(golpe, "gravity")) {
      return Component.translatable("cobblemon.battle.cant.gravity", pokemon.getDisplayName(false), golpe.getDisplayName());
    }
    return null;
  }
}
