package dev.victor.combatza.golpes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import dev.victor.combatza.CobblemonCombatZA;

// Quem tem animação de ataque no modpack (gerado pela ferramenta gerar-animacoes.py: o servidor não vê os modelos)
public class AnimacoesDeAtaque {
  private static final String ARQUIVO = "/cobblemon_combat_za/animacoes_de_ataque.json";
  private static Map<String, List<String>> especies = Map.of();

  private AnimacoesDeAtaque() {
  }

  public static void carregar() {
    Type tipoDoArquivo = TypeToken.getParameterized(Map.class, String.class,
        TypeToken.getParameterized(List.class, String.class).getType()).getType();
    try (InputStream entrada = AnimacoesDeAtaque.class.getResourceAsStream(ARQUIVO)) {
      if (entrada == null) {
        CobblemonCombatZA.LOGGER.error("Não achei o {} dentro do jar!", ARQUIVO);
        return;
      }
      especies = new Gson().fromJson(new InputStreamReader(entrada, StandardCharsets.UTF_8), tipoDoArquivo);
      CobblemonCombatZA.LOGGER.info("Espécies com animação de ataque: {}", especies.size());
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.error("Falha ao ler o {}", ARQUIVO, erro);
    }
  }

  // A animação que o cliente toca é a da categoria do golpe (physical, special ou status)
  public static boolean tem(Pokemon pokemon, Move golpe) {
    List<String> categorias = especies.get(pokemon.getSpecies().getResourceIdentifier().getPath());
    return categorias != null && categorias.contains(golpe.getDamageCategory().getName());
  }
}
