package dev.victor.combatza.combate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.CobblemonCombatZA;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

// 31/10 (raid em tempo real, dele: "quero que altere o mod dele para lutarmos usando o nosso mod"): tudo o que a nossa
// batalha pede ao Cobblemon Raid Dens fica AQUI. Como o RCT (treinadores.PonteDoRct), ele não entra no build: cada chamada é
// por REFLEXÃO, com os nomes lidos com javap no cobblemonraiddens-fabric-0.11.3+1.21.1.jar. Nada do mod de raid muda: a raid
// continua sendo dele (a barra de vida no alto, o tempo, as vidas, a vitória, as recompensas e a captura); a nossa batalha só
// tira a vida do chefão pelo caminho dele. O caminho (javap):
// - o chefão guarda o número da raid (IRaidAccessor.crd_getRaidId), e a raid valendo está em RaidHelper.ACTIVE_RAIDS;
// - a vida do chefão é da raid (RaidInstance.currentHealth: a vida do Pokémon × o multiplicador do chefão, e mais um tanto a
//   cada jogador que entra). syncHealth(jogador, batalha, dano) tira, conta o dano de cada um (as recompensas pedem um mínimo)
//   e, no zero, encerra com vitória (stopRaid(true): as recompensas e a captura);
// - removePlayer(jogador, batalha, false) é a derrota de um jogador (perde uma vida; sem vidas, fica de fora; todos de fora, a
//   raid falha). Só vale com a raid "em andamento", e ela só passa a isso quando entra a primeira batalha por turnos
//   (addBattle): aqui, quando começa a nossa;
// - os pedidos para entrar: RaidHelper.REQUEST_QUEUE (o dono da raid → RequestHandler, por nome de quem pediu), e o "aceito"
//   do dono é o RequestResponsePacket(true, nome).handleServer(dono).
// Se o mod de raid mudar numa versão nova, o erro vai para o log e a parte que pediu volta sem fazer nada
final class PonteDaRaid {
  private static final String MOD = "cobblemonraiddens";
  private static final String CLASSE_DA_AJUDA = "com.necro.raid.dens.common.raids.helpers.RaidHelper";
  private static final String CLASSE_DA_RESPOSTA = "com.necro.raid.dens.common.network.packets.RequestResponsePacket";
  private static final String NAO_COMECOU = "NOT_STARTED";
  private static final String EM_ANDAMENTO = "IN_PROGRESS";

  // Os métodos já achados (a vida do chefão é lida a cada tick pela IA e pelo painel)
  private static final Map<String, Method> METODOS = new HashMap<>();
  // Cada erro vai para o log uma vez só (a vida do chefão é lida a cada tick)
  private static final Set<String> AVISADOS = new HashSet<>();

  private PonteDaRaid() {
  }

  static boolean instalado() {
    return FabricLoader.getInstance().isModLoaded(MOD);
  }

  // A raid do chefão (a RaidInstance), ou null: sem o mod de raid, sem raid valendo ou já terminada
  static Object raidDe(PokemonEntity chefe) {
    if (!instalado()) {
      return null;
    }
    try {
      Object numero = metodo(chefe.getClass(), "crd_getRaidId").invoke(chefe);
      if (numero == null) {
        return null;
      }
      Object raid = valendo().get(numero);
      return raid == null || (Boolean) metodo(raid.getClass(), "isFinished").invoke(raid) ? null : raid;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("achar a raid", erro);
      return null;
    }
  }

  private static Map<?, ?> valendo() throws ReflectiveOperationException {
    return (Map<?, ?>) classe(CLASSE_DA_AJUDA).getField("ACTIVE_RAIDS").get(null);
  }

  static boolean estaNela(Object raid, ServerPlayer jogador) {
    return sim(raid, "isPlayerIn", jogador);
  }

  // Perdeu todas as vidas (fica de fora até a raid acabar)
  static boolean saiu(Object raid, ServerPlayer jogador) {
    return sim(raid, "hasFailed", jogador);
  }

  // A nossa batalha com o chefão começou: o jogador entra na raid (se ainda não estava) e ela passa a "em andamento"
  static void entrar(Object raid, ServerPlayer jogador) {
    try {
      if (!estaNela(raid, jogador)) {
        metodo(raid.getClass(), "addPlayer", ServerPlayer.class).invoke(raid, jogador);
      }
      Field campo = raid.getClass().getDeclaredField("raidState");
      campo.setAccessible(true);
      if (campo.get(raid) instanceof Enum<?> estado && NAO_COMECOU.equals(estado.name())) {
        for (Object outro : estado.getDeclaringClass().getEnumConstants()) {
          if (EM_ANDAMENTO.equals(((Enum<?>) outro).name())) {
            campo.set(raid, outro);
          }
        }
      }
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("entrar na raid", erro);
    }
  }

  // Tira da vida da raid, e o dano conta para o jogador (ele precisa estar na raid: sem ele, conta para o primeiro dela). Se um
  // roteiro do chefão (as ações que o mod de raid dispara quando ele leva dano) quebrar sem a batalha por turnos, o dano entra
  // do mesmo jeito
  static void ferir(Object raid, ServerPlayer jogador, float pv) {
    float antes = vida(raid);
    ServerPlayer quem = jogador != null && estaNela(raid, jogador) ? jogador : primeiro(raid);
    try {
      if (quem == null) {
        tirarDireto(raid, antes, pv);
        return;
      }
      metodo(raid.getClass(), "syncHealth", ServerPlayer.class, PokemonBattle.class, float.class)
          .invoke(raid, quem, null, pv);
    } catch (InvocationTargetException erro) {
      quebrou("syncHealth", erro);
      if (vida(raid) >= antes) {
        tirarDireto(raid, antes, pv);
      }
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("syncHealth", erro);
    }
  }

  private static void tirarDireto(Object raid, float antes, float pv) {
    try {
      float depois = Math.max(0, antes - pv);
      Field campo = raid.getClass().getDeclaredField("currentHealth");
      campo.setAccessible(true);
      campo.setFloat(raid, depois);
      if (depois <= 0) {
        metodo(raid.getClass(), "queueStopRaid").invoke(raid);
      }
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("tirar a vida", erro);
    }
  }

  private static ServerPlayer primeiro(Object raid) {
    try {
      List<?> jogadores = (List<?>) metodo(raid.getClass(), "getPlayers").invoke(raid);
      return jogadores.isEmpty() ? null : (ServerPlayer) jogadores.get(0);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("getPlayers", erro);
      return null;
    }
  }

  static void curar(Object raid, float pv) {
    try {
      metodo(raid.getClass(), "healBoss", float.class).invoke(raid, pv);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("healBoss", erro);
    }
  }

  // A derrota de um jogador na raid (todos os Pokémon dele desmaiaram)
  static void perdeu(Object raid, ServerPlayer jogador) {
    try {
      metodo(raid.getClass(), "removePlayer", ServerPlayer.class, PokemonBattle.class, boolean.class)
          .invoke(raid, jogador, null, false);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("removePlayer", erro);
    }
  }

  // Uma das vezes que o jogador pode torcer nesta raid (RaidInstance.playerMap → RaidPlayer.useCheer, o limite do chefão).
  // false = acabaram. Se o mod de raid não responder, deixa (o item é gasto do mesmo jeito)
  static boolean gastarUmApoio(Object raid, ServerPlayer jogador) {
    try {
      Field campo = raid.getClass().getDeclaredField("playerMap");
      campo.setAccessible(true);
      Object doJogador = ((Map<?, ?>) campo.get(raid)).get(jogador.getUUID());
      if (doJogador == null) {
        return true;
      }
      Method torcer = doJogador.getClass().getDeclaredMethod("useCheer");
      torcer.setAccessible(true);
      return (Boolean) torcer.invoke(doJogador);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("useCheer", erro);
      return true;
    }
  }

  static float vida(Object raid) {
    return numero(raid, "getCurrentHealth");
  }

  static float vidaMaxima(Object raid) {
    return numero(raid, "getMaxHealth");
  }

  // Os donos de raid com pedidos para entrar esperando
  static Set<?> donosComPedidos() {
    if (!instalado()) {
      return Set.of();
    }
    try {
      return Set.copyOf(pedidos().keySet());
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("REQUEST_QUEUE", erro);
      return Set.of();
    }
  }

  private static Map<?, ?> pedidos() throws ReflectiveOperationException {
    return (Map<?, ?>) classe(CLASSE_DA_AJUDA).getField("REQUEST_QUEUE").get(null);
  }

  // Este jogador pediu para entrar na raid deste dono (e ainda espera a resposta)
  static boolean pediu(UUID dono, ServerPlayer quem) {
    try {
      Object pedidosDoDono = pedidos().get(dono);
      if (pedidosDoDono == null) {
        return false;
      }
      Object quemPediu = metodo(pedidosDoDono.getClass(), "getPlayer", String.class)
          .invoke(pedidosDoDono, quem.getName().getString());
      return quemPediu instanceof Player jogador && jogador.getUUID().equals(quem.getUUID());
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("getPlayer", erro);
      return false;
    }
  }

  // O "aceito" do dono, como se ele tivesse clicado: o mod de raid confere a lotação, põe o jogador na raid e leva ele até lá
  static void aceitar(ServerPlayer dono, ServerPlayer quem) {
    try {
      Object resposta = classe(CLASSE_DA_RESPOSTA).getConstructor(boolean.class, String.class)
          .newInstance(true, quem.getName().getString());
      metodo(resposta.getClass(), "handleServer", ServerPlayer.class).invoke(resposta, dono);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("aceitar o pedido", erro);
    }
  }

  private static boolean sim(Object raid, String nome, ServerPlayer jogador) {
    try {
      return (Boolean) metodo(raid.getClass(), nome, ServerPlayer.class).invoke(raid, jogador);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou(nome, erro);
      return false;
    }
  }

  private static float numero(Object raid, String nome) {
    try {
      return (Float) metodo(raid.getClass(), nome).invoke(raid);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou(nome, erro);
      return 0f;
    }
  }

  private static Class<?> classe(String nome) throws ClassNotFoundException {
    return Class.forName(nome, false, PonteDaRaid.class.getClassLoader());
  }

  private static Method metodo(Class<?> classe, String nome, Class<?>... tipos) throws NoSuchMethodException {
    String chave = classe.getName() + "#" + nome + "#" + tipos.length;
    Method metodo = METODOS.get(chave);
    if (metodo == null) {
      metodo = classe.getMethod(nome, tipos);
      METODOS.put(chave, metodo);
    }
    return metodo;
  }

  private static void quebrou(String oQue, Throwable erro) {
    if (!AVISADOS.add(oQue)) {
      return;
    }
    Throwable causa = erro instanceof InvocationTargetException invocacao && invocacao.getCause() != null
        ? invocacao.getCause()
        : erro;
    CobblemonCombatZA.LOGGER.warn("RAID: o mod de raid não respondeu ({}): {}", oQue, causa.toString());
  }
}
