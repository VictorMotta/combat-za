package dev.victor.combatza.ia;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.config.Configuracao;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;

// 7.1 (decisões dele, 19/10: "depende da natureza dele e do Pokémon"; a conta da raiva, nossa; o nível conta): cada selvagem
// tem uma RAIVA, como a "aggro" do Fight or Flight, e ela diz o temperamento dele. A raiva soma:
// - uma base de 40;
// - o nível (nível 100 = +20);
// - a cara dele: (Ataque + Ataque Especial − Defesa − Defesa Especial) dos atributos base, ÷ 100, × 30 (de −30 a +30);
// - a natureza: Valente, Imprudente, Rígida e Teimosa +66; Atrevida, Destemida, Corajosa, Inquieta e Apressada +33;
//   7.1d (dele, 19/10: "não faz sentido um Calm correr de mim"): as medrosas, Tímida e Cuidadosa, −66 (fogem); as calmas,
//   Descontraída, Relaxada, Quieta, Atrapalhada, Calma, Dócil e Gentil, só −10 (ficam pacíficas, mais tranquilas);
// - Sombrio e Fantasma no escuro (luz até 7) +33, no claro (luz 12 ou mais) −33;
// - 7.1e: −33 se um Pokémon com dono, FORA DA BOLA, a até 9 blocos dele, tem Intimidação, Nervosismo ou Pressão (como no
//   Fight or Flight, getIntimidateCoefficient: uma caixa de 18 × 18 × 18 em volta do selvagem).
// Três faixas: 120 ou mais é AGRESSIVO (ataca sozinho); de 25 a 120, PACÍFICO (só revida); abaixo de 25, ARISCO (foge).
// E o nível manda: abaixo do 25 ele nunca ataca sozinho. (Era 100; dele, 20/10: muitos ficavam bravos, escolheu "menos
// bravos". A raiva para atacar e o nível ficam no arquivo de configuração.)
// 7.1c (decisões dele, 19/10, depois de ver todos fugindo): a espécie entra pela cara (os atributos base) e pelo tipo, e
// NÃO pelo "foge" / "se defende" do Cobblemon. Nos arquivos dele, só o Patrat diz "foge" e nenhum diz "não se defende"; o
// resto é o valor padrão, que no jogo é igual ao de quem escreveu ("só o que ele diz"). E abaixo do nível 15 vale a conta,
// como para os outros (antes, sempre fugia).
// 7.1e (dele: "tem Pokémon como o Mankey que atacam mesmo sendo Calm; veja como o FoF define isso"): ANTES da conta, como
// no Fight or Flight, as listas de espécies e os biomas, nesta ordem: nunca agressivos = PACÍFICO; sempre fogem = ARISCO;
// sempre agressivos e alfa = AGRESSIVO; Cavernas Exuberantes = PACÍFICO; Escuridão Profunda = AGRESSIVO. A regra do nível 25
// vale para todos (no Fight or Flight também)
public class Temperamento {
  public enum Tipo {
    AGRESSIVO, PACIFICO, ARISCO
  }

  // O tipo e o porquê (para o log)
  public record Decisao(Tipo tipo, double raiva, String porque) {
  }

  private static final double BASE = 40;
  private static final double PELO_NIVEL = 20;
  private static final double PELA_CARA = 30;
  private static final double PELA_NATUREZA = 33;
  private static final double NO_ESCURO = 33;
  private static final double PELA_INTIMIDACAO = -33;
  private static final double CAIXA_DA_INTIMIDACAO = 18;
  private static final double PACIFICO_A_PARTIR = 25;
  private static final int LUZ_DO_ESCURO = 7;
  private static final int LUZ_DO_CLARO = 12;
  // Os nomes do Cobblemon (cobblemon:brave) e quantas vezes a natureza pesa. As bravas são as listas do Fight or Flight; as
  // medrosas e as calmas, decisão dele (7.1d)
  private static final double MEDROSA = -2.0;
  private static final double CALMA = -0.3;
  private static final Map<String, Double> NATUREZAS = Map.ofEntries(
      Map.entry("brave", 2.0), Map.entry("rash", 2.0), Map.entry("adamant", 2.0), Map.entry("naughty", 2.0),
      Map.entry("sassy", 1.0), Map.entry("hardy", 1.0), Map.entry("bold", 1.0), Map.entry("impish", 1.0),
      Map.entry("hasty", 1.0), Map.entry("timid", MEDROSA), Map.entry("careful", MEDROSA),
      Map.entry("relaxed", CALMA), Map.entry("lax", CALMA), Map.entry("quiet", CALMA), Map.entry("bashful", CALMA),
      Map.entry("calm", CALMA), Map.entry("docile", CALMA), Map.entry("gentle", CALMA));
  // 7.1e: as listas do Fight or Flight (never_aggro, always_flee e always_aggro) ficam no arquivo de configuração desde a
  // 7.3 (Configuracao), com o nome da espécie em minúsculas, como ele compara
  private static final String ALFA = "alpha";
  // 21/10 (lote das formas): a Sinergia Equina (As One) tem o Nervosismo dentro
  private static final Set<String> ACALMAM = Set.of("intimidate", "unnerve", "pressure", "asoneglastrier",
      "asonespectrier");

  private Temperamento() {
  }

  public static Tipo de(PokemonEntity selvagem) {
    return decidir(selvagem).tipo();
  }

  public static Decisao decidir(PokemonEntity selvagem) {
    double raiva = raiva(selvagem);
    Decisao decisao = pelaEspecieOuLugar(selvagem, raiva);
    if (decisao == null) {
      Tipo tipo = raiva >= Configuracao.raivaParaAtacar() ? Tipo.AGRESSIVO
          : (raiva >= PACIFICO_A_PARTIR ? Tipo.PACIFICO : Tipo.ARISCO);
      decisao = new Decisao(tipo, raiva, "pela raiva");
    }
    int nivelMinimo = Configuracao.nivelParaAtacarSozinho();
    if (decisao.tipo() == Tipo.AGRESSIVO && selvagem.getPokemon().getLevel() < nivelMinimo) {
      return new Decisao(Tipo.PACIFICO, raiva, decisao.porque() + ", mas abaixo do nível " + nivelMinimo);
    }
    return decisao;
  }

  // A ordem do Fight or Flight: as listas de "nunca" passam na frente da de "sempre agressivo"; depois o bioma
  private static Decisao pelaEspecieOuLugar(PokemonEntity selvagem, double raiva) {
    Pokemon pokemon = selvagem.getPokemon();
    String especie = pokemon.getSpecies().getName().toLowerCase();
    if (Configuracao.nuncaAgressivos().contains(especie)) {
      return new Decisao(Tipo.PACIFICO, raiva, "espécie nunca agressiva");
    }
    if (Configuracao.sempreFogem().contains(especie)) {
      return new Decisao(Tipo.ARISCO, raiva, "espécie que sempre foge");
    }
    if (Configuracao.sempreAgressivos().contains(especie)) {
      return new Decisao(Tipo.AGRESSIVO, raiva, "espécie sempre agressiva");
    }
    if (pokemon.getAspects().contains(ALFA)) {
      return new Decisao(Tipo.AGRESSIVO, raiva, "alfa");
    }
    Holder<Biome> bioma = selvagem.level().getBiome(selvagem.blockPosition());
    if (bioma.is(Biomes.LUSH_CAVES)) {
      return new Decisao(Tipo.PACIFICO, raiva, "Cavernas Exuberantes");
    }
    if (bioma.is(Biomes.DEEP_DARK)) {
      return new Decisao(Tipo.AGRESSIVO, raiva, "Escuridão Profunda");
    }
    return null;
  }

  public static double raiva(PokemonEntity selvagem) {
    Pokemon pokemon = selvagem.getPokemon();
    Map<Stat, Integer> base = pokemon.getForm().getBaseStats();
    double cara = (base.getOrDefault(Stats.ATTACK, 0) + base.getOrDefault(Stats.SPECIAL_ATTACK, 0)
        - base.getOrDefault(Stats.DEFENCE, 0) - base.getOrDefault(Stats.SPECIAL_DEFENCE, 0)) / 100.0;
    return BASE + PELO_NIVEL * pokemon.getLevel() / 100.0 + PELA_CARA * Math.clamp(cara, -1.0, 1.0)
        + PELA_NATUREZA * NATUREZAS.getOrDefault(pokemon.getNature().getName().getPath(), 0.0)
        + peloEscuro(selvagem) + pelaIntimidacao(selvagem);
  }

  private static double peloEscuro(PokemonEntity selvagem) {
    Pokemon pokemon = selvagem.getPokemon();
    if (!AplicarStatus.temTipo(pokemon, ElementalTypes.DARK) && !AplicarStatus.temTipo(pokemon, ElementalTypes.GHOST)) {
      return 0;
    }
    int luz = selvagem.level().getMaxLocalRawBrightness(selvagem.blockPosition());
    if (luz <= LUZ_DO_ESCURO) {
      return NO_ESCURO;
    }
    return luz >= LUZ_DO_CLARO ? -NO_ESCURO : 0;
  }

  // 7.1e: como no Fight or Flight. Na primeira versão valia qualquer Pokémon do seu time, até na bola (errado: o Growlithe
  // na bola acalmava todo selvagem que te via)
  private static double pelaIntimidacao(PokemonEntity selvagem) {
    List<PokemonEntity> intimidando = selvagem.level().getEntitiesOfClass(PokemonEntity.class,
        AABB.ofSize(selvagem.position(), CAIXA_DA_INTIMIDACAO, CAIXA_DA_INTIMIDACAO, CAIXA_DA_INTIMIDACAO),
        pokemon -> pokemon.getOwner() != null && pokemon.isAlive()
            && ACALMAM.stream().anyMatch(habilidade -> Habilidades.tem(pokemon.getPokemon(), habilidade)));
    return intimidando.isEmpty() ? 0 : PELA_INTIMIDACAO;
  }
}
