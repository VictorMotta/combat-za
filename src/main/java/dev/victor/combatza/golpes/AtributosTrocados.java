package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 01/11 (as pendências, "como nos jogos"): os golpes que mexem nos ATRIBUTOS de verdade (não nos estágios), até o Pokémon
// voltar para a bola, desmaiar ou a batalha acabar: Truque de Força (troca o Ataque com a Defesa dele), Troca de Poder (o
// Ataque e o Atq. Esp. com a Defesa e a Def. Esp.), Dividir Defesa e Divisão Poderosa (os dois ficam com a média), Troca de
// Velocidade (troca a Velocidade). Quem faz a conta (Dano, Recargas, a carga do golpe) multiplica pelo multiplicador daqui
public final class AtributosTrocados {
  private record Troca(Pokemon pokemon, Map<String, Integer> valores) {
  }

  private static final Map<UUID, Troca> TROCADOS = new HashMap<>();

  private AtributosTrocados() {
  }

  // O golpe no alvo (GolpesDeStatus.depoisDeAcertar)
  public static void noAlvo(Execucao execucao, Entity alvo) {
    if (!(alvo instanceof PokemonEntity atingido) || HabilidadesQueReagem.caiu(atingido)) {
      return;
    }
    Pokemon pokemon = execucao.getPokemon();
    Pokemon doAlvo = atingido.getPokemon();
    switch (execucao.getGolpe().getName()) {
      case "guardsplit" -> dividir(pokemon, doAlvo, "def", "spd", "cobblemon.battle.activate.guardsplit");
      case "powersplit" -> dividir(pokemon, doAlvo, "atk", "spa", "cobblemon.battle.activate.powersplit");
      case "speedswap" -> {
        int minha = valor(pokemon, "spe");
        int dele = valor(doAlvo, "spe");
        definir(pokemon, "spe", dele);
        definir(doAlvo, "spe", minha);
        GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.activate.speedswap",
            pokemon.getDisplayName(false)));
      }
      default -> {
        // os outros golpes não mexem nos atributos
      }
    }
  }

  // O golpe sem alvo (GolpesDeStatus.semAlvo). true = era um destes
  public static boolean semAlvo(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    switch (execucao.getGolpe().getName()) {
      case "powertrick" -> {
        trocar(pokemon, "atk", "def");
        GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.start.powertrick",
            pokemon.getDisplayName(false)));
      }
      case "powershift" -> {
        trocar(pokemon, "atk", "def");
        trocar(pokemon, "spa", "spd");
        GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.start.powershift",
            pokemon.getDisplayName(false)));
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  private static void dividir(Pokemon pokemon, Pokemon doAlvo, String um, String outro, String frase) {
    for (String atributo : new String[] { um, outro }) {
      int media = (valor(pokemon, atributo) + valor(doAlvo, atributo)) / 2;
      definir(pokemon, atributo, media);
      definir(doAlvo, atributo, media);
    }
    GolpesDeStatus.avisar(pokemon, Component.translatable(frase, pokemon.getDisplayName(false),
        doAlvo.getDisplayName(false)));
  }

  private static void trocar(Pokemon pokemon, String um, String outro) {
    int deUm = valor(pokemon, um);
    definir(pokemon, um, valor(pokemon, outro));
    definir(pokemon, outro, deUm);
  }

  // O valor que vale agora (o trocado, ou o de verdade)
  private static int valor(Pokemon pokemon, String atributo) {
    Troca troca = TROCADOS.get(pokemon.getUuid());
    Integer trocado = troca == null ? null : troca.valores().get(atributo);
    return trocado != null ? trocado : deVerdade(pokemon, atributo);
  }

  private static int deVerdade(Pokemon pokemon, String atributo) {
    return switch (atributo) {
      case "atk" -> pokemon.getAttack();
      case "def" -> pokemon.getDefence();
      case "spa" -> pokemon.getSpecialAttack();
      case "spd" -> pokemon.getSpecialDefence();
      default -> pokemon.getSpeed();
    };
  }

  private static void definir(Pokemon pokemon, String atributo, int valor) {
    TROCADOS.computeIfAbsent(pokemon.getUuid(), uuid -> new Troca(pokemon, new HashMap<>())).valores().put(atributo, valor);
  }

  // Dano, Recargas e a carga: o atributo trocado sobre o de verdade (1 = não mudou)
  public static double multiplicador(Pokemon pokemon, String atributo) {
    if (TROCADOS.isEmpty() || !TROCADOS.containsKey(pokemon.getUuid())) {
      return 1.0;
    }
    int deVerdade = deVerdade(pokemon, atributo);
    return deVerdade <= 0 ? 1.0 : valor(pokemon, atributo) / (double) deVerdade;
  }

  // Voltou para a bola, desmaiou, saiu da batalha (GolpesDeStatus)
  public static void esquecer(Pokemon pokemon) {
    TROCADOS.remove(pokemon.getUuid());
  }

  public static void semBatalha() {
    TROCADOS.values().removeIf(troca -> !GerenciarCombates.estaEmCombate(troca.pokemon()));
  }

  public static void limpar() {
    TROCADOS.clear();
  }
}
