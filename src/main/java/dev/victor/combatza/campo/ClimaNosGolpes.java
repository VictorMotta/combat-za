package dev.victor.combatza.campo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.habilidades.HabilidadesDeForma;
import dev.victor.combatza.itens.ItensDeDano;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 5b.2: o que muda com o clima (Showdown, conditions.js, moves.js e abilities.js):
// - a precisão: Trovão e Furacão nunca erram na Chuva e têm 50% no Sol; as Tempestades (Álgida, Chocante, Saibrosa) nunca
//   erram na Chuva; a Nevasca nunca erra na Neve; Véu de Areia e Manto de Neve: quem ataca acerta ×0,8;
// - a carga: Raio Solar e Lâmina Solar, fora do Sol, carregam 2 s a mais ("absorveu a luz!"); o Disparo Elétrico, fora da
//   Chuva. E o Raio/Lâmina Solar perde metade do poder na Chuva, na Areia e na Neve;
// - a Esfera Climática: com clima, vira do tipo dele (Fogo, Água, Pedra, Gelo) e dobra o poder (50 → 100);
// - as curas: Luar, Síntese e Sol da Manhã curam 2/3 no Sol e 1/4 nos outros climas; Remendar Areia, 2/3 na Areia;
//   o Crescimento sobe 2 no Sol;
// - o Sol forte não deixa ninguém congelar;
// - as habilidades: Nado Rápido, Clorofila, Ímpeto de Areia e Ímpeto da Neve (Velocidade ×2 no clima delas); Prato de
//   Chuva e Corpo Gélido (+1/16 por turno); Pele Seca (+1/8 na Chuva, −1/8 no Sol); Poder Solar (Ataque Especial ×1,5 e
//   −1/8 por turno no Sol); Hidratação (cura o status na Chuva); Folha Guardiã (nada de status no Sol); Força da Areia
//   (Pedra, Terra e Aço ×1,3 na Areia); Chuvisco, Seca, Fluxo de Areia e Alerta de Neve (chamam o clima ao entrar);
//   Lançareia (chama a Areia ao levar golpe); Climatização e Bloqueio Aéreo (o clima não faz nada, EfeitosDoCampo.clima)
public class ClimaNosGolpes {
  private static final Set<String> TEMPESTADES = Set.of("thunder", "hurricane", "bleakwindstorm", "wildboltstorm",
      "sandsearstorm");
  private static final Set<String> METADE_NO_SOL = Set.of("thunder", "hurricane");
  private static final Set<String> SOLARES = Set.of("solarbeam", "solarblade");
  private static final String DISPARO_ELETRICO = "electroshot";
  private static final String NEVASCA = "blizzard";
  private static final String ESFERA_CLIMATICA = "weatherball";
  private static final String CRESCIMENTO = "growth";
  private static final String REMENDAR_AREIA = "shoreup";
  private static final Set<String> CURAM_COM_O_SOL = Set.of("synthesis", "moonlight", "morningsun");
  private static final double CARGA_A_MAIS = 2.0;
  private static final double SEM_ERRAR = -1;
  private static final double PRECISAO_NO_SOL = 50;
  private static final double EVASAO_DO_CLIMA = 0.8;
  private static final double FORCA_DA_AREIA = 5325.0 / 4096.0;
  private static final double PODER_SOLAR = 1.5;
  private static final double RAPIDO_NO_CLIMA = 2.0;
  private static final double DOIS_TERCOS = 2.0 / 3.0;
  private static final double UM_QUARTO = 0.25;
  private static final int UM_DEZESSEIS_AVOS = 16;
  private static final int UM_OITAVO = 8;
  // A habilidade que corre ×2 em cada clima
  private static final Map<String, Clima> RAPIDOS = Map.of("swiftswim", Clima.CHUVA, "chlorophyll", Clima.SOL,
      "sandrush", Clima.AREIA, "slushrush", Clima.NEVE);
  // E as que chamam o clima ao entrar (21/10, habilidades, lote da conta do dano: o Pulso de Oricalco chama o Sol, como a
  // Seca)
  private static final Map<String, Clima> CHAMAM_AO_ENTRAR = Map.of("drizzle", Clima.CHUVA, "drought", Clima.SOL,
      "sandstream", Clima.AREIA, "snowwarning", Clima.NEVE, "orichalcumpulse", Clima.SOL);
  private static final String LANCAREIA = "sandspit";
  private static final String FORCA_DA_AREIA_HAB = "sandforce";
  private static final String PODER_SOLAR_HAB = "solarpower";
  private static final String FOLHA_GUARDIA = "leafguard";

  private ClimaNosGolpes() {
  }

  // A precisão do golpe (de 0 a 100) com o clima; −1 = nunca erra
  public static double precisao(Pokemon atacante, Move golpe, Pokemon alvo, double precisao) {
    Clima clima = EfeitosDoCampo.climaEntre(atacante, alvo);
    String nome = golpe.getName();
    if (clima == Clima.CHUVA && TEMPESTADES.contains(nome) || clima == Clima.NEVE && NEVASCA.equals(nome)) {
      return SEM_ERRAR;
    }
    return clima == Clima.SOL && METADE_NO_SOL.contains(nome) ? PRECISAO_NO_SOL : precisao;
  }

  // Véu de Areia na Areia e Manto de Neve na Neve: quem ataca acerta menos
  public static double evasao(Pokemon alvo) {
    Clima clima = EfeitosDoCampo.clima(alvo);
    return clima == Clima.AREIA && Habilidades.tem(alvo, "sandveil")
        || clima == Clima.NEVE && Habilidades.tem(alvo, "snowcloak") ? EVASAO_DO_CLIMA : 1.0;
  }

  // O tipo do golpe agora (a Esfera Climática muda com o clima; o Julgamento, o Multiataque e a Tecno-Explosão, com o item)
  public static ElementalType tipo(Pokemon atacante, Move golpe) {
    // 21/10 (lote das formas): a Roda Áurea do Morpeko muda com a forma dele (Mudapetite)
    ElementalType daForma = HabilidadesDeForma.tipoDoGolpe(atacante, golpe);
    if (daForma != null) {
      return daForma;
    }
    ElementalType doItem = ItensDeDano.tipoDoGolpe(atacante, golpe);
    if (doItem != null) {
      return doItem;
    }
    // 21/10 (habilidades, lote da conta do dano): Aeração, Mãos de Fada, Refrigeração, Crosta Elétrica, Normalidade, Voz
    // Líquida
    ElementalType daHabilidade = HabilidadesDaConta.tipoDoGolpe(atacante, golpe);
    if (daHabilidade != null) {
      return daHabilidade;
    }
    if (!ESFERA_CLIMATICA.equals(golpe.getName())) {
      return golpe.getType();
    }
    return switch (EfeitosDoCampo.clima(atacante)) {
      case SOL -> ElementalTypes.FIRE;
      case CHUVA -> ElementalTypes.WATER;
      case AREIA -> ElementalTypes.ROCK;
      case NEVE -> ElementalTypes.ICE;
      default -> golpe.getType();
    };
  }

  // O poder: a Esfera Climática dobra com clima; o Raio Solar cai à metade na Chuva, na Areia e na Neve; a Força da Areia
  public static double poder(Pokemon atacante, Pokemon defensor, Move golpe) {
    Clima clima = EfeitosDoCampo.climaEntre(atacante, defensor);
    double multiplicador = 1.0;
    if (ESFERA_CLIMATICA.equals(golpe.getName()) && clima != Clima.NENHUM) {
      multiplicador *= 2;
    }
    if (SOLARES.contains(golpe.getName()) && clima != Clima.NENHUM && clima != Clima.SOL) {
      multiplicador *= 0.5;
    }
    ElementalType tipo = tipo(atacante, golpe);
    if (clima == Clima.AREIA && Habilidades.tem(atacante, FORCA_DA_AREIA_HAB)
        && (tipo == ElementalTypes.ROCK || tipo == ElementalTypes.GROUND || tipo == ElementalTypes.STEEL)) {
      multiplicador *= FORCA_DA_AREIA;
    }
    return multiplicador;
  }

  // O Poder Solar: Ataque Especial ×1,5 no Sol
  public static double ataque(Pokemon atacante, boolean fisico) {
    return !fisico && EfeitosDoCampo.clima(atacante) == Clima.SOL && Habilidades.tem(atacante, PODER_SOLAR_HAB)
        ? PODER_SOLAR : 1.0;
  }

  // Nado Rápido, Clorofila, Ímpeto de Areia e da Neve: Velocidade ×2 (na recarga e na carga, como o Lenço)
  public static double velocidade(Pokemon pokemon) {
    Clima doRapido = RAPIDOS.get(Habilidades.atual(pokemon));
    return doRapido != null && EfeitosDoCampo.clima(pokemon) == doRapido ? RAPIDO_NO_CLIMA : 1.0;
  }

  // Os ticks a mais na carga: o Raio Solar fora do Sol, o Disparo Elétrico fora da Chuva
  public static long cargaExtra(Pokemon pokemon, Move golpe) {
    return precisaCarregar(pokemon, golpe) ? Math.round(CARGA_A_MAIS * SharedConstants.TICKS_PER_SECOND) : 0;
  }

  private static boolean precisaCarregar(Pokemon pokemon, Move golpe) {
    Clima clima = EfeitosDoCampo.clima(pokemon);
    return SOLARES.contains(golpe.getName()) && clima != Clima.SOL
        || DISPARO_ELETRICO.equals(golpe.getName()) && clima != Clima.CHUVA;
  }

  // Começou a carregar: "absorveu a luz!" no histórico
  public static void aoCarregar(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    if (!precisaCarregar(pokemon, golpe)) {
      return;
    }
    String frase = DISPARO_ELETRICO.equals(golpe.getName()) ? "electroshot" : "solarbeam";
    CobblemonCombatZA.LOGGER.info("CLIMA: {} carrega {} (+2 s)", pokemon.getSpecies().getName(), golpe.getName());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.prepare." + frase, pokemon.getDisplayName(false)));
  }

  // A fração da cura dos golpes que curam com o Sol (e do Remendar Areia)
  public static double cura(Pokemon pokemon, Move golpe, double fracao) {
    Clima clima = EfeitosDoCampo.clima(pokemon);
    if (CURAM_COM_O_SOL.contains(golpe.getName()) && clima != Clima.NENHUM) {
      return clima == Clima.SOL ? DOIS_TERCOS : UM_QUARTO;
    }
    return REMENDAR_AREIA.equals(golpe.getName()) && clima == Clima.AREIA ? DOIS_TERCOS : fracao;
  }

  // O Crescimento sobe 2 em vez de 1 no Sol
  public static Map<String, Integer> reforcos(Execucao execucao, Map<String, Integer> reforcos) {
    if (!CRESCIMENTO.equals(execucao.getGolpe().getName()) || EfeitosDoCampo.clima(execucao.getPokemon()) != Clima.SOL) {
      return reforcos;
    }
    Map<String, Integer> dobrados = new HashMap<>();
    reforcos.forEach((atributo, quanto) -> dobrados.put(atributo, quanto * 2));
    return dobrados;
  }

  // Por que o status não pega com este clima (null = pega): o Sol não deixa congelar; a Folha Guardiã, nada
  public static Component bloqueiaStatus(Pokemon pokemon, String sigla) {
    if (EfeitosDoCampo.clima(pokemon) != Clima.SOL) {
      return null;
    }
    if (Habilidades.tem(pokemon, FOLHA_GUARDIA)) {
      return Component.translatable("cobblemon_combat_za.campo.nao_pega", Habilidades.nome(pokemon));
    }
    return "frz".equals(sigla) ? Component.translatable("cobblemon_combat_za.campo.nao_congela") : null;
  }

  // A cada turno de 3,5 s, nos Pokémon da batalha: Prato de Chuva, Corpo Gélido, Pele Seca, Poder Solar e Hidratação
  public static void porTurno(Arena arena, PokemonEntity entidade, Clima clima) {
    Pokemon pokemon = entidade.getPokemon();
    String habilidade = Habilidades.atual(pokemon);
    int cura = 0;
    int fere = 0;
    switch (habilidade) {
      case "raindish" -> cura = clima == Clima.CHUVA ? UM_DEZESSEIS_AVOS : 0;
      case "icebody" -> cura = clima == Clima.NEVE ? UM_DEZESSEIS_AVOS : 0;
      case "dryskin" -> {
        cura = clima == Clima.CHUVA ? UM_OITAVO : 0;
        fere = clima == Clima.SOL ? UM_OITAVO : 0;
      }
      case "solarpower" -> fere = clima == Clima.SOL ? UM_OITAVO : 0;
      case "hydration" -> {
        if (clima == Clima.CHUVA && pokemon.getStatus() != null) {
          String sigla = EfeitosDeStatus.siglaDoStatus(pokemon);
          pokemon.setStatus(null);
          CobblemonCombatZA.LOGGER.info("HIDRATAÇÃO: {} curou {} na chuva", pokemon.getSpecies().getName(), sigla);
          avisar(arena, pokemon, Component.translatable("cobblemon_combat_za.campo.curou_status",
              pokemon.getDisplayName(false), Habilidades.nome(pokemon)));
        }
      }
      default -> {
        // sem habilidade de clima
      }
    }
    if (cura > 0 && Vida.pvAtual(pokemon) < pokemon.getMaxHealth()) {
      int curou = Vida.curar(entidade, Math.max(1, pokemon.getMaxHealth() / cura));
      CobblemonCombatZA.LOGGER.info("CLIMA: {} +{} PV ({})", pokemon.getSpecies().getName(), curou, habilidade);
      avisar(arena, pokemon, Component.translatable("cobblemon_combat_za.campo.curou",
          pokemon.getDisplayName(false), Habilidades.nome(pokemon), curou));
    }
    if (fere > 0) {
      int pv = Math.max(1, pokemon.getMaxHealth() / fere);
      CobblemonCombatZA.LOGGER.info("CLIMA: {} −{} PV ({})", pokemon.getSpecies().getName(), pv, habilidade);
      avisar(arena, pokemon, Component.translatable("cobblemon_combat_za.campo.feriu",
          pokemon.getDisplayName(false), Habilidades.nome(pokemon), pv));
      Vida.ferir(entidade, pv);
    }
  }

  // Entrou na arena (EfeitosDoCampo confere quem chegou; fora da batalha, quem saiu da bola): Chuvisco, Seca, Fluxo de
  // Areia e Alerta de Neve
  public static void aoEntrar(Arena arena, PokemonEntity entidade, long agora) {
    Clima chama = CHAMAM_AO_ENTRAR.get(Habilidades.atual(entidade.getPokemon()));
    if (chama != null && arena.campo().clima() != chama) {
      CobblemonCombatZA.LOGGER.info("CLIMA: {} entrou com {}", entidade.getPokemon().getSpecies().getName(),
          Habilidades.atual(entidade.getPokemon()));
      EfeitosDoCampo.chamarClima(arena, entidade.getPokemon(), chama, agora);
    }
  }

  // Levou um golpe: a Lançareia chama a Areia
  public static void depoisDoAcerto(Entity alvo, double pvTirados, long agora) {
    if (pvTirados <= 0 || !(alvo instanceof PokemonEntity entidade) || !entidade.isAlive()
        || !Habilidades.tem(entidade.getPokemon(), LANCAREIA)) {
      return;
    }
    Arena arena = Arenas.de(entidade.getPokemon());
    if (arena != null && arena.campo().clima() != Clima.AREIA) {
      EfeitosDoCampo.chamarClima(arena, entidade.getPokemon(), Clima.AREIA, agora);
    }
  }

  // As curas e os ferimentos de cada turno: só o cartão da batalha (fora dela, a cada 3,5 s, seria só barulho)
  private static void avisar(Arena arena, Pokemon pokemon, Component frase) {
    for (ServerPlayer jogador : arena.jogadores()) {
      Feedback.historico(jogador, pokemon, frase);
    }
  }
}
