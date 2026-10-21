package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 21/10 (habilidades, lote das reações): as habilidades que mexem no ITEM SEGURADO:
// - Furto (apanhou de um golpe de contato) e Mágico (acertou um golpe de dano): sem item na mão, rouba o item do outro
//   (HabilidadesQueReagem chama roubar). Só entre inimigos, numa batalha; a Viscosidade segura, e as Pedras Mega e os
//   Cristais Z não saem da mão;
// - Alígero: gastou (ItensSegurados.gastar) ou perdeu o item: Velocidade ×2 (ItensDeDano.velocidade pergunta), até ganhar
//   outro item ou voltar para a bola;
// - Colheita e Pegar (HabilidadesDoTurno) leem o último item gasto de cada um.
// O roubo contra selvagem (escolha "como no Cobblemon", que devolve os itens dos seus Pokémon no fim da batalha): o que o
// SEU Pokémon rouba de um selvagem fica com ele; o que um selvagem rouba do seu Pokémon volta para o seu quando o ladrão sai
// da batalha (derrotado, capturado, fugiu) ou a batalha acaba, se o ladrão ainda estiver com ele (a baga roubada e comida
// some, como a baga comida numa batalha contra selvagem)
public class HabilidadesDosItens {
  private static final String FRASE = "cobblemon_combat_za.habilidade.";
  private static final String ALIGERO = "unburden";
  // De outro lote, mas é o roubo que pergunta (como no Showdown): a Viscosidade segura o item na mão
  // Os itens que ninguém tira da mão (as Pedras Mega e os Cristais Z do Mega Showdown do modpack, pelas etiquetas dele)
  private static final TagKey<Item> PEDRAS_MEGA = TagKey.create(Registries.ITEM,
      ResourceLocation.fromNamespaceAndPath("mega_showdown", "mega_stone"));
  private static final TagKey<Item> CRISTAIS_Z = TagKey.create(Registries.ITEM,
      ResourceLocation.fromNamespaceAndPath("mega_showdown", "z_crystal"));

  // O último item que o Pokémon gastou nesta batalha (a Colheita replanta a baga; o Pegar apanha o item gasto há pouco)
  private record ItemGasto(String id, ItemStack item, long tick) {
  }

  // Um item que um selvagem roubou de um Pokémon de jogador (volta para ele: devolverOsRoubos)
  private record Roubo(Pokemon vitima, Pokemon ladrao, ItemStack item) {
  }

  private static final Map<UUID, ItemGasto> GASTOS = new HashMap<>();
  // Alígero: a entidade que ficou sem o item (voltou para a bola, acabou, como nos jogos)
  private static final Map<UUID, PokemonEntity> SEM_O_ITEM = new HashMap<>();
  private static final List<Roubo> ROUBOS = new ArrayList<>();

  private HabilidadesDosItens() {
  }

  // 21/10: o Pokémon do treinador é uma cópia que some no fim (BatalhasDeTreinador): o item dele não sai da batalha com você
  private static boolean doTreinador(Pokemon pokemon) {
    return pokemon.getEntity() != null && BatalhasDeTreinador.ehDoTreinador(pokemon.getEntity());
  }

  // Furto e Mágico: o ladrão sem item fica com o item da vítima. "deOnde": a habilidade do ladrão, para a frase
  public static void roubar(Pokemon ladrao, Pokemon vitima, Component deOnde) {
    ItemStack daVitima = vitima.heldItem();
    // Só entre selvagem e Pokémon com dono: no duelo (PvP) ninguém rouba o item do outro jogador para sempre
    if (!ladrao.heldItem().isEmpty() || daVitima.isEmpty() || ladrao.isWild() == vitima.isWild()
        || GerenciarCombates.combateDe(ladrao) == null || daVitima.is(PEDRAS_MEGA) || daVitima.is(CRISTAIS_Z)
        || doTreinador(vitima) || HabilidadesDeProtecao.seguraOItem(vitima, ladrao)) {
      return;
    }
    Component nome = ItensSegurados.nome(vitima);
    ItemStack item = vitima.removeHeldItem();
    ladrao.swapHeldItem(item, false, true);
    // Algum mod recusou a troca (o aviso HELD_ITEM_PRE do Cobblemon pode ser cancelado): o item volta para a vítima
    if (ladrao.heldItem().isEmpty()) {
      vitima.swapHeldItem(item, false, true);
      return;
    }
    // O seu Pokémon tomou de volta um item que este selvagem tinha roubado: aquele roubo acabou aqui
    ROUBOS.removeIf(roubo -> roubo.ladrao().getUuid().equals(vitima.getUuid()));
    if (ladrao.isWild()) {
      ROUBOS.add(new Roubo(vitima, ladrao, item.copyWithCount(1)));
    }
    perdeuOItem(vitima);
    HabilidadesQueReagem.avisar(ladrao, Component.translatable("cobblemon.battle.item.thief", ladrao.getDisplayName(false),
        nome, vitima.getDisplayName(false)).append(" (").append(deOnde).append(")"));
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} roubou {} de {} ({})", ladrao.getSpecies().getName(), nome.getString(),
        vitima.getSpecies().getName(), Habilidades.atual(ladrao));
  }

  // O selvagem ladrão saiu da batalha, ou a batalha acabou para a vítima: o item volta (para a mão dela; se ela já segura
  // outro, para a mochila do dono). Sem o item (comeu a baga roubada), nada volta
  private static void devolverOsRoubos(Pokemon pokemon) {
    UUID uuid = pokemon.getUuid();
    List<Roubo> desfeitos = ROUBOS.stream()
        .filter(roubo -> roubo.vitima().getUuid().equals(uuid) || roubo.ladrao().getUuid().equals(uuid)).toList();
    ROUBOS.removeAll(desfeitos);
    for (Roubo roubo : desfeitos) {
      devolver(roubo);
    }
  }

  private static void devolver(Roubo roubo) {
    Pokemon ladrao = roubo.ladrao();
    Pokemon vitima = roubo.vitima();
    if (!ItemStack.isSameItemSameComponents(ladrao.heldItem(), roubo.item())) {
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} não está mais com o item roubado de {}: nada volta",
          ladrao.getSpecies().getName(), vitima.getSpecies().getName());
      return;
    }
    ItemStack item = ladrao.removeHeldItem();
    Component nome = item.getHoverName();
    ServerPlayer dono = vitima.getOwnerPlayer();
    if (vitima.heldItem().isEmpty()) {
      vitima.swapHeldItem(item, false, true);
      if (dono != null) {
        Feedback.barraDeAcao(dono, Component.translatable(FRASE + "devolvido", vitima.getDisplayName(false), nome,
            ladrao.getDisplayName(false)));
      }
    } else if (dono != null) {
      dono.getInventory().placeItemBackInInventory(item);
      Feedback.barraDeAcao(dono, Component.translatable(FRASE + "devolvido_mochila", vitima.getDisplayName(false), nome,
          ladrao.getDisplayName(false)));
    }
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} voltou para {} (roubado por {})", nome.getString(),
        vitima.getSpecies().getName(), ladrao.getSpecies().getName());
  }

  // ItensSegurados.gastar: o Pokémon gastou um item (antes de ele sumir da mão). A Colheita e o Pegar leem; o Alígero começa
  public static void gastouItem(Pokemon pokemon, String id) {
    PokemonEntity entidade = pokemon.getEntity();
    long agora = entidade != null && entidade.getServer() != null ? entidade.getServer().getTickCount() : 0;
    GASTOS.put(pokemon.getUuid(), new ItemGasto(id, pokemon.heldItem().copyWithCount(1), agora));
    perdeuOItem(pokemon);
  }

  private static void perdeuOItem(Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade != null && Habilidades.tem(pokemon, ALIGERO)) {
      SEM_O_ITEM.put(pokemon.getUuid(), entidade);
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} ficou sem o item: Velocidade ×2 (Alígero)",
          pokemon.getSpecies().getName());
    }
  }

  // ItensDeDano.velocidade: com Alígero, depois de gastar ou perder o item, a Velocidade dobra enquanto ele estiver sem item
  // (ou com o item gasto, que contra selvagem fica na mão), até voltar para a bola
  public static boolean aligero(Pokemon pokemon) {
    PokemonEntity semItem = SEM_O_ITEM.get(pokemon.getUuid());
    if (semItem == null || semItem != pokemon.getEntity() || !Habilidades.tem(pokemon, ALIGERO)) {
      return false;
    }
    return pokemon.heldItem().isEmpty() || ItensSegurados.usadoNestaBatalha(pokemon, ItensSegurados.id(pokemon));
  }

  // A Colheita: a última baga que ele comeu nesta batalha (null = nenhuma)
  public static ItemStack ultimaBaga(Pokemon pokemon) {
    ItemGasto gasto = GASTOS.get(pokemon.getUuid());
    return gasto != null && gasto.id().endsWith("berry") ? gasto.item().copy() : null;
  }

  // O Pegar: o item que ele gastou há menos de um turno e que não está mais na mão dele (null = nenhum)
  public static ItemStack gastouHaPouco(Pokemon pokemon, long agora) {
    ItemGasto gasto = GASTOS.get(pokemon.getUuid());
    if (gasto == null || agora - gasto.tick() > EfeitosDeStatus.ticksPorTurno()
        || !pokemon.heldItem().isEmpty() && ItensSegurados.id(pokemon).equals(gasto.id())) {
      return null;
    }
    return gasto.item().copy();
  }

  // A baga replantada ou o item apanhado: o gasto já foi usado (como o "lastItem" do Showdown, que fica vazio)
  public static void esquecerOGasto(Pokemon pokemon) {
    GASTOS.remove(pokemon.getUuid());
  }

  // A batalha começou ou acabou para ele (ou o selvagem saiu dela): o item roubado volta, e o resto acaba
  public static void esquecer(Pokemon pokemon) {
    devolverOsRoubos(pokemon);
    GASTOS.remove(pokemon.getUuid());
    SEM_O_ITEM.remove(pokemon.getUuid());
  }

  public static void limpar() {
    GASTOS.clear();
    SEM_O_ITEM.clear();
    ROUBOS.clear();
  }
}
