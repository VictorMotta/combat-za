package dev.victor.combatza.itens;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.GerenciarCombates;

// Lotes 2 e 3 dos itens segurados: o que um item deixa guardado no Pokémon até o próximo golpe (ou até o fim da batalha).
// - Baga Micle: o próximo golpe acerta mais fácil (×1,2 na precisão), uma vez;
// - Baga Custap: o próximo golpe sai sem a fase de carregar (a "prioridade" dos jogos, no tempo real), uma vez;
// - Baga Lansat: "está focando" (a Energia Focada): +2 no estágio de crítico até a batalha acabar;
// - Metrônomo: o último golpe e quantas vezes seguidas ele saiu (cada repetição fica mais forte).
// Tudo some no fim da batalha (ItensSegurados.esquecer)
public class EstadoDosItens {
  private record Seguidos(String golpe, int vezes) {
  }

  private static final Set<UUID> MICLE = new HashSet<>();
  private static final Set<UUID> CUSTAP = new HashSet<>();
  private static final Set<UUID> FOCADOS = new HashSet<>();
  private static final Map<UUID, Seguidos> SEGUIDOS = new HashMap<>();

  private EstadoDosItens() {
  }

  public static void guardarMicle(Pokemon pokemon) {
    MICLE.add(pokemon.getUuid());
  }

  public static boolean temMicle(Pokemon pokemon) {
    return MICLE.contains(pokemon.getUuid());
  }

  // O golpe sorteou a precisão: o efeito da Micle acabou (acertando ou não)
  public static void usouMicle(Pokemon pokemon) {
    MICLE.remove(pokemon.getUuid());
  }

  public static void guardarCustap(Pokemon pokemon) {
    CUSTAP.add(pokemon.getUuid());
  }

  // O próximo golpe tem a Custap guardada? Devolve e gasta
  public static boolean usarCustap(Pokemon pokemon) {
    return CUSTAP.remove(pokemon.getUuid());
  }

  public static void focar(Pokemon pokemon) {
    FOCADOS.add(pokemon.getUuid());
  }

  public static boolean focado(Pokemon pokemon) {
    return FOCADOS.contains(pokemon.getUuid());
  }

  // O Metrônomo: o golpe começou (numa batalha). O mesmo de antes conta mais uma vez; outro golpe recomeça do 1
  public static void comecouGolpe(Pokemon pokemon, Move golpe) {
    if (!GerenciarCombates.estaEmCombate(pokemon)) {
      return;
    }
    Seguidos antes = SEGUIDOS.get(pokemon.getUuid());
    int vezes = antes != null && antes.golpe().equals(golpe.getName()) ? antes.vezes() + 1 : 1;
    SEGUIDOS.put(pokemon.getUuid(), new Seguidos(golpe.getName(), vezes));
  }

  // Quantas vezes seguidas este golpe saiu (1 = a primeira)
  public static int vezesSeguidas(Pokemon pokemon, Move golpe) {
    Seguidos seguidos = SEGUIDOS.get(pokemon.getUuid());
    return seguidos != null && seguidos.golpe().equals(golpe.getName()) ? seguidos.vezes() : 1;
  }

  public static void esquecer(Pokemon pokemon) {
    UUID uuid = pokemon.getUuid();
    MICLE.remove(uuid);
    CUSTAP.remove(uuid);
    FOCADOS.remove(uuid);
    SEGUIDOS.remove(uuid);
  }

  public static void limpar() {
    MICLE.clear();
    CUSTAP.clear();
    FOCADOS.clear();
    SEGUIDOS.clear();
  }
}
