package dev.victor.combatza.habilidades;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.Clima;
import dev.victor.combatza.campo.EfeitosDoCampo;
import dev.victor.combatza.campo.Terreno;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;

// 21/10 (habilidades, lote da entrada e das imunidades): Protossíntese e Impulso Quark, as habilidades dos Pokémon Paradoxo
// (Showdown, abilities.js; a Energia Auxiliar em items.js):
// - no Sol (Protossíntese; o Sol extremo da Terra Desolada também conta) ou no Campo Elétrico (Impulso Quark), o MAIOR
//   atributo dele fica ×1,3 (×1,5 se for a Velocidade) enquanto o Sol (o campo) durar. O maior é escolhido na hora em que
//   ativa, com os estágios de agora; no empate, Ataque, Defesa, Ataque Especial, Defesa Especial, Velocidade;
// - sem o Sol (o campo), na batalha, a Energia Auxiliar que ele segura é gasta e o efeito dura até ele voltar para a bola,
//   desmaiar ou a batalha acabar (o Sol que chega ou vai embora não muda nada).
// A conta: atributo(), no Dano (Ataque, Defesa, Ataque Especial e Defesa Especial) e na carga do golpe (Execucao:
// Velocidade)
public class Paradoxo {
  private static final String PROTOSSINTESE = "protosynthesis";
  private static final String IMPULSO_QUARK = "quarkdrive";
  private static final String ENERGIA_AUXILIAR = "boosterenergy";
  private static final double FORTE = 5325.0 / 4096.0;
  private static final double VELOCIDADE = 1.5;
  private static final List<String> ATRIBUTOS = List.of("atk", "def", "spa", "spd", "spe");
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  // O atributo que ficou mais forte; "daBatalha" = a batalha em que a Energia Auxiliar foi gasta (null = pelo Sol ou campo)
  private record Ativo(Pokemon pokemon, String atributo, Combate daBatalha) {
  }

  private static final Map<UUID, Ativo> ATIVOS = new HashMap<>();

  private Paradoxo() {
  }

  // O multiplicador de um atributo ("atk", "def", "spa", "spd", "spe") dele agora
  public static double atributo(Pokemon pokemon, String atributo) {
    Ativo ativo = ATIVOS.get(pokemon.getUuid());
    if (ativo == null || !ativo.atributo().equals(atributo)) {
      return 1.0;
    }
    return "spe".equals(atributo) ? VELOCIDADE : FORTE;
  }

  // A cada 5 ticks (HabilidadesDeEntrada), em cada arena: liga com o Sol (o campo) ou a Energia Auxiliar, desliga sem eles
  public static void atualizar(Arena arena) {
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon pokemon = entidade.getPokemon();
      String habilidade = Habilidades.atual(pokemon);
      if (!PROTOSSINTESE.equals(habilidade) && !IMPULSO_QUARK.equals(habilidade) || !entidade.isAlive()
          || pokemon.isFainted()) {
        continue;
      }
      boolean peloCampo = PROTOSSINTESE.equals(habilidade) ? EfeitosDoCampo.climaQueVale(arena) == Clima.SOL
          : arena.campo().getTerreno() == Terreno.ELETRICO;
      Ativo ativo = ATIVOS.get(pokemon.getUuid());
      if (peloCampo && ativo == null) {
        ativar(pokemon, null);
      } else if (!peloCampo && ativo != null && ativo.daBatalha() == null) {
        acabar(pokemon);
        ativo = null;
      }
      if (!peloCampo && ativo == null && arena.naBatalha() && ItensSegurados.disponivel(pokemon, ENERGIA_AUXILIAR)) {
        Component item = ItensSegurados.gastarComNome(pokemon, ENERGIA_AUXILIAR);
        ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "paradoxo_energia", pokemon.getDisplayName(false),
            item, Habilidades.nome(pokemon)));
        ativar(pokemon, GerenciarCombates.combateDe(pokemon));
      }
    }
  }

  // Quem voltou para a bola, desmaiou, saiu da arena (o selvagem que fugiu) ou acabou a batalha da Energia Auxiliar: acabou
  public static void varrer() {
    ATIVOS.values().removeIf(ativo -> {
      Pokemon pokemon = ativo.pokemon();
      return pokemon.getEntity() == null || pokemon.isFainted() || Arenas.de(pokemon) == null
          || ativo.daBatalha() != null && GerenciarCombates.combateDe(pokemon) != ativo.daBatalha();
    });
  }

  public static void esquecer(Pokemon pokemon) {
    ATIVOS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    ATIVOS.clear();
  }

  private static void ativar(Pokemon pokemon, Combate daEnergia) {
    String atributo = maiorAtributo(pokemon);
    ATIVOS.put(pokemon.getUuid(), new Ativo(pokemon, atributo, daEnergia));
    CobblemonCombatZA.LOGGER.info("PARADOXO: {} ativou {} ({}): {} ×{}", pokemon.getSpecies().getName(),
        Habilidades.atual(pokemon), daEnergia != null ? "Energia Auxiliar" : "pelo clima ou campo", atributo,
        "spe".equals(atributo) ? VELOCIDADE : FORTE);
    if (daEnergia == null) {
      ItensSegurados.avisar(pokemon, PROTOSSINTESE.equals(Habilidades.atual(pokemon))
          ? Component.translatable("cobblemon.battle.activate.protosynthesis", pokemon.getDisplayName(false))
          : Component.translatable(FRASE + "quark_no_campo", pokemon.getDisplayName(false)));
    }
    ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "paradoxo_forte",
        Estagios.nomeDoAtributo(atributo, pokemon), Habilidades.nome(pokemon)));
  }

  private static void acabar(Pokemon pokemon) {
    ATIVOS.remove(pokemon.getUuid());
    CobblemonCombatZA.LOGGER.info("PARADOXO: {} voltou ao normal (sem o clima ou o campo)", pokemon.getSpecies().getName());
    ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "paradoxo_acabou", pokemon.getDisplayName(false),
        Habilidades.nome(pokemon)));
  }

  // O maior atributo com os estágios de agora (no empate, o primeiro da lista, como no Showdown)
  private static String maiorAtributo(Pokemon pokemon) {
    String maior = ATRIBUTOS.get(0);
    double valorDoMaior = -1;
    for (String atributo : ATRIBUTOS) {
      double valor = semEstagio(pokemon, atributo) * Estagios.multiplicador(pokemon, atributo);
      if (valor > valorDoMaior) {
        valorDoMaior = valor;
        maior = atributo;
      }
    }
    return maior;
  }

  private static int semEstagio(Pokemon pokemon, String atributo) {
    return switch (atributo) {
      case "atk" -> pokemon.getAttack();
      case "def" -> pokemon.getDefence();
      case "spa" -> pokemon.getSpecialAttack();
      case "spd" -> pokemon.getSpecialDefence();
      default -> pokemon.getSpeed();
    };
  }
}
