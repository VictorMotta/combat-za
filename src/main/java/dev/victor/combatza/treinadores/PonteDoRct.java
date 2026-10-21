package dev.victor.combatza.treinadores;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

// 21/10 (treinadores): tudo o que a batalha em tempo real pede ao RCT (Radical Cobblemon Trainers) fica AQUI. O RCT não
// entra no build (nem todo servidor tem ele, e o .jar dele não é baixado para compilar), então cada chamada é por REFLEXÃO:
// o nome do método, procurado na hora. Os nomes foram lidos com javap no rctmod-fabric-1.21.1-0.18.1-beta.jar e no
// rctapi-fabric-1.21.1-0.15.2-beta.jar (os métodos do RCT não mudam de nome no jogo; só os do Minecraft mudam, e esses vão
// como Entity.class e Player.class, que o Loom acerta no nosso .jar). Se o RCT mudar numa versão nova (um método sumiu), o
// erro vai para o log e quem chamou volta para a batalha por turnos de sempre. O caminho do próprio RCT (javap):
// - falar com o treinador: TrainerMob.startBattleWith(jogador) → canBattleAgainst → RCTMod.makeBattle (o rctapi monta a
//   batalha por turnos do Cobblemon) → se começou: setOpponent(jogador), TrainerManager.addBattle(jogador, treinador) e a
//   fala "on_battle_start" (ChatUtils.reply);
// - o fim (ModServer.onBattleVictory → removeBattleFromInitiator): TrainerManager.getBattle(UUID do jogador) → removeBattle
//   → TrainerBattle.distributeRewards(venceu). A vitória dá o progresso da série, o limite de nível, a contagem de derrotas
//   e o prêmio (o TrainerMob.finishBattle: a espera do treinador, a fala e o loot); a derrota conta a vitória para o
//   treinador
final class PonteDoRct {
  private static final String MOD = "rctmod";
  private static final String CLASSE_DO_TREINADOR = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob";
  private static final String CLASSE_DO_RCT = "com.gitlab.srcmc.rctmod.api.RCTMod";
  private static final String CLASSE_DAS_FALAS = "com.gitlab.srcmc.rctmod.api.utils.ChatUtils";
  private static final String FALA_DO_COMECO = "on_battle_start";

  // Um Pokémon do time do treinador nos dados do RCT: a espécie e a lista do item segurado ("heldItem" é uma lista de
  // alternativas: o rctapi dá ao Pokémon o primeiro item que existe no jogo)
  record PokemonDoTime(String especie, List<String> itensSegurados) {
  }

  // O que o RCT sabe do time do treinador: os Pokémon, a bolsa (item → quantidade; só uns 370 dos 1.559 treinadores têm uma)
  // e o máximo de itens por batalha (−1 = sem limite; os que têm bolsa usam 2 ou 4)
  record DadosDoTime(List<PokemonDoTime> pokemons, Map<String, Integer> bolsa, int maximoDeItens) {
  }

  private static Class<?> classeDoTreinador;
  private static boolean procurouAClasse;

  private PonteDoRct() {
  }

  static boolean instalado() {
    return FabricLoader.getInstance().isModLoaded(MOD);
  }

  // A classe TrainerMob do RCT, ou null sem o RCT (procurada uma vez só)
  private static Class<?> classeDoTreinador() {
    if (!procurouAClasse) {
      procurouAClasse = true;
      if (instalado()) {
        try {
          classeDoTreinador = Class.forName(CLASSE_DO_TREINADOR, false, PonteDoRct.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError erro) {
          quebrou("achar o treinador", erro);
        }
      }
    }
    return classeDoTreinador;
  }

  // É um treinador do RCT (o TrainerMob; a Associação dos Treinadores não batalha)
  static boolean ehTreinador(Entity entidade) {
    Class<?> classe = classeDoTreinador();
    return classe != null && classe.isInstance(entidade);
  }

  // O RCT deixa este treinador batalhar com ele agora? (a espera do treinador, a série, o limite de nível, se um dos dois já
  // está em batalha...). Na dúvida (erro), não
  static boolean podeBatalhar(LivingEntity treinador, ServerPlayer jogador) {
    try {
      return (Boolean) treinador.getClass().getMethod("canBattleAgainst", Entity.class).invoke(treinador, jogador);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("canBattleAgainst", erro);
      return false;
    }
  }

  // A batalha por turnos de sempre do RCT (a escolha "Por turnos"): o mesmo que falar com o treinador. false = deu erro
  static boolean porTurnos(LivingEntity treinador, ServerPlayer jogador) {
    try {
      treinador.getClass().getMethod("startBattleWith", Player.class).invoke(treinador, jogador);
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("startBattleWith", erro);
      return false;
    }
  }

  // A batalha em tempo real começou: o RCT anota como se fosse a dele (o que o startBattleWith faz depois que a batalha do
  // Cobblemon começa). false = deu erro (e o que já foi anotado é desfeito)
  static boolean comecou(LivingEntity treinador, ServerPlayer jogador) {
    Class<?> classe = classeDoTreinador();
    if (classe == null) {
      return false;
    }
    try {
      mudarOAdversario(classe, treinador, jogador);
      Object gerente = gerente();
      gerente.getClass().getMethod("addBattle", Player.class, classe).invoke(gerente, jogador, treinador);
      Class.forName(CLASSE_DAS_FALAS, false, PonteDoRct.class.getClassLoader())
          .getMethod("reply", classe, Player.class, String[].class)
          .invoke(null, treinador, jogador, new String[] { FALA_DO_COMECO });
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("começar a batalha", erro);
      cancelar(treinador, jogador.getUUID());
      return false;
    }
  }

  // O fim, como o ModServer do RCT faz no fim da batalha por turnos: tira a batalha da lista e distribui o resultado. false
  // = o RCT não tinha a batalha (ou deu erro)
  static boolean terminou(UUID jogador, boolean venceu) {
    try {
      Object gerente = gerente();
      Optional<?> batalha = (Optional<?>) gerente.getClass().getMethod("getBattle", UUID.class).invoke(gerente, jogador);
      if (batalha.isEmpty()) {
        return false;
      }
      gerente.getClass().getMethod("removeBattle", UUID.class).invoke(gerente, jogador);
      Object daBatalha = batalha.get();
      daBatalha.getClass().getMethod("distributeRewards", boolean.class).invoke(daBatalha, venceu);
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("distributeRewards", erro);
      return false;
    }
  }

  // A batalha não valeu (o treinador sumiu, o servidor fechou, o primeiro Pokémon não saiu): o RCT esquece dela, sem
  // resultado (o mesmo que o TrainerMob.cancelBattle, sem o aviso no log de "não está em batalha" do Cobblemon)
  static void cancelar(LivingEntity treinador, UUID jogador) {
    try {
      Object gerente = gerente();
      gerente.getClass().getMethod("removeBattle", UUID.class).invoke(gerente, jogador);
      Class<?> classe = classeDoTreinador();
      if (classe != null && treinador != null && adversario(treinador) instanceof Entity adversario
          && jogador.equals(adversario.getUUID())) {
        mudarOAdversario(classe, treinador, null);
      }
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("cancelar a batalha", erro);
    }
  }

  // Os dados do time do treinador (TrainerManager.getData(treinador).getTrainerTeam()); null = não deu para ler
  static DadosDoTime dadosDoTime(LivingEntity treinador) {
    Class<?> classe = classeDoTreinador();
    if (classe == null) {
      return null;
    }
    try {
      Object gerente = gerente();
      Object dados = gerente.getClass().getMethod("getData", classe).invoke(gerente, treinador);
      Object time = dados.getClass().getMethod("getTrainerTeam").invoke(dados);
      List<PokemonDoTime> pokemons = new ArrayList<>();
      for (Object modelo : (List<?>) chamar(time, "getTeam")) {
        pokemons.add(new PokemonDoTime(String.valueOf(chamar(modelo, "getSpecies")), itensSegurados(modelo)));
      }
      Map<String, Integer> bolsa = new LinkedHashMap<>();
      Object daBolsa = chamar(time, "getBag");
      if (daBolsa instanceof List<?> itensDaBolsa) {
        for (Object item : itensDaBolsa) {
          bolsa.merge(String.valueOf(chamar(item, "getItem")), (Integer) chamar(item, "getQuantity"), Integer::sum);
        }
      }
      Object regras = chamar(time, "getBattleRules");
      int maximo = regras == null ? -1 : (Integer) chamar(regras, "getMaxItemUses");
      return new DadosDoTime(pokemons, bolsa, maximo);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      quebrou("ler o time do treinador", erro);
      return null;
    }
  }

  // A lista do "heldItem" de um Pokémon dos dados. Sem "heldItem" no arquivo, o getHeldItems do rctapi quebra (o campo fica
  // vazio): sem item
  private static List<String> itensSegurados(Object modelo) {
    try {
      String[] itens = (String[]) chamar(modelo, "getHeldItems");
      return itens == null ? List.of() : Arrays.stream(itens).filter(Objects::nonNull).toList();
    } catch (ReflectiveOperationException | RuntimeException erro) {
      return List.of();
    }
  }

  // O id do treinador nos dados do RCT ("leader_brock_001"), para o log
  static String id(LivingEntity treinador) {
    try {
      return String.valueOf(chamar(treinador, "getTrainerId"));
    } catch (ReflectiveOperationException | RuntimeException | LinkageError erro) {
      return treinador.getName().getString();
    }
  }

  private static Object adversario(LivingEntity treinador) throws ReflectiveOperationException {
    return chamar(treinador, "getOpponent");
  }

  // O setOpponent é "protected" no TrainerMob: a reflexão abre a porta
  private static void mudarOAdversario(Class<?> classe, LivingEntity treinador, ServerPlayer jogador)
      throws ReflectiveOperationException {
    Method mudar = classe.getDeclaredMethod("setOpponent", Player.class);
    mudar.setAccessible(true);
    mudar.invoke(treinador, jogador);
  }

  // RCTMod.getInstance().getTrainerManager()
  private static Object gerente() throws ReflectiveOperationException {
    Class<?> rct = Class.forName(CLASSE_DO_RCT, false, PonteDoRct.class.getClassLoader());
    Object instancia = rct.getMethod("getInstance").invoke(null);
    return rct.getMethod("getTrainerManager").invoke(instancia);
  }

  private static Object chamar(Object dono, String metodo) throws ReflectiveOperationException {
    return dono.getClass().getMethod(metodo).invoke(dono);
  }

  private static void quebrou(String oQue, Throwable erro) {
    Throwable causa = erro instanceof InvocationTargetException invocacao && invocacao.getCause() != null
        ? invocacao.getCause()
        : erro;
    CobblemonCombatZA.LOGGER.warn("TREINADOR: o RCT não respondeu ({}): {}", oQue, causa.toString());
  }
}
