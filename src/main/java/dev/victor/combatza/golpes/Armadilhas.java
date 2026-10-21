package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.TiposNaBatalha;
import dev.victor.combatza.itens.ItensDoCampo;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 5.6c (escolhas dele: "zona no chão, ao entrar", "camadas como nos jogos"): Espinhos, Espinhos Tóxicos, Pedra Oculta e
// Teia Pegajosa deixam a armadilha onde você mira: um círculo de 3 blocos no chão, que dura até a batalha acabar (fora de
// batalha, 30 segundos). Cada inimigo que ENTRA leva uma vez, e só de novo depois de 5 segundos: Espinhos tiram 1/8, 1/6 ou
// 1/4 da vida (1, 2 ou 3 camadas); a Pedra Oculta, 1/8 vezes a fraqueza a Pedra; os Espinhos Tóxicos envenenam (2 camadas:
// veneno forte); a Teia baixa a Velocidade. Usar de novo no mesmo lugar empilha (Espinhos até 3, Tóxicos até 2; Pedra e
// Teia só renovam). Como nos jogos: quem voa (tipo Voador ou Levitação) passa por cima dos Espinhos, dos Tóxicos e da Teia,
// mas não da Pedra; um Pokémon Venenoso que pisa nos Tóxicos recolhe a armadilha. O Gume Incessante e o Machado de Pedra
// deixam Espinhos e Pedra Oculta embaixo de quem acertam
public class Armadilhas {
  public static final double RAIO = 3.0;
  private static final double ALTURA = 2.0;
  private static final long SEGUNDOS_FORA_DE_BATALHA = 30;
  private static final long SEGUNDOS_PARA_PISAR_DE_NOVO = 5;
  private static final int CONFERIR_A_CADA_TICKS = 5;
  private static final int MOSTRAR_A_CADA_TICKS = 10;
  private static final int PONTOS_DA_BORDA = 16;
  private static final int PARTICULAS_POR_CAMADA = 3;
  private static final int MAXIMO_POR_JOGADOR = 6;
  private static final double[] ESPINHOS_POR_CAMADA = { 1.0 / 8, 1.0 / 6, 1.0 / 4 };
  private static final String FRASE = "cobblemon_combat_za.armadilha.";
  private static final String LEVITACAO = "levitate";
  // 21/10 (golpes de status que faltavam): até onde o Giro Rápido, o Descondensar e o Arrumar recolhem armadilhas
  private static final double RAIO_DA_LIMPEZA = 16.0;

  // Uma armadilha recolhida: o golpe que a fez (spikes, toxicspikes, stealthrock, stickyweb) e se foi um selvagem que pôs
  // (a armadilha do selvagem pega no lado dos jogadores)
  public record Recolhida(String golpe, boolean doSelvagem) {
  }

  private enum Tipo {
    ESPINHOS(3), TOXICOS(2), PEDRA(1), TEIA(1);

    private final int camadasMaximas;

    Tipo(int camadasMaximas) {
      this.camadasMaximas = camadasMaximas;
    }
  }

  private static final Map<String, Tipo> DO_GOLPE = Map.of("spikes", Tipo.ESPINHOS, "toxicspikes", Tipo.TOXICOS,
      "stealthrock", Tipo.PEDRA, "stickyweb", Tipo.TEIA);
  // Showdown, onAfterHit: golpes de dano que deixam uma armadilha embaixo de quem acertam
  private static final Map<String, Tipo> DEPOIS_DO_ACERTO = Map.of("ceaselessedge", Tipo.ESPINHOS,
      "stoneaxe", Tipo.PEDRA);

  private static final class Armadilha {
    private final Execucao execucao;
    private final ServerPlayer jogador;
    private final ServerLevel nivel;
    private final Tipo tipo;
    private final Vec3 centro;
    private int camadas = 1;
    private boolean deBatalha;
    private long expiraNoTick;
    private Set<UUID> dentro = new HashSet<>();
    private final Map<UUID, Long> podePisarDeNovo = new HashMap<>();

    private Armadilha(Execucao execucao, ServerLevel nivel, Tipo tipo, Vec3 centro, long agora) {
      this.execucao = execucao;
      this.jogador = execucao.getJogador();
      this.nivel = nivel;
      this.tipo = tipo;
      this.centro = centro;
      renovar(agora);
    }

    // Em batalha, dura até ela acabar; fora, 30 segundos a partir de agora
    private void renovar(long agora) {
      deBatalha = GerenciarCombates.estaEmCombate(jogador);
      expiraNoTick = agora + SEGUNDOS_FORA_DE_BATALHA * SharedConstants.TICKS_PER_SECOND;
    }

    private Move golpe() {
      return execucao.getGolpe();
    }
  }

  private static final List<Armadilha> ARMADILHAS = new ArrayList<>();

  private Armadilhas() {
  }

  // O golpe de armadilha (forma ARMADILHA): no chão, embaixo de onde a mira está
  public static void colocar(Execucao execucao, PokemonEntity atacante) {
    Tipo tipo = DO_GOLPE.get(execucao.getGolpe().getName());
    if (tipo != null && atacante.level() instanceof ServerLevel nivel) {
      colocar(execucao, nivel, tipo, noChao(nivel, execucao.getMirada().getLocation()));
    }
  }

  // Gume Incessante e Machado de Pedra: embaixo de quem foi acertado
  public static void depoisDoAcerto(Execucao execucao, Entity alvo) {
    Tipo tipo = DEPOIS_DO_ACERTO.get(execucao.getGolpe().getName());
    if (tipo != null && alvo.level() instanceof ServerLevel nivel) {
      colocar(execucao, nivel, tipo, noChao(nivel, alvo.position()));
    }
  }

  // 21/10 (habilidades, lote das reações): o Detrito Tóxico espalha Espinhos Tóxicos embaixo de quem bateu. "daHabilidade" é
  // uma Execucao de Espinhos Tóxicos do dono da habilidade (HabilidadesQueReagem), para a armadilha ser do lado dele
  public static void toxicosEmbaixo(Execucao daHabilidade, Entity quem) {
    if (quem.level() instanceof ServerLevel nivel) {
      colocar(daHabilidade, nivel, Tipo.TOXICOS, noChao(nivel, quem.position()));
    }
  }

  public static void atualizar(long agora) {
    for (Armadilha armadilha : List.copyOf(ARMADILHAS)) {
      boolean acabou = armadilha.deBatalha ? !GerenciarCombates.estaEmCombate(armadilha.jogador)
          : agora > armadilha.expiraNoTick;
      if (acabou || armadilha.jogador.isRemoved()) {
        ARMADILHAS.remove(armadilha);
        continue;
      }
      if (agora % MOSTRAR_A_CADA_TICKS == 0) {
        mostrar(armadilha);
      }
      if (agora % CONFERIR_A_CADA_TICKS == 0) {
        conferir(armadilha, agora);
      }
    }
  }

  public static void limpar() {
    ARMADILHAS.clear();
  }

  // 21/10 (golpes de status que faltavam): o Giro Rápido e a Rotação Mortal recolhem as armadilhas que pegam em quem girou (as
  // do outro lado); o Descondensar e o Arrumar, todas. Nos jogos, as armadilhas são "do lado"; aqui, as que estão no chão
  // até 16 blocos de quem usou, no mesmo mundo
  public static List<Recolhida> recolher(PokemonEntity quem, boolean soAsQuePegamNele) {
    List<Recolhida> recolhidas = new ArrayList<>();
    for (Armadilha armadilha : List.copyOf(ARMADILHAS)) {
      boolean doSelvagem = armadilha.execucao.getPokemon().isWild();
      if (armadilha.nivel != quem.level() || noChaoDistancia(armadilha.centro, quem.position()) > RAIO_DA_LIMPEZA
          || soAsQuePegamNele && Lados.mesmoLado(armadilha.execucao.getPokemon(), quem.getPokemon())) {
        continue;
      }
      ARMADILHAS.remove(armadilha);
      recolhidas.add(new Recolhida(armadilha.golpe().getName(), doSelvagem));
      CobblemonCombatZA.LOGGER.info("ARMADILHA: {} recolheu {} ({} camada(s))", quem.getPokemon().getSpecies().getName(),
          armadilha.golpe().getName(), armadilha.camadas);
    }
    return recolhidas;
  }

  // Do mesmo jogador, do mesmo tipo e no mesmo lugar: empilha uma camada. Senão, uma armadilha nova
  private static void colocar(Execucao execucao, ServerLevel nivel, Tipo tipo, Vec3 centro) {
    long agora = execucao.getJogador().server.getTickCount();
    for (Armadilha armadilha : ARMADILHAS) {
      // 21/10 (habilidades, lote das reações): só empilha na armadilha do mesmo lado (o Detrito Tóxico do selvagem cai perto
      // dos Espinhos Tóxicos do seu Pokémon, e não pode virar uma camada a mais dos seus)
      boolean mesmoLado = Lados.mesmoLado(armadilha.execucao.getPokemon(), execucao.getPokemon());
      if (armadilha.jogador == execucao.getJogador() && armadilha.tipo == tipo && armadilha.nivel == nivel && mesmoLado
          && noChaoDistancia(armadilha.centro, centro) <= RAIO) {
        armadilha.camadas = Math.min(tipo.camadasMaximas, armadilha.camadas + 1);
        armadilha.renovar(agora);
        avisarQueFicou(execucao, armadilha);
        return;
      }
    }
    if (ARMADILHAS.stream().filter(a -> a.jogador == execucao.getJogador()).count() >= MAXIMO_POR_JOGADOR) {
      ARMADILHAS.stream().filter(a -> a.jogador == execucao.getJogador()).findFirst().ifPresent(ARMADILHAS::remove);
    }
    Armadilha nova = new Armadilha(execucao, nivel, tipo, centro, agora);
    ARMADILHAS.add(nova);
    avisarQueFicou(execucao, nova);
  }

  private static void avisarQueFicou(Execucao execucao, Armadilha armadilha) {
    CobblemonCombatZA.LOGGER.info("ARMADILHA: {} deixou {} no chão ({} camada(s))",
        execucao.getPokemon().getSpecies().getName(), armadilha.golpe().getName(), armadilha.camadas);
    Component frase = armadilha.tipo.camadasMaximas > 1
        ? Component.translatable(FRASE + "ficou_camada", armadilha.golpe().getDisplayName(), armadilha.camadas)
        : Component.translatable(FRASE + "ficou", armadilha.golpe().getDisplayName());
    Feedback.historico(execucao.getJogador(), execucao.getPokemon(), frase);
  }

  // Quem está dentro agora e não estava na última conferida acabou de ENTRAR
  private static void conferir(Armadilha armadilha, long agora) {
    boolean emBatalha = GerenciarCombates.estaEmCombate(armadilha.jogador);
    // 7.2: a armadilha do selvagem pega o lado de cá (você e os seus Pokémon)
    PokemonEntity quemColocou = armadilha.execucao.getEntidade();
    AABB caixa = new AABB(armadilha.centro, armadilha.centro).inflate(RAIO, ALTURA, RAIO);
    Set<UUID> dentroAgora = new HashSet<>();
    for (Entity entidade : armadilha.nivel.getEntities((Entity) null, caixa,
        e -> e instanceof LivingEntity && e.isAlive() && Lados.ehInimigoDe(quemColocou, e)
            && Lados.entraNaLutaDe(quemColocou, e, emBatalha))) {
      if (noChaoDistancia(entidade.position(), armadilha.centro) > RAIO) {
        continue;
      }
      dentroAgora.add(entidade.getUUID());
      boolean entrou = !armadilha.dentro.contains(entidade.getUUID());
      if (entrou && agora >= armadilha.podePisarDeNovo.getOrDefault(entidade.getUUID(), 0L)) {
        armadilha.podePisarDeNovo.put(entidade.getUUID(), agora + SEGUNDOS_PARA_PISAR_DE_NOVO
            * SharedConstants.TICKS_PER_SECOND);
        pisou(armadilha, (LivingEntity) entidade);
        if (!ARMADILHAS.contains(armadilha)) {
          return;
        }
      }
    }
    armadilha.dentro = dentroAgora;
  }

  private static void pisou(Armadilha armadilha, LivingEntity vitima) {
    Pokemon pokemon = vitima instanceof PokemonEntity entidade ? entidade.getPokemon() : null;
    if (pokemon != null && armadilha.tipo != Tipo.PEDRA && voa(pokemon)) {
      return;
    }
    // Lote 3 dos itens: as Botas Grossas
    if (pokemon != null && ItensDoCampo.botas(pokemon)) {
      CobblemonCombatZA.LOGGER.info("ARMADILHA: {} pisou em {} de Botas Grossas", pokemon.getSpecies().getName(),
          armadilha.golpe().getName());
      return;
    }
    if (vitima instanceof PokemonEntity entidade && pokemon.isWild()) {
      GerenciarCombates.entrarEmCombate(armadilha.jogador, entidade, null);
    }
    Feedback.historico(armadilha.jogador, vitima,
        Component.translatable(FRASE + "pisou", vitima.getName(), armadilha.golpe().getDisplayName()));
    Feedback.particulas(vitima, particula(armadilha.tipo), PONTOS_DA_BORDA);
    switch (armadilha.tipo) {
      case ESPINHOS -> ferir(armadilha, vitima, ESPINHOS_POR_CAMADA[armadilha.camadas - 1]);
      case PEDRA -> ferir(armadilha, vitima, fraquezaAPedra(pokemon) / 8);
      case TOXICOS -> {
        if (pokemon != null && AplicarStatus.temTipo(pokemon, ElementalTypes.POISON)) {
          ARMADILHAS.remove(armadilha);
          CobblemonCombatZA.LOGGER.info("ARMADILHA: {} recolheu os Espinhos Tóxicos", pokemon.getSpecies().getName());
          Feedback.historico(armadilha.jogador, vitima,
              Component.translatable(FRASE + "recolheu", vitima.getName(), armadilha.golpe().getDisplayName()));
          return;
        }
        AplicarStatus.tentar(armadilha.execucao, vitima, armadilha.camadas >= 2 ? "tox" : "psn");
      }
      case TEIA -> AplicarEfeitos.mudarUmAtributo(armadilha.execucao, vitima, "spe", -1);
    }
    CobblemonCombatZA.LOGGER.info("ARMADILHA: {} pisou em {} ({} camada(s))", vitima.getName().getString(),
        armadilha.golpe().getName(), armadilha.camadas);
  }

  // Uma fração da vida máxima, com a fonte do status (sem atacante, como a queimadura)
  private static void ferir(Armadilha armadilha, LivingEntity vitima, double fracao) {
    float dano = (float) (vitima.getMaxHealth() * fracao);
    vitima.hurt(EfeitosDeStatus.fonteDoStatus(vitima), dano);
  }

  // Showdown, Pedra Oculta: a fração vezes a efetividade de Pedra nos tipos dele (de 1/4 a 4). Monstro: normal
  private static double fraquezaAPedra(Pokemon pokemon) {
    if (pokemon == null) {
      return 1.0;
    }
    // 21/10 (lote das formas): os tipos de agora (TiposNaBatalha)
    double multiplicador = AIUtility.INSTANCE.getDamageMultiplier(ElementalTypes.ROCK, TiposNaBatalha.primario(pokemon));
    ElementalType segundo = TiposNaBatalha.secundario(pokemon);
    if (segundo != null) {
      multiplicador *= AIUtility.INSTANCE.getDamageMultiplier(ElementalTypes.ROCK, segundo);
    }
    return multiplicador;
  }

  // 5b.3: também quem fica fora do campo (CampoEBarreiras): voa = não está "no chão"
  // Lote 3 dos itens: o Balão de Ar também flutua; a Bola de Ferro "aterra" (até o Voador)
  public static boolean voa(Pokemon pokemon) {
    if (ItensDoCampo.aterrado(pokemon)) {
      return false;
    }
    return AplicarStatus.temTipo(pokemon, ElementalTypes.FLYING) || Habilidades.tem(pokemon, LEVITACAO)
        || ItensDoCampo.noBalao(pokemon);
  }

  // A borda do círculo e umas partículas dentro, mais com mais camadas
  private static void mostrar(Armadilha armadilha) {
    ParticleOptions particula = particula(armadilha.tipo);
    for (int i = 0; i < PONTOS_DA_BORDA; i++) {
      double angulo = 2 * Math.PI * i / PONTOS_DA_BORDA;
      armadilha.nivel.sendParticles(particula, armadilha.centro.x + Math.cos(angulo) * RAIO, armadilha.centro.y + 0.1,
          armadilha.centro.z + Math.sin(angulo) * RAIO, 1, 0, 0, 0, 0);
    }
    armadilha.nivel.sendParticles(particula, armadilha.centro.x, armadilha.centro.y + 0.1, armadilha.centro.z,
        PARTICULAS_POR_CAMADA * armadilha.camadas, RAIO / 2, 0.05, RAIO / 2, 0);
  }

  private static ParticleOptions particula(Tipo tipo) {
    return switch (tipo) {
      case ESPINHOS -> ParticleTypes.CRIT;
      case TOXICOS -> ParticleTypes.WITCH;
      case PEDRA -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
      case TEIA -> new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
    };
  }

  // O chão embaixo do ponto (até 6 blocos para baixo); sem chão, o próprio ponto
  private static Vec3 noChao(ServerLevel nivel, Vec3 ponto) {
    return Mira.noChao(nivel, ponto);
  }

  // A distância no chão (sem a altura)
  private static double noChaoDistancia(Vec3 a, Vec3 b) {
    double dx = a.x - b.x;
    double dz = a.z - b.z;
    return Math.sqrt(dx * dx + dz * dz);
  }
}
