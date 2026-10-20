package dev.victor.combatza.golpes;

public class Balanceamento {
  public static final double MULTIPLICADOR_DE_DANO = 1.0;
  public static final double STAB = 1.5;
  public static final double STAB_COM_ADAPTABILIDADE = 2.0;

  public static final double MULTIPLICADOR_CHANCE_CRITICO = 1.0;
  public static final double MULTIPLICADOR_DANO_CRITICO = 1.5;

  public static final double ALEATORIO_MINIMO = 0.85;
  public static final double ALEATORIO_MAXIMO = 1.0;

  // Contra monstro (zumbi, esqueleto...): dano = poder / 4 × nível / 50, × 1,5 se o golpe é do tipo do Pokémon. Era / 10 e sem
  // o tipo (ele achou fraco, 18/10): um Pokémon nível 30 com golpe de 40 do tipo dele mata um zumbi (20 de vida) em 3 golpes
  public static final double PODER_POR_PONTO_DE_VIDA_EM_MOB = 4.0;
  public static final double NIVEL_DE_REFERENCIA_EM_MOB = 50.0;
  public static final double PODER_CONTRA_MOB = 40.0;
  // 7.3: o golpe do selvagem em você (Dano.contraJogador): poder ÷ 20 × (1 + nível ÷ 50). O modo ("pouco" = metade, ou
  // "monstro") fica na Configuracao
  public static final double PODER_POR_DANO_NO_JOGADOR = 20.0;
  public static final double NIVEL_DO_DANO_NO_JOGADOR = 50.0;
  // 7.2d (escolha dele): a carga do selvagem (até o impacto) dura 50% a mais que a do seu Pokémon: dá tempo de ver o golpe
  // chegando e rolar
  public static final double CARGA_DO_SELVAGEM = 1.5;

  public static final double RAIO_NO_IMPACTO = 3.0;
  public static final double RAIO_EM_VOLTA_DO_POKEMON = 5.0;
  public static final double DANO_NA_BORDA_DA_AREA = 0.5;

  public static final double VELOCIDADE_DE_REFERENCIA = 80.0;
  public static final double FAIXA_DE_VELOCIDADE = 70.0;
  public static final double LIMITE_DO_CASTING = 0.25;

  public static final double SEGUNDOS_POR_PP = 1.0;
  public static final double SEGUNDOS_POR_TURNO = 3.5;
  // Quem entra trocando no meio da batalha fica este tempo sem atacar: a vez perdida da troca (escolha dele, 18/10)
  public static final double SEGUNDOS_DA_TROCA = 2.0;
  public static final double MULTIPLICADOR_DANO_DE_STATUS = 1.0;

  public static final int TURNOS_DE_SONO_MINIMO = 1;
  public static final int TURNOS_DE_SONO_MAXIMO = 3;
  public static final int TURNOS_DO_DESCANSO = 2;
  public static final double CHANCE_DE_DESCONGELAR = 0.2;
  public static final int TURNOS_CONGELADO_MAXIMO = 3;
  public static final double VELOCIDADE_NA_PARALISIA = 0.5;
  public static final double CHANCE_DE_FALHAR_NA_PARALISIA = 0.25;
  public static final double EMPURRAO_DO_FLINCH = 0.5;

  public static final double VELOCIDADE_DA_CORRIDA = 2.0;
  public static final double SEGUNDOS_MAXIMOS_DA_CORRIDA = 3.0;
  public static final double ALCANCE_DO_CONTATO = 1.0;

  public static final int TURNOS_DE_CONFUSAO_MINIMO = 1;
  public static final int TURNOS_DE_CONFUSAO_MAXIMO = 4;

  public static final double CHANCE_DE_SE_FERIR_NA_CONFUSAO = 0.33;
  public static final double PODER_DA_CONFUSAO = 40.0;

  public static final double CHANCE_DA_PELE_VERTENTE = 0.33;

  private Balanceamento() {
  }
}
