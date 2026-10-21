package dev.victor.combatza.ia;

import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.golpes.Balanceamento;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.golpes.Dano;
import dev.victor.combatza.golpes.EfeitosDeVida;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.FormaDoGolpe;
import dev.victor.combatza.golpes.GolpesDeStatus;
import dev.victor.combatza.golpes.GolpesDesligados;
import dev.victor.combatza.golpes.PrimeiroTurno;
import dev.victor.combatza.golpes.Recargas;
import dev.victor.combatza.habilidades.HabilidadesDeProtecao;
import dev.victor.combatza.itens.TravaDaEscolha;
import net.minecraft.SharedConstants;
import net.minecraft.world.entity.Entity;

// A escolha do golpe por NOTA (7.2a), separada na 7.4 para ser o "cérebro único" (decisão dele, 18/10: "a IA única: o mesmo
// cérebro escolhe alvo e golpe para o selvagem, para o guarda e para o Pokémon do NPC"). Cada golpe pronto ganha uma nota, e
// ganha a maior, com um pouco de sorte (de ×0,8 a ×1,2):
// - com dano: poder × 1,5 se é do tipo dele × efetividade contra o alvo × a distância certa (contato perto, longe longe);
// - sem dano: o status e o reforço valem muito nos primeiros 10 s da luta, uma vez cada; a cura, com menos da metade da vida.
// "soComDano" (os guardas contra monstros): golpes de status não servem, e os de área em volta dele (o Terremoto) quase
// nunca, porque pegariam os aliados
public class EscolhaDoGolpe {
  private static final double PERTO_PARA_CONTATO = 4.0;
  private static final double LONGE_PARA_DISTANCIA = 6.0;
  private static final double NA_DISTANCIA_CERTA = 1.3;
  private static final double CONTATO_DE_LONGE = 0.75;
  private static final double PODER_DE_QUEM_NAO_TEM = 60;
  private static final double SORTE_MINIMA = 0.8;
  private static final double SORTE_A_MAIS = 0.4;
  private static final double AREA_PERTO_DE_ALIADOS = 0.3;
  // E as notas dos golpes sem dano
  private static final double SEGUNDOS_DO_COMECO = 10;
  private static final double NOTA_DO_STATUS_NO_COMECO = 100;
  private static final double NOTA_DO_STATUS_DEPOIS = 25;
  private static final double NOTA_DO_REFORCO_NO_COMECO = 90;
  private static final double NOTA_DO_REFORCO_DEPOIS = 20;
  private static final double NOTA_DO_REPETIDO = 8;
  private static final double NOTA_DA_ARMADILHA = 70;
  private static final double NOTA_DA_CURA = 150;
  private static final double CURA_ABAIXO_DE = 0.5;
  private static final double NOTA_DO_RESTO = 5;

  private EscolhaDoGolpe() {
  }

  // O golpe pronto de nota maior; -1 = nenhum pronto (ou nenhum que sirva contra este alvo). "jaUsados" = os golpes que ele
  // já usou nesta luta; "comecouNoTick" = quando a luta dele começou (o "começo" do status e do reforço)
  public static int melhor(PokemonEntity atacante, Entity alvo, Set<String> jaUsados, long comecouNoTick, long agora,
      boolean soComDano) {
    Pokemon pokemon = atacante.getPokemon();
    int melhor = -1;
    double melhorNota = 0;
    for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
      Move golpe = pokemon.getMoveSet().get(slot);
      // 21/10 (habilidades, lote da entrada e das imunidades): nem o golpe que uma habilidade ou o clima forte segura
      if (golpe == null || Recargas.ticksRestantes(pokemon, slot, agora) > 0
          || PrimeiroTurno.passouDaHora(pokemon, golpe) || TravaDaEscolha.bloqueia(pokemon, slot, golpe)
          // 21/10: provocado, ele não escolhe golpe de status; no Bis, só o golpe preso
          || GolpesDesligados.bloqueia(pokemon, slot, golpe)
          || HabilidadesDeProtecao.golpeVaiFalhar(pokemon, golpe, alvo)) {
        continue;
      }
      double nota = nota(atacante, golpe, alvo, jaUsados, comecouNoTick, agora, soComDano)
          * (SORTE_MINIMA + atacante.getRandom().nextDouble() * SORTE_A_MAIS);
      if (nota > melhorNota) {
        melhorNota = nota;
        melhor = slot;
      }
    }
    return melhor;
  }

  private static double nota(PokemonEntity atacante, Move golpe, Entity alvo, Set<String> jaUsados, long comecouNoTick,
      long agora, boolean soComDano) {
    if (golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) {
      return soComDano ? 0 : notaSemDano(atacante, golpe, alvo, jaUsados, comecouNoTick, agora);
    }
    Pokemon pokemon = atacante.getPokemon();
    double efetividade = Dano.efetividadeContra(pokemon, golpe, alvo);
    if (efetividade <= 0) {
      return 0;
    }
    double poder = golpe.getPower() > 0 ? golpe.getPower() : PODER_DE_QUEM_NAO_TEM;
    boolean doTipoDele = golpe.getType() == pokemon.getPrimaryType() || golpe.getType() == pokemon.getSecondaryType();
    double nota = poder * (doTipoDele ? Balanceamento.STAB : 1.0) * efetividade;
    double distancia = atacante.distanceTo(alvo);
    if (EfeitosDosGolpes.fazContato(golpe)) {
      nota *= distancia <= PERTO_PARA_CONTATO ? NA_DISTANCIA_CERTA : CONTATO_DE_LONGE;
    } else if (distancia >= LONGE_PARA_DISTANCIA) {
      nota *= NA_DISTANCIA_CERTA;
    }
    if (soComDano && FormaDoGolpe.de(golpe) == FormaDoGolpe.AREA_TODOS) {
      nota *= AREA_PERTO_DE_ALIADOS;
    }
    return nota;
  }

  private static double notaSemDano(PokemonEntity atacante, Move golpe, Entity alvo, Set<String> jaUsados,
      long comecouNoTick, long agora) {
    Pokemon pokemon = atacante.getPokemon();
    if (EfeitosDeVida.ehDeCura(golpe)) {
      return pokemon.getCurrentHealth() < pokemon.getMaxHealth() * CURA_ABAIXO_DE ? NOTA_DA_CURA : 0;
    }
    // 21/10 (golpes de status que faltavam): a Atração no mesmo gênero, a Provocação em quem já está provocado, o Truque de
    // Agilidade sendo o mais rápido... não servem (GolpesDeStatus)
    if (!GolpesDeStatus.serveContra(atacante, golpe, alvo)) {
      return 0;
    }
    boolean jaUsou = jaUsados.contains(golpe.getName());
    FormaDoGolpe forma = FormaDoGolpe.de(golpe);
    if (forma == FormaDoGolpe.ARMADILHA) {
      return jaUsou ? 0 : NOTA_DA_ARMADILHA;
    }
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(golpe);
    if (efeito == null) {
      return NOTA_DO_RESTO;
    }
    boolean comeco = agora - comecouNoTick < SEGUNDOS_DO_COMECO * SharedConstants.TICKS_PER_SECOND;
    if (forma == FormaDoGolpe.SEM_ALVO || "self".equals(efeito.target())) {
      boolean reforco = efeito.boosts() != null || (efeito.self() != null && efeito.self().boosts() != null);
      if (!reforco) {
        return NOTA_DO_RESTO;
      }
      return jaUsou ? NOTA_DO_REPETIDO : (comeco ? NOTA_DO_REFORCO_NO_COMECO : NOTA_DO_REFORCO_DEPOIS);
    }
    // No alvo: o status (sono, veneno...), a confusão ou baixar um atributo. Em quem não é Pokémon, nada disso pega
    if (!(alvo instanceof PokemonEntity alvoPokemon)) {
      return 0;
    }
    if (efeito.status() != null && alvoPokemon.getPokemon().getStatus() != null) {
      return 0;
    }
    if ("confusion".equals(efeito.volatileStatus()) && Confusao.estaConfuso(alvoPokemon.getPokemon())) {
      return 0;
    }
    return jaUsou ? NOTA_DO_REPETIDO : (comeco ? NOTA_DO_STATUS_NO_COMECO : NOTA_DO_STATUS_DEPOIS);
  }
}
