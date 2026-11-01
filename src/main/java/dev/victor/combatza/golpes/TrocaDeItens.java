package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Raids;
import dev.victor.combatza.golpes.Marcas.Marca;
import dev.victor.combatza.habilidades.HabilidadesDeProtecao;
import dev.victor.combatza.habilidades.HabilidadesDosItens;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

// 01/11 (as pendências, decisão dele: "só durante a batalha"): os golpes que TROCAM ITENS segurados. Travessura e Hora da
// Troca trocam os itens dos dois; a Oferenda dá o item de quem usa para quem está sem. Quando a batalha acaba para um dos
// dois, cada item volta para o dono de antes (o que foi comido ou gasto no meio, não volta). A Pedra Mega, o Cristal Z, a
// Viscosidade e o chefão da raid seguram. O Gás Corrosivo deixa os itens de todos em volta sem efeito até saírem da luta
public final class TrocaDeItens {
  // Uma troca: os dois Pokémon e o item de cada um antes
  private record Troca(Pokemon um, ItemStack doUm, Pokemon outro, ItemStack doOutro) {
  }

  private static final List<Troca> TROCAS = new ArrayList<>();
  private static final int CONFERIR_A_CADA_TICKS = 20;

  private TrocaDeItens() {
  }

  // O golpe no alvo (GolpesDeStatus.depoisDeAcertar)
  public static void noAlvo(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity atingido) || HabilidadesQueReagem.caiu(atingido)) {
      return;
    }
    Pokemon pokemon = execucao.getPokemon();
    Pokemon doAlvo = atingido.getPokemon();
    switch (execucao.getGolpe().getName()) {
      case "trick", "switcheroo" -> trocar(pokemon, doAlvo, atacante, atingido, execucao.getGolpe().getName());
      case "bestow" -> oferecer(pokemon, doAlvo, atingido);
      case "corrosivegas" -> {
        if (doAlvo.heldItem().isEmpty() || HabilidadesDeProtecao.seguraOItem(doAlvo, pokemon)) {
          return;
        }
        Marcas.marcar(doAlvo, Marca.EMBARGO, execucao.getJogador().server.getTickCount(), Marcas.ATE_SAIR, 0, pokemon,
            "corrosivegas");
        GolpesDeStatus.avisar(doAlvo, Component.translatable("cobblemon.battle.enditem.corrosivegas",
            doAlvo.getDisplayName(false), ItensSegurados.nome(doAlvo), pokemon.getDisplayName(false)));
      }
      default -> {
        // os outros golpes não mexem nos itens
      }
    }
  }

  private static boolean segura(Pokemon dono, Pokemon quemTira, PokemonEntity entidade) {
    return HabilidadesDosItens.naoSaiDaMao(dono.heldItem()) || HabilidadesDeProtecao.seguraOItem(dono, quemTira)
        || Raids.ehChefe(entidade) || GerenciarCombates.combateDe(dono) == null;
  }

  private static void trocar(Pokemon pokemon, Pokemon doAlvo, PokemonEntity atacante, PokemonEntity atingido, String golpe) {
    if (pokemon.heldItem().isEmpty() && doAlvo.heldItem().isEmpty() || segura(pokemon, doAlvo, atacante)
        || segura(doAlvo, pokemon, atingido)) {
      falhou(pokemon);
      return;
    }
    ItemStack doUm = pokemon.removeHeldItem();
    ItemStack doOutro = doAlvo.removeHeldItem();
    pokemon.swapHeldItem(doOutro, false, true);
    doAlvo.swapHeldItem(doUm, false, true);
    TROCAS.add(new Troca(pokemon, doUm.copy(), doAlvo, doOutro.copy()));
    CobblemonCombatZA.LOGGER.info("TROCA DE ITENS: {} ({}) e {} ({})", pokemon.getSpecies().getName(),
        doUm.getHoverName().getString(), doAlvo.getSpecies().getName(), doOutro.getHoverName().getString());
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.activate." + golpe,
        pokemon.getDisplayName(false)));
    if (!doOutro.isEmpty()) {
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.item.trick", pokemon.getDisplayName(false),
          doOutro.getHoverName()));
    }
    if (!doUm.isEmpty()) {
      GolpesDeStatus.avisar(doAlvo, Component.translatable("cobblemon.battle.item.trick", doAlvo.getDisplayName(false),
          doUm.getHoverName()));
    }
  }

  private static void oferecer(Pokemon pokemon, Pokemon doAlvo, PokemonEntity atingido) {
    PokemonEntity atacante = pokemon.getEntity();
    if (pokemon.heldItem().isEmpty() || !doAlvo.heldItem().isEmpty() || atacante == null
        || segura(pokemon, doAlvo, atacante) || Raids.ehChefe(atingido)) {
      falhou(pokemon);
      return;
    }
    ItemStack item = pokemon.removeHeldItem();
    doAlvo.swapHeldItem(item, false, true);
    TROCAS.add(new Troca(pokemon, item.copy(), doAlvo, ItemStack.EMPTY));
    GolpesDeStatus.avisar(doAlvo, Component.translatable("cobblemon.battle.item.bestow", doAlvo.getDisplayName(false),
        item.getHoverName(), pokemon.getDisplayName(false)));
  }

  // A cada tick (GolpesDeStatus): a cada segundo, as trocas de quem saiu da batalha se desfazem
  public static void atualizar(long agora) {
    if (TROCAS.isEmpty() || agora % CONFERIR_A_CADA_TICKS != 0) {
      return;
    }
    for (Troca troca : List.copyOf(TROCAS)) {
      if (!GerenciarCombates.estaEmCombate(troca.um()) || !GerenciarCombates.estaEmCombate(troca.outro())) {
        TROCAS.remove(troca);
        desfazer(troca);
      }
    }
  }

  // Cada item volta para o dono de antes, se quem ficou com ele ainda o segura
  private static void desfazer(Troca troca) {
    ItemStack voltaParaOUm = ItemStack.EMPTY;
    ItemStack voltaParaOOutro = ItemStack.EMPTY;
    if (!troca.doUm().isEmpty() && ItemStack.isSameItemSameComponents(troca.outro().heldItem(), troca.doUm())) {
      voltaParaOUm = troca.outro().removeHeldItem();
    }
    if (!troca.doOutro().isEmpty() && ItemStack.isSameItemSameComponents(troca.um().heldItem(), troca.doOutro())) {
      voltaParaOOutro = troca.um().removeHeldItem();
    }
    devolver(troca.um(), voltaParaOUm);
    devolver(troca.outro(), voltaParaOOutro);
    CobblemonCombatZA.LOGGER.info("TROCA DE ITENS desfeita: {} e {}", troca.um().getSpecies().getName(),
        troca.outro().getSpecies().getName());
  }

  // Na mão vazia; senão, na mochila do dono (o selvagem com a mão ocupada fica sem: não acontece, a mão dele foi esvaziada)
  private static void devolver(Pokemon pokemon, ItemStack item) {
    if (item.isEmpty()) {
      return;
    }
    if (pokemon.heldItem().isEmpty()) {
      pokemon.swapHeldItem(item, false, true);
      return;
    }
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    if (dono != null) {
      dono.getInventory().placeItemBackInInventory(item);
    }
  }

  private static void falhou(Pokemon pokemon) {
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
  }

  // O servidor vai fechar: tudo volta antes de salvar
  public static void limpar() {
    for (Troca troca : List.copyOf(TROCAS)) {
      desfazer(troca);
    }
    TROCAS.clear();
  }
}
