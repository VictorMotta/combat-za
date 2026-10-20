package dev.victor.combatza.golpes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import dev.victor.combatza.CobblemonCombatZA;

public class AreaSemQueda {
  private static final String ARQUIVO = "/cobblemon_combat_za/area_sem_queda.json";
  private static Map<String, String> golpes = Map.of();

  private AreaSemQueda() {
  }

  public static void carregar() {
    Type tipoDoArquivo = TypeToken.getParameterized(Map.class, String.class, String.class).getType();
    try (InputStream entrada = AreaSemQueda.class.getResourceAsStream(ARQUIVO)) {
      if (entrada == null) {
        CobblemonCombatZA.LOGGER.error("Não achei o {} dentro do jar!", ARQUIVO);
        return;
      }
      golpes = new Gson().fromJson(new InputStreamReader(entrada, StandardCharsets.UTF_8), tipoDoArquivo);
      CobblemonCombatZA.LOGGER.info("Golpes de área sem queda: {} {}", golpes.size(), golpes.keySet());
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.error("Falha ao ler o {}", ARQUIVO, erro);
    }
  }

  public static boolean inclui(Move golpe) {
    return golpes.containsKey(golpe.getName());
  }
}
