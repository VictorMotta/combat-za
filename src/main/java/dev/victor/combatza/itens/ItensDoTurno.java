package dev.victor.combatza.itens;

import java.util.Map;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.Terreno;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.network.chat.Component;

// Lote 3 dos itens segurados: os que agem com o TEMPO, nos Pokémon da batalha (ItensDeVida chama, como as Sobras):
// - a cada tick, as 4 Sementes: com o campo delas valendo, Defesa +1 (Elétrica e de Grama) ou Defesa Especial +1 (Enevoada
//   e Psíquica), e a semente some;
// - a cada turno de 3,5 s: Orbe de Chamas (queima quem segura) e Orbe Tóxico (envenena muito), se ele pode pegar o status;
//   Farpa Grudenta (quem segura perde 1/8 da vida)
public class ItensDoTurno {
  private static final int FARPA = 8;

  private record Semente(Terreno terreno, String atributo) {
  }

  private static final Map<String, Semente> SEMENTES = Map.of(
      "electricseed", new Semente(Terreno.ELETRICO, "def"), "grassyseed", new Semente(Terreno.PLANTAS, "def"),
      "mistyseed", new Semente(Terreno.ENEVOADO, "spd"), "psychicseed", new Semente(Terreno.PSIQUICO, "spd"));

  private ItensDoTurno() {
  }

  public static void aCadaTick(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    String item = ItensSegurados.id(pokemon);
    Semente semente = SEMENTES.get(item);
    if (semente == null || CampoEBarreiras.terreno(pokemon) != semente.terreno()
        || !ItensSegurados.disponivel(pokemon, item)) {
      return;
    }
    Component nome = ItensSegurados.gastarComNome(pokemon, item);
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.generic", pokemon.getDisplayName(false),
        nome));
    ItensDeEstagio.mudar(pokemon, semente.atributo(), 1);
  }

  public static void porTurno(PokemonEntity entidade, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    String item = ItensSegurados.id(pokemon);
    switch (item) {
      case "flameorb" -> AplicarStatus.porItem(entidade, "brn", ItensSegurados.nome(pokemon), agora);
      case "toxicorb" -> AplicarStatus.porItem(entidade, "tox", ItensSegurados.nome(pokemon), agora);
      case "stickybarb" -> {
        int pv = Math.max(1, pokemon.getMaxHealth() / FARPA);
        ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.damage.item", pokemon.getDisplayName(false),
            ItensSegurados.nome(pokemon)).append(Component.translatable("cobblemon_combat_za.pv.perdeu", pv)));
        Vida.ferir(entidade, pv);
        CobblemonCombatZA.LOGGER.info("ITEM: {} −{} PV (Farpa Grudenta)", pokemon.getSpecies().getName(), pv);
      }
      default -> {
        // sem item de turno
      }
    }
  }
}
