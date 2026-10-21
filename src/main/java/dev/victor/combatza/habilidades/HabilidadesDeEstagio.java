package dev.victor.combatza.habilidades;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.golpes.AplicarEfeitos;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.itens.ItensDeEstagio;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// 21/10 (habilidades, lote da entrada e das imunidades): as habilidades que mexem nas MUDANÇAS DE ATRIBUTO (Showdown,
// onChangeBoost, onTryBoost e onAfterEachBoost):
// - Contrário: toda mudança vira o contrário (a Dança das Espadas baixa o Ataque; o Rosnado de um inimigo sobe);
// - Simplificar: toda mudança vale o dobro.
//   As duas moram em Estagios.mudar, por onde passa toda mudança (golpes, itens, habilidades e a Mochila);
// - Armadura Espelhada: a queda que vem de outro Pokémon volta para ele (menos com o atributo já no mínimo, −6);
// - Competitivo e Desafiador: cada atributo que um INIMIGO baixou de verdade dá +2 no Ataque Especial (Competitivo) ou no
//   Ataque (Desafiador). O Cócegas, que baixa dois, dá +4; a Intimidação e a Teia Pegajosa também acordam os dois;
// - Cão de Guarda: a Intimidação SOBE o Ataque dele em vez de baixar.
// E as duas contas que as habilidades desta leva usam: mudarProprio (uma habilidade mexe no próprio Pokémon: Espada
// Intrépida, Download, Desafiador...) e baixar (a habilidade de outro baixa: a Calda Adocicada e o reflexo da Armadura
// Espelhada), com as mesmas proteções da Intimidação (Corpo Claro, Amuleto Purificador, Névoa)
public class HabilidadesDeEstagio {
  private static final String CONTRARIO = "contrary";
  private static final String SIMPLIFICAR = "simple";
  private static final String ARMADURA_ESPELHADA = "mirrorarmor";
  private static final String CAO_DE_GUARDA = "guarddog";
  // O atributo que cada uma sobe quando um inimigo baixa algum
  private static final Map<String, String> DESAFIAM = Map.of("competitive", "spa", "defiant", "atk");
  private static final int DESAFIO = 2;
  // Fora da batalha, a mudança que veio de uma habilidade dura 30 s, como a de um item (na batalha, até ela acabar)
  private static final long SEGUNDOS = 30;
  private static final List<String> ORDEM = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  private HabilidadesDeEstagio() {
  }

  // Estagios.mudar: quanto a mudança pedida vale de verdade para este Pokémon (Contrário inverte, Simplificar dobra; o golpe
  // de quem tem Quebra-Molde ignora as duas, QuebraMolde)
  public static int ajustar(Pokemon pokemon, int quanto) {
    String habilidade = QuebraMolde.queVale(pokemon);
    if (CONTRARIO.equals(habilidade)) {
      return -quanto;
    }
    return SIMPLIFICAR.equals(habilidade) ? quanto * 2 : quanto;
  }

  // AplicarEfeitos e as quedas das habilidades: a queda vinda de "quemBaixou" bate na Armadura Espelhada da vítima e volta
  // para ele. true = não cai na vítima (mesmo que quem baixou já tenha saído da luta, como no Showdown). No mínimo (−6), a
  // armadura não age e a queda segue o caminho de sempre ("não pode cair mais")
  public static boolean espelhou(Pokemon quemBaixou, Pokemon vitima, String atributo, int quanto) {
    if (quanto >= 0 || quemBaixou == null || quemBaixou == vitima || !Habilidades.tem(vitima, ARMADURA_ESPELHADA)
        || Estagios.de(vitima, atributo) <= Estagios.MINIMO) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("ARMADURA ESPELHADA: {} devolveu a queda de {} para {}", vitima.getSpecies().getName(),
        atributo, quemBaixou.getSpecies().getName());
    ItensSegurados.avisar(vitima, Component.translatable(FRASE + "espelhou", vitima.getDisplayName(false),
        quemBaixou.getDisplayName(false), Habilidades.nome(vitima)));
    PokemonEntity entidade = quemBaixou.getEntity();
    if (entidade != null && entidade.isAlive() && !quemBaixou.isFainted()) {
      baixar(vitima, quemBaixou, Map.of(atributo, quanto), true);
    }
    return true;
  }

  // A Intimidação de "quem" em "vitima": o Cão de Guarda sobe o Ataque em vez de baixar; a Armadura Espelhada devolve a
  // queda para quem intimidou. true = a Intimidação já foi resolvida aqui
  public static boolean reagiuAIntimidacao(Pokemon quem, Pokemon vitima) {
    if (Habilidades.tem(vitima, CAO_DE_GUARDA)) {
      CobblemonCombatZA.LOGGER.info("CÃO DE GUARDA: {} foi intimidado por {} e o Ataque subiu",
          vitima.getSpecies().getName(), quem.getSpecies().getName());
      mudarProprio(vitima, Map.of("atk", 1));
      return true;
    }
    return espelhou(quem, vitima, "atk", -1);
  }

  // A habilidade de "quem" baixa os atributos de "vitima" (a Calda Adocicada; "doEspelho": o reflexo da Armadura Espelhada,
  // que não volta de novo). Passa pelas proteções de sempre, muda, avisa, deixa os itens reagirem e, vinda de um inimigo,
  // acorda o Competitivo e o Desafiador
  public static void baixar(Pokemon quem, Pokemon vitima, Map<String, Integer> quedas, boolean doEspelho) {
    baixar(quem, vitima, quedas, doEspelho, null);
  }

  // O mesmo, com a habilidade de onde veio no fim da frase ("(Pegajoso de Goodra)"; null = sem)
  public static void baixar(Pokemon quem, Pokemon vitima, Map<String, Integer> quedas, boolean doEspelho,
      Component deOnde) {
    if (vitima.isFainted() || vitima.getEntity() == null) {
      return;
    }
    long ate = ateQuando(vitima);
    Map<String, Integer> mudaram = new LinkedHashMap<>();
    boolean amuletoAvisou = false;
    for (String atributo : ORDEM) {
      Integer quanto = quedas.get(atributo);
      if (quanto == null) {
        continue;
      }
      if (Habilidades.naoDeixaCair(vitima, atributo)) {
        ItensSegurados.avisar(vitima, Component.translatable(Estagios.FRASE + "nao_caiu",
            Estagios.nomeDoAtributo(atributo, vitima), Habilidades.nome(vitima)));
        continue;
      }
      if (!doEspelho && espelhou(quem, vitima, atributo, quanto)) {
        continue;
      }
      // (a frase do Amuleto Purificador uma vez só, mesmo segurando dois atributos)
      if (ItensSegurados.tem(vitima, ItensSegurados.AMULETO_PURIFICADOR)) {
        amuletoAvisou = amuletoAvisou || ItensDeEstagio.amuletoSegura(vitima);
        continue;
      }
      if (CampoEBarreiras.bloqueiaQueda(vitima)) {
        ItensSegurados.avisar(vitima, Component.translatable("cobblemon.battle.activate.mist",
            vitima.getDisplayName(false)));
        continue;
      }
      int mudou = Estagios.mudar(vitima, atributo, quanto, ate);
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} baixou {} de {} (agora {})", quem.getSpecies().getName(), atributo,
          vitima.getSpecies().getName(), Estagios.de(vitima, atributo));
      MutableComponent frase = Component.translatable(Estagios.FRASE + AplicarEfeitos.comoMudou(quanto, mudou),
          Estagios.nomeDoAtributo(atributo, vitima));
      ItensSegurados.avisar(vitima, deOnde == null ? frase : frase.append(" (").append(deOnde).append(")"));
      if (mudou != 0) {
        mudaram.put(atributo, mudou);
      }
    }
    if (!mudaram.isEmpty()) {
      ItensDeEstagio.mudaram(vitima, mudaram);
    }
    if (!Lados.mesmoLado(quem, vitima)) {
      desafiar(vitima, mudaram);
    }
  }

  // Competitivo e Desafiador: "mudaram" é o que um inimigo mudou de verdade; cada atributo que caiu dá +2
  public static void desafiar(Pokemon pokemon, Map<String, Integer> mudaram) {
    String atributo = DESAFIAM.get(Habilidades.atual(pokemon));
    if (atributo == null || pokemon.isFainted() || pokemon.getEntity() == null) {
      return;
    }
    long quedas = mudaram.values().stream().filter(mudou -> mudou < 0).count();
    for (int vez = 0; vez < quedas; vez++) {
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} se irritou com a queda ({})", pokemon.getSpecies().getName(),
          Habilidades.atual(pokemon));
      mudarProprio(pokemon, Map.of(atributo, DESAFIO));
    }
  }

  // Uma habilidade mexe nos atributos do próprio Pokémon: muda, avisa com o nome da habilidade ("O Ataque de Zacian subiu!
  // (Espada Intrépida)") e deixa os itens reagirem (Erva Branca, Erva Imitadora...). Devolve quanto cada um mudou
  public static Map<String, Integer> mudarProprio(Pokemon pokemon, Map<String, Integer> pedidos) {
    long ate = ateQuando(pokemon);
    Map<String, Integer> mudaram = new LinkedHashMap<>();
    for (String atributo : ORDEM) {
      Integer quanto = pedidos.get(atributo);
      if (quanto == null) {
        continue;
      }
      int mudou = Estagios.mudar(pokemon, atributo, quanto, ate);
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} {} {} ({}; agora {})", pokemon.getSpecies().getName(), atributo,
          quanto > 0 ? "+" + quanto : quanto, Habilidades.atual(pokemon), Estagios.de(pokemon, atributo));
      ItensSegurados.avisar(pokemon, Component.translatable(Estagios.FRASE + AplicarEfeitos.comoMudou(quanto, mudou),
          Estagios.nomeDoAtributo(atributo, pokemon)).append(" (").append(Habilidades.nome(pokemon)).append(")"));
      if (mudou != 0) {
        mudaram.put(atributo, mudou);
      }
    }
    if (!mudaram.isEmpty()) {
      ItensDeEstagio.mudaram(pokemon, mudaram);
    }
    return mudaram;
  }

  private static long ateQuando(Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    long agora = entidade != null && entidade.getServer() != null ? entidade.getServer().getTickCount() : 0;
    return agora + SEGUNDOS * SharedConstants.TICKS_PER_SECOND;
  }
}
