package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimasFortes;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.campo.EfeitosDoCampo;
import dev.victor.combatza.config.Configuracao;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.habilidades.HabilidadesDeDano;
import dev.victor.combatza.habilidades.Paradoxo;
import dev.victor.combatza.habilidades.HabilidadesDeForma;
import dev.victor.combatza.habilidades.TiposNaBatalha;
import dev.victor.combatza.itens.Bagas;
import dev.victor.combatza.itens.ItensDeDano;
import dev.victor.combatza.itens.ItensDoCampo;
import dev.victor.combatza.itens.ItensSegurados;
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
  // O golpe num POKÉMON ignora a armadura (a tag minecraft:bypasses_armor): a armadura que o Cobblemon dá aos Pokémon de Defesa
  // alta cortava o dano de novo, por cima da Defesa que a nossa conta já usa (decisão dele, 20/10: "golpes ignoram")
  private static final ResourceKey<DamageType> TIPO_GOLPE_EM_POKEMON = ResourceKey.create(Registries.DAMAGE_TYPE,
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "golpe_em_pokemon"));

  // Lotes 2 e 3 dos itens: "gema" e "bagaDeResistencia" dizem que o item entrou na conta; quem gasta é o acerto (errou ou
  // desviou: o item fica)
  public record Resultado(float dano, double efetividade, boolean critico, boolean gema, boolean bagaDeResistencia) {
    public Resultado(float dano, double efetividade, boolean critico) {
      this(dano, efetividade, critico, false, false);
    }
  }

  private Dano() {
  }

  public static Resultado calcular(Pokemon atacante, Move golpe, Entity alvo, RandomSource sorte) {
    if (DanoEspecial.falha(atacante, golpe, alvo)) {
      return new Resultado(0, 0, false);
    }
    if (alvo instanceof PokemonEntity alvoPokemon) {
      return equilibrio(atacante, alvoPokemon, contraPokemon(atacante, golpe, alvoPokemon, sorte));
    }
    if (alvo instanceof Player) {
      return contraJogador(atacante, golpe, sorte);
    }
    return contraMob(atacante, golpe, sorte);
  }

  // 7.3: o golpe do selvagem em você, como o golpe de um monstro do Minecraft do nível dele: poder ÷ 20 × (1 + nível ÷ 50),
  // × 1,5 se o golpe é do tipo dele, com o crítico e a sorte. Nível 30, golpe de 40: 3,2 (um zumbi tira 3); nível 50, golpe
  // de 80 do tipo dele: 12 (um vindicador tira 13). No modo "pouco" (o padrão, na Configuracao), a metade. A armadura e a
  // dificuldade do mundo valem por cima, como num monstro (o tipo de dano golpe_pokemon não ignora armadura; o golpe_em_pokemon, num Pokémon, ignora). A conta de
  // monstro (contraMob) não servia: no nível 100, um golpe de 120 tirava 45, você morria de uma vez
  private static Resultado contraJogador(Pokemon atacante, Move golpe, RandomSource sorte) {
    boolean critico = sorte.nextDouble() < chanceDeCritico(atacante, golpe);
    double poder = DanoEspecial.temDanoFixo(golpe) ? Balanceamento.PODER_CONTRA_MOB
        : DanoEspecial.poder(atacante, golpe, null, sorte);
    double dano = poder / Balanceamento.PODER_POR_DANO_NO_JOGADOR
        * (1 + atacante.getLevel() / Balanceamento.NIVEL_DO_DANO_NO_JOGADOR) * stab(atacante, golpe)
        * multiplicadorDeCritico(critico) * aleatorio(sorte) * Balanceamento.MULTIPLICADOR_DE_DANO
        * Configuracao.multiplicadorDoDanoNoJogador() * ItensDeDano.doAtacante(atacante, golpe)
        * EfeitosDoCampo.contraQuemNaoEPokemon(atacante, golpe);
    return new Resultado((float) dano, 1.0, critico, ItensDeDano.usaGema(atacante, golpe), false);
  }

  // O equilíbrio da batalha (dele, 20/10: "um lvl 20 mata muito fácil um lvl 10", "cada server pode colocar do jeito que
  // quiser", "e o dano do nosso Pokémon também"): o golpe do selvagem num Pokémon de jogador tira a parte "danoDoSelvagem"
  // (padrão: a metade); o golpe do Pokémon de jogador num selvagem, a parte "danoDosSeusPokemon" (padrão: o normal)
  private static Resultado equilibrio(Pokemon atacante, PokemonEntity alvo, Resultado resultado) {
    boolean alvoSelvagem = alvo.getPokemon().isWild();
    double parte = atacante.isWild() && !alvoSelvagem ? Configuracao.danoDoSelvagem()
        : !atacante.isWild() && alvoSelvagem ? Configuracao.danoDosSeusPokemon() : 1.0;
    if (parte == 1.0) {
      return resultado;
    }
    return new Resultado((float) (resultado.dano() * parte), resultado.efetividade(), resultado.critico(), resultado.gema(),
        resultado.bagaDeResistencia());
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
    // 21/10 (habilidades, lote da conta do dano): HabilidadesDaConta ao lado de cada parte (a Ignorância no estágio)
    // 21/10 (habilidades, lote da entrada e das imunidades): Protossíntese e Impulso Quark no maior atributo (Paradoxo)
    double ataqueDoAtacante = (fisico ? atacante.getAttack() : atacante.getSpecialAttack())
        * HabilidadesDaConta.estagio(atacante, defensor, fisico ? "atk" : "spa")
        * HabilidadesDeDano.ataque(atacante, defensor, golpe, fisico) * ItensDeDano.ataque(atacante, fisico)
        * ClimaNosGolpes.ataque(atacante, fisico) * HabilidadesDaConta.ataque(atacante, defensor, golpe, fisico)
        * Paradoxo.atributo(atacante, fisico ? "atk" : "spa") * HabilidadesDeForma.ataque(atacante, fisico);
    double defesaDoAlvo = (fisico ? defensor.getDefence() : defensor.getSpecialDefence())
        * HabilidadesDaConta.estagio(defensor, atacante, fisico ? "def" : "spd") * ItensDeDano.defesa(defensor, fisico)
        * EfeitosDoCampo.defesa(defensor, fisico) * HabilidadesDaConta.defesa(atacante, defensor, fisico)
        * Paradoxo.atributo(defensor, fisico ? "def" : "spd") * HabilidadesDeForma.defesa(defensor, fisico);

    double poder = ItensDeDano.poder(atacante, golpe, HabilidadesDeDano.poder(atacante, defensor, golpe,
        DanoEspecial.poder(atacante, golpe, defensor, sorte))) * ClimaNosGolpes.poder(atacante, defensor, golpe)
        * CampoEBarreiras.poder(atacante, defensor, golpe) * HabilidadesDaConta.poder(atacante, defensor, golpe);
    double base = (2.0 * atacante.getLevel() / 5 + 2) * poder * ataqueDoAtacante / defesaDoAlvo / 50 + 2;
    boolean critico = !HabilidadesDeDano.semCritico(defensor) && !CampoEBarreiras.semCritico(defensor)
        && (HabilidadesDaConta.criticoCerto(atacante, defensor)
            || sorte.nextDouble() < chanceDeCritico(atacante, golpe));
    double queimadura = fisico && "brn".equals(EfeitosDeStatus.siglaDoStatus(atacante))
        && !HabilidadesDeDano.ignoraQueimadura(atacante) ? 0.5 : 1.0;
    // Lote 2: a baga que corta pela metade o super efetivo do tipo dela (a Chilan, qualquer golpe Normal)
    double baga = Bagas.resistencia(defensor, ClimaNosGolpes.tipo(atacante, golpe), efetividade);
    double danoEmPv = base * stab(atacante, golpe) * efetividade * multiplicadorDeCritico(critico) * aleatorio(sorte)
        * queimadura * baga * HabilidadesDeDano.danoFinal(defensor, efetividade) * ItensDeDano.danoFinal(atacante, efetividade)
        * EfeitosDoCampo.danoFinal(atacante, defensor, golpe) * CampoEBarreiras.barreira(defensor, fisico, critico)
        * HabilidadesDaConta.danoFinal(atacante, defensor, golpe, efetividade, critico)
        * Balanceamento.MULTIPLICADOR_DE_DANO;

    float danoNaVida = (float) (danoEmPv * alvo.getMaxHealth() / defensor.getMaxHealth());
    return new Resultado(danoNaVida, efetividade, critico, ItensDeDano.usaGema(atacante, golpe), baga < 1.0);
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
    boolean critico = sorte.nextDouble() < chanceDeCritico(atacante, golpe);
    double poder = DanoEspecial.temDanoFixo(golpe) ? Balanceamento.PODER_CONTRA_MOB
        : DanoEspecial.poder(atacante, golpe, null, sorte);
    double dano = poder / Balanceamento.PODER_POR_PONTO_DE_VIDA_EM_MOB * atacante.getLevel()
        / Balanceamento.NIVEL_DE_REFERENCIA_EM_MOB * stab(atacante, golpe) * multiplicadorDeCritico(critico)
        * aleatorio(sorte) * Balanceamento.MULTIPLICADOR_DE_DANO * ItensDeDano.doAtacante(atacante, golpe)
        * EfeitosDoCampo.contraQuemNaoEPokemon(atacante, golpe);
    return new Resultado((float) dano, 1.0, critico, ItensDeDano.usaGema(atacante, golpe), false);
  }

  // 5b.2: o tipo do golpe agora (a Esfera Climática muda com o clima). 21/10 (lote das formas): e os tipos dele agora
  // (TiposNaBatalha: Proteico, Troca de Cor...)
  private static double stab(Pokemon atacante, Move golpe) {
    ElementalType tipoDoGolpe = ClimaNosGolpes.tipo(atacante, golpe);
    boolean mesmoTipo = TiposNaBatalha.tem(atacante, tipoDoGolpe);
    return mesmoTipo ? HabilidadesDeDano.stab(atacante) : 1.0;
  }

  // Lote 3: o Balão de Ar (imune a Terra até estourar) e a Bola de Ferro (ela "aterra": a Levitação e o tipo Voador não
  // escapam da Terra); o Alvo Anelado tira as imunidades de tipo de quem segura
  private static double efetividade(Move golpe, Pokemon atacante, Pokemon defensor) {
    boolean aterrado = ItensSegurados.tem(defensor, ItensSegurados.BOLA_DE_FERRO);
    if (HabilidadesDeDano.imune(golpe, defensor) && !aterrado || ItensDoCampo.noBalao(defensor)
        && ClimaNosGolpes.tipo(atacante, golpe) == ElementalTypes.GROUND) {
      return 0;
    }
    double multiplicador = contraOTipo(golpe, atacante, defensor, TiposNaBatalha.primario(defensor));
    ElementalType segundoTipo = TiposNaBatalha.secundario(defensor);
    if (segundoTipo != null) {
      multiplicador *= contraOTipo(golpe, atacante, defensor, segundoTipo);
    }
    // 21/10: o Casco Tera (vida cheia: "pouco efetivo")
    return HabilidadesDaConta.cascoTera(defensor, multiplicador);
  }

  private static double contraOTipo(Move golpe, Pokemon atacante, Pokemon defensor, ElementalType tipoDoDefensor) {
    ElementalType tipoDoGolpe = ClimaNosGolpes.tipo(atacante, golpe);
    double multiplicador = AIUtility.INSTANCE.getDamageMultiplier(tipoDoGolpe, tipoDoDefensor);
    if (multiplicador <= 0 && (HabilidadesDeDano.ignoraImunidade(atacante, golpe, tipoDoDefensor)
        || ItensSegurados.tem(defensor, ItensSegurados.ALVO_ANELADO)
        || tipoDoGolpe == ElementalTypes.GROUND && tipoDoDefensor == ElementalTypes.FLYING
            && ItensSegurados.tem(defensor, ItensSegurados.BOLA_DE_FERRO))) {
      return 1.0;
    }
    // 21/10 (habilidades, lote da entrada e das imunidades): a Correnteza Delta tira a fraqueza do tipo Voador
    if (multiplicador > 1 && tipoDoDefensor == ElementalTypes.FLYING && ClimasFortes.protegeOVoador(defensor)) {
      return 1.0;
    }
    return multiplicador;
  }

  // O estágio de crítico: o do golpe (Talho, Folha Navalha: +1) e o dos itens (Lentes de Mira, Garra Afiada, Baga Lansat)
  private static double chanceDeCritico(Pokemon atacante, Move golpe) {
    int estagio = Math.clamp((long) golpe.getTemplate().getCritRatio() - 1 + ItensDeDano.estagioDeCritico(atacante)
        + HabilidadesDaConta.estagioDeCritico(atacante), 0, CHANCE_DE_CRITICO.length - 1);
    return CHANCE_DE_CRITICO[estagio] * Balanceamento.MULTIPLICADOR_CHANCE_CRITICO;
  }

  private static double multiplicadorDeCritico(boolean critico) {
    return critico ? Balanceamento.MULTIPLICADOR_DANO_CRITICO : 1.0;
  }

  private static double aleatorio(RandomSource sorte) {
    return Balanceamento.ALEATORIO_MINIMO
        + sorte.nextDouble() * (Balanceamento.ALEATORIO_MAXIMO - Balanceamento.ALEATORIO_MINIMO);
  }

  // Num Pokémon, sem a armadura dele; em você (e em monstros), a armadura vale, como contra um monstro
  public static DamageSource fonte(PokemonEntity atacante, Entity alvo) {
    return atacante.damageSources().source(alvo instanceof PokemonEntity ? TIPO_GOLPE_EM_POKEMON : TIPO_GOLPE_POKEMON, atacante);
  }
}
