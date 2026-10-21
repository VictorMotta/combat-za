package dev.victor.combatza.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.rede.PartyPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

// A PARTY (dele, 20/10: "uma funcionalidade nossa, de party simples, igual um MMO"; os comandos primeiro e a tela depois; "a
// party luta junta"). Uma party só: com o Open Parties and Claims no servidor, ela é a DELE (a mesma que divide o terreno
// protegido; PonteDoOpac); sem ele, é a nossa, aqui: até 4, quem convida vira o líder (só ele tira alguém; saiu, o próximo
// vira). 21/10 (dele: "a party não deveria ser desfeita saindo do jogo, deveria continuar mesmo em servidor dedicado"): ela fica
// guardada no mundo (PartiesSalvas); quem sai do jogo continua nela, aparece offline, e volta para ela ao entrar. O convite vale
// 60 s nos dois casos.
// Na party: quem está perto entra na sua batalha e o XP é dividido (GerenciarCombates, Experiencia); estranhos não entram na
// sua batalha e os golpes não pegam neles nem nos Pokémon deles (Lados). O seu jogo recebe a lista (PartyPayload) para o
// quadrinho da party, o brilho através da parede, a tela P e o mapa do Xaero
public class Parties {
  public static final int MAXIMO = 4;
  // Até onde a party entra junto na batalha (e segura na batalha quem está um pouco mais longe: Combate)
  public static final double PERTO_PARA_LUTAR = 32.0;
  static final String FRASE = "cobblemon_combat_za.party.";
  private static final int SEGUNDOS_DO_CONVITE = 60;
  private static final int A_CADA_TICKS = 10;
  private static final float PASSOS_DA_VIDA = 20f;
  // Com o OPAC no servidor, a party é a dele. Se ele mudar por dentro numa versão nova (um método que a PonteDoOpac usa sumiu),
  // a ponte se desliga com um aviso no log e a party volta a ser a nossa, em vez de fechar o servidor
  private static boolean comOpac = FabricLoader.getInstance().isModLoaded("openpartiesandclaims");

  // Um membro, do jeito que a lista precisa (a PonteDoOpac devolve assim, sem tipos do OPAC aqui)
  record Membro(UUID jogador, String nome, boolean lider) {
  }

  private static final class Party {
    private UUID lider;
    private final List<UUID> membros = new ArrayList<>();
  }

  private record Convite(UUID quemConvidou, long ate) {
  }

  private static final Map<UUID, Party> DE = new HashMap<>();
  // O último nome de cada membro (para quem está offline), guardado junto com a party
  private static final Map<UUID, String> NOMES = new HashMap<>();
  // Pelo UUID de quem foi convidado
  private static final Map<UUID, Convite> CONVITES = new HashMap<>();
  private static final Map<UUID, PartyPayload> ULTIMO_ENVIO = new HashMap<>();

  private Parties() {
  }

  // Você e você, ou dois da mesma party
  public static boolean saoAliados(Player a, Player b) {
    if (a == null || b == null) {
      return false;
    }
    if (a.getUUID().equals(b.getUUID())) {
      return true;
    }
    if (comOpac) {
      try {
        return PonteDoOpac.saoAliados(a, b);
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    Party party = DE.get(a.getUUID());
    return party != null && party == DE.get(b.getUUID());
  }

  // Os outros da party que estão online
  public static List<ServerPlayer> outrosOnline(ServerPlayer jogador) {
    if (comOpac) {
      try {
        return PonteDoOpac.outrosOnline(jogador);
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    List<ServerPlayer> outros = new ArrayList<>();
    Party party = DE.get(jogador.getUUID());
    if (party == null) {
      return outros;
    }
    for (UUID membro : party.membros) {
      ServerPlayer online = jogador.server.getPlayerList().getPlayer(membro);
      if (online != null && online != jogador) {
        outros.add(online);
      }
    }
    return outros;
  }

  public static int convidar(ServerPlayer de, ServerPlayer para) {
    if (de == para) {
      return erro(de, "voce_mesmo");
    }
    if (saoAliados(de, para)) {
      return erro(de, "ja_esta");
    }
    if (comOpac) {
      try {
        return PonteDoOpac.convidar(de, para);
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    if (DE.containsKey(para.getUUID())) {
      return erro(de, "ja_tem_party", para.getDisplayName());
    }
    Party party = DE.computeIfAbsent(de.getUUID(), uuid -> {
      Party nova = new Party();
      nova.lider = uuid;
      nova.membros.add(uuid);
      return nova;
    });
    if (party.membros.size() >= MAXIMO) {
      return erro(de, "cheia", MAXIMO);
    }
    anotarConvite(de, para);
    return 1;
  }

  // O convite (nos dois casos): fica 60 s, e a mensagem chega com os botões de aceitar e recusar
  static void anotarConvite(ServerPlayer de, ServerPlayer para) {
    CONVITES.put(para.getUUID(), new Convite(de.getUUID(),
        de.server.getTickCount() + SEGUNDOS_DO_CONVITE * (long) SharedConstants.TICKS_PER_SECOND));
    para.sendSystemMessage(Component.translatable(FRASE + "convite", de.getDisplayName(), SEGUNDOS_DO_CONVITE)
        .append(" ").append(botao("aceitar", "/party aceitar", ChatFormatting.GREEN))
        .append(" ").append(botao("recusar", "/party recusar", ChatFormatting.RED)));
    de.sendSystemMessage(Component.translatable(FRASE + "convidou", para.getDisplayName()));
    CobblemonCombatZA.LOGGER.info("PARTY: {} convidou {}{}", de.getName().getString(), para.getName().getString(),
        comOpac ? " (OPAC)" : "");
  }

  public static int aceitar(ServerPlayer quem) {
    Convite convite = CONVITES.remove(quem.getUUID());
    if (convite == null || quem.server.getTickCount() > convite.ate()) {
      return erro(quem, "sem_convite");
    }
    if (comOpac) {
      try {
        return PonteDoOpac.aceitar(quem, convite.quemConvidou());
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    Party party = DE.get(convite.quemConvidou());
    if (party == null) {
      return erro(quem, "sem_convite");
    }
    if (DE.containsKey(quem.getUUID())) {
      return erro(quem, "ja_tem_party_voce");
    }
    if (party.membros.size() >= MAXIMO) {
      return erro(quem, "cheia", MAXIMO);
    }
    party.membros.add(quem.getUUID());
    DE.put(quem.getUUID(), party);
    NOMES.put(quem.getUUID(), quem.getGameProfile().getName());
    ServerPlayer quemConvidou = quem.server.getPlayerList().getPlayer(convite.quemConvidou());
    if (quemConvidou != null) {
      NOMES.put(quemConvidou.getUUID(), quemConvidou.getGameProfile().getName());
    }
    salvar(quem.server);
    avisarTodos(quem.server, party, Component.translatable(FRASE + "entrou", quem.getDisplayName()));
    CobblemonCombatZA.LOGGER.info("PARTY: {} entrou ({} na party)", quem.getName().getString(), party.membros.size());
    return 1;
  }

  public static int recusar(ServerPlayer quem) {
    Convite convite = CONVITES.remove(quem.getUUID());
    if (convite == null) {
      return erro(quem, "sem_convite");
    }
    if (comOpac) {
      try {
        PonteDoOpac.recusou(quem, convite.quemConvidou());
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    ServerPlayer quemConvidou = quem.server.getPlayerList().getPlayer(convite.quemConvidou());
    if (quemConvidou != null) {
      quemConvidou.sendSystemMessage(Component.translatable(FRASE + "recusou", quem.getDisplayName()));
    }
    quem.sendSystemMessage(Component.translatable(FRASE + "voce_recusou"));
    return 1;
  }

  public static int sair(ServerPlayer quem) {
    if (comOpac) {
      try {
        return PonteDoOpac.sair(quem);
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    if (!DE.containsKey(quem.getUUID())) {
      return erro(quem, "sem_party");
    }
    tirar(quem.server, quem.getUUID(), Component.translatable(FRASE + "saiu", quem.getDisplayName()));
    quem.sendSystemMessage(Component.translatable(FRASE + "voce_saiu"));
    return 1;
  }

  public static int expulsar(ServerPlayer lider, ServerPlayer alvo) {
    if (comOpac) {
      try {
        return PonteDoOpac.expulsar(lider, alvo);
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    Party party = DE.get(lider.getUUID());
    if (party == null) {
      return erro(lider, "sem_party");
    }
    if (!party.lider.equals(lider.getUUID())) {
      return erro(lider, "so_o_lider");
    }
    if (alvo == lider || DE.get(alvo.getUUID()) != party) {
      return erro(lider, "nao_esta", alvo.getDisplayName());
    }
    tirar(lider.server, alvo.getUUID(), Component.translatable(FRASE + "expulso", alvo.getDisplayName()));
    alvo.sendSystemMessage(Component.translatable(FRASE + "voce_foi_expulso"));
    return 1;
  }

  // "/party": quem está na sua party
  public static int listar(ServerPlayer quem) {
    if (comOpac) {
      try {
        return PonteDoOpac.listar(quem);
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    Party party = DE.get(quem.getUUID());
    if (party == null) {
      return erro(quem, "sem_party");
    }
    MutableComponent lista = Component.translatable(FRASE + "lista", party.membros.size(), MAXIMO);
    for (UUID membro : party.membros) {
      ServerPlayer online = quem.server.getPlayerList().getPlayer(membro);
      lista.append(Component.literal("\n " + (membro.equals(party.lider) ? "♛ " : "• ")))
          .append(online != null ? online.getDisplayName() : Component.literal(nome(membro)).append(" (")
              .append(Component.translatable(FRASE + "offline")).append(")").withStyle(ChatFormatting.GRAY));
    }
    quem.sendSystemMessage(lista);
    return 1;
  }

  // Sai da party nossa (saiu por conta, foi tirado). Sobrou um: a party acaba. Era o líder: o próximo vira
  private static void tirar(MinecraftServer servidor, UUID quem, Component aviso) {
    Party party = DE.remove(quem);
    if (party == null) {
      return;
    }
    try {
      tirarDa(servidor, party, quem, aviso);
    } finally {
      salvar(servidor);
    }
  }

  private static void tirarDa(MinecraftServer servidor, Party party, UUID quem, Component aviso) {
    party.membros.remove(quem);
    avisarTodos(servidor, party, aviso);
    if (party.membros.size() <= 1) {
      for (UUID ultimo : party.membros) {
        DE.remove(ultimo);
        ServerPlayer online = servidor.getPlayerList().getPlayer(ultimo);
        if (online != null) {
          online.sendSystemMessage(Component.translatable(FRASE + "desfeita"));
        }
      }
      return;
    }
    if (party.lider.equals(quem)) {
      party.lider = party.membros.get(0);
      ServerPlayer novo = servidor.getPlayerList().getPlayer(party.lider);
      if (novo != null) {
        avisarTodos(servidor, party, Component.translatable(FRASE + "novo_lider", novo.getDisplayName()));
      }
    }
    CobblemonCombatZA.LOGGER.info("PARTY: {} saiu ({} na party)", quem, party.membros.size());
  }

  // Saiu do servidor: ele continua na party (na nossa, guardada no mundo; na do OPAC, o OPAC guarda) e aparece offline
  public static void aoDesconectar(ServerPlayer jogador) {
    CONVITES.remove(jogador.getUUID());
    ULTIMO_ENVIO.remove(jogador.getUUID());
  }

  // O mundo abriu: as parties guardadas voltam (com o OPAC, a party é a dele)
  public static void carregar(MinecraftServer servidor) {
    DE.clear();
    NOMES.clear();
    if (comOpac) {
      return;
    }
    PartiesSalvas salvas = salvas(servidor);
    for (PartiesSalvas.Salva salva : salvas.parties()) {
      Party party = new Party();
      party.lider = salva.lider();
      party.membros.addAll(salva.membros());
      party.membros.forEach(membro -> DE.put(membro, party));
    }
    NOMES.putAll(salvas.nomes());
    CobblemonCombatZA.LOGGER.info("PARTY: {} party(s) guardada(s) voltaram", salvas.parties().size());
  }

  // Guarda as parties de 2 ou mais (a de 1, esperando o primeiro aceitar, não) e os nomes dos membros
  private static void salvar(MinecraftServer servidor) {
    if (comOpac) {
      return;
    }
    List<PartiesSalvas.Salva> parties = new ArrayList<>();
    Map<UUID, String> nomes = new HashMap<>();
    for (Party party : new java.util.LinkedHashSet<>(DE.values())) {
      if (party.membros.size() >= 2) {
        parties.add(new PartiesSalvas.Salva(party.lider, List.copyOf(party.membros)));
        party.membros.forEach(membro -> nomes.put(membro, nome(membro)));
      }
    }
    salvas(servidor).trocar(parties, nomes);
  }

  private static PartiesSalvas salvas(MinecraftServer servidor) {
    return servidor.overworld().getDataStorage().computeIfAbsent(PartiesSalvas.FABRICA, PartiesSalvas.ARQUIVO);
  }

  // O nome que ficou guardado (para quem está offline)
  private static String nome(UUID jogador) {
    return NOMES.getOrDefault(jogador, jogador.toString().substring(0, 8));
  }

  // A cada 10 ticks: a lista da party vai para o jogo de cada um (só quando muda); os convites velhos somem
  public static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    if (agora % A_CADA_TICKS != 0) {
      return;
    }
    CONVITES.values().removeIf(convite -> agora > convite.ate());
    // Quem trocou de nome desde a última vez: o nome guardado acompanha
    boolean mudouNome = false;
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      String nome = jogador.getGameProfile().getName();
      if (DE.containsKey(jogador.getUUID()) && !nome.equals(NOMES.put(jogador.getUUID(), nome))) {
        mudouNome = true;
      }
    }
    if (mudouNome) {
      salvar(servidor);
    }
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      PartyPayload pacote = montar(jogador);
      if (!pacote.equals(ULTIMO_ENVIO.get(jogador.getUUID())) && ServerPlayNetworking.canSend(jogador, PartyPayload.TIPO)) {
        ULTIMO_ENVIO.put(jogador.getUUID(), pacote);
        ServerPlayNetworking.send(jogador, pacote);
      }
    }
  }

  // Os OUTROS da party (você já sabe a sua vida); quem te convidou; e se você é o líder
  private static PartyPayload montar(ServerPlayer jogador) {
    List<PartyPayload.Membro> membros = new ArrayList<>();
    for (Membro membro : outrosMembros(jogador)) {
      ServerPlayer online = jogador.server.getPlayerList().getPlayer(membro.jogador());
      float vida = online == null ? 0f
          : Math.round(online.getHealth() / Math.max(1f, online.getMaxHealth()) * PASSOS_DA_VIDA) / PASSOS_DA_VIDA;
      // A posição de meio em meio bloco (para não reenviar a lista a cada passinho)
      PartyPayload.Onde onde = online == null ? new PartyPayload.Onde(0, 0, 0, "")
          : new PartyPayload.Onde(Math.round(online.getX() * 2) / 2.0, Math.round(online.getY() * 2) / 2.0,
              Math.round(online.getZ() * 2) / 2.0, online.level().dimension().location().toString());
      membros.add(new PartyPayload.Membro(membro.jogador(), online == null ? membro.nome() : online.getGameProfile().getName(),
          vida, online != null && GerenciarCombates.estaEmCombate(online), membro.lider(), onde));
    }
    Convite convite = CONVITES.get(jogador.getUUID());
    ServerPlayer quemConvidou = convite == null ? null : jogador.server.getPlayerList().getPlayer(convite.quemConvidou());
    return new PartyPayload(membros, quemConvidou == null ? "" : quemConvidou.getGameProfile().getName(),
        souLider(jogador), comOpac);
  }

  // Os outros da party (com o OPAC, também os offline). Os waypoints da party (PontosDaParty) usam
  static List<Membro> outrosMembros(ServerPlayer jogador) {
    if (comOpac) {
      try {
        return PonteDoOpac.outrosMembros(jogador);
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    List<Membro> outros = new ArrayList<>();
    Party party = DE.get(jogador.getUUID());
    if (party != null) {
      for (UUID membro : party.membros) {
        if (!membro.equals(jogador.getUUID())) {
          ServerPlayer online = jogador.server.getPlayerList().getPlayer(membro);
          outros.add(new Membro(membro, online == null ? nome(membro) : online.getGameProfile().getName(),
              membro.equals(party.lider)));
        }
      }
    }
    return outros;
  }

  private static boolean souLider(ServerPlayer jogador) {
    if (comOpac) {
      try {
        return PonteDoOpac.ehDono(jogador);
      } catch (LinkageError erro) {
        opacQuebrou(erro);
      }
    }
    Party party = DE.get(jogador.getUUID());
    return party != null && party.lider.equals(jogador.getUUID());
  }

  private static MutableComponent botao(String nome, String comando, ChatFormatting cor) {
    return Component.translatable(FRASE + "botao_" + nome).withStyle(estilo -> estilo.withColor(cor).withBold(true)
        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, comando)));
  }

  private static void avisarTodos(MinecraftServer servidor, Party party, Component aviso) {
    for (UUID membro : party.membros) {
      ServerPlayer online = servidor.getPlayerList().getPlayer(membro);
      if (online != null) {
        online.sendSystemMessage(aviso);
      }
    }
  }

  private static void opacQuebrou(LinkageError erro) {
    if (comOpac) {
      comOpac = false;
      CobblemonCombatZA.LOGGER.error("PARTY: o Open Parties and Claims mudou e a ponte com ele não funciona mais; a party volta a ser a "
          + "do Cobblemon Combat ZA", erro);
    }
  }

  static int erro(ServerPlayer jogador, String chave, Object... argumentos) {
    jogador.sendSystemMessage(Component.translatable(FRASE + "erro." + chave, argumentos).withStyle(ChatFormatting.RED));
    return 0;
  }

  public static void limpar() {
    DE.clear();
    NOMES.clear();
    CONVITES.clear();
    ULTIMO_ENVIO.clear();
  }
}
