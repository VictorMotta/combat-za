package dev.victor.combatza.golpes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import dev.victor.combatza.CobblemonCombatZA;

public class EfeitosDosGolpes {
  public record Proprio(Map<String, Integer> boosts) {
  }

  public record Secundario(int chance, Map<String, Integer> boosts, String status, String volatileStatus,
      Proprio self) {
  }

  // multihit (5.4): [mínimo, máximo] de acertos; lockedmove (5.4): a fúria do Ultraje
  public record Efeito(String target, Map<String, Integer> boosts, String status, String volatileStatus, Proprio self,
      List<Secundario> secondaries, boolean powder, List<Integer> drain, List<Integer> recoil, List<Integer> heal,
      boolean mindBlownRecoil, String damage, boolean contact, List<Integer> multihit, boolean lockedmove) {
  }

  private static final String ARQUIVO = "/cobblemon_combat_za/efeitos_golpes.json";
  private static Map<String, Efeito> efeitos = Map.of();

  // Itens segurados (lote 3): as marcas dos golpes que os itens leem, num arquivo à parte (gerado junto, ferramentas/
  // gerar-efeitos.cjs): os socos (Luva de Soco) e os golpes de som (Spray de Garganta)
  private record Marcas(List<String> punch, List<String> sound) {
  }

  private static final String ARQUIVO_DAS_MARCAS = "/cobblemon_combat_za/marcas_golpes.json";
  private static Set<String> socos = Set.of();
  private static Set<String> deSom = Set.of();

  private EfeitosDosGolpes() {
  }

  public static void carregar() {
    Type tipoDoArquivo = TypeToken.getParameterized(Map.class, String.class, Efeito.class).getType();
    try (InputStream entrada = EfeitosDosGolpes.class.getResourceAsStream(ARQUIVO)) {
      if (entrada == null) {
        CobblemonCombatZA.LOGGER.error("Não achei o {} dentro do jar!", ARQUIVO);
        return;
      }
      efeitos = new Gson().fromJson(new InputStreamReader(entrada, StandardCharsets.UTF_8), tipoDoArquivo);
      CobblemonCombatZA.LOGGER.info("Efeitos carregados: {} golpes", efeitos.size());
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.error("Falha ao ler o {}", ARQUIVO, erro);
    }
    carregarAsMarcas();
  }

  private static void carregarAsMarcas() {
    try (InputStream entrada = EfeitosDosGolpes.class.getResourceAsStream(ARQUIVO_DAS_MARCAS)) {
      if (entrada == null) {
        CobblemonCombatZA.LOGGER.error("Não achei o {} dentro do jar!", ARQUIVO_DAS_MARCAS);
        return;
      }
      Marcas marcas = new Gson().fromJson(new InputStreamReader(entrada, StandardCharsets.UTF_8), Marcas.class);
      socos = new HashSet<>(marcas.punch());
      deSom = new HashSet<>(marcas.sound());
      CobblemonCombatZA.LOGGER.info("Marcas carregadas: {} socos, {} golpes de som", socos.size(), deSom.size());
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.error("Falha ao ler o {}", ARQUIVO_DAS_MARCAS, erro);
    }
  }

  public static boolean ehSoco(Move golpe) {
    return socos.contains(golpe.getName());
  }

  public static boolean ehDeSom(Move golpe) {
    return deSom.contains(golpe.getName());
  }

  public static Efeito de(Move golpe) {
    return efeitos.get(golpe.getName());
  }

  public static boolean fazContato(Move golpe) {
    Efeito efeito = de(golpe);
    return efeito != null && efeito.contact();
  }

  public static double fracao(List<Integer> fracao) {
    return (double) fracao.get(0) / fracao.get(1);
  }
}
