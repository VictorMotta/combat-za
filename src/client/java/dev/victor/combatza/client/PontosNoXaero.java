package dev.victor.combatza.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.rede.MeusPontosPayload;
import dev.victor.combatza.rede.PontosDaPartyPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

// Os waypoints da party no Xaero (dele, 20/10: "daria para pelo menos sincronizar os waypoints?" → o conjunto "Party"). Duas
// metades, a cada segundo:
// 1. Os SEUS: o mod cria no seu Xaero um conjunto de waypoints chamado "Party"; o que estiver nele vai para o servidor (só
//    quando muda), que repassa para a sua party (party.PontosDaParty).
// 2. Os DELES: os pontos que chegam do servidor entram no mapa pela porta que o Xaero tem para outros mods (os "waypoints de
//    terceiros", a mesma que o suporte dele ao mod Waystones usa: javap em SupportWaystones). Eles aparecem como "Casa (Victor)"
//    no minimapa, no mapa-múndi e no mundo, e não dá para apagar do seu lado (são do amigo).
// Esta classe só é carregada com o Xaero instalado (CobblemonCombatZAClient confere antes)
public class PontosNoXaero {
  public static final String CONJUNTO = "Party";
  private static final ResourceLocation ORIGEM = ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "party");
  private static final int A_CADA_TICKS = 20;

  private static List<PontosDaPartyPayload.Marca> marcas = List.of();
  // Chegou lista nova (ou o Xaero trocou de servidor): pôr no mapa no próximo segundo
  private static boolean mudou = false;
  // Os mundos em que há pontos da party no mapa (para tirar quando somem)
  private static final Set<String> mundosComPontos = new HashSet<>();
  // O que já foi mandado de cada mundo (só manda de novo quando muda)
  private static final Map<String, List<MeusPontosPayload.Ponto>> mandados = new HashMap<>();
  private static MinimapWorldRootContainer raizAnterior;
  private static int ticks = 0;
  // O Xaero mudou por dentro numa versão nova: os waypoints da party se desligam (um aviso no log), em vez de fechar o jogo
  private static boolean desligado = false;

  private PontosNoXaero() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(PontosDaPartyPayload.TIPO, (pacote, contexto) -> {
      marcas = pacote.marcas();
      mudou = true;
    });
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      if (desligado) {
        return;
      }
      try {
        atualizar(cliente);
      } catch (LinkageError erro) {
        desligado = true;
        CobblemonCombatZA.LOGGER.error("PARTY: o Xaero's Minimap mudou; os waypoints da party ficam desligados", erro);
      }
    });
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      marcas = List.of();
      mudou = false;
      mundosComPontos.clear();
      mandados.clear();
      raizAnterior = null;
    });
  }

  private static void atualizar(Minecraft cliente) {
    if (cliente.player == null || ++ticks < A_CADA_TICKS) {
      return;
    }
    ticks = 0;
    MinimapSession sessao = BuiltInHudModules.MINIMAP.getCurrentSession();
    MinimapWorldRootContainer raiz = sessao == null ? null : sessao.getWorldManager().getAutoRootContainer();
    if (raiz == null) {
      return;
    }
    // Entrou noutro servidor (ou o Xaero recarregou): os pontos da party vão de novo, e os seus também
    if (raiz != raizAnterior) {
      raizAnterior = raiz;
      mundosComPontos.clear();
      mandados.clear();
      mudou = true;
    }
    mandarOsSeus(sessao, raiz);
    if (mudou) {
      mostrarOsDaParty(sessao, raiz);
      mudou = false;
    }
  }

  // O conjunto "Party" do mundo em que você está: cria se não existe; mudou, vai para o servidor
  private static void mandarOsSeus(MinimapSession sessao, MinimapWorldRootContainer raiz) {
    MinimapWorld mundo = sessao.getWorldManager().getCurrentWorld();
    if (mundo == null || mundo.getDimId() == null || mundo.getContainer() == null || mundo.getContainer().getRoot() != raiz
        || !ClientPlayNetworking.canSend(MeusPontosPayload.TIPO)) {
      return;
    }
    WaypointSet conjunto = mundo.getWaypointSet(CONJUNTO);
    if (conjunto == null) {
      mundo.addWaypointSet(CONJUNTO);
      salvar(sessao, mundo);
      conjunto = mundo.getWaypointSet(CONJUNTO);
      if (conjunto == null) {
        return;
      }
    }
    List<MeusPontosPayload.Ponto> pontos = new ArrayList<>();
    for (Waypoint waypoint : conjunto.getWaypoints()) {
      if (pontos.size() >= MeusPontosPayload.MAXIMO) {
        break;
      }
      if (!waypoint.isTemporary()) {
        pontos.add(new MeusPontosPayload.Ponto(cortar(waypoint.getName(), MeusPontosPayload.MAXIMO_DO_NOME),
            cortar(waypoint.getInitials(), MeusPontosPayload.MAXIMO_DAS_INICIAIS), waypoint.getX(), waypoint.getY(),
            waypoint.getZ(), waypoint.getWaypointColor().ordinal()));
      }
    }
    String dimensao = mundo.getDimId().location().toString();
    if (!pontos.equals(mandados.get(dimensao))) {
      mandados.put(dimensao, pontos);
      ClientPlayNetworking.send(new MeusPontosPayload(dimensao, pontos));
    }
  }

  // Os pontos da party, mundo por mundo, na pasta de cada mundo do Xaero (como o suporte dele ao Waystones faz)
  private static void mostrarOsDaParty(MinimapSession sessao, MinimapWorldRootContainer raiz) {
    Map<String, List<PontosDaPartyPayload.Marca>> porMundo = new HashMap<>();
    for (PontosDaPartyPayload.Marca marca : marcas) {
      porMundo.computeIfAbsent(marca.mundo(), mundo -> new ArrayList<>()).add(marca);
    }
    Set<String> mundos = new HashSet<>(mundosComPontos);
    mundos.addAll(porMundo.keySet());
    for (String mundo : mundos) {
      ResourceLocation id = ResourceLocation.tryParse(mundo);
      if (id == null) {
        continue;
      }
      ResourceKey<Level> dimensao = ResourceKey.create(Registries.DIMENSION, id);
      MinimapWorldContainer pasta = raiz.addSubContainer(
          raiz.getPath().resolve(sessao.getDimensionHelper().getDimensionDirectoryName(dimensao)));
      ThirdPartyWaypoints daParty = pasta.getThirdPartyWaypointManager().get(ORIGEM);
      daParty.clear();
      List<PontosDaPartyPayload.Marca> doMundo = porMundo.getOrDefault(mundo, List.of());
      for (int i = 0; i < doMundo.size(); i++) {
        PontosDaPartyPayload.Marca marca = doMundo.get(i);
        MeusPontosPayload.Ponto ponto = marca.ponto();
        daParty.add(marca.dono() + "/" + i, new Waypoint(ponto.x(), ponto.y(), ponto.z(),
            ponto.nome() + " (" + marca.nomeDoDono() + ")", ponto.iniciais(), WaypointColor.fromIndex(ponto.cor())));
      }
    }
    mundosComPontos.clear();
    mundosComPontos.addAll(porMundo.keySet());
  }

  // O conjunto novo fica gravado no arquivo de waypoints do Xaero (senão some ao sair)
  private static void salvar(MinimapSession sessao, MinimapWorld mundo) {
    try {
      sessao.getWorldManagerIO().saveWorld(mundo);
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.warn("PARTY: não deu para salvar o conjunto {} no Xaero", CONJUNTO, erro);
    }
  }

  private static String cortar(String texto, int maximo) {
    String limpo = texto == null ? "" : texto.strip();
    return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo);
  }
}
