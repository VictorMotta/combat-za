package dev.victor.combatza.habilidades;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.Armadilhas;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.ia.IaDoSelvagem;
import dev.victor.combatza.itens.ItensDeEstagio;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.pvp.Duelo;
import dev.victor.combatza.pvp.Duelos;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 21/10 (habilidades, lote da entrada e das imunidades): as habilidades de PRENDER e de FUGIR, no tempo real:
// - Armadilharia (prende quem está no chão), Fio de Sombra (prende todos, menos outro Fio de Sombra) e Magnetismo (prende o
//   tipo Aço). Nos jogos, o preso não troca nem foge. Aqui: o selvagem que prende o SEU LUTADOR segura você na batalha
//   (fugir pede 24 blocos de distância em vez de 16, e ele também só sai da batalha a 24); o seu lutador que prende o
//   selvagem arisco não deixa ele fugir com a vida baixa. Ninguém fica preso sendo do tipo Fantasma, segurando a Casca
//   Descartável ou a Bola de Fumaça, ou com Fuga (como nos jogos). Com todos os seus Pokémon na bola, ninguém está preso;
// - Fuga Automática e Amarelar: a vida passou da metade para baixo (qualquer dano) → o seu Pokémon volta para a bola e você
//   manda o próximo (como o Botão Ejetor; só se houver outro para lutar), e o selvagem sai correndo da batalha (como o
//   Cartão Vermelho; nos jogos, o selvagem com elas foge);
// - Ventosas e Cão de Guarda: ninguém tira ele da luta à força (o Cartão Vermelho de quem ele acertou é gasto e não faz
//   nada)
public class HabilidadesDeFuga {
  private static final String ARMADILHARIA = "arenatrap";
  private static final String FIO_DE_SOMBRA = "shadowtag";
  private static final String MAGNETISMO = "magnetpull";
  private static final Set<String> SAEM_NA_METADE = Set.of("emergencyexit", "wimpout");
  private static final Set<String> NAO_SAEM_A_FORCA = Set.of("suctioncups", "guarddog");
  private static final float DISTANCIA_PRESO = 24.0f;
  private static final int AVISAR_A_CADA_TICKS = 20;
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  // A vida de cada Pokémon da batalha estava acima da metade na última conferida?
  private static final Map<UUID, Boolean> ACIMA_DA_METADE = new HashMap<>();
  // O aviso de "preso" de cada jogador: o lutador preso e a batalha (uma vez por lutador e por batalha)
  private record Preso(UUID lutador, Combate combate) {
  }

  private static final Map<UUID, Preso> AVISOU_PRESO = new HashMap<>();
  // Os selvagens que já tentaram fugir presos (o aviso uma vez)
  private static final Set<UUID> SELVAGENS_AVISADOS = new HashSet<>();

  private HabilidadesDeFuga() {
  }

  // "quem" prende "preso"? Só o outro lado; o Fantasma, a Casca Descartável, a Bola de Fumaça e a Fuga sempre escapam
  private static boolean prende(Pokemon quem, Pokemon preso) {
    if (Lados.mesmoLado(quem, preso) || quem.isFainted() || preso.isFainted() || escapaSempre(preso)) {
      return false;
    }
    return switch (Habilidades.atual(quem)) {
      case ARMADILHARIA -> !Armadilhas.voa(preso);
      case FIO_DE_SOMBRA -> !Habilidades.tem(preso, FIO_DE_SOMBRA);
      case MAGNETISMO -> AplicarStatus.temTipo(preso, ElementalTypes.STEEL);
      default -> false;
    };
  }

  private static boolean escapaSempre(Pokemon pokemon) {
    return AplicarStatus.temTipo(pokemon, ElementalTypes.GHOST) || Habilidades.tem(pokemon, "runaway")
        || ItensSegurados.tem(pokemon, "shedshell") || ItensSegurados.tem(pokemon, "smokeball");
  }

  // O selvagem da batalha que prende o lutador do jogador (null = nenhum, ou o lutador está na bola)
  private static PokemonEntity quemPrende(ServerPlayer jogador, Combate combate) {
    Pokemon lutador = Lutadores.de(jogador);
    if (lutador == null) {
      return null;
    }
    for (PokemonEntity selvagem : combate.getSelvagens()) {
      if (selvagem.isAlive() && !Desmaio.esta(selvagem) && prende(selvagem.getPokemon(), lutador)) {
        return selvagem;
      }
    }
    return null;
  }

  // Combate: a distância para o jogador fugir. Preso: 24 blocos
  public static float distanciaDeFuga(ServerPlayer jogador, Combate combate, float normal) {
    return quemPrende(jogador, combate) != null ? Math.max(normal, DISTANCIA_PRESO) : normal;
  }

  // Combate: o selvagem que prende o lutador de alguém da batalha só sai dela a 24 blocos (senão ele "ficava para trás" a
  // 16, e a batalha acabava antes de você poder fugir)
  public static float distanciaDoSelvagem(PokemonEntity selvagem, Combate combate, float normal) {
    for (ServerPlayer jogador : combate.getJogadores()) {
      Pokemon lutador = Lutadores.de(jogador);
      if (lutador != null && prende(selvagem.getPokemon(), lutador)) {
        return Math.max(normal, DISTANCIA_PRESO);
      }
    }
    return normal;
  }

  // IaDoSelvagem: o arisco com a vida baixa quer fugir; o lutador de alguém da batalha que prende ele não deixa (avisa uma
  // vez). true = está preso
  public static boolean selvagemPreso(PokemonEntity selvagem, List<ServerPlayer> jogadores) {
    Pokemon pokemon = selvagem.getPokemon();
    for (ServerPlayer jogador : jogadores) {
      Pokemon lutador = Lutadores.de(jogador);
      if (lutador == null || !prende(lutador, pokemon)) {
        continue;
      }
      if (SELVAGENS_AVISADOS.add(pokemon.getUuid())) {
        CobblemonCombatZA.LOGGER.info("PRESO: {} quis fugir, mas {} de {} não deixa", pokemon.getSpecies().getName(),
            Habilidades.atual(lutador), lutador.getSpecies().getName());
        Component frase = Component.translatable(FRASE + "selvagem_preso", pokemon.getDisplayName(false), de(lutador));
        for (ServerPlayer quemVe : jogadores) {
          Feedback.historico(quemVe, pokemon, frase);
        }
      }
      return true;
    }
    return false;
  }

  // ItensDoAcerto (o Cartão Vermelho): Ventosas e Cão de Guarda seguram ele na luta (o cartão é gasto do mesmo jeito, como
  // nos jogos). true = ele fica
  public static boolean naoSaiAForca(Pokemon pokemon) {
    if (!NAO_SAEM_A_FORCA.contains(Habilidades.atual(pokemon))) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("FICOU: {} não saiu da luta ({})", pokemon.getSpecies().getName(),
        Habilidades.atual(pokemon));
    ItensSegurados.avisar(pokemon, Component.translatable(FRASE + "ancorado", pokemon.getDisplayName(false),
        Habilidades.nome(pokemon)));
    return true;
  }

  // A cada 5 ticks (HabilidadesDeEntrada): a vida de quem está na batalha (Fuga Automática e Amarelar) e, a cada segundo,
  // o aviso de quem ficou preso
  public static void atualizar(long agora) {
    Set<UUID> naLuta = new HashSet<>();
    for (Combate combate : List.copyOf(GerenciarCombates.todos())) {
      for (PokemonEntity entidade : Arenas.daBatalha(combate).pokemons()) {
        Pokemon pokemon = entidade.getPokemon();
        naLuta.add(pokemon.getUuid());
        boolean acima = Vida.pvAtual(pokemon) * 2 > pokemon.getMaxHealth();
        Boolean antes = ACIMA_DA_METADE.put(pokemon.getUuid(), acima);
        if (Boolean.TRUE.equals(antes) && !acima && entidade.isAlive() && !pokemon.isFainted()
            && SAEM_NA_METADE.contains(Habilidades.atual(pokemon))) {
          sairNaMetade(combate, entidade);
        }
      }
      if (agora % AVISAR_A_CADA_TICKS == 0) {
        avisarPresos(combate);
      }
    }
    ACIMA_DA_METADE.keySet().retainAll(naLuta);
    SELVAGENS_AVISADOS.retainAll(naLuta);
    AVISOU_PRESO.values().removeIf(preso -> !GerenciarCombates.todos().contains(preso.combate()));
  }

  public static void limpar() {
    ACIMA_DA_METADE.clear();
    AVISOU_PRESO.clear();
    SELVAGENS_AVISADOS.clear();
  }

  // Fuga Automática e Amarelar: o seu volta para a bola (se houver outro para lutar); o selvagem sai correndo
  private static void sairNaMetade(Combate combate, PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    // 21/10: o Pokémon do treinador nunca sai da luta (BatalhasDeTreinador)
    if (!pokemon.isWild() && (!ItensDeEstagio.podeVoltar(pokemon) || !temOutroParaLutar(pokemon))
        || BatalhasDeTreinador.ehDoTreinador(entidade)) {
      return;
    }
    CobblemonCombatZA.LOGGER.info("FUGA NA METADE: {} saiu da luta com {}/{} PV ({})", pokemon.getSpecies().getName(),
        Vida.pvAtual(pokemon), pokemon.getMaxHealth(), Habilidades.atual(pokemon));
    Component frase = Component.translatable(FRASE + "saiu_da_luta", pokemon.getDisplayName(false),
        Habilidades.nome(pokemon));
    for (ServerPlayer jogador : combate.getJogadores()) {
      Feedback.historico(jogador, pokemon, frase);
    }
    if (pokemon.isWild()) {
      IaDoSelvagem.fazerFugir(entidade, Habilidades.atual(pokemon));
    } else {
      ItensDeEstagio.recolher(pokemon, Habilidades.atual(pokemon));
    }
  }

  private static boolean temOutroParaLutar(Pokemon pokemon) {
    // 21/10 (PvP): no duelo, só os N escolhidos contam
    Duelo duelo = Duelos.dueloDe(pokemon.getOwnerPlayer());
    if (duelo != null) {
      return duelo.temOutroComVida(pokemon.getOwnerPlayer(), pokemon);
    }
    for (Pokemon outro : Cobblemon.INSTANCE.getStorage().getParty(pokemon.getOwnerPlayer())) {
      if (outro != pokemon && !outro.isFainted()) {
        return true;
      }
    }
    return false;
  }

  // O aviso de quem acabou de ficar preso (uma vez por lutador e por batalha): quanto precisa se afastar para fugir
  private static void avisarPresos(Combate combate) {
    for (ServerPlayer jogador : combate.getJogadores()) {
      PokemonEntity selvagem = quemPrende(jogador, combate);
      Pokemon lutador = Lutadores.de(jogador);
      if (selvagem == null || lutador == null) {
        AVISOU_PRESO.remove(jogador.getUUID());
        continue;
      }
      Preso agora = new Preso(lutador.getUuid(), combate);
      if (agora.equals(AVISOU_PRESO.put(jogador.getUUID(), agora))) {
        continue;
      }
      Pokemon quemPrende = selvagem.getPokemon();
      CobblemonCombatZA.LOGGER.info("PRESO: {} de {} prende {} (fugir: {} blocos)", Habilidades.atual(quemPrende),
          quemPrende.getSpecies().getName(), lutador.getSpecies().getName(), DISTANCIA_PRESO);
      Feedback.historico(jogador, quemPrende, Component.translatable(FRASE + "preso", de(quemPrende),
          lutador.getDisplayName(false), (int) DISTANCIA_PRESO));
    }
  }

  // "Armadilharia de Dugtrio"
  private static Component de(Pokemon pokemon) {
    return Component.translatable(FRASE + "de", Habilidades.nome(pokemon), pokemon.getDisplayName(false));
  }
}
