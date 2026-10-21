package dev.victor.combatza.pvp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonFaintedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.battles.ChallengeManager;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.rede.EscolhaDoDueloPayload;
import dev.victor.combatza.rede.QuantosNoDueloPayload;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 21/10 (PvP; decisões dele: "o PvP começa pelo desafio de batalha do próprio Cobblemon", "quem desafia escolhe quantos
// Pokémon", "os golpes só pegam nos Pokémon", "com XP"). Como o duelo começa:
// 1. Você desafia pela roda do Cobblemon (Batalha, Simples, o Pokémon da frente). O Cobblemon não tem evento de desafio:
//    a cada tick, o desafio que cada jogador mandou (ChallengeManager.getOutboundRequest) é conferido. Desafio novo de 1
//    contra 1, de um Pokémon por lado, com o nosso mod nos dois: abre para você a tela do duelo (client.TelaDoDuelo),
//    "quantos Pokémon cada um usa?" (de 1 até o menor time com vida; já vem marcado o último que você usou, ou 3). O outro
//    fica sabendo no chat;
// 2. O outro aceita pela roda do Cobblemon. O Cobblemon vai começar a batalha por turnos e avisa antes
//    (BATTLE_STARTED_PRE, isPvP): a batalha dele é cancelada sem mensagem (a mesma chave de silêncio da batalha contra
//    selvagem) e começa o Duelo;
// 3. Sem o nosso mod num dos dois, nas duplas/triplas/multi, num desafio que não passou pela conferência, ou se algo der
//    errado ao montar o duelo: fica a batalha por turnos do Cobblemon (e um aviso no log).
// Daqui também saem as regras de "quem pega em quem" no duelo (Lados e Mira perguntam aqui): o golpe de quem duela só pega
// nos Pokémon do adversário, e os Pokémon do duelo só levam o golpe dele
public class Duelos {
  private static final String FRASE = "cobblemon_combat_za.duelo.";
  private static final int TAMANHO_DO_TIME = 6;
  // Escolha minha (vetável): o número marcado na primeira vez, o "3 de 6" dos duelos oficiais
  private static final int SUGERIDO_DA_PRIMEIRA_VEZ = 3;

  // Um desafio de batalha do Cobblemon (1 contra 1) esperando a resposta. tempoReal = false: fica por turnos (sem o mod num
  // dos dois); só é guardado para não ser conferido de novo a cada tick
  private static final class Desafio {
    private final UUID id;
    private final UUID desafiante;
    private final UUID desafiado;
    private final boolean tempoReal;
    private final int maximo;
    private int quantos;

    private Desafio(UUID id, UUID desafiante, UUID desafiado, boolean tempoReal, int maximo, int quantos) {
      this.id = id;
      this.desafiante = desafiante;
      this.desafiado = desafiado;
      this.tempoReal = tempoReal;
      this.maximo = maximo;
      this.quantos = quantos;
    }
  }

  private static final Map<UUID, Desafio> DESAFIOS = new HashMap<>();
  // O último número que cada um escolheu (a tela já abre com ele)
  private static final Map<UUID, Integer> ULTIMA_ESCOLHA = new HashMap<>();
  // O histórico do duelo vai para os dois uma vez só por tick: a mesma frase chega duas vezes quando quem avisa manda para
  // cada jogador da batalha (os itens, o clima)
  private static long tickDoHistorico = -1;
  private static final Map<UUID, Set<Component>> JA_VIU = new HashMap<>();

  private Duelos() {
  }

  public static void registrar() {
    PayloadTypeRegistry.playS2C().register(EscolhaDoDueloPayload.TIPO, EscolhaDoDueloPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(QuantosNoDueloPayload.TIPO, QuantosNoDueloPayload.STREAM_CODEC);
    ServerPlayNetworking.registerGlobalReceiver(QuantosNoDueloPayload.TIPO,
        (pedido, contexto) -> escolheu(contexto.player(), pedido.quantos()));
    // Antes do nosso aviso de batalha (o da batalha contra selvagem, NORMAL): cancelado aqui, ele nem chega lá
    CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGH, Duelos::aoComecarBatalha);
    // Antes da troca do lutador (Lutadores.segurarATroca, NORMAL): quem não está no duelo nem chega a recolher o lutador
    CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.HIGHEST, Duelos::antesDeSoltar);
    CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.LOW, Duelos::aoSoltar);
    CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.LOW, Duelos::aoDesmaiar);
    ServerTickEvents.END_SERVER_TICK.register(Duelos::conferirOsDesafios);
    ServerLifecycleEvents.SERVER_STOPPED.register(servidor -> limpar());
  }

  public static void limpar() {
    DESAFIOS.clear();
    ULTIMA_ESCOLHA.clear();
    JA_VIU.clear();
    tickDoHistorico = -1;
  }

  // O duelo em que o jogador está; null = nenhum
  public static Duelo dueloDe(ServerPlayer jogador) {
    return jogador != null && GerenciarCombates.combateDoJogador(jogador) instanceof Duelo duelo ? duelo : null;
  }

  public static boolean estaEmDuelo(ServerPlayer jogador) {
    return dueloDe(jogador) != null;
  }

  // O duelo de um Pokémon do time de um dos dois; null = não é de duelo nenhum
  private static Duelo dueloDoPokemon(Pokemon pokemon) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    Duelo duelo = dueloDe(dono);
    return duelo != null && duelo.doTime(pokemon) ? duelo : null;
  }

  // Lados.mesmoLado: dois Pokémon do mesmo duelo estão do mesmo lado se são do mesmo jogador. null = não são do mesmo duelo
  public static Boolean mesmoLadoNoDuelo(Pokemon um, Pokemon outro) {
    Duelo duelo = dueloDoPokemon(um);
    if (duelo == null || duelo != dueloDoPokemon(outro)) {
      return null;
    }
    return duelo.ladoDe(um) == duelo.ladoDe(outro);
  }

  // O Pokémon é um dos N do adversário de "jogador" no duelo
  public static boolean ehDoAdversario(ServerPlayer jogador, Pokemon pokemon) {
    Duelo duelo = dueloDe(jogador);
    return duelo != null && duelo.doAdversario(jogador, pokemon);
  }

  // Lados.ehInimigoDe. null = sem duelo no meio (a regra de sempre)
  public static Boolean ehInimigoNoDuelo(Pokemon atacante, Entity entidade) {
    Duelo doAtacante = atacante.isWild() ? null : dueloDe(atacante.getOwnerPlayer());
    if (doAtacante != null) {
      return entidade instanceof PokemonEntity alvo && doAtacante.doAdversario(atacante.getOwnerPlayer(), alvo.getPokemon());
    }
    // Quem está fora do duelo não briga com os Pokémon dele
    return entidade instanceof PokemonEntity alvo && dueloDoPokemon(alvo.getPokemon()) != null ? false : null;
  }

  // Lados.foraDaSuaLuta: true = o golpe passa por ele. null = sem duelo no meio (a regra de sempre)
  public static Boolean foraDoDuelo(Pokemon atacante, Entity alvo) {
    Boolean inimigo = ehInimigoNoDuelo(atacante, alvo);
    return inimigo == null ? null : !inimigo;
  }

  // Lados.miraAtravessa: no duelo, a sua mira só para nos Pokémon do adversário. null = você não está num duelo
  public static Boolean miraAtravessa(ServerPlayer jogador, Entity entidade) {
    Duelo duelo = dueloDe(jogador);
    if (duelo == null) {
      return null;
    }
    return !(entidade instanceof PokemonEntity pokemon && duelo.doAdversario(jogador, pokemon.getPokemon()));
  }

  // CampoEBarreiras: no duelo, cada jogador é um lado do campo (true = o de quem desafiou). null = não é de duelo
  public static Boolean ladoNoDuelo(Pokemon pokemon) {
    Duelo duelo = dueloDoPokemon(pokemon);
    return duelo == null ? null : duelo.ladoDe(pokemon);
  }

  // Feedback.historico: no duelo, a frase sobre um dos N vai para os dois jogadores (cada um uma vez por tick); sobre outro
  // Pokémon, para ninguém (não é do duelo). null = quem ia ver não está num duelo (o caminho de sempre)
  public static List<ServerPlayer> quemVeNoHistorico(ServerPlayer jogador, Pokemon sobreQuem, Component mensagem) {
    Duelo duelo = dueloDe(jogador);
    if (duelo == null) {
      return null;
    }
    if (sobreQuem.isWild() || !duelo.doTime(sobreQuem)) {
      return List.of();
    }
    long agora = jogador.server.getTickCount();
    if (agora != tickDoHistorico) {
      tickDoHistorico = agora;
      JA_VIU.clear();
    }
    List<ServerPlayer> quem = new ArrayList<>();
    for (ServerPlayer duelista : List.of(jogador, duelo.outro(jogador))) {
      if (!duelista.hasDisconnected() && JA_VIU.computeIfAbsent(duelista.getUUID(), uuid -> new HashSet<>()).add(mensagem)) {
        quem.add(duelista);
      }
    }
    return quem;
  }

  // 1. A conferência dos desafios: o novo abre a tela de quem desafiou; o que sumiu sem virar duelo fecha a tela
  private static void conferirOsDesafios(MinecraftServer servidor) {
    Set<UUID> abertos = new HashSet<>();
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      ChallengeManager.BattleChallenge desafio = ChallengeManager.INSTANCE.getOutboundRequest(jogador.getUUID());
      if (!(desafio instanceof ChallengeManager.SinglesBattleChallenge umContraUm)
          || desafio.getBattleFormat().getBattleType().getPokemonPerSide() != 1) {
        continue;
      }
      abertos.add(desafio.getRequestID());
      if (!DESAFIOS.containsKey(desafio.getRequestID())) {
        novoDesafio(umContraUm);
      }
    }
    Iterator<Desafio> desafios = DESAFIOS.values().iterator();
    while (desafios.hasNext()) {
      Desafio desafio = desafios.next();
      if (!abertos.contains(desafio.id)) {
        desafios.remove();
        fecharATela(servidor.getPlayerList().getPlayer(desafio.desafiante));
      }
    }
  }

  private static void novoDesafio(ChallengeManager.SinglesBattleChallenge desafio) {
    ServerPlayer desafiante = desafio.getSender();
    ServerPlayer desafiado = desafio.getReceiver();
    boolean tempoReal = temOMod(desafiante) && temOMod(desafiado);
    int maximo = Math.min(comVida(desafiante), comVida(desafiado));
    int quantos = Math.clamp(ULTIMA_ESCOLHA.getOrDefault(desafiante.getUUID(), SUGERIDO_DA_PRIMEIRA_VEZ), 1, Math.max(1,
        maximo));
    DESAFIOS.put(desafio.getRequestID(), new Desafio(desafio.getRequestID(), desafiante.getUUID(), desafiado.getUUID(),
        tempoReal, maximo, quantos));
    if (!tempoReal) {
      CobblemonCombatZA.LOGGER.warn("DUELO: {} desafiou {}, mas um dos dois está sem o Cobblemon Combat ZA: a batalha fica "
          + "por turnos, a do Cobblemon", desafiante.getGameProfile().getName(), desafiado.getGameProfile().getName());
      return;
    }
    CobblemonCombatZA.LOGGER.info("DUELO: {} desafiou {} (até {} Pokémon; marcado {})",
        desafiante.getGameProfile().getName(), desafiado.getGameProfile().getName(), maximo, quantos);
    ServerPlayNetworking.send(desafiante, new EscolhaDoDueloPayload(desafiado.getGameProfile().getName(), maximo, quantos));
    desafiado.sendSystemMessage(Component.translatable(FRASE + "chegou", desafiante.getName(), quantos)
        .withStyle(ChatFormatting.GOLD));
  }

  // Da tela: o número escolhido (vale para este desafio e fica marcado nos próximos), ou cancelar o desafio
  private static void escolheu(ServerPlayer jogador, int quantos) {
    ChallengeManager.BattleChallenge desafioDoCobblemon = ChallengeManager.INSTANCE.getOutboundRequest(jogador.getUUID());
    Desafio desafio = desafioDoCobblemon == null ? null : DESAFIOS.get(desafioDoCobblemon.getRequestID());
    if (desafio == null || !desafio.tempoReal) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "desafio_acabou"));
      return;
    }
    if (quantos == QuantosNoDueloPayload.CANCELAR) {
      ChallengeManager.INSTANCE.cancelRequest(desafioDoCobblemon, false);
      return;
    }
    if (quantos < 1 || quantos > Math.max(1, desafio.maximo)) {
      return;
    }
    desafio.quantos = quantos;
    ULTIMA_ESCOLHA.put(jogador.getUUID(), quantos);
    ServerPlayer desafiado = desafioDoCobblemon.getReceiver();
    Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "voce_escolheu", quantos, desafiado.getName()));
    desafiado.sendSystemMessage(Component.translatable(FRASE + "escolheu", jogador.getName(), quantos)
        .withStyle(ChatFormatting.GOLD));
    CobblemonCombatZA.LOGGER.info("DUELO: {} escolheu {} Pokémon contra {}", jogador.getGameProfile().getName(), quantos,
        desafiado.getGameProfile().getName());
  }

  // 2. O desafio foi aceito e o Cobblemon vai começar a batalha por turnos
  private static void aoComecarBatalha(BattleStartedEvent.Pre evento) {
    PokemonBattle batalha = evento.getBattle();
    if (!batalha.isPvP() || batalha.getPlayers().size() != 2
        || batalha.getFormat().getBattleType().getPokemonPerSide() != 1) {
      return;
    }
    ServerPlayer um = batalha.getPlayers().get(0);
    ServerPlayer outro = batalha.getPlayers().get(1);
    Desafio desafio = tirarODesafio(um, outro);
    if (desafio == null || !desafio.tempoReal) {
      return;
    }
    ServerPlayer desafiante = um.getUUID().equals(desafio.desafiante) ? um : outro;
    ServerPlayer desafiado = desafiante == um ? outro : um;
    // Algum dos dois numa batalha nossa (contra selvagem, outro duelo ou um treinador, até na abertura dele, quando ainda não
    // há Combate): nem duelo, nem batalha por turnos no meio dela
    for (ServerPlayer jogador : List.of(desafiante, desafiado)) {
      if (GerenciarCombates.estaEmCombate(jogador) || BatalhasDeTreinador.estaEmBatalha(jogador)) {
        cancelar(evento);
        Component porque = Component.translatable(FRASE + "em_batalha", jogador.getName());
        desafiante.sendSystemMessage(porque.copy().withStyle(ChatFormatting.RED));
        desafiado.sendSystemMessage(porque.copy().withStyle(ChatFormatting.RED));
        return;
      }
    }
    List<UUID> timeDoDesafiante;
    List<UUID> timeDoDesafiado;
    int quantos;
    try {
      quantos = Math.min(desafio.quantos, Math.min(comVida(desafiante), comVida(desafiado)));
      timeDoDesafiante = time(desafiante, daFrente(batalha, desafiante), quantos);
      timeDoDesafiado = time(desafiado, daFrente(batalha, desafiado), quantos);
    } catch (RuntimeException erro) {
      CobblemonCombatZA.LOGGER.warn("DUELO: não deu para montar o duelo em tempo real; fica a batalha por turnos do "
          + "Cobblemon", erro);
      return;
    }
    if (quantos < 1 || timeDoDesafiante.isEmpty() || timeDoDesafiado.isEmpty()) {
      CobblemonCombatZA.LOGGER.warn("DUELO: um dos times está vazio; fica a batalha por turnos do Cobblemon");
      return;
    }
    // A batalha do Cobblemon só é cancelada depois que o duelo está de pé: se algo der errado ao montar, ela segue
    Duelo duelo = null;
    try {
      duelo = new Duelo(desafiante, desafiado, quantos, timeDoDesafiante, timeDoDesafiado);
      GerenciarCombates.adicionar(duelo);
      duelo.comecar();
    } catch (RuntimeException erro) {
      CobblemonCombatZA.LOGGER.warn("DUELO: o duelo em tempo real não começou; fica a batalha por turnos do Cobblemon",
          erro);
      if (duelo != null) {
        duelo.desfazer();
      }
      return;
    }
    cancelar(evento);
    avisarOComeco(duelo, quantos, batalha.getFormat().getAdjustLevel() > 0);
  }

  private static void avisarOComeco(Duelo duelo, int quantos, boolean comNivelAjustado) {
    for (ServerPlayer jogador : duelo.getJogadores()) {
      jogador.sendSystemMessage(Component.translatable(FRASE + "comecou", duelo.outro(jogador).getName(), quantos)
          .withStyle(ChatFormatting.GOLD));
      jogador.sendSystemMessage(Component.translatable(FRASE + "regras", (int) Duelo.DISTANCIA_PARA_DESISTIR)
          .withStyle(ChatFormatting.GRAY));
      if (comNivelAjustado) {
        jogador.sendSystemMessage(Component.translatable(FRASE + "nivel_ignorado").withStyle(ChatFormatting.GRAY));
      }
    }
  }

  private static void cancelar(BattleStartedEvent.Pre evento) {
    evento.cancel();
    evento.setReason(Component.translatable(CobblemonCombatZA.CHAVE_SILENCIO));
  }

  // O desafio entre os dois que a conferência viu (o Cobblemon já tirou o dele da lista antes de começar a batalha)
  private static Desafio tirarODesafio(ServerPlayer um, ServerPlayer outro) {
    Iterator<Desafio> desafios = DESAFIOS.values().iterator();
    while (desafios.hasNext()) {
      Desafio desafio = desafios.next();
      boolean dosDois = desafio.desafiante.equals(um.getUUID()) && desafio.desafiado.equals(outro.getUUID())
          || desafio.desafiante.equals(outro.getUUID()) && desafio.desafiado.equals(um.getUUID());
      if (dosDois) {
        desafios.remove();
        return desafio;
      }
    }
    return null;
  }

  // O Pokémon da frente que o jogador escolheu no desafio do Cobblemon: o primeiro do lado dele na batalha que ia começar
  private static UUID daFrente(PokemonBattle batalha, ServerPlayer jogador) {
    for (BattleActor ator : batalha.getActors()) {
      if (ator instanceof PlayerBattleActor doJogador && jogador.equals(doJogador.getEntity())) {
        for (BattlePokemon pokemon : ator.getPokemonList()) {
          if (!pokemon.getOriginalPokemon().isFainted()) {
            return pokemon.getOriginalPokemon().getUuid();
          }
        }
      }
    }
    return null;
  }

  // Os N do jogador: o da frente e os próximos com vida, na ordem do time dele
  private static List<UUID> time(ServerPlayer jogador, UUID daFrente, int quantos) {
    List<UUID> time = new ArrayList<>();
    List<Pokemon> comVida = new ArrayList<>();
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (!pokemon.isFainted()) {
        comVida.add(pokemon);
      }
    }
    comVida.stream().filter(pokemon -> pokemon.getUuid().equals(daFrente)).findFirst()
        .ifPresent(pokemon -> time.add(pokemon.getUuid()));
    for (Pokemon pokemon : comVida) {
      if (time.size() >= quantos) {
        break;
      }
      if (!time.contains(pokemon.getUuid())) {
        time.add(pokemon.getUuid());
      }
    }
    return time;
  }

  private static int comVida(ServerPlayer jogador) {
    int comVida = 0;
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (!pokemon.isFainted()) {
        comVida++;
      }
    }
    return Math.min(TAMANHO_DO_TIME, comVida);
  }

  private static boolean temOMod(ServerPlayer jogador) {
    return ServerPlayNetworking.canSend(jogador, EscolhaDoDueloPayload.TIPO);
  }

  private static void fecharATela(ServerPlayer jogador) {
    if (jogador != null && temOMod(jogador)) {
      ServerPlayNetworking.send(jogador, new EscolhaDoDueloPayload("", EscolhaDoDueloPayload.FECHAR, 0));
    }
  }

  // Só os N do duelo saem da bola enquanto ele dura
  private static void antesDeSoltar(PokemonSentEvent.Pre evento) {
    Pokemon pokemon = evento.getPokemon();
    ServerPlayer dono = pokemon.getOwnerPlayer();
    Duelo duelo = dueloDe(dono);
    if (duelo != null && !duelo.doTime(pokemon)) {
      evento.cancel();
      Feedback.barraDeAcao(dono, Component.translatable(FRASE + "fora_do_time", pokemon.getDisplayName(false),
          duelo.getQuantos()));
      CobblemonCombatZA.LOGGER.info("DUELO: {} não está no time de {}: não sai da bola", pokemon.getSpecies().getName(),
          dono.getGameProfile().getName());
    }
  }

  // O adversário vê quem entrou ("Fulano enviou Pikachu!"); o dono já vê o "eu escolho você" (Lutadores)
  private static void aoSoltar(PokemonSentEvent.Post evento) {
    Pokemon pokemon = evento.getPokemon();
    Duelo duelo = dueloDoPokemon(pokemon);
    if (duelo != null) {
      ServerPlayer dono = pokemon.getOwnerPlayer();
      Feedback.historicoDaBatalha(duelo.outro(dono), pokemon,
          Component.translatable("cobblemon.battle.switch.other", dono.getName(), pokemon.getDisplayName(false)));
    }
  }

  private static void aoDesmaiar(PokemonFaintedEvent evento) {
    Pokemon pokemon = evento.getPokemon();
    Duelo duelo = dueloDoPokemon(pokemon);
    if (duelo != null) {
      duelo.desmaiou(pokemon.getOwnerPlayer(), pokemon);
    }
  }
}
