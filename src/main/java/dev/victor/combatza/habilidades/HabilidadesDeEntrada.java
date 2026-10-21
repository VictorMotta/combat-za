package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.Barreira;
import dev.victor.combatza.campo.CampoDaBatalha;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimasFortes;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.golpes.Dano;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

// 21/10 (habilidades, lote da entrada e das imunidades): as habilidades que agem quando o Pokémon ENTRA na batalha (ela
// começou, o selvagem chegou, o lutador foi trocado: EfeitosDoCampo.quemEntrou, a cada 5 ticks, chama aoEntrar) ou SAI dela:
// - Antecipação (estremece se um inimigo tem golpe super efetivo ou de nocaute nele), Profecia (revela o golpe mais forte
//   de um inimigo) e Bisbilhotar (revela o item de cada inimigo): avisos no histórico da batalha;
// - Download: soma a Defesa e a Defesa Especial dos inimigos; Defesa menor ou igual → Ataque Especial +1, senão Ataque +1;
// - Espada Intrépida (Ataque +1) e Escudo Destemido (Defesa +1): uma vez por batalha;
// - Calda Adocicada: a Evasão dos inimigos cai 1, uma vez por batalha;
// - Limpa-Vidro: Refletir, Tela de Luz e Véu de Aurora acabam, dos dois lados;
// - Filho do Vento: entrou com o Vento de Cauda do lado dele, ou o Vento de Cauda começou do lado dele: Ataque +1 (o golpe
//   de vento que ele absorve está em HabilidadesDeProtecao);
// - Regeneração: voltou para a bola no meio da batalha depois de ter lutado: recupera 1/3 da vida;
// - Desajeitado: o item que ele segura não faz nada (ItensSegurados.id), menos os que só mexem nos EVs (Pulseira Macho...).
// E o relógio desta leva: os climas fortes (campo.ClimasFortes), os Paradoxo (Paradoxo), o Ruminante (HabilidadesDeBaga) e
// a fuga (HabilidadesDeFuga). A Rompe-Auras ficou com quem faz a Aura Negra e a Aura Feérica (a conta do dano)
public class HabilidadesDeEntrada {
  private static final int A_CADA_TICKS = 5;
  private static final Set<String> NOCAUTE = Set.of("fissure", "guillotine", "horndrill", "sheercold");
  private static final Set<String> CONTRA_ATAQUES = Set.of("counter", "metalburst", "mirrorcoat");
  private static final double PODER_DO_NOCAUTE = 150;
  private static final double PODER_DO_CONTRA_ATAQUE = 120;
  private static final double PODER_SEM_NUMERO = 80;
  private static final String REGENERACAO = "regenerator";
  private static final int TERCO = 3;
  private static final String FILHO_DO_VENTO = "windrider";
  private static final String DESAJEITADO = "klutz";
  // Os itens que valem mesmo com o Desajeitado (Showdown, ignoreKlutz)
  private static final Set<String> IGNORAM_O_DESAJEITADO = Set.of("machobrace", "poweranklet", "powerband", "powerbelt",
      "powerbracer", "powerlens", "powerweight", "abilityshield");
  // Uma vez por batalha: o atributo que cada uma sobe
  private static final Map<String, String> UMA_VEZ = Map.of("intrepidsword", "atk", "dauntlessshield", "def");
  private static final String CALDA_ADOCICADA = "supersweetsyrup";
  private static final List<Barreira> TELAS = List.of(Barreira.REFLETIR, Barreira.TELA_DE_LUZ, Barreira.VEU_DE_AURORA);
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  // Quem já usou a habilidade de uma vez (Espada Intrépida, Escudo Destemido, Calda Adocicada) e em que batalha
  private static final Map<UUID, Combate> USOU_NA_BATALHA = new HashMap<>();
  // Os Pokémon dos jogadores que lutaram (foram o lutador) e em que batalha: a Regeneração
  private static final Map<UUID, Combate> LUTARAM = new HashMap<>();
  // Filho do Vento: o lado dele tinha o Vento de Cauda na última conferida?
  private static final Map<UUID, Boolean> VENTO_DO_LADO = new HashMap<>();

  private HabilidadesDeEntrada() {
  }

  // EfeitosDoCampo.quemEntrou: acabou de chegar na arena. Os climas fortes valem em qualquer arena; o resto, só na batalha
  public static void aoEntrar(Arena arena, PokemonEntity entidade) {
    ClimasFortes.aoEntrar(arena, entidade);
    Pokemon pokemon = entidade.getPokemon();
    Combate combate = GerenciarCombates.combateDe(pokemon);
    if (!arena.naBatalha() || combate == null || !entidade.isAlive() || pokemon.isFainted()) {
      return;
    }
    if (!pokemon.isWild()) {
      LUTARAM.put(pokemon.getUuid(), combate);
    }
    List<Pokemon> inimigos = inimigos(arena, pokemon);
    String habilidade = Habilidades.atual(pokemon);
    switch (habilidade) {
      case "anticipation" -> antecipacao(pokemon, inimigos);
      case "forewarn" -> profecia(pokemon, inimigos, entidade.getRandom());
      case "frisk" -> bisbilhotar(pokemon, inimigos);
      case "download" -> download(pokemon, inimigos);
      case "intrepidsword", "dauntlessshield" -> {
        if (primeiraVez(pokemon, combate)) {
          HabilidadesDeEstagio.mudarProprio(pokemon, Map.of(UMA_VEZ.get(habilidade), 1));
        }
      }
      case CALDA_ADOCICADA -> caldaAdocicada(pokemon, combate, inimigos);
      case "screencleaner" -> limparTelas(arena, pokemon);
      case FILHO_DO_VENTO -> {
        boolean temVento = ventoDoLado(arena.campo(), pokemon);
        VENTO_DO_LADO.put(pokemon.getUuid(), temVento);
        if (temVento) {
          HabilidadesDeEstagio.mudarProprio(pokemon, Map.of("atk", 1));
        }
      }
      default -> {
        // sem habilidade de entrada desta leva
      }
    }
  }

  // CobblemonCombatZA.aoRecolher: voltou para a bola. A Regeneração cura 1/3 de quem lutou nesta batalha (lutar de novo
  // para curar de novo: o guarda que só está fora não cura)
  public static void aoRecolher(Pokemon pokemon) {
    Paradoxo.esquecer(pokemon);
    HabilidadesDeBaga.esquecer(pokemon);
    VENTO_DO_LADO.remove(pokemon.getUuid());
    Combate lutou = LUTARAM.remove(pokemon.getUuid());
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (lutou == null || dono == null || pokemon.isFainted() || !Habilidades.tem(pokemon, REGENERACAO)
        || !GerenciarCombates.todos().contains(lutou)) {
      return;
    }
    int antes = pokemon.getCurrentHealth();
    int depois = Math.min(pokemon.getMaxHealth(), antes + pokemon.getMaxHealth() / TERCO);
    if (depois <= antes) {
      return;
    }
    pokemon.setCurrentHealth(depois);
    CobblemonCombatZA.LOGGER.info("REGENERAÇÃO: {} voltou para a bola e recuperou {} PV ({}/{})",
        pokemon.getSpecies().getName(), depois - antes, depois, pokemon.getMaxHealth());
    Feedback.historicoDoLutador(dono, pokemon, Component.translatable(FRASE + "regenerou", pokemon.getDisplayName(false),
        Habilidades.nome(pokemon)).append(Component.translatable("cobblemon_combat_za.pv.ganhou", depois - antes)));
  }

  // ItensSegurados.id: com o Desajeitado, o item não faz nada
  public static boolean desajeitado(Pokemon pokemon, String item) {
    return Habilidades.tem(pokemon, DESAJEITADO) && !IGNORAM_O_DESAJEITADO.contains(item);
  }

  // A cada tick (CobblemonCombatZA): o Ruminante; a cada 5 ticks, em cada arena (as batalhas e o campo pessoal de quem está
  // fora delas, como o EfeitosDoCampo), os climas fortes, os Paradoxo, o Vento de Cauda do Filho do Vento e a fuga
  public static void atualizar(MinecraftServer servidor, long agora) {
    HabilidadesDeBaga.atualizar(agora);
    if (agora % A_CADA_TICKS != 0) {
      return;
    }
    List<Arena> arenas = new ArrayList<>();
    for (Combate combate : List.copyOf(GerenciarCombates.todos())) {
      arenas.add(Arenas.daBatalha(combate));
    }
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      if (!GerenciarCombates.estaEmCombate(jogador)) {
        arenas.add(Arenas.doJogador(jogador));
      }
    }
    Set<UUID> comVento = new HashSet<>();
    for (Arena arena : arenas) {
      ClimasFortes.atualizar(arena);
      Paradoxo.atualizar(arena);
      if (arena.naBatalha()) {
        filhoDoVento(arena, comVento);
      }
    }
    VENTO_DO_LADO.keySet().retainAll(comVento);
    Paradoxo.varrer();
    HabilidadesDeFuga.atualizar(agora);
    USOU_NA_BATALHA.values().removeIf(combate -> !GerenciarCombates.todos().contains(combate));
    LUTARAM.values().removeIf(combate -> !GerenciarCombates.todos().contains(combate));
  }

  public static void limpar() {
    USOU_NA_BATALHA.clear();
    LUTARAM.clear();
    VENTO_DO_LADO.clear();
    ClimasFortes.limpar();
    Paradoxo.limpar();
    HabilidadesDeBaga.limpar();
    HabilidadesDeFuga.limpar();
    HabilidadesDeProtecao.limpar();
  }

  // Os inimigos na arena: para o seu lutador, os selvagens; para o selvagem, o lutador de cada jogador
  private static List<Pokemon> inimigos(Arena arena, Pokemon pokemon) {
    List<Pokemon> inimigos = new ArrayList<>();
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon outro = entidade.getPokemon();
      if (!Lados.mesmoLado(outro, pokemon) && entidade.isAlive() && !outro.isFainted()) {
        inimigos.add(outro);
      }
    }
    return inimigos;
  }

  private static boolean primeiraVez(Pokemon pokemon, Combate combate) {
    return USOU_NA_BATALHA.put(pokemon.getUuid(), combate) != combate;
  }

  // Um inimigo tem golpe de dano super efetivo nele, ou de nocaute (Fissura, Guilhotina...)
  private static void antecipacao(Pokemon pokemon, List<Pokemon> inimigos) {
    for (Pokemon inimigo : inimigos) {
      for (Move golpe : inimigo.getMoveSet().getMoves()) {
        if (golpe.getDamageCategory() != DamageCategories.INSTANCE.getSTATUS() && (NOCAUTE.contains(golpe.getName())
            || Dano.efetividadeContra(inimigo, golpe, pokemon.getEntity()) > 1)) {
          CobblemonCombatZA.LOGGER.info("ANTECIPAÇÃO: {} estremeceu ({} de {})", pokemon.getSpecies().getName(),
              golpe.getName(), inimigo.getSpecies().getName());
          ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.ability.anticipation",
              pokemon.getDisplayName(false)));
          return;
        }
      }
    }
  }

  // O golpe de maior poder dos inimigos (nocaute = 150; Contra-Ataque, Explosão Metálica e Casaco Espelhado = 120; os de
  // dano sem número = 80); no empate, sorteia
  private static void profecia(Pokemon pokemon, List<Pokemon> inimigos, RandomSource sorte) {
    List<Move> golpes = new ArrayList<>();
    List<Pokemon> donos = new ArrayList<>();
    double maior = 1;
    for (Pokemon inimigo : inimigos) {
      for (Move golpe : inimigo.getMoveSet().getMoves()) {
        double poder = poderNaProfecia(golpe);
        if (poder > maior) {
          golpes.clear();
          donos.clear();
          maior = poder;
        }
        if (poder == maior) {
          golpes.add(golpe);
          donos.add(inimigo);
        }
      }
    }
    if (golpes.isEmpty()) {
      return;
    }
    int qual = sorte.nextInt(golpes.size());
    CobblemonCombatZA.LOGGER.info("PROFECIA: {} viu {} de {}", pokemon.getSpecies().getName(), golpes.get(qual).getName(),
        donos.get(qual).getSpecies().getName());
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.activate.forewarn",
        pokemon.getDisplayName(false), donos.get(qual).getDisplayName(false), golpes.get(qual).getDisplayName())
        .append(" (").append(Habilidades.nome(pokemon)).append(")"));
  }

  private static double poderNaProfecia(Move golpe) {
    if (NOCAUTE.contains(golpe.getName())) {
      return PODER_DO_NOCAUTE;
    }
    if (CONTRA_ATAQUES.contains(golpe.getName())) {
      return PODER_DO_CONTRA_ATAQUE;
    }
    boolean status = golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
    return golpe.getPower() == 1 || golpe.getPower() <= 0 && !status ? PODER_SEM_NUMERO : golpe.getPower();
  }

  // O item de cada inimigo que segura algum
  private static void bisbilhotar(Pokemon pokemon, List<Pokemon> inimigos) {
    for (Pokemon inimigo : inimigos) {
      if (inimigo.heldItem().isEmpty()) {
        continue;
      }
      CobblemonCombatZA.LOGGER.info("BISBILHOTAR: {} achou o item de {}", pokemon.getSpecies().getName(),
          inimigo.getSpecies().getName());
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.item.frisk", inimigo.getDisplayName(false),
          ItensSegurados.nome(inimigo), pokemon.getDisplayName(false)));
    }
  }

  // A Defesa e a Defesa Especial dos inimigos, com os estágios: ataca pelo lado mais fraco
  private static void download(Pokemon pokemon, List<Pokemon> inimigos) {
    double defesa = 0;
    double defesaEspecial = 0;
    for (Pokemon inimigo : inimigos) {
      defesa += inimigo.getDefence() * Estagios.multiplicador(inimigo, "def");
      defesaEspecial += inimigo.getSpecialDefence() * Estagios.multiplicador(inimigo, "spd");
    }
    if (defesa > 0 && defesa >= defesaEspecial) {
      HabilidadesDeEstagio.mudarProprio(pokemon, Map.of("spa", 1));
    } else if (defesaEspecial > 0) {
      HabilidadesDeEstagio.mudarProprio(pokemon, Map.of("atk", 1));
    }
  }

  private static void caldaAdocicada(Pokemon pokemon, Combate combate, List<Pokemon> inimigos) {
    if (inimigos.isEmpty() || !primeiraVez(pokemon, combate)) {
      return;
    }
    CobblemonCombatZA.LOGGER.info("CALDA ADOCICADA: {} baixa a Evasão de {} inimigo(s)", pokemon.getSpecies().getName(),
        inimigos.size());
    ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "calda", pokemon.getDisplayName(false),
        Habilidades.nome(pokemon)));
    for (Pokemon inimigo : inimigos) {
      HabilidadesDeEstagio.baixar(pokemon, inimigo, Map.of("evasion", -1), false);
    }
  }

  // Limpa-Vidro: as telas dos dois lados acabam
  private static void limparTelas(Arena arena, Pokemon pokemon) {
    boolean limpou = false;
    for (boolean lado : new boolean[] { true, false }) {
      Map<Barreira, CampoDaBatalha.Prazo> barreiras = arena.campo().barreiras(lado);
      for (Barreira tela : TELAS) {
        if (barreiras.remove(tela) == null) {
          continue;
        }
        if (!limpou) {
          arena.avisar(pokemon, Component.translatable(FRASE + "limpou_telas", pokemon.getDisplayName(false),
              Habilidades.nome(pokemon)));
          limpou = true;
        }
        CobblemonCombatZA.LOGGER.info("LIMPA-VIDRO: {} acabou com {}", pokemon.getSpecies().getName(), tela);
        arena.avisar(null, tela.acabou(lado));
      }
    }
  }

  // O Vento de Cauda começou do lado de quem tem o Filho do Vento: Ataque +1 (Showdown, onAllySideConditionStart)
  private static void filhoDoVento(Arena arena, Set<UUID> comVento) {
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon pokemon = entidade.getPokemon();
      if (!entidade.isAlive() || pokemon.isFainted() || !Habilidades.tem(pokemon, FILHO_DO_VENTO)) {
        continue;
      }
      comVento.add(pokemon.getUuid());
      boolean temVento = ventoDoLado(arena.campo(), pokemon);
      Boolean antes = VENTO_DO_LADO.put(pokemon.getUuid(), temVento);
      if (Boolean.FALSE.equals(antes) && temVento) {
        CobblemonCombatZA.LOGGER.info("FILHO DO VENTO: o Vento de Cauda começou do lado de {}",
            pokemon.getSpecies().getName());
        HabilidadesDeEstagio.mudarProprio(pokemon, Map.of("atk", 1));
      }
    }
  }

  // O seu lado (o dos jogadores; no duelo, o de cada jogador) ou o dos selvagens
  private static boolean ventoDoLado(CampoDaBatalha campo, Pokemon pokemon) {
    return campo.barreiras(CampoEBarreiras.doSeuLado(pokemon)).containsKey(Barreira.VENTO_DE_CAUDA);
  }
}
