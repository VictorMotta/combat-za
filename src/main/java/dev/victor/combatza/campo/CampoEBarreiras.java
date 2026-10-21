package dev.victor.combatza.campo;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Armadilhas;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.QuebraMolde;
import dev.victor.combatza.habilidades.HabilidadesDePrioridade;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.pvp.Duelos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

// 5b.3: o CAMPO e as BARREIRAS (Showdown, conditions.js e moves.js). "No chão" = quem não voa (nem é do tipo Voador, nem tem
// Levitação: a mesma regra das armadilhas).
// - Campos (5 turnos = 17,5 s; 8 = 28 s com o Extensor de Terreno; o novo troca o velho): Elétrico, de Plantas e Psíquico
//   deixam o tipo deles ×1,3 em quem ataca do chão. Plantas: cura 1/16 por turno de quem está no chão, e Terremoto,
//   Tremor e Magnitude tiram a metade em quem está no chão. Enevoado: no chão, nada de status nem confusão, e Dragão tira a
//   metade. Elétrico: no chão, ninguém dorme. Psíquico: golpe rápido (prioridade) não pega em quem está no chão. As
//   habilidades -Gênese chamam o campo ao entrar; a Semeadora, ao levar golpe;
// - Barreiras, de um lado (o seu: os lutadores; o outro: os selvagens): Refletir (físico) e Tela de Luz (especial) tiram a
//   metade do dano (2/3 se o lado tem mais de um na luta; o crítico passa); o Véu de Aurora faz os dois, só na Neve; 5 turnos,
//   8 com a Argila de Luz. Vento de Cauda: Velocidade ×2 por 4 turnos. Salvaguarda: nada de status nem confusão vindos do
//   outro. Névoa: nada de queda de atributo vinda do outro (nem a Intimidação). Cântico da Sorte: sem crítico.
// P5-10: valem numa ARENA (a batalha, ou o campo pessoal fora dela). Fora da batalha, o Refletir corta o golpe de um monstro
// (menos a magia) e a Tela de Luz, a magia (a poção da bruxa, a presa do evocador)
public class CampoEBarreiras {
  private static final Map<String, Terreno> TERRENO_DO_GOLPE = Map.of("electricterrain", Terreno.ELETRICO,
      "grassyterrain", Terreno.PLANTAS, "mistyterrain", Terreno.ENEVOADO, "psychicterrain", Terreno.PSIQUICO);
  // (21/10, habilidades, lote da conta do dano: o Motor Hádron chama o Campo Elétrico, como a Eletrogênese)
  private static final Map<String, Terreno> TERRENO_AO_ENTRAR = Map.of("electricsurge", Terreno.ELETRICO,
      "grassysurge", Terreno.PLANTAS, "mistysurge", Terreno.ENEVOADO, "psychicsurge", Terreno.PSIQUICO,
      "hadronengine", Terreno.ELETRICO);
  private static final Map<String, Barreira> BARREIRA_DO_GOLPE = Map.of("reflect", Barreira.REFLETIR,
      "lightscreen", Barreira.TELA_DE_LUZ, "auroraveil", Barreira.VEU_DE_AURORA, "tailwind", Barreira.VENTO_DE_CAUDA,
      "safeguard", Barreira.SALVAGUARDA, "mist", Barreira.NEVOA, "luckychant", Barreira.CANTICO_DA_SORTE);
  private static final Set<String> TREMORES = Set.of("earthquake", "bulldoze", "magnitude");
  private static final String SEMEADORA = "seedsower";
  private static final String EXTENSOR_DE_TERRENO = "terrainextender";
  private static final String ARGILA_DE_LUZ = "lightclay";
  private static final int TURNOS_DO_CAMPO = 5;
  private static final int TURNOS_COM_ITEM = 8;
  private static final double FORTE_NO_CAMPO = 5325.0 / 4096.0;
  private static final double METADE = 0.5;
  private static final double TELA_EM_MAIS_DE_UM = 2732.0 / 4096.0;
  private static final double VENTO_DE_CAUDA = 2.0;
  private static final int CURA_DAS_PLANTAS = 16;
  private static final int PARTICULAS = 16;
  private static final double ESPALHAR = 6.0;
  private static final DustParticleOptions NEVOA_ROSA = new DustParticleOptions(new Vector3f(0.96f, 0.65f, 0.84f), 1.2f);

  private CampoEBarreiras() {
  }

  // 21/10 (PvP): no duelo, cada jogador é um lado: o de quem desafiou fica no "seu", o de quem aceitou no outro (pvp.Duelos)
  // O lado do campo de "pokemon": true = o dos jogadores (no duelo, o de quem desafiou: pvp.Duelos)
  public static boolean doSeuLado(Pokemon pokemon) {
    Boolean noDuelo = Duelos.ladoNoDuelo(pokemon);
    return noDuelo != null ? noDuelo : !pokemon.isWild();
  }

  private static boolean noChao(Pokemon pokemon) {
    return pokemon != null && !Armadilhas.voa(pokemon);
  }

  public static Terreno terreno(Pokemon pokemon) {
    Arena arena = pokemon == null ? null : Arenas.de(pokemon);
    return arena == null ? Terreno.NENHUM : arena.campo().getTerreno();
  }

  public static boolean temBarreira(Pokemon pokemon, Barreira barreira) {
    Arena arena = Arenas.de(pokemon);
    return arena != null && arena.campo().barreiras(doSeuLado(pokemon)).containsKey(barreira);
  }

  // O golpe é de campo ou de barreira? Então chama (ou falha) e devolve true
  public static boolean aoUsarGolpe(Execucao execucao, PokemonEntity atacante) {
    String nome = execucao.getGolpe().getName();
    Terreno terreno = TERRENO_DO_GOLPE.get(nome);
    Barreira barreira = BARREIRA_DO_GOLPE.get(nome);
    if (terreno == null && barreira == null || !(atacante.level() instanceof ServerLevel nivel)) {
      return false;
    }
    Pokemon pokemon = execucao.getPokemon();
    particulas(nivel, terreno != null ? terreno : Terreno.NENHUM, atacante, PARTICULAS * 2);
    Arena arena = Arenas.de(pokemon);
    if (arena == null) {
      return true;
    }
    long agora = nivel.getServer().getTickCount();
    if (terreno != null) {
      chamarTerreno(arena, pokemon, terreno, agora);
    } else {
      levantar(arena, pokemon, barreira, agora);
    }
    return true;
  }

  public static void chamarTerreno(Arena arena, Pokemon quem, Terreno novo, long agora) {
    CampoDaBatalha campo = arena.campo();
    if (campo.getTerreno() == novo) {
      arena.avisar(quem, Component.translatable("cobblemon.battle.nothing"));
      return;
    }
    int turnos = ItensSegurados.tem(quem, EXTENSOR_DE_TERRENO) ? TURNOS_COM_ITEM : TURNOS_DO_CAMPO;
    long duracao = turnos * EfeitosDeStatus.ticksPorTurno();
    campo.mudarOTerreno(novo, new CampoDaBatalha.Prazo(agora + duracao, duracao));
    CobblemonCombatZA.LOGGER.info("CAMPO: {} chamou {} por {} turnos ({})", quem.getSpecies().getName(), novo, turnos,
        arena.naBatalha() ? "na batalha" : "fora da batalha");
    arena.avisar(quem, novo.comecou());
  }

  private static void levantar(Arena arena, Pokemon quem, Barreira barreira, long agora) {
    boolean lado = doSeuLado(quem);
    Map<Barreira, CampoDaBatalha.Prazo> barreiras = arena.campo().barreiras(lado);
    boolean semNeve = barreira == Barreira.VEU_DE_AURORA && EfeitosDoCampo.climaQueVale(arena) != Clima.NEVE;
    if (barreiras.containsKey(barreira) || semNeve) {
      CobblemonCombatZA.LOGGER.info("BARREIRA: {} falhou ({})", barreira, semNeve ? "sem neve" : "já tem");
      arena.avisar(quem, Component.translatable("cobblemon.battle.nothing"));
      return;
    }
    int turnos = barreira.ehTela() && ItensSegurados.tem(quem, ARGILA_DE_LUZ) ? TURNOS_COM_ITEM : barreira.turnos();
    long duracao = turnos * EfeitosDeStatus.ticksPorTurno();
    barreiras.put(barreira, new CampoDaBatalha.Prazo(agora + duracao, duracao));
    CobblemonCombatZA.LOGGER.info("BARREIRA: {} levantou {} ({} lado, {}) por {} turnos", quem.getSpecies().getName(),
        barreira, lado ? "seu" : "dos selvagens", arena.naBatalha() ? "na batalha" : "fora da batalha", turnos);
    arena.avisar(quem, barreira.comecou(lado));
    // 21/10 (habilidades, lote das reações): o Vento de Cauda deixa carregado quem tem Energia Eólica do mesmo lado
    if (barreira == Barreira.VENTO_DE_CAUDA) {
      HabilidadesQueReagem.ventoDeCauda(arena.pokemons(), lado);
    }
  }

  // A cada tick (EfeitosDoCampo.atualizar): o fim do campo e das barreiras, as partículas (a cada segundo) e a cura do
  // Campo de Plantas (a cada turno)
  public static void atualizar(Arena arena, long agora, boolean virouSegundo, boolean virouTurno) {
    acabarOsQueVenceram(arena, agora);
    // 21/10: o Truque de Agilidade (o fim e as partículas)
    SalaTrick.atualizar(arena, agora, virouSegundo);
    CampoDaBatalha campo = arena.campo();
    if (virouSegundo && campo.getTerreno() != Terreno.NENHUM) {
      for (ServerPlayer jogador : arena.jogadores()) {
        particulas(jogador.serverLevel(), campo.getTerreno(), jogador, PARTICULAS);
      }
    }
    if (virouTurno && campo.getTerreno() == Terreno.PLANTAS) {
      curarNoCampoDePlantas(arena);
    }
  }

  private static void acabarOsQueVenceram(Arena arena, long agora) {
    CampoDaBatalha campo = arena.campo();
    if (campo.getTerreno() != Terreno.NENHUM && agora >= campo.getPrazoDoTerreno().fim()) {
      Terreno acabou = campo.getTerreno();
      campo.mudarOTerreno(Terreno.NENHUM, null);
      CobblemonCombatZA.LOGGER.info("CAMPO: {} acabou", acabou);
      arena.avisar(null, acabou.acabou());
    }
    for (boolean lado : new boolean[] { true, false }) {
      Iterator<Map.Entry<Barreira, CampoDaBatalha.Prazo>> barreiras = campo.barreiras(lado).entrySet().iterator();
      while (barreiras.hasNext()) {
        Map.Entry<Barreira, CampoDaBatalha.Prazo> barreira = barreiras.next();
        if (agora >= barreira.getValue().fim()) {
          barreiras.remove();
          CobblemonCombatZA.LOGGER.info("BARREIRA: {} acabou", barreira.getKey());
          arena.avisar(null, barreira.getKey().acabou(lado));
        }
      }
    }
  }

  // O Campo de Plantas cura 1/16 de quem está no chão (o cartão só na batalha)
  private static void curarNoCampoDePlantas(Arena arena) {
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon pokemon = entidade.getPokemon();
      if (!entidade.isAlive() || pokemon.isFainted() || !noChao(pokemon)
          || Vida.pvAtual(pokemon) >= pokemon.getMaxHealth()) {
        continue;
      }
      int curou = Vida.curar(entidade, Math.max(1, pokemon.getMaxHealth() / CURA_DAS_PLANTAS));
      CobblemonCombatZA.LOGGER.info("CAMPO DE PLANTAS: {} +{} PV", pokemon.getSpecies().getName(), curou);
      for (ServerPlayer jogador : arena.jogadores()) {
        Feedback.historico(jogador, pokemon, Component.translatable("cobblemon.battle.heal.grassyterrain",
            pokemon.getDisplayName(false)).append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
      }
    }
  }

  // Entrou na arena: as -Gêneses chamam o campo
  public static void aoEntrar(Arena arena, PokemonEntity entidade, long agora) {
    Terreno chama = TERRENO_AO_ENTRAR.get(Habilidades.atual(entidade.getPokemon()));
    if (chama != null && arena.campo().getTerreno() != chama) {
      CobblemonCombatZA.LOGGER.info("CAMPO: {} entrou com {}", entidade.getPokemon().getSpecies().getName(),
          Habilidades.atual(entidade.getPokemon()));
      chamarTerreno(arena, entidade.getPokemon(), chama, agora);
    }
  }

  // Levou um golpe: a Semeadora chama o Campo de Plantas
  public static void depoisDoAcerto(Entity alvo, double pvTirados, long agora) {
    if (pvTirados <= 0 || !(alvo instanceof PokemonEntity entidade) || !entidade.isAlive()
        || !Habilidades.tem(entidade.getPokemon(), SEMEADORA)) {
      return;
    }
    Arena arena = Arenas.de(entidade.getPokemon());
    if (arena != null && arena.campo().getTerreno() != Terreno.PLANTAS) {
      chamarTerreno(arena, entidade.getPokemon(), Terreno.PLANTAS, agora);
    }
  }

  // O poder (Dano): o tipo do campo ×1,3 em quem ataca do chão; Terremoto no Plantas e Dragão no Enevoado, metade.
  // "defensor" null = um monstro ou você
  public static double poder(Pokemon atacante, Pokemon defensor, Move golpe) {
    Terreno terreno = terreno(atacante);
    if (terreno == Terreno.NENHUM) {
      terreno = terreno(defensor);
    }
    ElementalType tipo = ClimaNosGolpes.tipo(atacante, golpe);
    double multiplicador = 1.0;
    if (terreno.fortalece() != null && terreno.fortalece() == tipo && noChao(atacante)) {
      multiplicador *= FORTE_NO_CAMPO;
    }
    if (terreno == Terreno.PLANTAS && TREMORES.contains(golpe.getName()) && noChao(defensor)
        || terreno == Terreno.ENEVOADO && tipo == ElementalTypes.DRAGON && noChao(defensor)) {
      multiplicador *= METADE;
    }
    return multiplicador;
  }

  // O dano final (Dano): Refletir, Tela de Luz e Véu de Aurora do lado de quem apanha (o crítico passa)
  public static double barreira(Pokemon defensor, boolean fisico, boolean critico) {
    Arena arena = Arenas.de(defensor);
    // 21/10 (habilidades, lote da conta do dano): o golpe de quem tem Infiltrado passa pelas barreiras
    if (critico || arena == null || QuebraMolde.infiltrando()) {
      return 1.0;
    }
    return protecao(arena, doSeuLado(defensor), fisico);
  }

  // P5-10: o golpe de um monstro no seu Pokémon (DefesaDoPokemon): a magia é "especial" (Tela de Luz), o resto é "físico"
  // (Refletir); o Véu de Aurora, os dois
  public static double contraMonstro(Pokemon vitima, DamageSource fonte) {
    Arena arena = Arenas.de(vitima);
    return arena == null ? 1.0 : protecao(arena, true, !fonte.is(DamageTypeTags.WITCH_RESISTANT_TO));
  }

  private static double protecao(Arena arena, boolean lado, boolean fisico) {
    Map<Barreira, CampoDaBatalha.Prazo> barreiras = arena.campo().barreiras(lado);
    boolean protegido = barreiras.containsKey(fisico ? Barreira.REFLETIR : Barreira.TELA_DE_LUZ)
        || barreiras.containsKey(Barreira.VEU_DE_AURORA);
    if (!protegido) {
      return 1.0;
    }
    long doLado = arena.pokemons().stream().filter(e -> doSeuLado(e.getPokemon()) == lado).count();
    return doLado > 1 ? TELA_EM_MAIS_DE_UM : METADE;
  }

  public static boolean semCritico(Pokemon defensor) {
    return temBarreira(defensor, Barreira.CANTICO_DA_SORTE);
  }

  // O Vento de Cauda: Velocidade ×2 (na recarga e na carga)
  public static double velocidade(Pokemon pokemon) {
    return temBarreira(pokemon, Barreira.VENTO_DE_CAUDA) ? VENTO_DE_CAUDA : 1.0;
  }

  // O Campo Psíquico: o golpe rápido de um inimigo não pega em quem está no chão (true = bloqueou, já avisou)
  public static boolean bloqueiaPrioridade(Execucao execucao, Entity alvo) {
    // 21/10 (habilidades, lote das reações): a prioridade que vem da habilidade (Asas de Vendaval, Triagem) também conta
    boolean rapido = execucao.getGolpe().getTemplate().getPriority() > 0
        || HabilidadesDePrioridade.ganhaPrioridade(execucao.getPokemon(), execucao.getGolpe());
    if (!rapido || !(alvo instanceof PokemonEntity entidade)) {
      return false;
    }
    Pokemon pokemon = entidade.getPokemon();
    if (terreno(pokemon) != Terreno.PSIQUICO || !noChao(pokemon)
        || doSeuLado(pokemon) == doSeuLado(execucao.getPokemon())) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("CAMPO PSÍQUICO: {} protegido de {}", pokemon.getSpecies().getName(),
        execucao.getGolpe().getName());
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon_combat_za.campo.protegido_psiquico", pokemon.getDisplayName(false)));
    return true;
  }

  // Por que o status não pega (null = pega). "execucao" null = ele mesmo (o Descansar)
  public static Component bloqueiaStatus(Execucao execucao, Pokemon pokemon, String sigla) {
    Terreno terreno = terreno(pokemon);
    if (terreno == Terreno.ENEVOADO && noChao(pokemon)) {
      return Component.translatable("cobblemon_combat_za.campo.protegido_enevoado");
    }
    if (terreno == Terreno.ELETRICO && noChao(pokemon) && "slp".equals(sigla)) {
      return Component.translatable("cobblemon_combat_za.campo.sem_sono");
    }
    return salvaguarda(execucao, pokemon) ? Component.translatable("cobblemon_combat_za.campo.protegido_salvaguarda") : null;
  }

  // A confusão: o Campo Enevoado e a Salvaguarda também protegem
  public static Component bloqueiaConfusao(Execucao execucao, Pokemon pokemon) {
    if (terreno(pokemon) == Terreno.ENEVOADO && noChao(pokemon)) {
      return Component.translatable("cobblemon_combat_za.campo.protegido_enevoado");
    }
    return salvaguarda(execucao, pokemon) ? Component.translatable("cobblemon_combat_za.campo.protegido_salvaguarda") : null;
  }

  // A Salvaguarda segura o que vem do outro lado; 21/10: o golpe de quem tem Infiltrado passa
  private static boolean salvaguarda(Execucao execucao, Pokemon pokemon) {
    return veioDoOutroLado(execucao, pokemon) && !QuebraMolde.infiltra(execucao)
        && temBarreira(pokemon, Barreira.SALVAGUARDA);
  }

  // A Névoa: a queda de atributo vinda de outro não pega
  public static boolean bloqueiaQueda(Pokemon pokemon) {
    return temBarreira(pokemon, Barreira.NEVOA);
  }

  private static boolean veioDoOutroLado(Execucao execucao, Pokemon pokemon) {
    return execucao != null && execucao.getPokemon() != pokemon;
  }

  // Rente ao chão, em volta de quem está na arena
  private static void particulas(ServerLevel nivel, Terreno terreno, Entity onde, int quantidade) {
    ParticleOptions tipo = switch (terreno) {
      case ELETRICO -> ParticleTypes.ELECTRIC_SPARK;
      case PLANTAS -> ParticleTypes.HAPPY_VILLAGER;
      case ENEVOADO -> NEVOA_ROSA;
      case PSIQUICO -> ParticleTypes.WITCH;
      default -> ParticleTypes.END_ROD;
    };
    nivel.sendParticles(tipo, onde.getX(), onde.getY() + 0.2, onde.getZ(), quantidade, ESPALHAR, 0.1, ESPALHAR, 0.0);
  }
}
