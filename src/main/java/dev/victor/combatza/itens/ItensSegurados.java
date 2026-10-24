package dev.victor.combatza.itens;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.item.components.HeldItemEffectComponent;
import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager;

import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.habilidades.HabilidadesDeBaga;
import dev.victor.combatza.habilidades.HabilidadesDeEntrada;
import dev.victor.combatza.habilidades.HabilidadesDosItens;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.pvp.Duelos;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 9.1 (parte 4 da sequência de 20/10: "os itens segurados, em lotes"; decidido por ele em 19/10: "etapa própria, em lotes,
// começando pelos mais usados"). No Cobblemon, o efeito de um item segurado roda DENTRO da batalha por turnos (o Showdown); a
// nossa luta não passa por lá, então nenhum funcionava. Aqui, como as habilidades da 4H: cada item pelo id do Showdown ("lifeorb",
// "leftovers"...), que o próprio Cobblemon traduz do item (CobblemonHeldItemManager.showdownId, javap: também respeita os itens
// de outros mods). O lote 1 está em ItensDeDano (os números), ItensDeVida (frutas, Sobras, Lodo, Orbe), ItensDeContato (Elmo)
// e TravaDaEscolha. Os lotes 2 e 3 (dele, 20/10: "preciso que termine os lotes 2 e 3, como uma pessoa vai jogar o Cobblemon
// sem usar os itens?"), por quando agem: Bagas, ItensDoAcerto, ItensDeEstagio, ItensDeCarga, ItensDoTurno e ItensDoCampo (e
// o que fica guardado no Pokémon, EstadoDosItens). Vale para o seu Pokémon e para o selvagem (o Cobblemon faz o selvagem
// nascer com item)
public class ItensSegurados {
  public static final String SOBRAS = "leftovers";
  public static final String LODO_ESCURO = "blacksludge";
  public static final String ORAN = "oranberry";
  public static final String SITRUS = "sitrusberry";
  public static final String SUCO_DE_BAGA = "berryjuice";
  public static final String LUM = "lumberry";
  public static final String CHERI = "cheriberry";
  public static final String CHESTO = "chestoberry";
  public static final String PECHA = "pechaberry";
  public static final String RAWST = "rawstberry";
  public static final String ASPEAR = "aspearberry";
  public static final String PERSIM = "persimberry";
  public static final String ORBE_DA_VIDA = "lifeorb";
  public static final String FAIXA_DA_ESCOLHA = "choiceband";
  public static final String LENTES_DA_ESCOLHA = "choicespecs";
  public static final String LENCO_DA_ESCOLHA = "choicescarf";
  public static final String FAIXA_MUSCULOSA = "muscleband";
  public static final String OCULOS_DE_SABEDORIA = "wiseglasses";
  public static final String FAIXA_PROFISSIONAL = "expertbelt";
  public static final String CINTA_DE_FOCO = "focussash";
  public static final String COLETE_DE_ATAQUE = "assaultvest";
  public static final String EVIOLITA = "eviolite";
  public static final String ELMO_ROCHOSO = "rockyhelmet";
  // Lotes 2 e 3: os que mais de uma classe consulta
  public static final String BOLA_DE_FERRO = "ironball";
  public static final String BALAO_DE_AR = "airballoon";
  public static final String ALVO_ANELADO = "ringtarget";
  public static final String PROTETORES = "protectivepads";
  public static final String LUVA_DE_SOCO = "punchingglove";
  public static final String CAPA_FURTIVA = "covertcloak";
  public static final String AMULETO_PURIFICADOR = "clearamulet";
  public static final String OCULOS_DE_PROTECAO = "safetygoggles";
  public static final String GUARDA_CHUVA = "utilityumbrella";
  public static final String BOTAS_GROSSAS = "heavydutyboots";
  private static final String NENHUM = "";

  // Os itens que valem uma vez por batalha e ficam com o Pokémon (a Cinta de Foco), por Pokémon
  private static final Map<UUID, Set<String>> USADOS = new HashMap<>();

  private ItensSegurados() {
  }

  // O id do Showdown do item que ele segura ("" sem item, ou com um item sem efeito de batalha)
  public static String id(Pokemon pokemon) {
    String id = CobblemonHeldItemManager.INSTANCE.showdownId(pokemon.heldItem());
    // 21/10 (habilidades, lote da entrada e das imunidades): com o Desajeitado, o item não faz nada na batalha
    return id == null || HabilidadesDeEntrada.desajeitado(pokemon, id) ? NENHUM : id;
  }

  // O mesmo id sem a lista do Showdown, que só existe no servidor (no seu jogo conectado a um servidor ela vem vazia): o efeito
  // marcado no item, senão o nome dele sem o "_" (javap no BaseCobblemonHeldItemManager.load: o Cobblemon faz assim)
  public static String idNoCliente(ItemStack item) {
    HeldItemEffectComponent efeito = item.get(CobblemonItemComponents.HELD_ITEM_EFFECT);
    if (efeito != null) {
      return efeito.getShowdownId();
    }
    return item.isEmpty() ? NENHUM : BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().replace("_", "");
  }

  public static boolean tem(Pokemon pokemon, String id) {
    return id.equals(id(pokemon));
  }

  // O nome do item, como aparece no jogo (já traduzido)
  public static Component nome(Pokemon pokemon) {
    return pokemon.heldItem().getHoverName();
  }

  // Usou o item. Como o Cobblemon faz numa batalha contra selvagem (javap no shouldConsumeItem): as frutas e as ervas (a etiqueta
  // "consumed_in_wild_battle") somem; os outros (a Cinta de Foco) ficam com ele e só valem de novo na próxima batalha
  public static void gastar(Pokemon pokemon, String id) {
    // 21/10: comeu uma baga (Bochechões e Ruminante; a baga ainda está na mão, para o nome)
    HabilidadesDeBaga.comeu(pokemon, id);
    // 21/10 (habilidades, lote das reações): a Colheita, o Pegar e o Alígero anotam o item gasto (antes de ele sumir)
    HabilidadesDosItens.gastouItem(pokemon, id);
    // 23/10: o Suco de Baga também some ao ser bebido (não está na etiqueta do Cobblemon); sem a anotação de "usado nesta
    // batalha", um suco novo dado no meio da luta é bebido quando a vida cair da metade (dele: "não tá usando o suco de baga")
    if (someAoGastar(pokemon) || SUCO_DE_BAGA.equals(id)) {
      pokemon.removeHeldItem();
    } else {
      USADOS.computeIfAbsent(pokemon.getUuid(), uuid -> new HashSet<>()).add(id);
    }
  }

  // 23/10: a lista do Cobblemon de "some ao gastar", pelo tipo de batalha, como na batalha por turnos dele: contra selvagem e
  // contra treinador, as bagas, as ervas e a bola de neve; no duelo (PvP), só as bagas (as ervas voltam no fim). O resto
  // (Cinta de Foco, Balão, Gemas, Política de Fraqueza...) não some: vale uma vez por batalha e volta a valer na próxima
  private static boolean someAoGastar(Pokemon pokemon) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    TagKey<Item> lista = dono != null && Duelos.estaEmDuelo(dono) ? CobblemonItemTags.CONSUMED_IN_PVP_BATTLE
        : dono != null && BatalhasDeTreinador.estaEmBatalha(dono) ? CobblemonItemTags.CONSUMED_IN_NPC_BATTLE
            : CobblemonItemTags.CONSUMED_IN_WILD_BATTLE;
    return pokemon.heldItem().is(lista);
  }

  // O item de uma vez ainda vale (ele segura, e não gastou nesta batalha: a Cinta de Foco e o Balão ficam com ele)
  public static boolean disponivel(Pokemon pokemon, String id) {
    return tem(pokemon, id) && !usadoNestaBatalha(pokemon, id);
  }

  // Gasta e devolve o nome, para a frase (gasto, o item pode sumir da mão)
  public static Component gastarComNome(Pokemon pokemon, String id) {
    Component nome = nome(pokemon);
    gastar(pokemon, id);
    return nome;
  }

  // A frase de um item vai para o histórico de quem está na batalha dele (fora de uma batalha, nenhuma)
  public static void avisar(Pokemon pokemon, Component frase) {
    Combate combate = GerenciarCombates.combateDe(pokemon);
    if (combate == null) {
      return;
    }
    for (ServerPlayer jogador : combate.getJogadores()) {
      Feedback.historico(jogador, pokemon, frase);
    }
  }

  // O contato que conta para as reações (Elmo Rochoso, Estática, Farpa Grudenta...): os Protetores tiram de todos os golpes de
  // quem segura; a Luva de Soco, dos socos. 21/10 (habilidades, lote da conta do dano): a habilidade Longo Alcance, de todos
  // os golpes de quem tem
  public static boolean fazContato(Pokemon atacante, Move golpe) {
    if (!EfeitosDosGolpes.fazContato(golpe) || HabilidadesDaConta.semContato(atacante)) {
      return false;
    }
    String item = id(atacante);
    return !PROTETORES.equals(item) && !(LUVA_DE_SOCO.equals(item) && EfeitosDosGolpes.ehSoco(golpe));
  }

  public static boolean usadoNestaBatalha(Pokemon pokemon, String id) {
    Set<String> usados = USADOS.get(pokemon.getUuid());
    return usados != null && usados.contains(id);
  }

  // A batalha acabou para ele: os itens de uma vez voltam a valer, e a Escolha destrava
  public static void fimDaBatalha(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      esquecer(pokemon);
    }
  }

  public static void esquecer(Pokemon pokemon) {
    USADOS.remove(pokemon.getUuid());
    TravaDaEscolha.esquecer(pokemon);
    EstadoDosItens.esquecer(pokemon);
    // 21/10 (habilidades, lote das reações): o que as habilidades guardam na batalha acaba, e o item roubado volta
    HabilidadesQueReagem.esquecer(pokemon);
  }

  public static void limpar() {
    USADOS.clear();
    TravaDaEscolha.limpar();
    EstadoDosItens.limpar();
    HabilidadesQueReagem.limpar();
  }
}
