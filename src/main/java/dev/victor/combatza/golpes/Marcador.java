package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.api.moves.Move;

import net.minecraft.world.phys.Vec3;

// 6.11c (o marcador, decidido por ele em 19/10: "com o foco, o mouse move o marcador do Pokémon em qualquer direção e o WASD
// continua andando; soltar = vai e ataca"): para onde o seu Pokémon vai ANTES de soltar o golpe, como um deslocamento no chão a
// partir dos pés do alvo do foco (assim ele acompanha o alvo que anda). As mesmas regras no seu jogo (a prévia) e no servidor
// (que confere o que chegou), para os dois limitarem igual
public class Marcador {
  // Em volta do alvo: até o alcance do golpe menos 2 blocos (o "2 dentro do alcance" da 5.6b); no Terremoto (área em volta
  // de si), até o raio dele, para o alvo nunca ficar de fora; e, longe, ele começa a 1,5 bloco do alvo
  private static final double FOLGA_DO_ALCANCE = 2.0;
  private static final double PERTO_DO_ALVO_NA_AREA_EM_VOLTA = 1.5;

  private Marcador() {
  }

  // Os golpes que usam o marcador: os de longe com alvo (único sem contato, área no alvo) e a área em volta de si. O de
  // contato corre até o alvo de qualquer jeito (Corrida); armadilha e os de si mesmo não vão a lugar nenhum
  public static boolean precisa(Move golpe) {
    FormaDoGolpe forma = FormaDoGolpe.de(golpe);
    return forma == FormaDoGolpe.UNICO && !EfeitosDosGolpes.fazContato(golpe) || forma == FormaDoGolpe.AREA_INIMIGOS
        || forma == FormaDoGolpe.AREA_TODOS;
  }

  private static double limite(Move golpe) {
    return FormaDoGolpe.de(golpe) == FormaDoGolpe.AREA_TODOS ? Balanceamento.RAIO_EM_VOLTA_DO_POKEMON
        : Mira.alcanceDo(golpe) - FOLGA_DO_ALCANCE;
  }

  // O deslocamento só no chão (sem altura) e no máximo no limite
  public static Vec3 limitar(Move golpe, Vec3 deslocamento) {
    Vec3 noChao = new Vec3(deslocamento.x, 0, deslocamento.z);
    double maximo = limite(golpe);
    return noChao.length() > maximo ? noChao.normalize().scale(maximo) : noChao;
  }

  // Onde o marcador começa: onde o Pokémon já está (soltar sem mexer o mouse = o golpe de sempre). Fora do limite: no
  // caminho até o alvo, no limite; no Terremoto, a 1,5 bloco do alvo
  public static Vec3 inicial(Move golpe, Vec3 doPokemon, Vec3 doAlvo) {
    Vec3 deslocamento = new Vec3(doPokemon.x - doAlvo.x, 0, doPokemon.z - doAlvo.z);
    if (deslocamento.length() <= limite(golpe)) {
      return deslocamento;
    }
    if (FormaDoGolpe.de(golpe) == FormaDoGolpe.AREA_TODOS && deslocamento.lengthSqr() > 1.0E-4) {
      return deslocamento.normalize().scale(PERTO_DO_ALVO_NA_AREA_EM_VOLTA);
    }
    return limitar(golpe, deslocamento);
  }
}
