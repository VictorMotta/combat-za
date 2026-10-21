package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class Confusao {
  private static final int TICKS_DOS_PASSARINHOS = 30;
  private static final ResourceLocation PASSARINHOS = ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID,
      "passarinhos_da_confusao");

  private record Confuso(Pokemon pokemon, long acabaNoTick) {
  }

  private static final Map<UUID, Confuso> CONFUSOS = new HashMap<>();

  private Confusao() {
  }

  public static void tentar(Execucao execucao, Entity alvo) {
    if (!(alvo instanceof PokemonEntity entidade)) {
      return;
    }
    Pokemon pokemon = entidade.getPokemon();
    if (pokemon.isFainted() || estaConfuso(pokemon)) {
      return;
    }
    if (Habilidades.tem(pokemon, Habilidades.RITMO_PROPRIO)) {
      Feedback.historico(execucao.getJogador(), pokemon, Component.translatable(
          "cobblemon_combat_za.status.nao_ficou_confuso", pokemon.getDisplayName(false),
          Habilidades.nome(pokemon)));
      CobblemonCombatZA.LOGGER.info("CONFUSÃO BLOQUEADA: {} tem Ritmo Próprio", pokemon.getSpecies().getName());
      return;
    }
    // 5b.3: o Campo Enevoado e a Salvaguarda
    Component doCampo = CampoEBarreiras.bloqueiaConfusao(execucao, pokemon);
    if (doCampo != null) {
      CobblemonCombatZA.LOGGER.info("CONFUSÃO BLOQUEADA: {} ({})", pokemon.getSpecies().getName(), doCampo.getString());
      Feedback.historico(execucao.getJogador(), pokemon, pokemon.getDisplayName(false).copy().append(" ").append(doCampo));
      return;
    }
    int turnos = entidade.getRandom().nextIntBetweenInclusive(Balanceamento.TURNOS_DE_CONFUSAO_MINIMO,
        Balanceamento.TURNOS_DE_CONFUSAO_MAXIMO);
    long agora = execucao.getJogador().server.getTickCount();
    CONFUSOS.put(pokemon.getUuid(), new Confuso(pokemon, agora + turnos * EfeitosDeStatus.ticksPorTurno()));
    CobblemonCombatZA.LOGGER.info("CONFUSO: {} por {} turno(s)", pokemon.getSpecies().getName(), turnos);
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.start.confusion", pokemon.getDisplayName(false)));
  }

  // Lote 2 dos itens: as bagas de cura (Figy, Wiki...) confundem quem não gosta do sabor. Sem golpe por trás: quem chama avisa.
  // Devolve se confundiu (o Ritmo Próprio não deixa, como sempre)
  public static boolean confundirPorItem(PokemonEntity entidade, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    if (pokemon.isFainted() || estaConfuso(pokemon) || Habilidades.tem(pokemon, Habilidades.RITMO_PROPRIO)) {
      return false;
    }
    int turnos = entidade.getRandom().nextIntBetweenInclusive(Balanceamento.TURNOS_DE_CONFUSAO_MINIMO,
        Balanceamento.TURNOS_DE_CONFUSAO_MAXIMO);
    CONFUSOS.put(pokemon.getUuid(), new Confuso(pokemon, agora + turnos * EfeitosDeStatus.ticksPorTurno()));
    CobblemonCombatZA.LOGGER.info("CONFUSO: {} por {} turno(s) (baga)", pokemon.getSpecies().getName(), turnos);
    return true;
  }

  public static boolean estaConfuso(Pokemon pokemon) {
    return CONFUSOS.containsKey(pokemon.getUuid());
  }

  public static boolean seFeriu(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if (!estaConfuso(pokemon) || entidade.getRandom().nextFloat() >= Balanceamento.CHANCE_DE_SE_FERIR_NA_CONFUSAO) {
      return false;
    }
    float dano = Dano.daConfusao(entidade, entidade.getRandom());
    entidade.hurt(Dano.fonte(entidade, entidade), dano);
    Feedback.avisarQuemImporta(pokemon, Component.literal("🌀 ")
        .append(Component.translatable("cobblemon.battle.damage.confusion", pokemon.getDisplayName(false))));
    CobblemonCombatZA.LOGGER.info("CONFUSÃO: {} se feriu ({} de dano)", pokemon.getSpecies().getName(),
        String.format("%.1f", dano));
    return true;
  }

  public static void atualizar(long agora) {
    for (UUID uuid : List.copyOf(CONFUSOS.keySet())) {
      Confuso confuso = CONFUSOS.get(uuid);
      if (agora >= confuso.acabaNoTick()) {
        acabar(confuso.pokemon());
      } else if (agora % TICKS_DOS_PASSARINHOS == 0) {
        Feedback.efeito(confuso.pokemon().getEntity(), PASSARINHOS, "root");
      }
    }
  }

  public static void esquecer(Pokemon pokemon) {
    CONFUSOS.remove(pokemon.getUuid());
  }

  public static void limpar() {
    CONFUSOS.clear();
  }

  private static void acabar(Pokemon pokemon) {
    CONFUSOS.remove(pokemon.getUuid());
    Component mensagem = Component.translatable("cobblemon.battle.end.confusion", pokemon.getDisplayName(false));
    CobblemonCombatZA.LOGGER.info("FIM DA CONFUSÃO: {}", mensagem.getString());
    Feedback.avisarQuemImporta(pokemon, mensagem);
  }
}
