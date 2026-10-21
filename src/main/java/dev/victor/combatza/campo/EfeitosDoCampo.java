package dev.victor.combatza.campo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.Balanceamento;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaBatalha;
import dev.victor.combatza.habilidades.HabilidadesDeEntrada;
import dev.victor.combatza.itens.ItensDoCampo;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

// 5b.1 (parte 5 da sequência de 20/10; plano em aulas/planos/plano-5b-clima-campo-barreiras.md): o CLIMA.
// - O do mundo: chovendo em céu aberto onde o jogador está (na batalha, o primeiro dela) = Chuva; nevando (bioma frio) = Neve;
//   deserto, caverna, debaixo de teto = nada. Dura enquanto chover;
// - O do golpe: Dança da Chuva, Dia Ensolarado, Tempestade de Areia, Granizo/Paisagem Nevada (= Neve, como na Geração 9):
//   5 turnos (17,5 s), 8 (28 s) com a pedra certa (Úmida, Quente, Lisa, Gelada). Vence o do mundo enquanto dura. O mesmo
//   clima que já vale: "Mas nada aconteceu.";
// - O que muda (Showdown): Chuva = Água ×1,5 e Fogo ×0,5; Sol = o contrário; Areia = Pedra com Defesa Especial ×1,5 e 1/16
//   da vida a cada turno em quem não é Pedra, Terra ou Aço; Neve = Gelo com Defesa ×1,5.
// P5-10 (veto dele, 20/10: "fora de batalha pode adicionar também a mesma coisa de dentro"): tudo isso vale numa ARENA (Arena,
// Arenas): a batalha, ou o campo pessoal de cada jogador fora dela (os Pokémon dele fora da bola). Contra monstros e contra
// você, vale o lado de quem ataca (contraQuemNaoEPokemon)
public class EfeitosDoCampo {
  private static final double FORTE = 1.5;
  private static final double FRACO = 0.5;
  private static final int TURNOS = 5;
  private static final int TURNOS_COM_PEDRA = 8;
  private static final int AREIA_FERE = 16;
  private static final int PARTICULAS = 24;
  private static final double ESPALHAR = 6.0;
  private static final Map<String, Clima> DO_GOLPE = Map.of(
      "raindance", Clima.CHUVA, "sunnyday", Clima.SOL, "sandstorm", Clima.AREIA,
      "hail", Clima.NEVE, "snowscape", Clima.NEVE, "chillyreception", Clima.NEVE);
  private static final Map<Clima, String> PEDRA = Map.of(
      Clima.CHUVA, "damprock", Clima.SOL, "heatrock", Clima.AREIA, "smoothrock", Clima.NEVE, "icyrock");
  // Quem a areia não fere: as habilidades (Invólucro, Véu de Areia, Ímpeto de Areia, Força da Areia, Guarda Mágica) e os
  // Óculos de Proteção
  private static final Set<String> SEM_AREIA = Set.of("overcoat", "sandveil", "sandrush", "sandforce", "magicguard");
  private static final String OCULOS_DE_PROTECAO = "safetygoggles";
  private static final Set<String> ANULAM_O_CLIMA = Set.of("cloudnine", "airlock");
  private static final int CONFERIR_QUEM_ENTROU = 5;

  private EfeitosDoCampo() {
  }

  // O clima que vale para este Pokémon agora (o selvagem fora da batalha: nenhum)
  public static Clima clima(Pokemon pokemon) {
    Arena arena = Arenas.de(pokemon);
    // Lote 3 dos itens: com o Guarda-chuva Útil, o Sol e a Chuva não valem para ele
    return arena == null ? Clima.NENHUM : ItensDoCampo.comGuardaChuva(pokemon, climaQueVale(arena));
  }

  // 5b.2: com Climatização ou Bloqueio Aéreo na arena, o clima continua lá (no ícone) mas não faz nada
  public static Clima climaQueVale(Arena arena) {
    Clima clima = arena.campo().clima();
    if (clima == Clima.NENHUM) {
      return clima;
    }
    // 21/10 (habilidades, lote da entrada e das imunidades): com os ventos fortes da Correnteza Delta, nenhum clima vale
    if (ClimasFortes.ventosFortes(arena)) {
      return Clima.NENHUM;
    }
    for (PokemonEntity entidade : arena.pokemons()) {
      if (ANULAM_O_CLIMA.contains(Habilidades.atual(entidade.getPokemon()))) {
        return Clima.NENHUM;
      }
    }
    return clima;
  }

  // O clima entre quem ataca e quem apanha ("defensor" null = um monstro ou você)
  public static Clima climaEntre(Pokemon atacante, Pokemon defensor) {
    Clima doAtacante = clima(atacante);
    if (doAtacante != Clima.NENHUM || defensor == null) {
      return doAtacante;
    }
    // Lote 3 dos itens: o clima do lado de quem apanha também passa pelo Guarda-chuva de quem ataca
    return ItensDoCampo.comGuardaChuva(atacante, clima(defensor));
  }

  // O golpe é de clima? Então chama o clima (ou falha) e devolve true
  public static boolean aoUsarGolpe(Execucao execucao, PokemonEntity atacante) {
    Clima novo = DO_GOLPE.get(execucao.getGolpe().getName());
    if (novo == null) {
      return false;
    }
    if (!(atacante.level() instanceof ServerLevel nivel)) {
      return true;
    }
    particulas(nivel, novo, atacante.blockPosition(), PARTICULAS * 2);
    Arena arena = Arenas.de(execucao.getPokemon());
    if (arena != null) {
      chamarClima(arena, execucao.getPokemon(), novo, nivel.getServer().getTickCount());
    }
    return true;
  }

  // O golpe ou a habilidade (5b.2) chama o clima nesta arena
  public static void chamarClima(Arena arena, Pokemon quem, Clima novo, long agora) {
    // 21/10 (habilidades, lote da entrada e das imunidades): um clima forte (Terra Desolada...) não deixa outro entrar
    if (ClimasFortes.barra(arena, quem)) {
      return;
    }
    CampoDaBatalha campo = arena.campo();
    if (campo.clima() == novo) {
      CobblemonCombatZA.LOGGER.info("CLIMA: {} já vale, nada aconteceu", novo);
      arena.avisar(quem, Component.translatable("cobblemon.battle.nothing"));
      return;
    }
    int turnos = ItensSegurados.tem(quem, PEDRA.get(novo)) ? TURNOS_COM_PEDRA : TURNOS;
    long duracao = turnos * EfeitosDeStatus.ticksPorTurno();
    campo.chamarClima(novo, new CampoDaBatalha.Prazo(agora + duracao, duracao));
    CobblemonCombatZA.LOGGER.info("CLIMA: {} chamou {} por {} turnos ({})", quem.getSpecies().getName(), novo, turnos,
        arena.naBatalha() ? "na batalha" : "fora da batalha");
    arena.avisar(quem, novo.comecou());
  }

  // A cada tick do servidor, em cada arena: as batalhas e o campo pessoal de cada jogador fora delas
  public static void atualizar(MinecraftServer servidor, long agora) {
    for (Combate combate : List.copyOf(GerenciarCombates.todos())) {
      atualizar(Arenas.daBatalha(combate), combate.getJogadores().isEmpty() ? null : combate.getJogadores().get(0),
          agora);
    }
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      if (!GerenciarCombates.estaEmCombate(jogador)) {
        atualizar(Arenas.doJogador(jogador), jogador, agora);
      }
    }
  }

  // O fim do clima do golpe, o clima do mundo (a cada segundo, onde "quemLe" está), as partículas, o campo e as barreiras,
  // quem entrou, a areia e as habilidades (a cada turno de 3,5 s)
  private static void atualizar(Arena arena, ServerPlayer quemLe, long agora) {
    boolean virouSegundo = agora % SharedConstants.TICKS_PER_SECOND == 0;
    boolean virouTurno = agora % EfeitosDeStatus.ticksPorTurno() == 0;
    CampoDaBatalha campo = arena.campo();
    if (campo.getDoGolpe() != Clima.NENHUM && agora >= campo.getPrazoDoClima().fim()) {
      Clima acabou = campo.getDoGolpe();
      campo.acabarOClimaDoGolpe();
      CobblemonCombatZA.LOGGER.info("CLIMA: {} acabou (volta o do mundo: {})", acabou, campo.getDoMundo());
      arena.avisar(null, acabou.acabou());
    }
    if (virouSegundo && quemLe != null) {
      // A chuva do mundo muda toda hora (uma árvore em cima, um teto): sem cartão, o ícone da passiva mostra
      campo.lerOMundo(doMundo(quemLe));
      if (campo.getDoGolpe() != Clima.NENHUM) {
        for (ServerPlayer jogador : arena.jogadores()) {
          particulas(jogador.serverLevel(), campo.getDoGolpe(), jogador.blockPosition(), PARTICULAS);
        }
      }
    }
    CampoEBarreiras.atualizar(arena, agora, virouSegundo, virouTurno);
    if (agora % CONFERIR_QUEM_ENTROU == 0) {
      quemEntrou(arena, agora);
    }
    Clima vale = climaQueVale(arena);
    if (virouTurno && vale == Clima.AREIA) {
      areia(arena);
    }
    if (virouTurno && vale != Clima.NENHUM) {
      for (PokemonEntity entidade : arena.pokemons()) {
        if (entidade.isAlive() && !entidade.getPokemon().isFainted()) {
          ClimaNosGolpes.porTurno(arena, entidade, ItensDoCampo.comGuardaChuva(entidade.getPokemon(), vale));
        }
      }
    }
  }

  // 5b.2: quem acabou de chegar (o começo da batalha, o selvagem que entrou, o lutador trocado; fora da batalha, quem saiu
  // da bola) "entra"
  private static void quemEntrou(Arena arena, long agora) {
    Map<UUID, PokemonEntity> agoraNaArena = new HashMap<>();
    for (PokemonEntity entidade : arena.pokemons()) {
      agoraNaArena.put(entidade.getPokemon().getUuid(), entidade);
    }
    for (UUID chegou : arena.campo().chegaram(agoraNaArena.keySet())) {
      ClimaNosGolpes.aoEntrar(arena, agoraNaArena.get(chegou), agora);
      CampoEBarreiras.aoEntrar(arena, agoraNaArena.get(chegou), agora);
      // 21/10 (habilidades, lote da conta do dano): a Vigilância, o Senhor Supremo e as frases de entrada
      HabilidadesDaBatalha.aoEntrar(arena, agoraNaArena.get(chegou), agora);
      // 21/10 (habilidades, lote da entrada e das imunidades): os climas fortes, Download, Bisbilhotar, Limpa-Vidro...
      HabilidadesDeEntrada.aoEntrar(arena, agoraNaArena.get(chegou));
    }
  }

  // Chovendo, com o céu aberto em cima de você: a precipitação do bioma diz se é chuva ou neve (o deserto não tem nenhuma)
  private static Clima doMundo(ServerPlayer jogador) {
    ServerLevel nivel = jogador.serverLevel();
    BlockPos pos = jogador.blockPosition();
    if (!nivel.isRaining() || !nivel.canSeeSky(pos)
        || nivel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
      return Clima.NENHUM;
    }
    Biome.Precipitation chuva = nivel.getBiome(pos).value().getPrecipitationAt(pos);
    return switch (chuva) {
      case RAIN -> Clima.CHUVA;
      case SNOW -> Clima.NEVE;
      default -> Clima.NENHUM;
    };
  }

  // A areia fere 1/16 da vida a cada turno (como o veneno), menos Pedra, Terra e Aço
  private static void areia(Arena arena) {
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon pokemon = entidade.getPokemon();
      if (!entidade.isAlive() || pokemon.isFainted() || AplicarStatus.temTipo(pokemon, ElementalTypes.ROCK)
          || AplicarStatus.temTipo(pokemon, ElementalTypes.GROUND) || AplicarStatus.temTipo(pokemon, ElementalTypes.STEEL)
          || SEM_AREIA.contains(Habilidades.atual(pokemon)) || ItensSegurados.tem(pokemon, OCULOS_DE_PROTECAO)) {
        continue;
      }
      float dano = (float) (entidade.getMaxHealth() / AREIA_FERE * Balanceamento.MULTIPLICADOR_DANO_DE_STATUS);
      boolean pegou = entidade.hurt(EfeitosDeStatus.fonteDoStatus(entidade), dano);
      CobblemonCombatZA.LOGGER.info("AREIA: {} perdeu 1/16 da vida | aplicado? {}", pokemon.getSpecies().getName(), pegou);
    }
  }

  // O dano final (Dano.contraPokemon): a Chuva e o Sol no Fogo e na Água ("defensor" null = um monstro ou você)
  public static double danoFinal(Pokemon atacante, Pokemon defensor, Move golpe) {
    Clima clima = climaEntre(atacante, defensor);
    // Lote 3 dos itens: o Guarda-chuva Útil de qualquer um dos dois tira o Sol e a Chuva deste golpe
    if (ItensDoCampo.comGuardaChuva(atacante, clima) != clima
        || defensor != null && ItensDoCampo.comGuardaChuva(defensor, clima) != clima) {
      return 1.0;
    }
    ElementalType tipo = ClimaNosGolpes.tipo(atacante, golpe);
    if (clima == Clima.CHUVA) {
      return tipo == ElementalTypes.WATER ? FORTE : tipo == ElementalTypes.FIRE ? FRACO : 1.0;
    }
    if (clima == Clima.SOL) {
      return tipo == ElementalTypes.FIRE ? FORTE : tipo == ElementalTypes.WATER ? FRACO : 1.0;
    }
    return 1.0;
  }

  // P5-10: contra um monstro ou contra você, vale o lado de quem ataca: o clima no tipo do golpe, o poder com o clima (Esfera
  // Climática, Raio Solar, Força da Areia) e o campo (o tipo dele, em quem ataca do chão)
  public static double contraQuemNaoEPokemon(Pokemon atacante, Move golpe) {
    return danoFinal(atacante, null, golpe) * ClimaNosGolpes.poder(atacante, null, golpe)
        * CampoEBarreiras.poder(atacante, null, golpe);
  }

  // A defesa de quem apanha: Pedra na Areia (Defesa Especial) e Gelo na Neve (Defesa)
  public static double defesa(Pokemon defensor, boolean fisico) {
    Clima clima = clima(defensor);
    if (clima == Clima.AREIA && !fisico && AplicarStatus.temTipo(defensor, ElementalTypes.ROCK)) {
      return FORTE;
    }
    return clima == Clima.NEVE && fisico && AplicarStatus.temTipo(defensor, ElementalTypes.ICE) ? FORTE : 1.0;
  }

  private static void particulas(ServerLevel nivel, Clima clima, BlockPos onde, int quantidade) {
    ParticleOptions tipo = switch (clima) {
      case CHUVA -> ParticleTypes.FALLING_WATER;
      case SOL -> ParticleTypes.END_ROD;
      case AREIA -> new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
      case NEVE -> ParticleTypes.SNOWFLAKE;
      default -> null;
    };
    if (tipo != null) {
      int vezes = clima == Clima.SOL ? quantidade / 6 : quantidade;
      nivel.sendParticles(tipo, onde.getX() + 0.5, onde.getY() + 4.0, onde.getZ() + 0.5, vezes, ESPALHAR, 2.0,
          ESPALHAR, 0.0);
    }
  }
}
