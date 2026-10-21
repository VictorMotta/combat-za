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
  // Os limites das opções do equilíbrio (fora deles, vale o padrão)
  private static final double MAIOR_MULTIPLICADOR_DE_DANO = 5;
  private static final double MENOR_ALCANCE = 1;
  private static final double MAIOR_ALCANCE = 64;
  private static final double MENOS_SEGUNDOS_PARA_NOTAR = 0.25;
  private static final double MAIS_SEGUNDOS_PARA_NOTAR = 60;
  private static final double LIMITE_DA_RAIVA = 1000;
  private static final int MAIOR_NIVEL_PARA_ATACAR = 1000;
  // As opções que o arquivo precisa ter: faltando alguma (um arquivo de uma versão antiga), ele é gravado de novo com elas,
  // mantendo os valores que já tinha
  // O 0.5 da 0.2.0-beta.3 (o padrão de então, "fraco demais") vira o padrão novo, uma vez, nos arquivos daquela versão (os que
  // ainda não têm "versaoDoArquivo"). Um valor escolhido depois fica
  private static final String VERSAO_DO_ARQUIVO = "versaoDoArquivo";
  private static final int VERSAO = 2;
  private static final double DANO_DO_SELVAGEM_DA_BETA_3 = 0.5;
  private static final List<String> OPCOES = List.of(VERSAO_DO_ARQUIVO, "danoDoSelvagem", "danoDosSeusPokemon", "alcanceDaVisaoDeDia",
      "alcanceDaVisaoDeNoite", "segundosParaNotar", "raivaParaAtacar", "nivelParaAtacarSozinho");

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
    // O equilíbrio da batalha (dele, 20/10: "vamos nerfar um pouco o dano dos selvagens, isso tá muito caótico, um lvl 20 mata
    // muito fácil um lvl 10, e tá muito fácil do selvagem te ver"; "cada server pode colocar do jeito que quiser"; "e o dano do
    // nosso Pokémon também"). Escolheu: os golpes do selvagem nos seus Pokémon tiram 50%; os dos seus nos selvagens, 100%.
    // 21/10 (dele, testando: "deixamos fraco demais os selvagens"): 75%
    private double danoDoSelvagem = 0.75;
    private double danoDosSeusPokemon = 1.0;
    // A visão do selvagem (escolheu "bem mais difícil"; era 16 e 10 blocos e 1,5 s)
    private double alcanceDaVisaoDeDia = 10;
    private double alcanceDaVisaoDeNoite = 6;
    private double segundosParaNotar = 3;
    // Quem ataca sozinho depois de te notar (escolheu "menos bravos"; a raiva era 100)
    private double raivaParaAtacar = 120;
    private int nivelParaAtacarSozinho = 25;
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
      if (lidos != null && !texto.contains("\"" + VERSAO_DO_ARQUIVO + "\"")
          && lidos.danoDoSelvagem == DANO_DO_SELVAGEM_DA_BETA_3) {
        lidos.danoDoSelvagem = new Dados().danoDoSelvagem;
        CobblemonCombatZA.LOGGER.info("CONFIGURAÇÃO: o dano do selvagem da 0.2.0-beta.3 (0.5) virou o padrão novo ({})",
            lidos.danoDoSelvagem);
      }
      usar(lidos == null ? new Dados() : lidos);
      arquivoComErro = false;
      // 6.10 desfeita (dele, 19/10: "volta o chat ao normal, deixa a pessoa configurar o chat dela"): um arquivo com as
      // opções do chat, que não existem mais, é gravado de novo sem elas (com os valores que você já tinha)
      // E um arquivo de uma versão antiga, sem as opções novas, ganha as opções (com os valores padrão delas)
      if (texto.contains("\"opacidadeDoChat\"") || OPCOES.stream().anyMatch(opcao -> !texto.contains("\"" + opcao + "\""))) {
        salvar();
      }
      CobblemonCombatZA.LOGGER.info("CONFIGURAÇÃO: dano no jogador '{}', chance de mirar em você {}, dano do selvagem {}, "
          + "dano dos seus Pokémon {}, visão {}/{} blocos em {} s, raiva para atacar {} (a partir do nível {})",
          atual.danoNoJogador, atual.chanceDeMirarEmVoce, atual.danoDoSelvagem, atual.danoDosSeusPokemon,
          atual.alcanceDaVisaoDeDia, atual.alcanceDaVisaoDeNoite, atual.segundosParaNotar, atual.raivaParaAtacar,
          atual.nivelParaAtacarSozinho);
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

  public static double danoDoSelvagem() {
    return atual.danoDoSelvagem;
  }

  public static double danoDosSeusPokemon() {
    return atual.danoDosSeusPokemon;
  }

  public static double alcanceDaVisaoDeDia() {
    return atual.alcanceDaVisaoDeDia;
  }

  public static double alcanceDaVisaoDeNoite() {
    return atual.alcanceDaVisaoDeNoite;
  }

  public static double segundosParaNotar() {
    return atual.segundosParaNotar;
  }

  public static double raivaParaAtacar() {
    return atual.raivaParaAtacar;
  }

  public static int nivelParaAtacarSozinho() {
    return atual.nivelParaAtacarSozinho;
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
    dados.danoDoSelvagem = entre(dados.danoDoSelvagem, 0, MAIOR_MULTIPLICADOR_DE_DANO, padrao.danoDoSelvagem);
    dados.danoDosSeusPokemon = entre(dados.danoDosSeusPokemon, 0, MAIOR_MULTIPLICADOR_DE_DANO, padrao.danoDosSeusPokemon);
    dados.alcanceDaVisaoDeDia = entre(dados.alcanceDaVisaoDeDia, MENOR_ALCANCE, MAIOR_ALCANCE, padrao.alcanceDaVisaoDeDia);
    dados.alcanceDaVisaoDeNoite = entre(dados.alcanceDaVisaoDeNoite, MENOR_ALCANCE, MAIOR_ALCANCE,
        padrao.alcanceDaVisaoDeNoite);
    dados.segundosParaNotar = entre(dados.segundosParaNotar, MENOS_SEGUNDOS_PARA_NOTAR, MAIS_SEGUNDOS_PARA_NOTAR,
        padrao.segundosParaNotar);
    dados.raivaParaAtacar = entre(dados.raivaParaAtacar, -LIMITE_DA_RAIVA, LIMITE_DA_RAIVA, padrao.raivaParaAtacar);
    if (dados.nivelParaAtacarSozinho < 0 || dados.nivelParaAtacarSozinho > MAIOR_NIVEL_PARA_ATACAR) {
      dados.nivelParaAtacarSozinho = padrao.nivelParaAtacarSozinho;
    }
    dados.sempreAgressivos = limpa(dados.sempreAgressivos, padrao.sempreAgressivos);
    dados.nuncaAgressivos = limpa(dados.nuncaAgressivos, padrao.nuncaAgressivos);
    dados.sempreFogem = limpa(dados.sempreFogem, padrao.sempreFogem);
    atual = dados;
    sempreAgressivos = Set.copyOf(dados.sempreAgressivos);
    nuncaAgressivos = Set.copyOf(dados.nuncaAgressivos);
    sempreFogem = Set.copyOf(dados.sempreFogem);
  }

  // Um número de "minimo" a "maximo"; fora disso (ou um valor que não é número), o padrão
  private static double entre(double valor, double minimo, double maximo, double padrao) {
    return valor >= minimo && valor <= maximo ? valor : padrao;
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
        + "  // A versão deste arquivo (o mod usa para atualizar os padrões antigos; não mude)\n"
        + "  \"" + VERSAO_DO_ARQUIVO + "\": " + VERSAO + ",\n"
        + "  // Dano dos golpes dos selvagens em você: \"pouco\" (metade) ou \"monstro\" (como um monstro do Minecraft do nível\n"
        + "  // dele). Muda também no jogo: /combatza dano pouco | /combatza dano monstro\n"
        + "  \"danoNoJogador\": " + gson.toJson(atual.danoNoJogador) + ",\n"
        + "  // Com um Pokémon seu fora, a chance de um golpe DE LONGE do selvagem ir em você em vez dele (de 0 a 1)\n"
        + "  \"chanceDeMirarEmVoce\": " + atual.chanceDeMirarEmVoce + ",\n"
        + "  // Na batalha, depois de usar um item da mochila (poção, reanimador, X) ou de dar/tirar o item segurado de um\n"
        + "  // Pokémon seu, quantos segundos você espera para usar outro (de 0 a 60; 0 = sem espera)\n"
        + "  \"segundosEntreItens\": " + atual.segundosEntreItens + ",\n"
        + "  // O EQUILÍBRIO DA BATALHA. Depois de editar: /combatza recarregar\n"
        + "  // A parte do dano normal (o dos jogos) que os golpes dos selvagens tiram dos Pokémon dos jogadores: 1 = o normal,\n"
        + "  // 0.75 = três quartos (de 0 a 5). Não muda o dano neles em você (danoNoJogador) nem o que tira por turno (queimadura...)\n"
        + "  \"danoDoSelvagem\": " + atual.danoDoSelvagem + ",\n"
        + "  // O mesmo para os golpes dos Pokémon dos jogadores nos selvagens: 1 = o normal, 1.5 = 50% a mais (de 0 a 5)\n"
        + "  \"danoDosSeusPokemon\": " + atual.danoDosSeusPokemon + ",\n"
        + "  // Até quantos blocos o selvagem te vê de frente, de dia e de noite (de 1 a 64), e quantos segundos te vendo para\n"
        + "  // te notar (o \"?\" enchendo; agachado, o dobro; de 0.25 a 60)\n"
        + "  \"alcanceDaVisaoDeDia\": " + atual.alcanceDaVisaoDeDia + ",\n"
        + "  \"alcanceDaVisaoDeNoite\": " + atual.alcanceDaVisaoDeNoite + ",\n"
        + "  \"segundosParaNotar\": " + atual.segundosParaNotar + ",\n"
        + "  // Depois de te notar, ataca sozinho quem tem esta raiva ou mais (ela vem da natureza, do nível, dos atributos e do\n"
        + "  // escuro; dá 0 a uns 170; menos raiva = mais selvagens atacam) e pelo menos este nível. Os outros só revidam ou fogem\n"
        + "  \"raivaParaAtacar\": " + atual.raivaParaAtacar + ",\n"
        + "  \"nivelParaAtacarSozinho\": " + atual.nivelParaAtacarSozinho + ",\n"
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
