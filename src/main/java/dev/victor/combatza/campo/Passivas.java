package dev.victor.combatza.campo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.EterEElixir;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.rede.PassivasPayload;
import dev.victor.combatza.rede.PassivasPayload.Passiva;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// P5-10 (veto dele, 20/10: "se for um status deixa tanto dentro quanto fora da batalha um iconezinho com tempo contando, como
// se fosse uma passiva de RPG"): a cada 5 ticks, a lista do que vale para cada jogador (na batalha, o campo dela; fora, o
// pessoal) vai para o seu jogo, só quando muda. Na batalha, as barreiras dos selvagens também (com a borda rosa)
public class Passivas {
  private static final int A_CADA_TICKS = 5;
  private static final Map<UUID, PassivasPayload> ULTIMO_ENVIO = new HashMap<>();

  private Passivas() {
  }

  public static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    if (agora % A_CADA_TICKS != 0) {
      return;
    }
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      Combate combate = GerenciarCombates.combateDoJogador(jogador);
      Arena arena = combate != null ? Arenas.daBatalha(combate) : Arenas.doJogador(jogador);
      List<Passiva> passivas = montar(arena, agora);
      // P1-7: o Éter e o Elixir (a recarga mais rápida) dos Pokémon dele
      passivas.addAll(EterEElixir.passivasDe(jogador, agora));
      PassivasPayload pacote = new PassivasPayload(passivas);
      if (!pacote.equals(ULTIMO_ENVIO.get(jogador.getUUID()))
          && ServerPlayNetworking.canSend(jogador, PassivasPayload.TIPO)) {
        ULTIMO_ENVIO.put(jogador.getUUID(), pacote);
        ServerPlayNetworking.send(jogador, pacote);
      }
    }
  }

  private static List<Passiva> montar(Arena arena, long agora) {
    List<Passiva> passivas = new ArrayList<>();
    CampoDaBatalha campo = arena.campo();
    if (campo.getDoGolpe() != Clima.NENHUM) {
      passivas.add(comPrazo(campo.getDoGolpe().nome(), Passiva.TODOS, campo.getPrazoDoClima(), agora));
    } else if (campo.getDoMundo() != Clima.NENHUM) {
      passivas.add(new Passiva(campo.getDoMundo().nome(), Passiva.TODOS, Passiva.SEM_RELOGIO, Passiva.SEM_RELOGIO));
    }
    if (campo.getTerreno() != Terreno.NENHUM) {
      passivas.add(comPrazo(campo.getTerreno().nome(), Passiva.TODOS, campo.getPrazoDoTerreno(), agora));
    }
    campo.barreiras(true).forEach((barreira, prazo) -> passivas.add(comPrazo(barreira.nome(), Passiva.SEU_LADO, prazo,
        agora)));
    if (arena.naBatalha()) {
      campo.barreiras(false).forEach((barreira, prazo) -> passivas.add(comPrazo(barreira.nome(), Passiva.SELVAGENS, prazo,
          agora)));
    }
    return passivas;
  }

  private static Passiva comPrazo(String nome, int lado, CampoDaBatalha.Prazo prazo, long agora) {
    return new Passiva(nome, lado, segundos(prazo.fim() - agora), segundos(prazo.duracao()));
  }

  private static int segundos(long ticks) {
    return (int) Math.max(0, Math.ceil(ticks / (double) SharedConstants.TICKS_PER_SECOND));
  }

  public static void esquecer(ServerPlayer jogador) {
    ULTIMO_ENVIO.remove(jogador.getUUID());
  }

  public static void limpar() {
    ULTIMO_ENVIO.clear();
  }
}
