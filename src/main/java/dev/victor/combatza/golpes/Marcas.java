package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.GerenciarCombates;

// 01/11 (as pendências, "como nos jogos"): as MARCAS que os golpes de status deixam num Pokémon (os "volatile status" do
// Showdown: Inabilitar, Bocejo, Semente Sanguessuga, Focalizar Energia, Levitação Magnética...). Cada marca tem um fim (um
// tick, ou "até sair"), um número (a Estocagem conta de 1 a 3) e quem a deixou (a Semente cura quem plantou; o Olhar Malvado
// só prende enquanto quem olhou estiver na luta). Como nos jogos, todas acabam quando o Pokémon volta para a bola, desmaia
// ou sai da batalha (GolpesDeStatus). As marcas "de turno" agem a cada 3,5 s (GolpesQueMarcam.turno)
public final class Marcas {
  public static final long ATE_SAIR = Long.MAX_VALUE;

  public enum Marca {
    INABILITADO, ATORMENTADO, SEM_CURA, EMBARGO, APRISIONOU, BOCEJO, PESADELO, SEMENTE, ARCO_AQUA, ENRAIZADO, FOCO, AGUCADO,
    MIRA_TRAVADA, ESTOCAGEM, MALDICAO, LACO_DO_DESTINO, RANCOR, CARGA, MINIMIZADO, ENROLADO, PICHE, LEVITANDO, TELECINESE,
    IDENTIFICADO, OLHO_MILAGROSO, CAPA_MAGICA, ARREBATADOR, POLEIRO, ELETRIFICADO, POLVORA, MAOZINHA, NAO_FOGE, CHAVE_DE_POLVO,
    AUTOTOMIA, ANIMO_DO_DRAGAO
  }

  public static final class Dado {
    private final Pokemon pokemon;
    private final long ate;
    private int valor;
    private final Pokemon deQuem;
    private final String golpe;
    private long proximoTurno;

    private Dado(Pokemon pokemon, long ate, int valor, Pokemon deQuem, String golpe, long proximoTurno) {
      this.pokemon = pokemon;
      this.ate = ate;
      this.valor = valor;
      this.deQuem = deQuem;
      this.golpe = golpe;
      this.proximoTurno = proximoTurno;
    }

    public Pokemon pokemon() {
      return pokemon;
    }

    public int valor() {
      return valor;
    }

    public void mudarValor(int novo) {
      valor = novo;
    }

    public Pokemon deQuem() {
      return deQuem;
    }

    public String golpe() {
      return golpe;
    }
  }

  private static final Map<UUID, EnumMap<Marca, Dado>> MARCAS = new HashMap<>();

  private Marcas() {
  }

  // "ticks": quanto dura (ATE_SAIR = até ele sair da luta); o primeiro turno vem 3,5 s depois
  public static Dado marcar(Pokemon pokemon, Marca marca, long agora, long ticks, int valor, Pokemon deQuem, String golpe) {
    long ate = ticks == ATE_SAIR ? ATE_SAIR : agora + ticks;
    Dado dado = new Dado(pokemon, ate, valor, deQuem, golpe, agora + EfeitosDeStatus.ticksPorTurno());
    MARCAS.computeIfAbsent(pokemon.getUuid(), uuid -> new EnumMap<>(Marca.class)).put(marca, dado);
    return dado;
  }

  public static Dado dado(Pokemon pokemon, Marca marca) {
    if (pokemon == null || MARCAS.isEmpty()) {
      return null;
    }
    EnumMap<Marca, Dado> doPokemon = MARCAS.get(pokemon.getUuid());
    return doPokemon == null ? null : doPokemon.get(marca);
  }

  public static boolean tem(Pokemon pokemon, Marca marca) {
    return dado(pokemon, marca) != null;
  }

  public static int valor(Pokemon pokemon, Marca marca) {
    Dado dado = dado(pokemon, marca);
    return dado == null ? 0 : dado.valor;
  }

  public static boolean tirar(Pokemon pokemon, Marca marca) {
    EnumMap<Marca, Dado> doPokemon = MARCAS.get(pokemon.getUuid());
    if (doPokemon == null || doPokemon.remove(marca) == null) {
      return false;
    }
    if (doPokemon.isEmpty()) {
      MARCAS.remove(pokemon.getUuid());
    }
    return true;
  }

  // Todas as marcas de um tipo (a do Aprisionar, a do Olhar Malvado: quem prende quem)
  public static List<Dado> todas(Marca marca) {
    List<Dado> todas = new ArrayList<>();
    for (EnumMap<Marca, Dado> doPokemon : MARCAS.values()) {
      Dado dado = doPokemon.get(marca);
      if (dado != null) {
        todas.add(dado);
      }
    }
    return todas;
  }

  // A cada tick (GolpesDeStatus): as que acabaram saem (com a frase do fim), e as de turno agem
  public static void atualizar(long agora) {
    if (MARCAS.isEmpty()) {
      return;
    }
    for (EnumMap<Marca, Dado> doPokemon : List.copyOf(MARCAS.values())) {
      for (Map.Entry<Marca, Dado> marca : List.copyOf(doPokemon.entrySet())) {
        Dado dado = marca.getValue();
        if (agora >= dado.ate) {
          tirar(dado.pokemon, marca.getKey());
          GolpesQueMarcam.acabou(dado.pokemon, marca.getKey());
        } else if (agora >= dado.proximoTurno) {
          dado.proximoTurno += EfeitosDeStatus.ticksPorTurno();
          GolpesQueMarcam.turno(dado.pokemon, marca.getKey(), dado, agora);
        }
      }
    }
  }

  // Quem saiu da batalha perde as marcas (a cada segundo, GolpesDeStatus)
  public static void semBatalha() {
    MARCAS.values().removeIf(doPokemon -> doPokemon.isEmpty()
        || !GerenciarCombates.estaEmCombate(doPokemon.values().iterator().next().pokemon));
  }

  public static void esquecer(Pokemon pokemon) {
    MARCAS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    MARCAS.clear();
  }
}
