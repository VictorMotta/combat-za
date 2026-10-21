package dev.victor.combatza.treinadores;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.config.Configuracao;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.StatusNossos;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 21/10 (treinadores, decisão dele: "itens só em treinadores específicos; não deixa roubado, não são todos que usam"). Os
// itens são a BOLSA DO PRÓPRIO RCT ("bag" nos dados do treinador): só quem tem bolsa usa (uns 370 dos 1.559: ginásios, Elite
// 4, campeões, chefes, ace trainers), com a quantidade de lá e o máximo por batalha das regras dele ("maxItemUses": 2 ou 4).
// Nos dados só há Restauração Total, Remédio Superior/Fino/comum e as Poções. A IA, como a dos jogos:
// - com a vida em 1/4 ou menos, cura: o item que cobre o que falta com menos sobra (sem nenhum que cubra, o mais forte); com
//   um status junto, a Restauração Total, que cura os dois;
// - com um status (sono, paralisia...) e a vida na metade ou menos, a Restauração Total só pelo status;
// - entre um item e outro, a MESMA espera do jogador (Configuracao.segundosEntreItens, 5 s). Quanto cada item cura são os
//   números do Cobblemon (data/cobblemon/mechanics/potions.json e remedies.json): Poção 20 PV, Superpoção 60, Hiperpoção
//   120, Poção Máxima e Restauração Total tudo; Remédio 20, Fino 60, Superior 120
final class BolsaDoTreinador {
  private static final int TUDO = Integer.MAX_VALUE;
  private static final double VIDA_PARA_CURAR = 0.25;
  private static final double VIDA_PARA_CURAR_O_STATUS = 0.5;
  private static final int OLHAR_A_CADA = 10;
  private static final int BRILHOS = 10;

  // O que cada item da bolsa faz: quanto cura (0 = nada; TUDO = a vida toda) e se tira o status
  private record Efeito(int cura, boolean curaStatus, SoundEvent som) {
  }

  private static final Map<String, Efeito> EFEITOS = Map.of(
      "cobblemon:potion", new Efeito(20, false, CobblemonSounds.MEDICINE_SPRAY_USE),
      "cobblemon:super_potion", new Efeito(60, false, CobblemonSounds.MEDICINE_SPRAY_USE),
      "cobblemon:hyper_potion", new Efeito(120, false, CobblemonSounds.MEDICINE_SPRAY_USE),
      "cobblemon:max_potion", new Efeito(TUDO, false, CobblemonSounds.MEDICINE_SPRAY_USE),
      "cobblemon:full_restore", new Efeito(TUDO, true, CobblemonSounds.MEDICINE_SPRAY_USE),
      "cobblemon:full_heal", new Efeito(0, true, CobblemonSounds.MEDICINE_SPRAY_USE),
      "cobblemon:remedy", new Efeito(20, false, CobblemonSounds.MEDICINE_HERB_USE),
      "cobblemon:fine_remedy", new Efeito(60, false, CobblemonSounds.MEDICINE_HERB_USE),
      "cobblemon:superb_remedy", new Efeito(120, false, CobblemonSounds.MEDICINE_HERB_USE));

  private final Map<String, Integer> quantidades = new LinkedHashMap<>();
  // Quantos itens ainda pode usar nesta batalha (−1 = sem limite)
  private int usosQueFaltam;
  private long proximoNoTick;

  BolsaDoTreinador(PonteDoRct.DadosDoTime dados) {
    if (dados == null) {
      return;
    }
    dados.bolsa().forEach((item, quantidade) -> {
      if (EFEITOS.containsKey(item) && quantidade > 0) {
        quantidades.put(item, quantidade);
      } else {
        CobblemonCombatZA.LOGGER.info("TREINADOR: item da bolsa sem uso no tempo real: {} x{}", item, quantidade);
      }
    });
    usosQueFaltam = dados.maximoDeItens();
  }

  boolean vazia() {
    return quantidades.isEmpty() || usosQueFaltam == 0;
  }

  // A cada meio segundo, com o Pokémon dele na luta: usa um item se a IA dos jogos usaria. true = usou
  boolean talvezUsar(ServerPlayer jogador, Component treinador, PokemonEntity entidade, long agora) {
    if (vazia() || agora < proximoNoTick || agora % OLHAR_A_CADA != 0) {
      return false;
    }
    Pokemon pokemon = entidade.getPokemon();
    int pv = Vida.pvAtual(pokemon);
    int maximo = pokemon.getMaxHealth();
    boolean comStatus = !EfeitosDeStatus.siglaDoStatus(pokemon).isEmpty();
    String escolhido = null;
    if (pv <= maximo * VIDA_PARA_CURAR) {
      escolhido = comStatus && quantidades.containsKey("cobblemon:full_restore") ? "cobblemon:full_restore"
          : paraCurar(maximo - pv);
    } else if (comStatus && pv <= maximo * VIDA_PARA_CURAR_O_STATUS) {
      escolhido = paraOStatus();
    }
    if (escolhido == null) {
      return false;
    }
    usar(jogador, treinador, entidade, escolhido, agora);
    return true;
  }

  // O que cobre a falta com menos sobra; sem nenhum que cubra, o que mais cura
  private String paraCurar(int falta) {
    String cobre = null;
    String maisForte = null;
    for (String item : quantidades.keySet()) {
      int cura = EFEITOS.get(item).cura();
      if (cura <= 0) {
        continue;
      }
      if (cura >= falta && (cobre == null || cura < EFEITOS.get(cobre).cura())) {
        cobre = item;
      }
      if (maisForte == null || cura > EFEITOS.get(maisForte).cura()) {
        maisForte = item;
      }
    }
    return cobre != null ? cobre : maisForte;
  }

  // Só pelo status: primeiro o que não cura vida (a Cura Total), senão a Restauração Total
  private String paraOStatus() {
    String escolhido = null;
    for (String item : quantidades.keySet()) {
      Efeito efeito = EFEITOS.get(item);
      if (efeito.curaStatus() && (escolhido == null || efeito.cura() < EFEITOS.get(escolhido).cura())) {
        escolhido = item;
      }
    }
    return escolhido;
  }

  private void usar(ServerPlayer jogador, Component treinador, PokemonEntity entidade, String item, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    Efeito efeito = EFEITOS.get(item);
    int curou = efeito.cura() > 0 ? Vida.curar(entidade, efeito.cura() == TUDO ? pokemon.getMaxHealth() : efeito.cura()) : 0;
    if (efeito.curaStatus()) {
      pokemon.setStatus(null);
      StatusNossos.esquecer(pokemon);
      EfeitosDeStatus.esquecer(pokemon);
      Confusao.esquecer(pokemon);
    }
    quantidades.merge(item, -1, Integer::sum);
    quantidades.values().removeIf(quantidade -> quantidade <= 0);
    if (usosQueFaltam > 0) {
      usosQueFaltam--;
    }
    proximoNoTick = agora + (long) Configuracao.segundosEntreItens() * SharedConstants.TICKS_PER_SECOND;
    entidade.level().playSound(null, entidade.blockPosition(), efeito.som(), SoundSource.NEUTRAL, 1f, 1f);
    Feedback.particulas(entidade, ParticleTypes.HAPPY_VILLAGER, BRILHOS);
    Item doJogo = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item));
    Feedback.historicoDaBatalha(jogador, pokemon, Component.translatable("cobblemon.battle.bagitem.use", treinador,
        new ItemStack(doJogo).getHoverName(), pokemon.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("TREINADOR: {} usou {} em {} (+{} PV{}); sobram {}, usos {}", treinador.getString(), item,
        pokemon.getSpecies().getName(), curou, efeito.curaStatus() ? ", status curado" : "", quantidades, usosQueFaltam);
  }
}
