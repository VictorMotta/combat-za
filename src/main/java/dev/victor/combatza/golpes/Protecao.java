package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaDupla;
import dev.victor.combatza.habilidades.HabilidadesDeProtecao;
import dev.victor.combatza.habilidades.HabilidadesDeEstagio;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 01/11 (a Proteção em tempo real, decisão dele: "escudo curto"): Proteger, Detectar e os parecidos viram um ESCUDO de 1,5 s
// que bloqueia todo golpe que nos jogos a Proteção segura (a marca "protect" do Showdown: os de status em si mesmo e alguns
// outros passam). Usar seguido, sem outro golpe no meio, tem cada vez menos chance, como nos jogos: 1/3, depois 1/9...
// Os escudos que castigam quem encosta: Escudo Espinhoso (1/8 da vida), Abrigo Venenoso (envenena), Escudo do Rei (Ataque
// −1), Obstruir (Defesa −2), Armadilha de Seda (Velocidade −1) e Baluarte Ardente (queima). O Punho Invisível atravessa o
// escudo com golpes de contato
public final class Protecao {
  private static final int TICKS_DO_ESCUDO = 30;
  private static final double CHANCE_SEGUIDA = 1.0 / 3;
  private static final double ESPINHOS = 1.0 / 8;
  private static final int PARTICULAS = 12;

  private enum Tipo {
    SIMPLES, ESPINHOS, VENENO, REI, OBSTRUIR, SEDA, BALUARTE
  }

  private static final Map<String, Tipo> GOLPES = Map.of("protect", Tipo.SIMPLES, "detect", Tipo.SIMPLES, "maxguard",
      Tipo.SIMPLES, "spikyshield", Tipo.ESPINHOS, "banefulbunker", Tipo.VENENO, "kingsshield", Tipo.REI, "obstruct",
      Tipo.OBSTRUIR, "silktrap", Tipo.SEDA, "burningbulwark", Tipo.BALUARTE);

  private record Escudo(Tipo tipo, Move golpe, long ateOTick) {
  }

  // 01/11: os escudos do LADO (quem usou e os aliados): Guarda Ampla (golpes de área), Guarda Rápida (golpes rápidos), Escudo
  // Astuto (golpes de status de outro) e Esteira Bloqueio (golpes de dano). Não caem com o uso seguido, como na 6ª geração
  private enum DoLado {
    AMPLA, RAPIDA, ASTUTA, ESTEIRA
  }

  private static final Map<String, DoLado> GOLPES_DO_LADO = Map.of("wideguard", DoLado.AMPLA, "quickguard", DoLado.RAPIDA,
      "craftyshield", DoLado.ASTUTA, "matblock", DoLado.ESTEIRA);

  private record EscudoDoLado(DoLado tipo, String golpe, long ateOTick) {
  }

  private static final Map<UUID, Escudo> ESCUDOS = new HashMap<>();
  private static final Map<UUID, EscudoDoLado> DO_LADO = new HashMap<>();
  // 01/11: a Resistência (Endure): na janela, o golpe que derrubaria deixa 1 PV (até quando, por Pokémon)
  private static final Map<UUID, Long> RESISTENTES = new HashMap<>();
  private static final String RESISTENCIA = "endure";
  // Quantas Proteções seguidas cada Pokémon usou (com sucesso), sem outro golpe no meio
  private static final Map<UUID, Integer> SEGUIDAS = new HashMap<>();

  private Protecao() {
  }

  public static boolean ehProtecao(Move golpe) {
    return GOLPES.containsKey(golpe.getName()) || RESISTENCIA.equals(golpe.getName());
  }

  // Execucao: começou um golpe que não é de Proteção; a próxima Proteção volta a ter chance cheia
  public static void comecouGolpe(Pokemon pokemon, Move golpe) {
    if (!ehProtecao(golpe)) {
      SEGUIDAS.remove(pokemon.getUuid());
    }
  }

  // ControleDeGolpes, no impacto do golpe em si mesmo: true = era uma Proteção (subiu o escudo, ou falhou)
  public static boolean usou(Execucao execucao, PokemonEntity atacante) {
    Move golpe = execucao.getGolpe();
    Tipo tipo = GOLPES.get(golpe.getName());
    if (GOLPES_DO_LADO.containsKey(golpe.getName())) {
      levantarNoLado(execucao, atacante, GOLPES_DO_LADO.get(golpe.getName()));
      return true;
    }
    boolean resistencia = RESISTENCIA.equals(golpe.getName());
    if (tipo == null && !resistencia) {
      return false;
    }
    Pokemon pokemon = execucao.getPokemon();
    int seguidas = SEGUIDAS.getOrDefault(pokemon.getUuid(), 0);
    if (atacante.getRandom().nextDouble() >= Math.pow(CHANCE_SEGUIDA, seguidas)) {
      SEGUIDAS.remove(pokemon.getUuid());
      CobblemonCombatZA.LOGGER.info("PROTEÇÃO: {} falhou ({} seguidas)", pokemon.getSpecies().getName(), seguidas);
      Feedback.historico(execucao.getJogador(), pokemon, Component.translatable("cobblemon_combat_za.protecao.falhou",
          pokemon.getDisplayName(false), golpe.getDisplayName()));
      return true;
    }
    SEGUIDAS.put(pokemon.getUuid(), seguidas + 1);
    long agora = atacante.getServer() == null ? 0 : atacante.getServer().getTickCount();
    if (resistencia) {
      RESISTENTES.put(pokemon.getUuid(), agora + TICKS_DO_ESCUDO);
      CobblemonCombatZA.LOGGER.info("RESISTÊNCIA: {} aguenta qualquer golpe por {} ticks", pokemon.getSpecies().getName(),
          TICKS_DO_ESCUDO);
      Feedback.historico(execucao.getJogador(), pokemon,
          Component.translatable("cobblemon.battle.singleturn.endure", pokemon.getDisplayName(false)));
      return true;
    }
    ESCUDOS.put(pokemon.getUuid(), new Escudo(tipo, golpe, agora + TICKS_DO_ESCUDO));
    Feedback.particulas(atacante, ParticleTypes.END_ROD, PARTICULAS);
    CobblemonCombatZA.LOGGER.info("PROTEÇÃO: {} levantou {} por {} ticks", pokemon.getSpecies().getName(), golpe.getName(),
        TICKS_DO_ESCUDO);
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.singleturn.protect", pokemon.getDisplayName(false)));
    return true;
  }

  private static void levantarNoLado(Execucao execucao, PokemonEntity atacante, DoLado tipo) {
    Pokemon pokemon = execucao.getPokemon();
    long agora = atacante.getServer() == null ? 0 : atacante.getServer().getTickCount();
    String golpe = execucao.getGolpe().getName();
    DO_LADO.put(pokemon.getUuid(), new EscudoDoLado(tipo, golpe, agora + TICKS_DO_ESCUDO));
    for (Pokemon aliado : HabilidadesDaDupla.aliados(pokemon)) {
      DO_LADO.put(aliado.getUuid(), new EscudoDoLado(tipo, golpe, agora + TICKS_DO_ESCUDO));
    }
    Feedback.particulas(atacante, ParticleTypes.END_ROD, PARTICULAS);
    CobblemonCombatZA.LOGGER.info("PROTEÇÃO DO LADO: {} levantou {}", pokemon.getSpecies().getName(), golpe);
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.singleturn." + golpe, pokemon.getDisplayName(false)));
  }

  // O escudo do lado segura este golpe?
  private static boolean doLadoSegura(Execucao execucao, PokemonEntity atacante, PokemonEntity protegido, long agora) {
    EscudoDoLado escudo = DO_LADO.get(protegido.getPokemon().getUuid());
    if (escudo == null || agora >= escudo.ateOTick() || Lados.mesmoLado(execucao.getPokemon(), protegido.getPokemon())) {
      return false;
    }
    Move golpe = execucao.getGolpe();
    boolean status = golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
    boolean segura = switch (escudo.tipo()) {
      case AMPLA -> FormaDoGolpe.de(golpe) == FormaDoGolpe.AREA_INIMIGOS || FormaDoGolpe.de(golpe) == FormaDoGolpe.AREA_TODOS;
      case RAPIDA -> HabilidadesDeProtecao.temPrioridade(execucao.getPokemon(), golpe);
      case ASTUTA -> status;
      case ESTEIRA -> !status && EfeitosDosGolpes.temMarca(golpe, "protect");
    };
    if (segura) {
      Pokemon pokemon = protegido.getPokemon();
      CobblemonCombatZA.LOGGER.info("PROTEÇÃO DO LADO: {} segurou {} ({})", pokemon.getSpecies().getName(),
          golpe.getName(), escudo.golpe());
      Feedback.particulas(protegido, ParticleTypes.END_ROD, PARTICULAS);
      Feedback.avisoNoAlvo(protegido, "protegeu");
      Feedback.historico(execucao.getJogador(), pokemon, Component.translatable("cobblemon.battle.activate."
          + escudo.golpe(), pokemon.getDisplayName(false), "", execucao.getPokemon().getDisplayName(false)));
    }
    return segura;
  }

  // ControleDeGolpes.acertar, antes do dano: na janela da Resistência, o golpe que derrubaria deixa 1 PV
  public static float resistiu(Entity alvo, float dano) {
    if (RESISTENTES.isEmpty() || !(alvo instanceof PokemonEntity entidade)) {
      return dano;
    }
    Long ate = RESISTENTES.get(entidade.getPokemon().getUuid());
    long agora = entidade.getServer() == null ? 0 : entidade.getServer().getTickCount();
    float sobra = entidade.getHealth() - Vida.naVida(entidade, 1);
    if (ate == null || agora >= ate || dano < entidade.getHealth() || sobra <= 0) {
      return dano;
    }
    Pokemon pokemon = entidade.getPokemon();
    CobblemonCombatZA.LOGGER.info("RESISTÊNCIA: {} ficou com 1 PV", pokemon.getSpecies().getName());
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.activate.endure", pokemon.getDisplayName(false)));
    return sobra;
  }

  // ControleDeGolpes, antes do acerto: o alvo está com o escudo e o golpe é dos que a Proteção segura. true = bloqueou
  public static boolean bloqueou(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (ESCUDOS.isEmpty() && DO_LADO.isEmpty() || !(alvo instanceof PokemonEntity protegido) || protegido == atacante) {
      return false;
    }
    if (!DO_LADO.isEmpty() && atacante.getServer() != null
        && doLadoSegura(execucao, atacante, protegido, atacante.getServer().getTickCount())) {
      return true;
    }
    Pokemon pokemon = protegido.getPokemon();
    Escudo escudo = ESCUDOS.get(pokemon.getUuid());
    long agora = atacante.getServer() == null ? 0 : atacante.getServer().getTickCount();
    Move golpe = execucao.getGolpe();
    if (escudo == null || agora >= escudo.ateOTick() || !EfeitosDosGolpes.temMarca(golpe, "protect")) {
      return false;
    }
    boolean contato = EfeitosDosGolpes.fazContato(golpe);
    // O Punho Invisível: o golpe de contato atravessa
    if (contato && Habilidades.tem(execucao.getPokemon(), "unseenfist")) {
      CobblemonCombatZA.LOGGER.info("PUNHO INVISÍVEL: {} atravessou a proteção de {}",
          execucao.getPokemon().getSpecies().getName(), pokemon.getSpecies().getName());
      return false;
    }
    CobblemonCombatZA.LOGGER.info("PROTEÇÃO: {} segurou {} de {}", pokemon.getSpecies().getName(), golpe.getName(),
        execucao.getPokemon().getSpecies().getName());
    Feedback.particulas(protegido, ParticleTypes.END_ROD, PARTICULAS);
    Feedback.avisoNoAlvo(protegido, "protegeu");
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.activate.protect", pokemon.getDisplayName(false)));
    if (contato) {
      castigar(execucao, escudo, atacante, pokemon);
    }
    return true;
  }

  private static void castigar(Execucao execucao, Escudo escudo, PokemonEntity atacante, Pokemon protegido) {
    Pokemon quemBateu = atacante.getPokemon();
    Component deOnde = escudo.golpe().getDisplayName();
    switch (escudo.tipo()) {
      case ESPINHOS -> Vida.ferir(atacante, quemBateu.getMaxHealth() * ESPINHOS);
      case VENENO -> AplicarStatus.pelaHabilidade(execucao, atacante, "psn", deOnde);
      case BALUARTE -> AplicarStatus.pelaHabilidade(execucao, atacante, "brn", deOnde);
      case REI -> HabilidadesDeEstagio.baixar(protegido, quemBateu, Map.of("atk", -1), false, deOnde);
      case OBSTRUIR -> HabilidadesDeEstagio.baixar(protegido, quemBateu, Map.of("def", -2), false, deOnde);
      case SEDA -> HabilidadesDeEstagio.baixar(protegido, quemBateu, Map.of("spe", -1), false, deOnde);
      default -> {
        // a Proteção simples só segura
      }
    }
  }

  // A cada segundo, os escudos que acabaram saem
  public static void atualizar(long agora) {
    if (agora % SharedConstants.TICKS_PER_SECOND == 0) {
      ESCUDOS.values().removeIf(escudo -> agora >= escudo.ateOTick());
      DO_LADO.values().removeIf(escudo -> agora >= escudo.ateOTick());
      RESISTENTES.values().removeIf(ate -> agora >= ate);
    }
  }

  public static void esquecer(Pokemon pokemon) {
    ESCUDOS.remove(pokemon.getUuid());
    SEGUIDAS.remove(pokemon.getUuid());
    DO_LADO.remove(pokemon.getUuid());
    RESISTENTES.remove(pokemon.getUuid());
  }

  public static void limpar() {
    ESCUDOS.clear();
    SEGUIDAS.clear();
    DO_LADO.clear();
    RESISTENTES.clear();
  }
}
