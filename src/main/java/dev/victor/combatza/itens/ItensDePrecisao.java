package dev.victor.combatza.itens;

import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.golpes.AgendaDeGolpes;
import dev.victor.combatza.golpes.Jatos;

// Lote 2 dos itens segurados: os que mexem na chance de ACERTAR (Precisao.chanceDeAcertar), com os números do Showdown:
// - Lentes Largas: ×1,1 (4505/4096);
// - Lentes de Aumento (nos jogos, quando quem segura age depois do alvo): ×1,2 contra um alvo que está no meio de um golpe
//   (carregando ou soltando), o "agir depois" do tempo real;
// - Baga Micle guardada (Bagas): o próximo golpe ×1,2;
// - Pó Brilhante em quem apanha: ×0,9 (3686/4096)
public class ItensDePrecisao {
  private static final double LENTES_LARGAS = 4505.0 / 4096.0;
  private static final double LENTES_DE_AUMENTO = 4915.0 / 4096.0;
  private static final double MICLE = 4915.0 / 4096.0;
  private static final double PO_BRILHANTE = 3686.0 / 4096.0;

  private ItensDePrecisao() {
  }

  public static double multiplicador(Pokemon atacante, Pokemon alvo) {
    String item = ItensSegurados.id(atacante);
    double multiplicador = 1.0;
    if ("widelens".equals(item)) {
      multiplicador *= LENTES_LARGAS;
    }
    if ("zoomlens".equals(item) && (AgendaDeGolpes.estaOcupado(alvo) || Jatos.estaJorrando(alvo))) {
      multiplicador *= LENTES_DE_AUMENTO;
    }
    if (EstadoDosItens.temMicle(atacante)) {
      multiplicador *= MICLE;
    }
    if (ItensSegurados.tem(alvo, "brightpowder")) {
      multiplicador *= PO_BRILHANTE;
    }
    return multiplicador;
  }
}
