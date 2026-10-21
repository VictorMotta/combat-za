package dev.victor.combatza.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.victor.combatza.CobblemonCombatZA;
import net.fabricmc.loader.api.FabricLoader;

// As cores da prévia do golpe (dele, 21/10: "estava no gelo, fui usar a habilidade segurando, e a marcação era quase da mesma
// tonalidade do gelo"; escolheu: o contorno sempre, e poder escolher a cor do contorno e a de dentro). Os dois padrões: DENTRO, a
// cor do tipo do golpe; o CONTORNO, preto por fora e branco por dentro (aparece em qualquer chão: gelo, neve, água, areia). O
// preto por fora fica sempre (é ele que dá o contraste); você escolhe a cor de dentro e a do contorno de dentro (TelaDasCores).
// Ficam em config/cobblemon_combat_za_previa.json, no seu jogo
public class CoresDaPrevia {
  public static final int PRETO = 0xFF000000;
  public static final int BRANCO = 0xFFFFFFFF;
  // Cores fortes, que quase não existem no chão do Minecraft
  static final int[] PALETA = { 0xFFFF2BD6, 0xFFFFE600, 0xFFFF7A00, 0xFFFF2B2B, 0xFF7CFF00, 0xFF00E5FF, 0xFF2B5BFF,
      0xFF8A2BFF, BRANCO, PRETO };
  private static final Path ARQUIVO = FabricLoader.getInstance().getConfigDir().resolve("cobblemon_combat_za_previa.json");
  private static final String DENTRO = "dentro";
  private static final String CONTORNO = "contorno";

  // null = o padrão (a cor do tipo; o contorno branco)
  private static Integer dentro = null;
  private static Integer contorno = null;

  private CoresDaPrevia() {
  }

  public static void registrar() {
    carregar();
  }

  // A cor de dentro: a escolhida, ou a do tipo do golpe
  static int dentro(int doTipo) {
    return dentro == null ? doTipo : dentro;
  }

  static int contornoDeFora() {
    return PRETO;
  }

  static int contornoDeDentro() {
    return contorno == null ? BRANCO : contorno;
  }

  static Integer escolhidaDentro() {
    return dentro;
  }

  static Integer escolhidaContorno() {
    return contorno;
  }

  static void escolherDentro(Integer cor) {
    dentro = cor;
    salvar();
  }

  static void escolherContorno(Integer cor) {
    contorno = cor;
    salvar();
  }

  private static void carregar() {
    try {
      if (Files.exists(ARQUIVO)) {
        JsonObject json = JsonParser.parseString(Files.readString(ARQUIVO)).getAsJsonObject();
        dentro = lerCor(json, DENTRO);
        contorno = lerCor(json, CONTORNO);
      }
    } catch (IOException | RuntimeException erro) {
      dentro = null;
      contorno = null;
      CobblemonCombatZA.LOGGER.warn("CORES: não consegui ler {} ({}); ficam as cores padrão", ARQUIVO, erro.getMessage());
    }
  }

  // "#RRGGBB" = uma cor escolhida; sem a chave (ou "padrao"), o padrão
  private static Integer lerCor(JsonObject json, String chave) {
    if (!json.has(chave) || !json.get(chave).getAsString().startsWith("#")) {
      return null;
    }
    return 0xFF000000 | Integer.parseInt(json.get(chave).getAsString().substring(1), 16);
  }

  private static void salvar() {
    JsonObject json = new JsonObject();
    json.addProperty(DENTRO, dentro == null ? "padrao" : String.format("#%06X", dentro & 0xFFFFFF));
    json.addProperty(CONTORNO, contorno == null ? "padrao" : String.format("#%06X", contorno & 0xFFFFFF));
    try {
      Files.writeString(ARQUIVO, new GsonBuilder().setPrettyPrinting().create().toJson(json));
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.warn("CORES: não consegui gravar {} ({})", ARQUIVO, erro.getMessage());
    }
  }
}
