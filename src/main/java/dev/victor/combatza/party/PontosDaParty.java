package dev.victor.combatza.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.rede.MeusPontosPayload;
import dev.victor.combatza.rede.MeusPontosPayload.Ponto;
import dev.victor.combatza.rede.PontosDaPartyPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// Os waypoints da party (dele, 20/10: "daria para pelo menos sincronizar os waypoints?" → escolheu o conjunto "Party" e a
// tecla). O seu jogo manda os waypoints do conjunto "Party" do seu Xaero (client.PontosNoXaero); o servidor guarda junto com
// o mundo (PontosSalvos) e, a cada segundo, manda para cada jogador os pontos dos OUTROS da party dele (só quando muda). Saiu
// da party: os seus somem do mapa deles. O servidor confere o que chega (quantos, o tamanho do nome, a cor)
public class PontosDaParty {
  private static final int A_CADA_TICKS = 20;
  private static final int MAXIMO_DE_MUNDOS = 10;
  private static final int CORES = 21;
  private static final Map<UUID, PontosDaPartyPayload> ULTIMO_ENVIO = new HashMap<>();

  private PontosDaParty() {
  }

  private static PontosSalvos salvos(MinecraftServer servidor) {
    return servidor.overworld().getDataStorage().computeIfAbsent(PontosSalvos.FABRICA, PontosSalvos.ARQUIVO);
  }

  public static void receber(ServerPlayer jogador, MeusPontosPayload pacote) {
    String mundo = pacote.mundo();
    if (mundo.isEmpty() || mundo.length() > 100 || ResourceLocation.tryParse(mundo) == null) {
      return;
    }
    List<Ponto> pontos = pacote.pontos().stream().limit(MeusPontosPayload.MAXIMO).map(PontosDaParty::conferido).toList();
    salvos(jogador.server).guardar(jogador.getUUID(), jogador.getGameProfile().getName(), mundo, pontos, MAXIMO_DE_MUNDOS);
    CobblemonCombatZA.LOGGER.info("PARTY: {} tem {} waypoint(s) no conjunto Party em {}", jogador.getName().getString(),
        pontos.size(), mundo);
  }

  private static Ponto conferido(Ponto ponto) {
    return new Ponto(cortar(ponto.nome().strip(), MeusPontosPayload.MAXIMO_DO_NOME),
        cortar(ponto.iniciais().strip(), MeusPontosPayload.MAXIMO_DAS_INICIAIS), ponto.x(), ponto.y(), ponto.z(),
        Math.floorMod(ponto.cor(), CORES));
  }

  private static String cortar(String texto, int maximo) {
    return texto.length() <= maximo ? texto : texto.substring(0, maximo);
  }

  public static void atualizar(MinecraftServer servidor) {
    if (servidor.getTickCount() % A_CADA_TICKS != 0) {
      return;
    }
    PontosSalvos salvos = salvos(servidor);
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      if (!ServerPlayNetworking.canSend(jogador, PontosDaPartyPayload.TIPO)) {
        continue;
      }
      List<PontosDaPartyPayload.Marca> marcas = new ArrayList<>();
      for (Parties.Membro membro : Parties.outrosMembros(jogador)) {
        PontosSalvos.Dono dono = salvos.de(membro.jogador());
        if (dono == null) {
          continue;
        }
        String nome = membro.nome().equals("?") ? dono.nome() : membro.nome();
        dono.porMundo().forEach((mundo, pontos) -> pontos.forEach(
            ponto -> marcas.add(new PontosDaPartyPayload.Marca(membro.jogador(), nome, mundo, ponto))));
      }
      PontosDaPartyPayload pacote = new PontosDaPartyPayload(marcas);
      if (!pacote.equals(ULTIMO_ENVIO.get(jogador.getUUID()))) {
        ULTIMO_ENVIO.put(jogador.getUUID(), pacote);
        ServerPlayNetworking.send(jogador, pacote);
      }
    }
  }

  // Saiu do servidor: na volta, recebe tudo de novo
  public static void esquecer(ServerPlayer jogador) {
    ULTIMO_ENVIO.remove(jogador.getUUID());
  }

  public static void limpar() {
    ULTIMO_ENVIO.clear();
  }
}
