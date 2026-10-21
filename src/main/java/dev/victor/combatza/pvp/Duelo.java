package dev.victor.combatza.pvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.AvisoDaBatalha;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.Experiencia;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.combate.SoltarNoSelvagem;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Mira;
import dev.victor.combatza.habilidades.Intimidacao;
import dev.victor.combatza.ia.Parceiro;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 21/10 (PvP; decisões dele: "começa pelo desafio de batalha do próprio Cobblemon", "quem desafia escolhe quantos Pokémon",
// "os golpes só pegam nos Pokémon", "com XP"): o duelo em tempo real entre dois jogadores. É uma batalha como a contra
// selvagem (Combate: o painel, o histórico, os itens com a espera, o clima e o campo, os itens segurados, as habilidades, a
// Mega), só que sem selvagem: do outro lado está o lutador do adversário. Quem cria é o Duelos, quando o desafio é aceito.
// - O time: os N escolhidos, o da frente no desafio do Cobblemon e os próximos com vida, na ordem do time. Só eles saem da
//   bola: os outros voltam no começo e não saem até o fim (Duelos.antesDeSoltar);
// - Cada um tem UM lutador (Lutadores) e troca com o R, como contra selvagem. Sem lutador por 15 s (desmaiou e ninguém
//   entrou, ou ele recolheu), o próximo do time entra sozinho: a troca forçada dos jogos, para ninguém enrolar;
// - Perde quem fica sem nenhum dos N. Desiste (perde também) quem se afasta mais de 24 blocos do meio do duelo (o aviso
//   vem a partir de 18), sai do jogo, morre ou muda de mundo;
// - No duelo o desmaio é só desmaio: nada de ficar caído no chão nem de captura (o Pokémon tem dono);
// - O XP: cada Pokémon do adversário que desmaia dá o XP de derrotar a quem o enfrentou (Experiencia.derrotadoNoDuelo)
public class Duelo extends Combate {
  public static final float DISTANCIA_PARA_DESISTIR = 24.0f;
  private static final float DISTANCIA_DO_AVISO = 18.0f;
  private static final int TICKS_ENTRE_AVISOS = 2 * SharedConstants.TICKS_PER_SECOND;
  public static final int SEGUNDOS_PARA_MANDAR = 15;
  // A troca forçada só conta depois da abertura (os dois da frente saindo da bola), ou, se um deles não sair, depois disto
  private static final int TICKS_DA_ABERTURA = 5 * SharedConstants.TICKS_PER_SECOND;
  // Onde o Pokémon sai da bola: na linha até o adversário, a até 3 blocos de você (metade do caminho, se ele está perto)
  private static final double DISTANCIA_DE_SOLTAR = 3.0;
  private static final double SOLTAR_MAIS_PERTO = 1.0;
  private static final double ANTES_DA_PAREDE = 0.5;
  private static final String FRASE = "cobblemon_combat_za.duelo.";

  // Por que o duelo acabou para um dos dois
  private enum Derrota {
    NENHUMA, DESISTIU, SEM_POKEMON
  }

  private final ServerPlayer desafiante;
  private final ServerPlayer desafiado;
  private final int quantos;
  // O time de cada jogador (UUIDs dos Pokémon), na ordem em que entram: o primeiro é o da frente
  private final Map<UUID, List<UUID>> times = new HashMap<>();
  private final Vec3 meio;
  private final ResourceKey<Level> mundo;
  // Para o XP: de cada jogador, quem do time dele enfrentou cada Pokémon do adversário (a chave é o UUID do Pokémon de lá)
  private final Map<UUID, Map<UUID, Set<UUID>>> enfrentaram = new HashMap<>();
  private final Map<UUID, Long> semLutadorDesde = new HashMap<>();
  private final Map<UUID, Long> ultimoAvisoDeLonge = new HashMap<>();
  // Os dois primeiros lutadores já estão frente a frente (aí vale a Intimidação do começo, dos dois lados de uma vez)
  private boolean abriu = false;
  private boolean acabou = false;

  public Duelo(ServerPlayer desafiante, ServerPlayer desafiado, int quantos, List<UUID> timeDoDesafiante,
      List<UUID> timeDoDesafiado) {
    super(desafiante.server.getTickCount());
    this.desafiante = desafiante;
    this.desafiado = desafiado;
    this.quantos = quantos;
    times.put(desafiante.getUUID(), List.copyOf(timeDoDesafiante));
    times.put(desafiado.getUUID(), List.copyOf(timeDoDesafiado));
    meio = desafiante.position().add(desafiado.position()).scale(0.5);
    mundo = desafiante.level().dimension();
    adicionarJogador(desafiante);
    adicionarJogador(desafiado);
  }

  public int getQuantos() {
    return quantos;
  }

  public ServerPlayer outro(ServerPlayer jogador) {
    return jogador == desafiante || jogador.getUUID().equals(desafiante.getUUID()) ? desafiado : desafiante;
  }

  // O lado do campo (as barreiras): true = o de quem desafiou
  public boolean ladoDe(ServerPlayer jogador) {
    return jogador.getUUID().equals(desafiante.getUUID());
  }

  public boolean ladoDe(Pokemon pokemon) {
    return desafiante.getUUID().equals(pokemon.getOwnerUUID());
  }

  // Um dos N escolhidos de um dos dois jogadores
  public boolean doTime(Pokemon pokemon) {
    UUID dono = pokemon.getOwnerUUID();
    List<UUID> time = dono == null ? null : times.get(dono);
    return time != null && time.contains(pokemon.getUuid());
  }

  // Um dos N escolhidos do adversário de "jogador"
  public boolean doAdversario(ServerPlayer jogador, Pokemon pokemon) {
    return doTime(pokemon) && outro(jogador).getUUID().equals(pokemon.getOwnerUUID());
  }

  // Do outro lado está o lutador do adversário. Antes dos dois estarem frente a frente, ninguém: a Intimidação de quem sai
  // da bola no começo espera e pega dos dois lados juntos (abrir)
  @Override
  public List<PokemonEntity> inimigosDe(ServerPlayer jogador) {
    Pokemon lutador = abriu ? Lutadores.de(outro(jogador)) : null;
    return lutador == null || lutador.getEntity() == null ? List.of() : List.of(lutador.getEntity());
  }

  // No duelo não entra selvagem (GerenciarCombates.entrarEmCombate já não deixa; isto é a garantia)
  @Override
  public boolean adicionarSelvagem(PokemonEntity selvagem) {
    return false;
  }

  @Override
  public boolean terminou() {
    return acabou;
  }

  // Duelos: deu errado ao montar (fica a batalha por turnos do Cobblemon). Sai da lista no próximo tick, com a limpeza de
  // sempre, sem aviso de fim
  public void desfazer() {
    acabou = true;
  }

  // Duelos, logo depois de criar: os de fora do time voltam para a bola, e o da frente sai (ou, se já está fora, vira o
  // lutador)
  public void comecar() {
    for (ServerPlayer jogador : List.of(desafiante, desafiado)) {
      Parceiro.desligar(jogador);
      Pokemon primeiro = primeiroComVida(jogador);
      for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
        PokemonEntity entidade = pokemon.getEntity();
        if (entidade != null && pokemon != primeiro) {
          entidade.recallWithAnimation();
        }
      }
      if (primeiro == null) {
        continue;
      }
      if (primeiro.getEntity() != null) {
        Lutadores.comecou(jogador, primeiro);
      } else {
        SoltarNoSelvagem.jogar(jogador, primeiro, ondeSoltar(jogador));
      }
    }
    CobblemonCombatZA.LOGGER.info("DUELO: {} x {}, {} Pokémon de cada lado", desafiante.getGameProfile().getName(),
        desafiado.getGameProfile().getName(), quantos);
  }

  // A cada tick (GerenciarCombates.atualizar): quem enfrentou quem (o XP), a abertura, quem perdeu ou desistiu, e a troca
  // forçada. No lugar da saída de jogadores da batalha contra selvagem
  @Override
  public void removerJogadoresForaDeCombate() {
    if (acabou) {
      return;
    }
    long agora = desafiante.server.getTickCount();
    anotarQuemEnfrentou();
    abrir();
    Derrota doDesafiante = derrota(desafiante, agora);
    Derrota doDesafiado = derrota(desafiado, agora);
    if (doDesafiante != Derrota.NENHUMA || doDesafiado != Derrota.NENHUMA) {
      terminar(doDesafiante, doDesafiado);
      return;
    }
    if (abriu || agora - getComecouNoTick() > TICKS_DA_ABERTURA) {
      trocaForcada(desafiante, agora);
      trocaForcada(desafiado, agora);
    }
  }

  // Um Pokémon do time desmaiou (Duelos.aoDesmaiar): o cartão para os dois, e o XP de derrotar para o outro lado, se o
  // Cobblemon dá XP no PvP (a opção allowExperienceFromPvP, ligada por padrão)
  public void desmaiou(ServerPlayer dono, Pokemon pokemon) {
    Feedback.historico(dono, pokemon, Component.translatable("cobblemon.battle.fainted", pokemon.getDisplayName(false)));
    ServerPlayer outro = outro(dono);
    if (acabou || outro.hasDisconnected() || !Cobblemon.INSTANCE.getConfig().getAllowExperienceFromPvP()) {
      return;
    }
    Set<UUID> quem = new HashSet<>(enfrentaram.getOrDefault(outro.getUUID(), Map.of())
        .getOrDefault(pokemon.getUuid(), Set.of()));
    // O golpe que derruba no mesmo tick em que o lutador de lá entrou ainda não foi anotado
    Pokemon lutadorDeLa = Lutadores.de(outro);
    if (lutadorDeLa != null) {
      quem.add(lutadorDeLa.getUuid());
    }
    Experiencia.derrotadoNoDuelo(outro, pokemon, quem);
  }

  private void anotarQuemEnfrentou() {
    Pokemon doDesafiante = Lutadores.de(desafiante);
    Pokemon doDesafiado = Lutadores.de(desafiado);
    if (doDesafiante == null || doDesafiado == null) {
      return;
    }
    anotar(desafiante, doDesafiante, doDesafiado);
    anotar(desafiado, doDesafiado, doDesafiante);
  }

  private void anotar(ServerPlayer jogador, Pokemon meu, Pokemon deLa) {
    enfrentaram.computeIfAbsent(jogador.getUUID(), uuid -> new HashMap<>())
        .computeIfAbsent(deLa.getUuid(), uuid -> new HashSet<>()).add(meu.getUuid());
  }

  // Os dois primeiros lutadores frente a frente: a Intimidação de quem entrou no começo pega agora, dos dois lados
  private void abrir() {
    if (abriu) {
      return;
    }
    Pokemon doDesafiante = Lutadores.de(desafiante);
    Pokemon doDesafiado = Lutadores.de(desafiado);
    if (doDesafiante == null || doDesafiado == null) {
      return;
    }
    abriu = true;
    Intimidacao.aoEntrar(doDesafiante.getEntity(), List.of(doDesafiado.getEntity()));
    Intimidacao.aoEntrar(doDesafiado.getEntity(), List.of(doDesafiante.getEntity()));
    CobblemonCombatZA.LOGGER.info("DUELO: {} x {}, frente a frente", doDesafiante.getSpecies().getName(),
        doDesafiado.getSpecies().getName());
  }

  private Derrota derrota(ServerPlayer jogador, long agora) {
    if (jogador.isRemoved() || jogador.hasDisconnected() || !jogador.isAlive()
        || !jogador.level().dimension().equals(mundo)) {
      return Derrota.DESISTIU;
    }
    double distancia = Math.sqrt(jogador.distanceToSqr(meio));
    if (distancia > DISTANCIA_PARA_DESISTIR) {
      return Derrota.DESISTIU;
    }
    if (distancia > DISTANCIA_DO_AVISO
        && agora - ultimoAvisoDeLonge.getOrDefault(jogador.getUUID(), Long.MIN_VALUE / 2) >= TICKS_ENTRE_AVISOS) {
      ultimoAvisoDeLonge.put(jogador.getUUID(), agora);
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "volte", (int) DISTANCIA_PARA_DESISTIR));
    }
    return semNinguemDoTime(jogador) ? Derrota.SEM_POKEMON : Derrota.NENHUMA;
  }

  // Alguém do time de "jogador", fora "menos", ainda pode lutar? (o Forte Vínculo: ainda há inimigo no duelo)
  public boolean temOutroComVida(ServerPlayer jogador, Pokemon menos) {
    List<UUID> time = times.getOrDefault(jogador.getUUID(), List.of());
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (time.contains(pokemon.getUuid()) && !pokemon.getUuid().equals(menos.getUuid()) && !pokemon.isFainted()) {
        return true;
      }
    }
    return false;
  }

  // Nenhum dos N pode lutar (desmaiado, ou nem está mais no time)
  private boolean semNinguemDoTime(ServerPlayer jogador) {
    return primeiroComVida(jogador) == null;
  }

  // O primeiro do time com vida, na ordem do time (o da frente primeiro); null = nenhum
  private Pokemon primeiroComVida(ServerPlayer jogador) {
    List<UUID> time = times.getOrDefault(jogador.getUUID(), List.of());
    for (UUID uuid : time) {
      for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
        if (pokemon.getUuid().equals(uuid) && !pokemon.isFainted()) {
          return pokemon;
        }
      }
    }
    return null;
  }

  // Sem lutador e ninguém do time fora da bola: avisa uma vez; passados os 15 s, o primeiro com vida entra sozinho
  private void trocaForcada(ServerPlayer jogador, long agora) {
    if (Lutadores.de(jogador) != null || alguemDoTimeFora(jogador)) {
      semLutadorDesde.remove(jogador.getUUID());
      return;
    }
    Long desde = semLutadorDesde.get(jogador.getUUID());
    if (desde == null) {
      semLutadorDesde.put(jogador.getUUID(), agora);
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "mande_o_proximo", SEGUNDOS_PARA_MANDAR));
      return;
    }
    if (agora - desde < (long) SEGUNDOS_PARA_MANDAR * SharedConstants.TICKS_PER_SECOND) {
      return;
    }
    semLutadorDesde.put(jogador.getUUID(), agora);
    Pokemon proximo = primeiroComVida(jogador);
    if (proximo != null && proximo.getEntity() == null) {
      CobblemonCombatZA.LOGGER.info("DUELO: {} ficou {} s sem lutador; {} entra sozinho", jogador.getGameProfile().getName(),
          SEGUNDOS_PARA_MANDAR, proximo.getSpecies().getName());
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "entrou_sozinho", proximo.getDisplayName(false)));
      SoltarNoSelvagem.jogar(jogador, proximo, ondeSoltar(jogador));
    }
  }

  private boolean alguemDoTimeFora(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      PokemonEntity entidade = pokemon.getEntity();
      if (entidade != null && entidade.isAlive() && !pokemon.isFainted() && doTime(pokemon)) {
        return true;
      }
    }
    return false;
  }

  // Na linha até o adversário, no chão; com uma parede no meio, antes dela
  private Vec3 ondeSoltar(ServerPlayer jogador) {
    Vec3 caminho = outro(jogador).position().subtract(jogador.position()).multiply(1, 0, 1);
    Vec3 direcao = caminho.lengthSqr() > 0.01 ? caminho : jogador.getLookAngle().multiply(1, 0, 1);
    direcao = direcao.lengthSqr() > 1.0E-4 ? direcao.normalize() : new Vec3(0, 0, 1);
    double distancia = Math.clamp(caminho.length() / 2, SOLTAR_MAIS_PERTO, DISTANCIA_DE_SOLTAR);
    Vec3 ponto = jogador.position().add(direcao.scale(distancia));
    BlockHitResult parede = jogador.serverLevel().clip(new ClipContext(jogador.getEyePosition(), ponto.add(0, 1, 0),
        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, jogador));
    if (parede.getType() == HitResult.Type.BLOCK) {
      ponto = parede.getLocation().subtract(direcao.scale(ANTES_DA_PAREDE));
    }
    return Mira.noChao(jogador.serverLevel(), ponto);
  }

  // O fim: o aviso para cada um (com o XP do duelo embaixo) e a frase no chat. A limpeza de cada jogador (o lutador, os
  // estágios, a Mega, os itens, o campo) é a de sempre, a do GerenciarCombates, logo em seguida
  private void terminar(Derrota doDesafiante, Derrota doDesafiado) {
    acabou = true;
    if (doDesafiante != Derrota.NENHUMA && doDesafiado != Derrota.NENHUMA) {
      for (ServerPlayer jogador : List.of(desafiante, desafiado)) {
        avisar(jogador, Component.translatable(FRASE + "empate"), "acabou", outro(jogador));
      }
      CobblemonCombatZA.LOGGER.info("DUELO: {} x {} acabou sem vencedor", desafiante.getGameProfile().getName(),
          desafiado.getGameProfile().getName());
      return;
    }
    boolean desafianteVenceu = doDesafiante == Derrota.NENHUMA;
    ServerPlayer vencedor = desafianteVenceu ? desafiante : desafiado;
    ServerPlayer perdedor = outro(vencedor);
    boolean desistiu = (desafianteVenceu ? doDesafiado : doDesafiante) == Derrota.DESISTIU;
    avisar(vencedor, desistiu ? Component.translatable(FRASE + "ele_desistiu", perdedor.getName())
        : Component.translatable(FRASE + "venceu", perdedor.getName()), "venceu", perdedor);
    avisar(perdedor, desistiu ? Component.translatable(FRASE + "voce_desistiu")
        : Component.translatable(FRASE + "perdeu", vencedor.getName()), "perdeu", vencedor);
    for (ServerPlayer jogador : List.of(vencedor, perdedor)) {
      if (!jogador.hasDisconnected()) {
        if (desistiu) {
          jogador.sendSystemMessage(Component.translatable("cobblemon.battle.forfeit", perdedor.getName()));
        }
        jogador.sendSystemMessage(Component.literal("⚔ ")
            .append(Component.translatable("cobblemon.battle.win", vencedor.getName())));
      }
    }
    CobblemonCombatZA.LOGGER.info("DUELO: {} venceu {} ({})", vencedor.getGameProfile().getName(),
        perdedor.getGameProfile().getName(), desistiu ? "desistência" : "sem Pokémon");
  }

  // Os rostos do aviso: o time de quem estava do outro lado
  private void avisar(ServerPlayer jogador, Component texto, String tipo, ServerPlayer doOutroLado) {
    if (jogador.hasDisconnected()) {
      Experiencia.esquecer(jogador);
      return;
    }
    List<Pokemon> rostos = new ArrayList<>();
    List<UUID> time = times.getOrDefault(doOutroLado.getUUID(), List.of());
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(doOutroLado)) {
      if (time.contains(pokemon.getUuid())) {
        rostos.add(pokemon);
      }
    }
    AvisoDaBatalha.doDuelo(jogador, texto, tipo, rostos);
  }
}
