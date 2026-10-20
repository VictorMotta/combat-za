package dev.victor.combatza.golpes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

// As partículas prontas do Cobblemon para cada golpe (assets/cobblemon/bedrock/particles/moves/<golpe>/), feitas para a
// batalha por turnos: uma parte sai de quem ataca ("actor"), outra acontece em quem leva ("target"). O projétil usa a de
// quem leva no IMPACTO, quando acerta um monstro ou o chão (num Pokémon, vale o efeito da batalha: AnimacoesDeBatalha).
// A lista particulas_dos_golpes.json foi montada lendo o jar do Cobblemon (82 golpes, 66 com impacto). O "rastro" saiu na
// 5.2c: soltas no meio do caminho, as partículas de jato (Revólver d'Água) saíam para qualquer lado
public class ParticulasDosGolpes {
  private static final String ARQUIVO = "/cobblemon_combat_za/particulas_dos_golpes.json";
  private static final double DISTANCIA = 64.0;

  private record Particulas(String rastro, String impacto) {
  }

  private static Map<String, Particulas> golpes = Map.of();

  private ParticulasDosGolpes() {
  }

  public static void carregar() {
    Type tipoDoArquivo = TypeToken.getParameterized(Map.class, String.class, Particulas.class).getType();
    try (InputStream entrada = ParticulasDosGolpes.class.getResourceAsStream(ARQUIVO)) {
      if (entrada == null) {
        CobblemonCombatZA.LOGGER.error("Não achei o {} dentro do jar!", ARQUIVO);
        return;
      }
      golpes = new Gson().fromJson(new InputStreamReader(entrada, StandardCharsets.UTF_8), tipoDoArquivo);
      CobblemonCombatZA.LOGGER.info("Partículas dos golpes: {}", golpes.size());
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.error("Falha ao ler o {}", ARQUIVO, erro);
    }
  }

  public static void impacto(Move golpe, ServerLevel nivel, Vec3 ponto) {
    Particulas particulas = golpes.get(golpe.getName());
    if (particulas != null && particulas.impacto() != null) {
      enviar(nivel, particulas.impacto(), ponto);
    }
  }

  // O pacote do Cobblemon que solta uma partícula num ponto do mundo (o de entidade, que já usamos, precisa de um modelo)
  private static void enviar(ServerLevel nivel, String efeito, Vec3 ponto) {
    ResourceLocation id = ResourceLocation.tryParse(efeito);
    if (id != null) {
      new SpawnSnowstormParticlePacket(id, ponto).sendToPlayersAround(ponto.x, ponto.y, ponto.z, DISTANCIA,
          nivel.dimension(), jogador -> false);
    }
  }
}
