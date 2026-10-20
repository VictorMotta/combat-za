package dev.victor.combatza.combate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.captura.Irritados;
import dev.victor.combatza.captura.ChanceDeCaptura;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.rede.PlacasPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// A cada 5 ticks, conta a cada jogador o que as placas dos selvagens perto dele precisam (a HUD do Z-A, itens 3 e 4).
// Só manda quando algo mudou desde o último envio para aquele jogador
public class PlacasDosSelvagens {
  private static final int A_CADA_TICKS = 5;
  private static final double DISTANCIA = 32.0;
  private static final Map<UUID, List<PlacasPayload.Placa>> ULTIMO_ENVIO = new HashMap<>();
  // 8.1b: as setas de captura que cada jogador viu por último em cada selvagem perto (até 12 blocos); mudou, vai para o log
  private static final double PERTO_PARA_O_LOG = 12.0;
  private static final Map<UUID, Map<Integer, String>> SETAS_ANOTADAS = new HashMap<>();

  private PlacasDosSelvagens() {
  }

  public static void atualizar(MinecraftServer servidor) {
    if (servidor.getTickCount() % A_CADA_TICKS != 0) {
      return;
    }
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      List<PlacasPayload.Placa> placas = placasDe(jogador);
      if (!placas.equals(ULTIMO_ENVIO.get(jogador.getUUID())) && ServerPlayNetworking.canSend(jogador, PlacasPayload.TIPO)) {
        ULTIMO_ENVIO.put(jogador.getUUID(), placas);
        ServerPlayNetworking.send(jogador, new PlacasPayload(placas));
      }
    }
  }

  // Em ordem de entidade, para a mesma situação dar sempre a mesma lista (e não reenviar à toa)
  private static List<PlacasPayload.Placa> placasDe(ServerPlayer jogador) {
    List<PokemonEntity> emCombate = GerenciarCombates.selvagensNoCombateDe(jogador);
    List<PlacasPayload.Placa> placas = new ArrayList<>();
    Map<Integer, String> setasAntes = SETAS_ANOTADAS.getOrDefault(jogador.getUUID(), Map.of());
    Map<Integer, String> setasAgora = new HashMap<>();
    for (PokemonEntity selvagem : jogador.level().getEntitiesOfClass(PokemonEntity.class,
        jogador.getBoundingBox().inflate(DISTANCIA), entidade -> entidade.isAlive() && entidade.getPokemon().isWild())) {
      Pokemon pokemon = selvagem.getPokemon();
      PersistentStatusContainer status = pokemon.getStatus();
      List<PlacasPayload.Estagio> estagios = new ArrayList<>();
      Estagios.atuais(pokemon).forEach((atributo, valor) -> estagios.add(new PlacasPayload.Estagio(atributo, valor)));
      ChanceDeCaptura.Conta conta = ChanceDeCaptura.calcular(jogador, selvagem);
      anotarSeMudou(jogador, selvagem, conta, setasAntes, setasAgora);
      placas.add(new PlacasPayload.Placa(selvagem.getId(), pokemon.getLevel(), pokemon.getGender().name(),
          status == null ? "" : status.getStatus().getShowdownName(), emCombate.contains(selvagem), estagios,
          conta.setas(), conta.brilha(), Irritados.esta(pokemon), Desmaio.segundos(selvagem, jogador.server.getTickCount())));
    }
    SETAS_ANOTADAS.put(jogador.getUUID(), setasAgora);
    placas.sort(Comparator.comparingInt(PlacasPayload.Placa::entidade));
    return placas;
  }

  public static void esquecer(ServerPlayer jogador) {
    ULTIMO_ENVIO.remove(jogador.getUUID());
    SETAS_ANOTADAS.remove(jogador.getUUID());
  }

  public static void limpar() {
    ULTIMO_ENVIO.clear();
    SETAS_ANOTADAS.clear();
  }

  // 8.1b: um selvagem chegou perto, ou as setas dele mudaram: a conta inteira vai para o log, com a chance do Cobblemon puro
  // (a que o Catch Rate Display calcula) do lado, para conferir
  private static void anotarSeMudou(ServerPlayer jogador, PokemonEntity selvagem, ChanceDeCaptura.Conta conta,
      Map<Integer, String> antes, Map<Integer, String> agora) {
    if (jogador.distanceTo(selvagem) > PERTO_PARA_O_LOG) {
      return;
    }
    String naTela = comoAparece(conta);
    agora.put(selvagem.getId(), naTela);
    if (naTela.equals(antes.get(selvagem.getId()))) {
      return;
    }
    CobblemonCombatZA.LOGGER.info(
        "CAPTURA (setas) para {}: {} nv {} → {} = {}% com o nosso mod (extras ×{}{}); Cobblemon puro {}% "
            + "| PV {}/{}, bola {}, status ×{}",
        jogador.getGameProfile().getName(), selvagem.getPokemon().getSpecies().getName(),
        selvagem.getPokemon().getLevel(), naTela, String.format("%.1f", conta.chance() * 100),
        String.format("%.2f", conta.extras()), conta.pelasCostas() ? ", pelas costas" : "",
        String.format("%.1f", conta.chanceDoCobblemonPuro() * 100), conta.pv(), conta.pvMaximo(), conta.bola(),
        String.format("%.1f", conta.status()));
  }

  // O que aparece ao lado do ")": "1 seta", "3 setas", "4 setas brilhando" (8.1d)
  private static String comoAparece(ChanceDeCaptura.Conta conta) {
    String setas = conta.setas() == 1 ? "1 seta" : conta.setas() + " setas";
    return conta.brilha() ? setas + " brilhando" : setas;
  }
}
