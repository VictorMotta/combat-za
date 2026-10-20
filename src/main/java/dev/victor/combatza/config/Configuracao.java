package dev.victor.combatza.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.Gson;

import dev.victor.combatza.CobblemonCombatZA;
import net.fabricmc.loader.api.FabricLoader;

// 7.3 (decisão dele, 19/10: "arquivo + comando"): o arquivo de configuração do mod, config/cobblemon_combat_za.json. O mod
// cria com os valores padrão se ele não existir, e explica cada opção num comentário (o Gson lê JSON com comentários //).
// Dá para editar com o jogo fechado, ou mudar no jogo com o comando /combatza (ComandoCombatZA), que também salva aqui.
// 7.3b: um erro no arquivo (uma vírgula sobrando, um número entre aspas) não impede o jogo de abrir: o mod avisa no log, fica
// com o que já valia e não grava por cima do arquivo (você perderia o que editou) até você arrumar e usar /combatza recarregar
public class Configuracao {
  public static final String POUCO = "pouco";
  public static final String MONSTRO = "monstro";
  private static final double DANO_POUCO = 0.5;
  private static final double DANO_MONSTRO = 1.0;
  private static final String ARQUIVO = "cobblemon_combat_za.json";
  private static final int MAXIMO_ENTRE_ITENS = 60;

  // O que fica no arquivo. Os nomes dos campos são os nomes no JSON
  private static final class Dados {
    private String danoNoJogador = POUCO;
    private double chanceDeMirarEmVoce = 0.2;
    // As listas do Fight or Flight (7.1e), com o nome da espécie em minúsculas
    private List<String> sempreAgressivos = List.of("mankey", "primeape", "voltorb", "electrode", "beedrill", "gyarados",
        "eternatus", "heatran", "darkrai", "giratina", "regice", "regirock", "registeel", "regieleki", "regidrago",
        "regigigas", "overqwil", "zoroark");
    private List<String> nuncaAgressivos = List.of("slowpoke", "chansey", "wooloo", "vivillon", "mareep", "unown");
    private List<String> sempreFogem = List.of("wimpod", "abra");
    // 9.1b (dele, 20/10: "uns 5 s, configurável"): a espera entre dois itens da mochila na batalha
    private int segundosEntreItens = 5;
  }

  private static Dados atual = new Dados();
  private static Set<String> sempreAgressivos = Set.copyOf(atual.sempreAgressivos);
  private static Set<String> nuncaAgressivos = Set.copyOf(atual.nuncaAgressivos);
  private static Set<String> sempreFogem = Set.copyOf(atual.sempreFogem);
  private static boolean arquivoComErro;

  private Configuracao() {
  }

  // Na inicialização do mod e no /combatza recarregar. Sem arquivo, cria. Devolve se leu direito
  public static boolean carregar() {
    Path caminho = caminho();
    if (!Files.exists(caminho)) {
      usar(new Dados());
      arquivoComErro = false;
      salvar();
      CobblemonCombatZA.LOGGER.info("CONFIGURAÇÃO: criei {} com os valores padrão", caminho);
      return true;
    }
    try {
      String texto = Files.readString(caminho, StandardCharsets.UTF_8);
      Dados lidos = new Gson().fromJson(texto, Dados.class);
      usar(lidos == null ? new Dados() : lidos);
      arquivoComErro = false;
      // 6.10 desfeita (dele, 19/10: "volta o chat ao normal, deixa a pessoa configurar o chat dela"): um arquivo com as
      // opções do chat, que não existem mais, é gravado de novo sem elas (com os valores que você já tinha)
      if (texto.contains("\"opacidadeDoChat\"")) {
        salvar();
      }
      CobblemonCombatZA.LOGGER.info("CONFIGURAÇÃO: dano no jogador '{}', chance de mirar em você {}",
          atual.danoNoJogador, atual.chanceDeMirarEmVoce);
      return true;
    } catch (IOException | RuntimeException erro) {
      // O Gson avisa um erro de vários jeitos (JsonParseException, NumberFormatException...): todos caem aqui
      arquivoComErro = true;
      CobblemonCombatZA.LOGGER.error("CONFIGURAÇÃO: o arquivo {} tem um erro ({}). Fico com o que já valia e não gravo por "
          + "cima dele: arrume e use /combatza recarregar", caminho, erro.getMessage());
      return false;
    }
  }

  // Devolve se gravou no arquivo (com erro no arquivo, a mudança vale só até fechar o jogo)
  public static boolean mudarDanoNoJogador(String modo) {
    atual.danoNoJogador = modo;
    return salvar();
  }

  public static String danoNoJogador() {
    return atual.danoNoJogador;
  }

  public static double multiplicadorDoDanoNoJogador() {
    return MONSTRO.equals(atual.danoNoJogador) ? DANO_MONSTRO : DANO_POUCO;
  }

  public static double chanceDeMirarEmVoce() {
    return atual.chanceDeMirarEmVoce;
  }

  public static int segundosEntreItens() {
    return atual.segundosEntreItens;
  }

  public static Set<String> sempreAgressivos() {
    return sempreAgressivos;
  }

  public static Set<String> nuncaAgressivos() {
    return nuncaAgressivos;
  }

  public static Set<String> sempreFogem() {
    return sempreFogem;
  }

  // Valores fora do lugar voltam para o padrão: um modo que não existe, uma chance fora de 0 a 1, uma lista que faltou, um
  // nome vazio na lista (7.3b: uma vírgula sobrando vira um vazio). Tudo é conferido antes de trocar o que vale
  private static void usar(Dados dados) {
    Dados padrao = new Dados();
    String modo = dados.danoNoJogador == null ? "" : dados.danoNoJogador.toLowerCase(Locale.ROOT).trim();
    dados.danoNoJogador = POUCO.equals(modo) || MONSTRO.equals(modo) ? modo : padrao.danoNoJogador;
    if (!(dados.chanceDeMirarEmVoce >= 0 && dados.chanceDeMirarEmVoce <= 1)) {
      dados.chanceDeMirarEmVoce = padrao.chanceDeMirarEmVoce;
    }
    if (dados.segundosEntreItens < 0 || dados.segundosEntreItens > MAXIMO_ENTRE_ITENS) {
      dados.segundosEntreItens = padrao.segundosEntreItens;
    }
    dados.sempreAgressivos = limpa(dados.sempreAgressivos, padrao.sempreAgressivos);
    dados.nuncaAgressivos = limpa(dados.nuncaAgressivos, padrao.nuncaAgressivos);
    dados.sempreFogem = limpa(dados.sempreFogem, padrao.sempreFogem);
    atual = dados;
    sempreAgressivos = Set.copyOf(dados.sempreAgressivos);
    nuncaAgressivos = Set.copyOf(dados.nuncaAgressivos);
    sempreFogem = Set.copyOf(dados.sempreFogem);
  }

  // Os nomes em minúsculas e sem os vazios; sem a lista no arquivo, a padrão
  private static List<String> limpa(List<String> especies, List<String> padrao) {
    if (especies == null) {
      return padrao;
    }
    List<String> lista = new ArrayList<>();
    for (String especie : especies) {
      if (especie != null && !especie.isBlank()) {
        lista.add(especie.toLowerCase(Locale.ROOT).trim());
      }
    }
    return lista;
  }

  // O arquivo é escrito à mão, para levar os comentários que explicam cada opção. Devolve se gravou
  private static boolean salvar() {
    if (arquivoComErro) {
      CobblemonCombatZA.LOGGER.warn("CONFIGURAÇÃO: não gravei {}: ele tem um erro (veja acima)", caminho());
      return false;
    }
    Gson gson = new Gson();
    String texto = "{\n"
        + "  // Dano dos golpes dos selvagens em você: \"pouco\" (metade) ou \"monstro\" (como um monstro do Minecraft do nível\n"
        + "  // dele). Muda também no jogo: /combatza dano pouco | /combatza dano monstro\n"
        + "  \"danoNoJogador\": " + gson.toJson(atual.danoNoJogador) + ",\n"
        + "  // Com um Pokémon seu fora, a chance de um golpe DE LONGE do selvagem ir em você em vez dele (de 0 a 1)\n"
        + "  \"chanceDeMirarEmVoce\": " + atual.chanceDeMirarEmVoce + ",\n"
        + "  // Na batalha, depois de usar um item da mochila (poção, reanimador, X) ou de dar/tirar o item segurado de um\n"
        + "  // Pokémon seu, quantos segundos você espera para usar outro (de 0 a 60; 0 = sem espera)\n"
        + "  \"segundosEntreItens\": " + atual.segundosEntreItens + ",\n"
        + "  // O temperamento pela espécie (nome em inglês, como no Cobblemon), antes da conta da raiva. Nunca agressivos e\n"
        + "  // sempre fogem passam na frente dos sempre agressivos. Depois de editar: /combatza recarregar\n"
        + "  \"sempreAgressivos\": " + gson.toJson(atual.sempreAgressivos) + ",\n"
        + "  \"nuncaAgressivos\": " + gson.toJson(atual.nuncaAgressivos) + ",\n"
        + "  \"sempreFogem\": " + gson.toJson(atual.sempreFogem) + "\n"
        + "}\n";
    try {
      Files.writeString(caminho(), texto, StandardCharsets.UTF_8);
      return true;
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.error("CONFIGURAÇÃO: não consegui salvar {}", caminho(), erro);
      return false;
    }
  }

  private static Path caminho() {
    return FabricLoader.getInstance().getConfigDir().resolve(ARQUIVO);
  }
}
