package dev.victor.combatza.itens;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.rede.TravaDaEscolhaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

// 9.1, lote 1: a TRAVA dos itens da Escolha (Faixa, Lentes e Lenço: +50%, mas presos num golpe só, como nos jogos). Na batalha,
// o primeiro golpe que ele usa trava; os outros três ficam riscados no losango (como o Fingimento gasto) até ele voltar para a
// bola, desmaiar, perder o item ou a batalha acabar. E o Colete de Ataque: os golpes de status ficam desligados
public class TravaDaEscolha {
  private static final int SEM_TRAVA = -1;
  // O golpe (o lugar dele, 0 a 3) em que cada Pokémon está preso
  private static final Map<UUID, Integer> PRESOS = new HashMap<>();

  private TravaDaEscolha() {
  }

  // 21/10 (habilidades, lote da conta do dano): a habilidade Táticas de Gorila trava igual
  private static boolean temEscolha(Pokemon pokemon) {
    String item = ItensSegurados.id(pokemon);
    return ItensSegurados.FAIXA_DA_ESCOLHA.equals(item) || ItensSegurados.LENTES_DA_ESCOLHA.equals(item)
        || ItensSegurados.LENCO_DA_ESCOLHA.equals(item) || HabilidadesDaConta.travaComoEscolha(pokemon);
  }

  // Este golpe está desligado agora? (preso em outro, ou de status com o Colete). Perdeu o item: destrava
  public static boolean bloqueia(Pokemon pokemon, int slot, Move golpe) {
    // 21/10 (habilidades, lote das reações): o golpe desabilitado pelo Corpo Maldito também fica desligado
    if (HabilidadesQueReagem.desabilitado(pokemon, golpe)) {
      return true;
    }
    if (ItensSegurados.tem(pokemon, ItensSegurados.COLETE_DE_ATAQUE)
        && golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) {
      return true;
    }
    Integer preso = PRESOS.get(pokemon.getUuid());
    if (preso != null && !temEscolha(pokemon)) {
      esquecer(pokemon);
      return false;
    }
    return preso != null && preso != slot;
  }

  // Por que o golpe está desligado (para a barra de ação)
  public static net.minecraft.network.chat.Component porque(Pokemon pokemon, Move golpe) {
    if (HabilidadesQueReagem.desabilitado(pokemon, golpe)) {
      return net.minecraft.network.chat.Component.translatable("cobblemon.battle.cant.disable",
          pokemon.getDisplayName(false), golpe.getDisplayName());
    }
    if (ItensSegurados.tem(pokemon, ItensSegurados.COLETE_DE_ATAQUE)
        && golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) {
      return net.minecraft.network.chat.Component.translatable("cobblemon_combat_za.item.colete",
          pokemon.getDisplayName(false), ItensSegurados.nome(pokemon));
    }
    Integer preso = PRESOS.get(pokemon.getUuid());
    Move travado = preso == null ? null : pokemon.getMoveSet().get(preso);
    // (presa pelas Táticas de Gorila, o nome da habilidade no lugar do item)
    net.minecraft.network.chat.Component deQuem = HabilidadesDaConta.travaComoEscolha(pokemon)
        ? Habilidades.nome(pokemon) : ItensSegurados.nome(pokemon);
    return net.minecraft.network.chat.Component.translatable("cobblemon_combat_za.item.trava",
        pokemon.getDisplayName(false), travado == null ? golpe.getDisplayName() : travado.getDisplayName(), deQuem);
  }

  // Usou um golpe: com a Escolha, na batalha, trava nele
  public static void usou(Pokemon pokemon, int slot) {
    if (!temEscolha(pokemon) || PRESOS.containsKey(pokemon.getUuid()) || !GerenciarCombates.estaEmCombate(pokemon)) {
      return;
    }
    PRESOS.put(pokemon.getUuid(), slot);
    avisarODono(pokemon, slot);
    CobblemonCombatZA.LOGGER.info("ITEM: {} travou no golpe {} ({})", pokemon.getSpecies().getName(), slot,
        ItensSegurados.id(pokemon));
  }

  public static void esquecer(Pokemon pokemon) {
    if (PRESOS.remove(pokemon.getUuid()) != null) {
      avisarODono(pokemon, SEM_TRAVA);
    }
  }

  public static void limpar() {
    PRESOS.clear();
  }

  // O seu jogo risca os outros golpes no losango (TravaDaEscolhaNoCliente)
  private static void avisarODono(Pokemon pokemon, int slot) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    if (dono != null && ServerPlayNetworking.canSend(dono, TravaDaEscolhaPayload.TIPO)) {
      ServerPlayNetworking.send(dono, new TravaDaEscolhaPayload(pokemon.getUuid(), slot));
    }
  }
}
