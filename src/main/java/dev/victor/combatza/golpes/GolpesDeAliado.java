package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.Terreno;
import dev.victor.combatza.golpes.Marcas.Marca;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaDupla;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// 01/11 (as pendências, "como nos jogos"): os golpes de ALIADO. A mira só vai em inimigos, então o aliado é achado pelo golpe:
// o Pokémon do mesmo lado mais perto de quem usou (na dupla, o seu outro Pokémon; numa party, o Pokémon de um amigo; num bando,
// outro selvagem). Sem aliado na luta, "Mas falhou!":
// - Mãozinha (o próximo golpe do aliado 1,5× por 1 turno), Treinamento (Ataque e Defesa +1), Névoa Aromática (Def. Esp. +1),
//   Decorar (Ataque e Atq. Esp. +2), Bramido do Dragão (crítico +1; +2 no Dragão), Pulso de Cura e Cura Floral (metade da
//   vida; a Floral 2/3 no Campo de Plantas), Depois de Você (o aliado age já: as recargas dele zeram), Troca de Aliados (troca
//   de lugar com ele);
// - Engrenar e Fluxo Magnético (quem tem Mais ou Menos do lado: Ataque e Atq. Esp. / Defesa e Def. Esp. +1), Escudo Florido
//   (a Defesa dos de Planta da luta +1), Arar (Ataque e Atq. Esp. dos de Planta no chão +1);
// - no inimigo: Adiar (as recargas dele esperam 1 turno a mais) e Holofote (vira o centro das atenções do lado dele)
public final class GolpesDeAliado {
  // Os golpes de aliado que no Showdown miram "normal" (um alvo qualquer): aqui vão no aliado
  public static final Set<String> NO_ALIADO = Set.of("healpulse", "floralhealing", "decorate");
  private static final Set<String> MAIS_E_MENOS = Set.of("plus", "minus");

  private GolpesDeAliado() {
  }

  // O golpe sem alvo (GolpesDeStatus.semAlvo). true = era um destes
  public static boolean semAlvo(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = execucao.getPokemon();
    String golpe = execucao.getGolpe().getName();
    long agora = execucao.getJogador().server.getTickCount();
    switch (golpe) {
      case "helpinghand", "coaching", "aromaticmist", "decorate", "dragoncheer", "healpulse", "floralhealing", "afteryou",
          "allyswitch" -> {
        PokemonEntity aliado = aliadoMaisPerto(atacante);
        if (aliado == null) {
          GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
          return true;
        }
        noAliado(execucao, atacante, aliado, golpe, agora);
      }
      case "gearup", "magneticflux" -> {
        for (PokemonEntity quem : doLado(atacante)) {
          if (MAIS_E_MENOS.contains(Habilidades.atual(quem.getPokemon()))) {
            boolean ataque = "gearup".equals(golpe);
            AplicarEfeitos.mudarUmAtributo(execucao, quem, ataque ? "atk" : "def", 1);
            AplicarEfeitos.mudarUmAtributo(execucao, quem, ataque ? "spa" : "spd", 1);
          }
        }
      }
      case "flowershield", "rototiller" -> {
        for (PokemonEntity quem : todos(atacante)) {
          if (!AplicarStatus.temTipo(quem.getPokemon(), ElementalTypes.GRASS)) {
            continue;
          }
          if ("flowershield".equals(golpe)) {
            AplicarEfeitos.mudarUmAtributo(execucao, quem, "def", 1);
          } else if (quem.onGround() && !GolpesQueMarcam.flutuando(quem.getPokemon())) {
            AplicarEfeitos.mudarUmAtributo(execucao, quem, "atk", 1);
            AplicarEfeitos.mudarUmAtributo(execucao, quem, "spa", 1);
          }
        }
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  private static void noAliado(Execucao execucao, PokemonEntity atacante, PokemonEntity aliado, String golpe, long agora) {
    Pokemon pokemon = execucao.getPokemon();
    Pokemon doAliado = aliado.getPokemon();
    CobblemonCombatZA.LOGGER.info("ALIADO: {} usou {} em {}", pokemon.getSpecies().getName(), golpe,
        doAliado.getSpecies().getName());
    switch (golpe) {
      case "helpinghand" -> {
        Marcas.marcar(doAliado, Marca.MAOZINHA, agora, EfeitosDeStatus.ticksPorTurno(), 0, pokemon, golpe);
        GolpesDeStatus.avisar(doAliado, Component.translatable("cobblemon.battle.singleturn.helpinghand",
            doAliado.getDisplayName(false), pokemon.getDisplayName(false)));
      }
      case "coaching" -> {
        AplicarEfeitos.mudarUmAtributo(execucao, aliado, "atk", 1);
        AplicarEfeitos.mudarUmAtributo(execucao, aliado, "def", 1);
      }
      case "aromaticmist" -> AplicarEfeitos.mudarUmAtributo(execucao, aliado, "spd", 1);
      case "decorate" -> {
        AplicarEfeitos.mudarUmAtributo(execucao, aliado, "atk", 2);
        AplicarEfeitos.mudarUmAtributo(execucao, aliado, "spa", 2);
      }
      case "dragoncheer" -> {
        int estagio = AplicarStatus.temTipo(doAliado, ElementalTypes.DRAGON) ? 2 : 1;
        Marcas.marcar(doAliado, Marca.ANIMO_DO_DRAGAO, agora, Marcas.ATE_SAIR, estagio, pokemon, golpe);
        GolpesDeStatus.avisar(doAliado, Component.translatable("cobblemon.battle.start.focusenergy",
            doAliado.getDisplayName(false)));
      }
      case "healpulse", "floralhealing" -> {
        boolean noCampo = "floralhealing".equals(golpe) && CampoEBarreiras.terreno(doAliado) == Terreno.PLANTAS;
        if (Vida.pvAtual(doAliado) >= doAliado.getMaxHealth()) {
          GolpesDeStatus.avisar(doAliado, Component.translatable("cobblemon.battle.fail.heal",
              doAliado.getDisplayName(false)));
          return;
        }
        int curou = Vida.curar(aliado, doAliado.getMaxHealth() * (noCampo ? 2.0 / 3 : 0.5));
        GolpesDeStatus.avisar(doAliado, Component.translatable("cobblemon.battle.heal.generic",
            doAliado.getDisplayName(false)).append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
      }
      case "afteryou" -> {
        for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
          Recargas.zerar(doAliado, slot, agora);
        }
        GolpesDeStatus.avisar(doAliado, Component.translatable("cobblemon.battle.activate.afteryou",
            doAliado.getDisplayName(false)));
      }
      default -> {
        // allyswitch
        Vec3 meu = atacante.position();
        Vec3 dele = aliado.position();
        atacante.teleportTo(dele.x, dele.y, dele.z);
        aliado.teleportTo(meu.x, meu.y, meu.z);
        GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.activate.allyswitch",
            pokemon.getDisplayName(false), doAliado.getDisplayName(false)));
      }
    }
  }

  // No inimigo (GolpesDeStatus.depoisDeAcertar): Adiar e Holofote
  public static void noAlvo(Execucao execucao, Entity alvo) {
    if (!(alvo instanceof PokemonEntity atingido) || HabilidadesQueReagem.caiu(atingido)) {
      return;
    }
    Pokemon doAlvo = atingido.getPokemon();
    long agora = execucao.getJogador().server.getTickCount();
    switch (execucao.getGolpe().getName()) {
      case "quash" -> {
        Recargas.esperar(doAlvo, agora, EfeitosDeStatus.ticksPorTurno());
        GolpesDeStatus.avisar(doAlvo, Component.translatable("cobblemon.battle.activate.quash",
            doAlvo.getDisplayName(false)));
      }
      case "spotlight" -> {
        Redirecionamento.centro(doAlvo, agora);
        GolpesDeStatus.avisar(doAlvo, Component.translatable("cobblemon.battle.singleturn.spotlight",
            doAlvo.getDisplayName(false)));
      }
      default -> {
        // os outros golpes não agem aqui
      }
    }
  }

  // O aliado de pé mais perto de quem usou (null = nenhum)
  private static PokemonEntity aliadoMaisPerto(PokemonEntity atacante) {
    PokemonEntity maisPerto = null;
    for (Pokemon aliado : HabilidadesDaDupla.aliados(atacante.getPokemon())) {
      PokemonEntity entidade = aliado.getEntity();
      if (entidade != null && HabilidadesQueReagem.dePe(entidade)
          && (maisPerto == null || atacante.distanceToSqr(entidade) < atacante.distanceToSqr(maisPerto))) {
        maisPerto = entidade;
      }
    }
    return maisPerto;
  }

  // Quem usou e os aliados de pé
  private static List<PokemonEntity> doLado(PokemonEntity atacante) {
    List<PokemonEntity> lado = new ArrayList<>(List.of(atacante));
    for (Pokemon aliado : HabilidadesDaDupla.aliados(atacante.getPokemon())) {
      if (aliado.getEntity() != null) {
        lado.add(aliado.getEntity());
      }
    }
    return lado;
  }

  // Todos de pé na arena (fora da batalha, só quem usou)
  private static List<PokemonEntity> todos(PokemonEntity atacante) {
    Arena arena = Arenas.de(atacante.getPokemon());
    if (arena == null) {
      return List.of(atacante);
    }
    return arena.pokemons().stream().filter(HabilidadesQueReagem::dePe).toList();
  }
}
