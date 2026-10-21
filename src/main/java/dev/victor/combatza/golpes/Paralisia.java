package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class Paralisia {
  public static final ResourceLocation ID_LENTO = ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID,
      "paralisia");

  private static final Map<UUID, Pokemon> LENTOS = new HashMap<>();

  private Paralisia() {
  }

  public static double multiplicador(Pokemon pokemon) {
    // 21/10 (habilidades, lote da conta do dano): com Pés Rápidos, a paralisia não deixa mais lento
    return estaParalisado(pokemon) && !HabilidadesDaConta.semLentidaoDaParalisia(pokemon)
        ? Balanceamento.VELOCIDADE_NA_PARALISIA : 1.0;
  }

  public static boolean falhouAoUsarGolpe(PokemonEntity entidade) {
    return estaParalisado(entidade.getPokemon())
        && entidade.getRandom().nextFloat() < Balanceamento.CHANCE_DE_FALHAR_NA_PARALISIA;
  }

  public static void deixarLento(PokemonEntity entidade) {
    AttributeInstance velocidade = entidade.getAttribute(Attributes.MOVEMENT_SPEED);
    if (velocidade == null) {
      return;
    }
    velocidade.addOrUpdateTransientModifier(new AttributeModifier(ID_LENTO,
        Balanceamento.VELOCIDADE_NA_PARALISIA - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    LENTOS.put(entidade.getPokemon().getUuid(), entidade.getPokemon());
  }

  public static void atualizar() {
    for (UUID uuid : List.copyOf(LENTOS.keySet())) {
      Pokemon pokemon = LENTOS.get(uuid);
      if (!estaParalisado(pokemon)) {
        soltar(pokemon);
      }
    }
  }

  public static void limpar() {
    LENTOS.clear();
  }

  private static boolean estaParalisado(Pokemon pokemon) {
    return "par".equals(EfeitosDeStatus.siglaDoStatus(pokemon));
  }

  private static void soltar(Pokemon pokemon) {
    LENTOS.remove(pokemon.getUuid());
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade == null) {
      return;
    }

    AttributeInstance velocidade = entidade.getAttribute(Attributes.MOVEMENT_SPEED);
    if (velocidade != null) {
      velocidade.removeModifier(ID_LENTO);
    }
  }
}
