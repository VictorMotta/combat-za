package dev.victor.combatza.treinadores;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 21/10 (treinadores, decisão dele: "o golpe Z não existe no tempo real: o Pokémon usa o item reserva"). No RCT, o item
// segurado de cada Pokémon é uma LISTA de alternativas (["mega_showdown:waterium_z", "water_gem"]): o rctapi dá o primeiro
// que existe no jogo (javap no PokemonModelConverter). Com o Mega Showdown no modpack, 35 Pokémon de treinadores seguram um
// Cristal Z, que na nossa batalha não faz nada; então o Pokémon troca pelo próximo item da lista que existe (a Gema do tipo,
// quase sempre). Sem o Mega Showdown, o rctapi já deu a Gema, e nada muda aqui
final class CristalZ {
  // A etiqueta dos Cristais Z do Mega Showdown (data/mega_showdown/tags/item/z_crystal.json); sem ele, nenhum item tem ela
  private static final TagKey<Item> CRISTAIS_Z = TagKey.create(Registries.ITEM,
      ResourceLocation.fromNamespaceAndPath("mega_showdown", "z_crystal"));
  // O rctapi completa os ids sem dono com "cobblemon:" (Locations.withNamespace)
  private static final String DONO_PADRAO = "cobblemon";

  private CristalZ() {
  }

  // Cada Pokémon do time com Cristal Z passa a segurar o item reserva da lista dele nos dados do RCT
  static void trocarPelaReserva(List<Pokemon> time, PonteDoRct.DadosDoTime dados) {
    if (dados == null) {
      return;
    }
    Set<Integer> usados = new HashSet<>();
    for (Pokemon pokemon : time) {
      ItemStack segurado = pokemon.heldItem();
      if (segurado.isEmpty() || !segurado.is(CRISTAIS_Z)) {
        continue;
      }
      String cristal = BuiltInRegistries.ITEM.getKey(segurado.getItem()).toString();
      Item reserva = reserva(pokemon, cristal, dados.pokemons(), usados);
      if (reserva == null) {
        CobblemonCombatZA.LOGGER.info("TREINADOR: {} segura {} e não tem item reserva", pokemon.getSpecies().getName(),
            cristal);
        continue;
      }
      pokemon.swapHeldItem(new ItemStack(reserva), false, false);
      CobblemonCombatZA.LOGGER.info("TREINADOR: {} trocou {} (golpe Z não existe no tempo real) pela reserva {}",
          pokemon.getSpecies().getName(), cristal, BuiltInRegistries.ITEM.getKey(reserva));
    }
  }

  // O Pokémon dos dados com a mesma espécie e o cristal na lista; a reserva é o primeiro item DEPOIS do cristal que existe
  private static Item reserva(Pokemon pokemon, String cristal, List<PonteDoRct.PokemonDoTime> modelos, Set<Integer> usados) {
    String especie = soLetras(pokemon.getSpecies().getResourceIdentifier().getPath());
    for (int i = 0; i < modelos.size(); i++) {
      PonteDoRct.PokemonDoTime modelo = modelos.get(i);
      List<String> itens = modelo.itensSegurados();
      int posicao = itens.stream().map(CristalZ::comDono).toList().indexOf(cristal);
      if (usados.contains(i) || posicao < 0 || !especie.equals(soLetras(semDono(modelo.especie())))) {
        continue;
      }
      usados.add(i);
      for (String depois : itens.subList(posicao + 1, itens.size())) {
        ResourceLocation id = ResourceLocation.tryParse(comDono(depois));
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
          return BuiltInRegistries.ITEM.get(id);
        }
      }
      return null;
    }
    return null;
  }

  private static String comDono(String id) {
    String limpo = id.trim().toLowerCase(Locale.ROOT);
    return limpo.contains(":") ? limpo : DONO_PADRAO + ":" + limpo;
  }

  private static String semDono(String id) {
    int doisPontos = id.indexOf(':');
    return doisPontos < 0 ? id : id.substring(doisPontos + 1);
  }

  // "Mr. Mime", "mr_mime" e "mrmime" viram "mrmime"
  private static String soLetras(String nome) {
    return nome.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}
