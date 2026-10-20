package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.campo.EfeitosDoCampo;
import dev.victor.combatza.config.Configuracao;
import dev.victor.combatza.habilidades.HabilidadesDeDano;
import dev.victor.combatza.itens.ItensDeDano;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class Dano {
  private static final double[] CHANCE_DE_CRITICO = { 1.0 / 24, 1.0 / 8, 1.0 / 2, 1.0 };
  private static final ResourceKey<DamageType> TIPO_GOLPE_POKEMON = ResourceKey.create(Registries.DAMAGE_TYPE,
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "golpe_pokemon"));

  public record Resultado(float dano, double efetividade, boolean critico) {
  }

  private Dano() {
  }

  public static Resultado calcular(Pokemon atacante, Move golpe, Entity alvo, RandomSource sorte) {
    if (DanoEspecial.falha(atacante, golpe, alvo)) {
      return new Resultado(0, 0, false);
    }
    if (alvo instanceof PokemonEntity alvoPokemon) {
      return contraPokemon(atacante, golpe, alvoPokemon, sorte);
    }
    if (alvo instanceof Player) {
      return contraJogador(atacante, golpe, sorte);
    }
    return contraMob(atacante, golpe, sorte);
  }

  // 7.3: o golpe do selvagem em você, como o golpe de um monstro do Minecraft do nível dele: poder ÷ 20 × (1 + nível ÷ 50),
  // × 1,5 se o golpe é do tipo dele, com o crítico e a sorte. Nível 30, golpe de 40: 3,2 (um zumbi tira 3); nível 50, golpe
  // de 80 do tipo dele: 12 (um vindicador tira 13). No modo "pouco" (o padrão, na Configuracao), a metade. A armadura e a
  // dificuldade do mundo valem por cima, como num monstro (o tipo de dano golpe_pokemon não ignora armadura). A conta de
  // monstro (contraMob) não servia: no nível 100, um golpe de 120 tirava 45, você morria de uma vez
  private static Resultado contraJogador(Pokemon atacante, Move golpe, RandomSource sorte) {
    boolean critico = sorte.nextDouble() < chanceDeCritico(golpe);
    double poder = DanoEspecial.temDanoFixo(golpe) ? Balanceamento.PODER_CONTRA_MOB
        : DanoEspecial.poder(atacante, golpe, null, sorte);
    double dano = poder / Balanceamento.PODER_POR_DANO_NO_JOGADOR
        * (1 + atacante.getLevel() / Balanceamento.NIVEL_DO_DANO_NO_JOGADOR) * stab(atacante, golpe)
        * multiplicadorDeCritico(critico) * aleatorio(sorte) * Balanceamento.MULTIPLICADOR_DE_DANO
        * Configuracao.multiplicadorDoDanoNoJogador() * ItensDeDano.doAtacante(atacante, golpe)
        * EfeitosDoCampo.contraQuemNaoEPokemon(atacante, golpe);
    return new Resultado((float) dano, 1.0, critico);
  }

  // 7.2: para a IA do selvagem escolher o golpe (IaDoSelvagem): o multiplicador do tipo contra o alvo (em quem não é
  // Pokémon, 1)
  public static double efetividadeContra(Pokemon atacante, Move golpe, Entity alvo) {
    if (alvo instanceof PokemonEntity alvoPokemon) {
      return efetividade(golpe, atacante, alvoPokemon.getPokemon());
    }
    return 1.0;
  }

  private static Resultado contraPokemon(Pokemon atacante, Move golpe, PokemonEntity alvo, RandomSource sorte) {
    Pokemon defensor = alvo.getPokemon();
    double efetividade = efetividade(golpe, atacante, defensor);
    if (DanoEspecial.temDanoFixo(golpe)) {
      double danoFixo = efetividade > 0 ? DanoEspecial.danoFixo(atacante, golpe, defensor, sorte) : 0;
      return new Resultado(Vida.naVida(alvo, danoFixo), efetividade > 0 ? 1.0 : 0.0, false);
    }

    boolean fisico = golpe.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
    double ataqueDoAtacante = (fisico ? atacante.getAttack() : atacante.getSpecialAttack())
        * Estagios.multiplicador(atacante, fisico ? "atk" : "spa")
        * HabilidadesDeDano.ataque(atacante, defensor, golpe, fisico) * ItensDeDano.ataque(atacante, fisico)
        * ClimaNosGolpes.ataque(atacante, fisico);
    double defesaDoAlvo = (fisico ? defensor.getDefence() : defensor.getSpecialDefence())
        * Estagios.multiplicador(defensor, fisico ? "def" : "spd") * ItensDeDano.defesa(defensor, fisico)
        * EfeitosDoCampo.defesa(defensor, fisico);

    double poder = ItensDeDano.poder(atacante, golpe, HabilidadesDeDano.poder(atacante, defensor, golpe,
        DanoEspecial.poder(atacante, golpe, defensor, sorte))) * ClimaNosGolpes.poder(atacante, defensor, golpe)
        * CampoEBarreiras.poder(atacante, defensor, golpe);
    double base = (2.0 * atacante.getLevel() / 5 + 2) * poder * ataqueDoAtacante / defesaDoAlvo / 50 + 2;
    boolean critico = !HabilidadesDeDano.semCritico(defensor) && !CampoEBarreiras.semCritico(defensor)
        && sorte.nextDouble() < chanceDeCritico(golpe);
    double queimadura = fisico && "brn".equals(EfeitosDeStatus.siglaDoStatus(atacante))
        && !HabilidadesDeDano.ignoraQueimadura(atacante) ? 0.5 : 1.0;
    double danoEmPv = base * stab(atacante, golpe) * efetividade * multiplicadorDeCritico(critico) * aleatorio(sorte)
        * queimadura * HabilidadesDeDano.danoFinal(defensor, efetividade) * ItensDeDano.danoFinal(atacante, efetividade)
        * EfeitosDoCampo.danoFinal(atacante, defensor, golpe) * CampoEBarreiras.barreira(defensor, fisico, critico)
        * Balanceamento.MULTIPLICADOR_DE_DANO;

    float danoNaVida = (float) (danoEmPv * alvo.getMaxHealth() / defensor.getMaxHealth());
    return new Resultado(danoNaVida, efetividade, critico);
  }

  public static float daConfusao(PokemonEntity entidade, RandomSource sorte) {
    Pokemon pokemon = entidade.getPokemon();
    double ataque = pokemon.getAttack() * Estagios.multiplicador(pokemon, "atk");
    double defesa = pokemon.getDefence() * Estagios.multiplicador(pokemon, "def");
    double base = (2.0 * pokemon.getLevel() / 5 + 2) * Balanceamento.PODER_DA_CONFUSAO * ataque / defesa / 50 + 2;
    double danoEmPv = Math.max(1, base * aleatorio(sorte) * Balanceamento.MULTIPLICADOR_DE_DANO);
    return (float) (danoEmPv * entidade.getMaxHealth() / pokemon.getMaxHealth());
  }

  private static Resultado contraMob(Pokemon atacante, Move golpe, RandomSource sorte) {
    boolean critico = sorte.nextDouble() < chanceDeCritico(golpe);
    double poder = DanoEspecial.temDanoFixo(golpe) ? Balanceamento.PODER_CONTRA_MOB
        : DanoEspecial.poder(atacante, golpe, null, sorte);
    double dano = poder / Balanceamento.PODER_POR_PONTO_DE_VIDA_EM_MOB * atacante.getLevel()
        / Balanceamento.NIVEL_DE_REFERENCIA_EM_MOB * stab(atacante, golpe) * multiplicadorDeCritico(critico)
        * aleatorio(sorte) * Balanceamento.MULTIPLICADOR_DE_DANO * ItensDeDano.doAtacante(atacante, golpe)
        * EfeitosDoCampo.contraQuemNaoEPokemon(atacante, golpe);
    return new Resultado((float) dano, 1.0, critico);
  }

  // 5b.2: o tipo do golpe agora (a Esfera Climática muda com o clima)
  private static double stab(Pokemon atacante, Move golpe) {
    ElementalType tipoDoGolpe = ClimaNosGolpes.tipo(atacante, golpe);
    boolean mesmoTipo = tipoDoGolpe == atacante.getPrimaryType() || tipoDoGolpe == atacante.getSecondaryType();
    return mesmoTipo ? HabilidadesDeDano.stab(atacante) : 1.0;
  }

  private static double efetividade(Move golpe, Pokemon atacante, Pokemon defensor) {
    if (HabilidadesDeDano.imune(golpe, defensor)) {
      return 0;
    }
    double multiplicador = contraOTipo(golpe, atacante, defensor.getPrimaryType());
    ElementalType segundoTipo = defensor.getSecondaryType();
    if (segundoTipo != null) {
      multiplicador *= contraOTipo(golpe, atacante, segundoTipo);
    }
    return multiplicador;
  }

  private static double contraOTipo(Move golpe, Pokemon atacante, ElementalType tipoDoDefensor) {
    double multiplicador = AIUtility.INSTANCE.getDamageMultiplier(ClimaNosGolpes.tipo(atacante, golpe), tipoDoDefensor);
    if (multiplicador <= 0 && HabilidadesDeDano.ignoraImunidade(atacante, golpe, tipoDoDefensor)) {
      return 1.0;
    }
    return multiplicador;
  }

  private static double chanceDeCritico(Move golpe) {
    int estagio = Math.clamp((long) golpe.getTemplate().getCritRatio() - 1, 0, CHANCE_DE_CRITICO.length - 1);
    return CHANCE_DE_CRITICO[estagio] * Balanceamento.MULTIPLICADOR_CHANCE_CRITICO;
  }

  private static double multiplicadorDeCritico(boolean critico) {
    return critico ? Balanceamento.MULTIPLICADOR_DANO_CRITICO : 1.0;
  }

  private static double aleatorio(RandomSource sorte) {
    return Balanceamento.ALEATORIO_MINIMO
        + sorte.nextDouble() * (Balanceamento.ALEATORIO_MAXIMO - Balanceamento.ALEATORIO_MINIMO);
  }

  public static DamageSource fonte(PokemonEntity atacante) {
    return atacante.damageSources().source(TIPO_GOLPE_POKEMON, atacante);
  }
}
