package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.ActiveRidingContext;
import com.cobblemon.mod.common.api.riding.behaviour.RidingController;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.ia.Andar;
import dev.victor.combatza.rede.CorridaMontadaPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// 7b (dele, 19/10: "quando a gente tá montado em um Charizard, dá para brigar em cima dele, e aí se for uma habilidade de
// perto, tem que fazer o Charizard assumir o controle e ir até o bicho atacar mesmo com a gente em cima"). As decisões dele:
// os golpes são do selecionado, como sempre (com a setinha na montaria, é ela que ataca); depois da pancada, o controle volta
// na hora, ali mesmo; a corrida não dá para cancelar (termina na pancada, ou quando não alcança, nos 3 s da Corrida).
// Como (pesquisa com javap no Cobblemon 1.7.3 e nas fontes do Minecraft): numa montaria, quem calcula o movimento é o JOGO de
// quem está em cima (o "condutor", getControllingPassenger), e o servidor só confere. Durante a corrida, o MontariaSemCondutorMixin
// faz a montaria ficar SEM condutor nos dois lados: no servidor ela vira um Pokémon comum, e a nossa Corrida leva ela; no seu
// jogo, ele para de pilotar e passa a aceitar a posição do servidor. O seu jogo fica sabendo pelo CorridaMontadaPayload. Os
// estados ficam em dois mapas (o do servidor e o do cliente), porque no mundo de um jogador só os dois rodam no mesmo programa
public class CorridaMontada {
  // Voando, reta pelo ar a 0,6 bloco por tick (24 blocos em 2 s, dentro dos 3 s da Corrida)
  private static final double VELOCIDADE_DO_VOO = 0.6;
  // Por segurança, nenhuma corrida montada passa de 5 s (a Corrida desiste em 3)
  private static final long SEGURANCA = 5L * SharedConstants.TICKS_PER_SECOND;

  private record Montada(PokemonEntity montaria, Execucao execucao, long comecou) {
  }

  // No servidor: pelo número da entidade da montaria
  private static final Map<Integer, Montada> NO_SERVIDOR = new HashMap<>();
  // No seu jogo: as montarias que estão correndo sozinhas agora
  private static final Set<Integer> NO_CLIENTE = new HashSet<>();

  private CorridaMontada() {
  }

  // Começo da corrida (Corrida.atualizar, a cada tick): se quem ataca tem um jogador em cima e o golpe é de contato, ela
  // assume. Devolve se esta corrida é montada
  public static boolean assumir(Execucao execucao) {
    PokemonEntity montaria = execucao.getEntidade();
    if (montaria == null || execucao.getPokemon().isWild() || !EfeitosDosGolpes.fazContato(execucao.getGolpe())) {
      return false;
    }
    Montada atual = NO_SERVIDOR.get(montaria.getId());
    if (atual != null) {
      return atual.execucao() == execucao;
    }
    if (montaria.getPassengers().stream().noneMatch(Player.class::isInstance)) {
      return false;
    }
    long agora = montaria.getServer() == null ? 0 : montaria.getServer().getTickCount();
    NO_SERVIDOR.put(montaria.getId(), new Montada(montaria, execucao, agora));
    montaria.getNavigation().stop();
    avisar(montaria, true);
    CobblemonCombatZA.LOGGER.info("MONTADA: {} assumiu para {} ({})", montaria.getPokemon().getSpecies().getName(),
        execucao.getGolpe().getName(), voando(montaria) ? "voando, reto pelo ar" : "pelo chão");
    return true;
  }

  // O Mixin pergunta a cada vez que o jogo quer saber quem pilota (é muitas vezes: com os mapas vazios, sai na hora)
  public static boolean semCondutor(PokemonEntity montaria) {
    if (montaria.level().isClientSide()) {
      return !NO_CLIENTE.isEmpty() && NO_CLIENTE.contains(montaria.getId());
    }
    return !NO_SERVIDOR.isEmpty() && NO_SERVIDOR.containsKey(montaria.getId());
  }

  // Voando agora? (o estilo de montaria do Cobblemon: chão, água ou ar)
  public static boolean voando(PokemonEntity montaria) {
    RidingController controle = montaria.getRidingController();
    ActiveRidingContext contexto = controle == null ? null : controle.getContext();
    return contexto != null && contexto.getStyle() == RidingStyle.AIR;
  }

  // Pelo ar: reto até o alvo, a cada tick (no chão, a Corrida usa o caminho de sempre)
  public static void voarAte(PokemonEntity montaria, Entity alvo) {
    montaria.getNavigation().stop();
    Vec3 caminho = alvo.getBoundingBox().getCenter().subtract(montaria.position().add(0, montaria.getBbHeight() / 2, 0));
    if (caminho.lengthSqr() > 1.0E-4) {
      montaria.setDeltaMovement(caminho.normalize().scale(VELOCIDADE_DO_VOO));
      montaria.hasImpulse = true;
    }
  }

  // A cada tick, depois da agenda dos golpes: o controle volta na pancada, quando o golpe acabou (não alcançou, cancelado,
  // recolhido), quando ninguém está mais em cima, quando a montaria sumiu, ou passados 5 s
  public static void atualizar(MinecraftServer servidor) {
    if (NO_SERVIDOR.isEmpty()) {
      return;
    }
    long agora = servidor.getTickCount();
    for (Montada montada : List.copyOf(NO_SERVIDOR.values())) {
      PokemonEntity montaria = montada.montaria();
      Execucao execucao = montada.execucao();
      String porque = null;
      if (!montaria.isAlive() || montaria.isRemoved()) {
        porque = "a montaria sumiu";
      } else if (execucao.jaImpactou()) {
        porque = "pancada";
      } else if (AgendaDeGolpes.emExecucao(execucao.getPokemon()) != execucao) {
        porque = "o golpe acabou";
      } else if (montaria.getPassengers().stream().noneMatch(Player.class::isInstance)) {
        porque = "ninguém em cima";
      } else if (agora - montada.comecou() > SEGURANCA) {
        porque = "5 s";
      }
      if (porque != null) {
        devolver(montaria, porque);
      }
    }
  }

  private static void devolver(PokemonEntity montaria, String porque) {
    NO_SERVIDOR.remove(montaria.getId());
    if (montaria.isAlive() && !montaria.isRemoved()) {
      Andar.parar(montaria);
      avisar(montaria, false);
    }
    CobblemonCombatZA.LOGGER.info("MONTADA: controle devolvido ({})", porque);
  }

  // Avisa quem vê a montaria e quem está em cima (com a posição do servidor, para o seu jogo não dar tranco)
  private static void avisar(PokemonEntity montaria, boolean correndo) {
    CorridaMontadaPayload pacote = new CorridaMontadaPayload(montaria.getId(), correndo, montaria.getX(), montaria.getY(),
        montaria.getZ());
    Set<ServerPlayer> quem = new HashSet<>(PlayerLookup.tracking(montaria));
    for (Entity passageiro : montaria.getPassengers()) {
      if (passageiro instanceof ServerPlayer jogador) {
        quem.add(jogador);
      }
    }
    for (ServerPlayer jogador : quem) {
      if (ServerPlayNetworking.canSend(jogador, CorridaMontadaPayload.TIPO)) {
        ServerPlayNetworking.send(jogador, pacote);
      }
    }
  }

  // O seu jogo recebeu o aviso (CorridaMontadaNoCliente)
  public static void marcarNoCliente(int montaria, boolean correndo) {
    if (correndo) {
      NO_CLIENTE.add(montaria);
    } else {
      NO_CLIENTE.remove(montaria);
    }
  }

  public static void esquecerNoCliente() {
    NO_CLIENTE.clear();
  }

  // Saiu do jogo: as corridas em que ele estava em cima acabam
  public static void esquecer(ServerPlayer jogador) {
    for (Montada montada : List.copyOf(NO_SERVIDOR.values())) {
      if (montada.montaria().getPassengers().contains(jogador)) {
        devolver(montada.montaria(), "saiu do jogo");
      }
    }
  }

  public static void limpar() {
    NO_SERVIDOR.clear();
  }
}
