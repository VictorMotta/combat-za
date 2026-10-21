package dev.victor.combatza.campo;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.network.chat.Component;

// 21/10 (habilidades, lote da entrada e das imunidades): os CLIMAS FORTES das habilidades lendárias (Showdown,
// conditions.js e abilities.js):
// - Terra Desolada (Groudon Primitivo): o Sol extremo. Vale tudo o que o Sol vale (Fogo ×1,5, ninguém congela, Clorofila,
//   Protossíntese...), e os golpes de Água com dano EVAPORAM (não fazem nada);
// - Mar Primordial (Kyogre Primitivo): a Chuva torrencial. Vale tudo o que a Chuva vale, e os golpes de Fogo com dano se
//   APAGAM;
// - Correnteza Delta (Rayquaza Mega): os ventos fortes. Nenhum outro clima vale (nem a chuva do mundo), e o golpe super
//   efetivo no tipo Voador fica normal nessa parte do tipo (Dano).
// Começam quando quem tem a habilidade ENTRA na arena (a batalha, ou o campo pessoal fora dela, como os climas da 5b) e
// duram ENQUANTO ele (ou outro com a mesma habilidade) estiver lá: sem relógio. Nenhum golpe nem habilidade de clima comum
// troca um clima forte ("O sol extremamente forte não diminuiu nada!"); um clima forte troca o outro. Quando ele sai (volta
// para a bola, desmaia, a batalha acaba), o clima forte acaba e volta o do mundo. Climatização e Bloqueio Aéreo anulam
// também os fortes (o Showdown faz igual)
public class ClimasFortes {
  public enum Forte {
    SOL_EXTREMO("desolateland", Clima.SOL, ElementalTypes.WATER),
    CHUVA_TORRENCIAL("primordialsea", Clima.CHUVA, ElementalTypes.FIRE),
    VENTOS_FORTES("deltastream", Clima.NENHUM, null);

    private final String habilidade;
    private final Clima clima;
    private final ElementalType apaga;

    Forte(String habilidade, Clima clima, ElementalType apaga) {
      this.habilidade = habilidade;
      this.clima = clima;
      this.apaga = apaga;
    }

    // O clima de sempre por trás dele (o Sol, a Chuva; os ventos não têm)
    public Clima clima() {
      return clima;
    }

    private static Forte daHabilidade(Pokemon pokemon) {
      String habilidade = Habilidades.atual(pokemon);
      for (Forte forte : values()) {
        if (forte.habilidade.equals(habilidade)) {
          return forte;
        }
      }
      return null;
    }

    private static Forte doClima(Clima clima) {
      return clima == Clima.SOL ? SOL_EXTREMO : clima == Clima.CHUVA ? CHUVA_TORRENCIAL : null;
    }

    private Component frase(String quando) {
      return Component.translatable("cobblemon_combat_za.campo.forte." + habilidade + "." + quando);
    }
  }

  // O clima forte que vale numa arena
  private record Ativo(Forte forte) {
  }

  // O "prazo" do clima forte: sem fim (o ícone da passiva fica sem relógio: Passivas)
  private static final CampoDaBatalha.Prazo SEM_FIM = new CampoDaBatalha.Prazo(Long.MAX_VALUE, Long.MAX_VALUE);
  private static final Set<String> ANULAM_O_CLIMA = Set.of("cloudnine", "airlock");
  // Por campo (cada batalha e cada campo pessoal tem o seu); o campo de uma batalha que acabou some sozinho daqui
  private static final Map<CampoDaBatalha, Ativo> ATIVOS = new WeakHashMap<>();

  private ClimasFortes() {
  }

  public static boolean semRelogio(CampoDaBatalha.Prazo prazo) {
    return prazo == SEM_FIM;
  }

  // Entrou na arena (HabilidadesDeEntrada.aoEntrar): quem tem a habilidade chama o clima forte dela (o mesmo que já vale:
  // nada muda)
  public static void aoEntrar(Arena arena, PokemonEntity entidade) {
    Forte forte = Forte.daHabilidade(entidade.getPokemon());
    if (forte == null || !entidade.isAlive()) {
      return;
    }
    Ativo ativo = valendo(arena.campo());
    if (ativo != null && ativo.forte() == forte) {
      return;
    }
    CampoDaBatalha campo = arena.campo();
    if (forte.clima() != Clima.NENHUM) {
      campo.chamarClima(forte.clima(), SEM_FIM);
    } else {
      campo.acabarOClimaDoGolpe();
    }
    ATIVOS.put(campo, new Ativo(forte));
    CobblemonCombatZA.LOGGER.info("CLIMA FORTE: {} entrou com {} ({})", entidade.getPokemon().getSpecies().getName(),
        forte, arena.naBatalha() ? "na batalha" : "fora da batalha");
    arena.avisar(entidade.getPokemon(), forte.frase("comecou"));
  }

  // A cada 5 ticks (HabilidadesDeEntrada), em cada arena: sem ninguém com a habilidade lá, o clima forte acaba. O Sol ou a
  // Chuva forte que veio de outro campo (a batalha que acabou devolve o clima ao campo pessoal: Arenas) fica se quem tem a
  // habilidade veio junto; senão, acaba em silêncio
  public static void atualizar(Arena arena) {
    CampoDaBatalha campo = arena.campo();
    Ativo ativo = valendo(campo);
    if (ativo == null) {
      ATIVOS.remove(campo);
      Forte veioDeOutro = Forte.doClima(campo.getDoGolpe());
      if (veioDeOutro != null && semRelogio(campo.getPrazoDoClima())) {
        if (quemTem(arena, veioDeOutro) != null) {
          ATIVOS.put(campo, new Ativo(veioDeOutro));
        } else {
          campo.acabarOClimaDoGolpe();
          CobblemonCombatZA.LOGGER.info("CLIMA FORTE: {} veio de outro campo sem quem o chamou e acabou", veioDeOutro);
        }
      }
      return;
    }
    if (quemTem(arena, ativo.forte()) != null) {
      return;
    }
    ATIVOS.remove(campo);
    if (ativo.forte().clima() != Clima.NENHUM) {
      campo.acabarOClimaDoGolpe();
    }
    CobblemonCombatZA.LOGGER.info("CLIMA FORTE: {} acabou (ninguém com a habilidade na arena)", ativo.forte());
    arena.avisar(null, ativo.forte().frase("acabou"));
  }

  // EfeitosDoCampo.chamarClima: com um clima forte valendo, nenhum clima comum entra. true = barrado (já avisou)
  public static boolean barra(Arena arena, Pokemon quem) {
    Ativo ativo = valendo(arena.campo());
    if (ativo == null) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("CLIMA FORTE: {} tentou mudar o clima, mas {} não deixa", quem.getSpecies().getName(),
        ativo.forte());
    arena.avisar(quem, ativo.forte().frase("barrou"));
    return true;
  }

  // EfeitosDoCampo.climaQueVale: com os ventos fortes, nenhum outro clima vale
  public static boolean ventosFortes(Arena arena) {
    Ativo ativo = valendo(arena.campo());
    return ativo != null && ativo.forte() == Forte.VENTOS_FORTES;
  }

  // Dano: os ventos fortes tiram a fraqueza do tipo Voador (o super efetivo nessa parte do tipo fica normal)
  public static boolean protegeOVoador(Pokemon defensor) {
    return forteQueVale(defensor) == Forte.VENTOS_FORTES;
  }

  // HabilidadesDeProtecao.golpeFalhou (na hora do golpe sair, ControleDeGolpes): o golpe de Água com dano no Sol extremo
  // evapora; o de Fogo na Chuva torrencial se apaga. true = não faz nada (já avisou)
  public static boolean apagou(Execucao execucao) {
    if (!vaiApagar(execucao.getPokemon(), execucao.getGolpe())) {
      return false;
    }
    Forte forte = forteQueVale(execucao.getPokemon());
    CobblemonCombatZA.LOGGER.info("CLIMA FORTE: {} de {} não fez nada ({})", execucao.getGolpe().getName(),
        execucao.getPokemon().getSpecies().getName(), forte);
    Feedback.historico(execucao.getJogador(), execucao.getPokemon(), forte.frase("apagou"));
    return true;
  }

  // O mesmo, sem fazer nada: a IA não escolhe o golpe que vai se apagar
  public static boolean vaiApagar(Pokemon atacante, Move golpe) {
    Forte forte = forteQueVale(atacante);
    return forte != null && forte.apaga != null && golpe.getDamageCategory() != DamageCategories.INSTANCE.getSTATUS()
        && ClimaNosGolpes.tipo(atacante, golpe) == forte.apaga;
  }

  public static void limpar() {
    ATIVOS.clear();
  }

  // O clima forte que vale onde o Pokémon está (null = nenhum, ou anulado pela Climatização ou pelo Bloqueio Aéreo)
  private static Forte forteQueVale(Pokemon pokemon) {
    Arena arena = pokemon == null ? null : Arenas.de(pokemon);
    Ativo ativo = arena == null ? null : valendo(arena.campo());
    if (ativo == null) {
      return null;
    }
    for (PokemonEntity entidade : arena.pokemons()) {
      if (ANULAM_O_CLIMA.contains(Habilidades.atual(entidade.getPokemon()))) {
        return null;
      }
    }
    return ativo.forte();
  }

  // O registro ainda vale? (o Sol e a Chuva fortes têm de continuar no campo, sem fim: a batalha que começou leva o clima do
  // campo pessoal e o deixa vazio, Arenas.entrouNaBatalha)
  private static Ativo valendo(CampoDaBatalha campo) {
    Ativo ativo = ATIVOS.get(campo);
    if (ativo == null || ativo.forte().clima() == Clima.NENHUM) {
      return ativo;
    }
    return campo.getDoGolpe() == ativo.forte().clima() && semRelogio(campo.getPrazoDoClima()) ? ativo : null;
  }

  // Alguém na arena (vivo) com a habilidade deste clima forte
  private static Pokemon quemTem(Arena arena, Forte forte) {
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon pokemon = entidade.getPokemon();
      if (entidade.isAlive() && !pokemon.isFainted() && Habilidades.tem(pokemon, forte.habilidade)) {
        return pokemon;
      }
    }
    return null;
  }
}
