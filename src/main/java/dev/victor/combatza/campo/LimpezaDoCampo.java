package dev.victor.combatza.campo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.AplicarEfeitos;
import dev.victor.combatza.golpes.Armadilhas;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Preso;
import dev.victor.combatza.habilidades.Habilidades;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 21/10 (golpes de status que faltavam): os golpes que LIMPAM o campo (Showdown, moves.js):
// - Quebra-telha, Caninos Psíquicos e Investida Furiosa: antes do dano, quebram o Refletir, a Tela de Luz e o Véu de Aurora
//   do lado do alvo. No Showdown isso é o "onTryHit", que vem antes da imunidade e do desvio: quebra mesmo se não acertar;
// - Descondensar: a Evasão do alvo cai 1 (a Névoa ainda segura essa queda); somem do lado do alvo o Refletir, a Tela de Luz, o
//   Véu de Aurora, a Salvaguarda e a Névoa; as armadilhas dos DOIS lados; e o campo (Elétrico, de Plantas...);
// - Giro Rápido e Rotação Mortal: depois do acerto, quem girou se livra de quem o prendia (Preso) e recolhe as armadilhas do
//   outro lado (as que pegam nele). Com a Força Absoluta, não, como no Showdown;
// - Arrumar: recolhe as armadilhas dos dois lados e sobe o Ataque e a Velocidade em 1.
// As armadilhas daqui são zonas no chão (Armadilhas): "do lado" = as que estão perto, até 16 blocos
public class LimpezaDoCampo {
  private static final Set<String> QUEBRAM_AS_TELAS = Set.of("brickbreak", "psychicfangs", "ragingbull");
  private static final List<Barreira> TELAS = List.of(Barreira.REFLETIR, Barreira.TELA_DE_LUZ, Barreira.VEU_DE_AURORA);
  private static final List<Barreira> O_DESCONDENSAR_LEVA = List.of(Barreira.REFLETIR, Barreira.TELA_DE_LUZ,
      Barreira.VEU_DE_AURORA, Barreira.SALVAGUARDA, Barreira.NEVOA);
  private static final String ARRUMAR = "tidyup";
  private static final String FORCA_ABSOLUTA = "sheerforce";
  private static final int PARTICULAS = 15;

  private LimpezaDoCampo() {
  }

  // ControleDeGolpes.acertar, antes da conta do dano (só no primeiro acerto do golpe)
  public static void antesDoDano(Execucao execucao, Entity alvo) {
    if (QUEBRAM_AS_TELAS.contains(execucao.getGolpe().getName()) && alvo instanceof PokemonEntity entidade) {
      tirarBarreiras(entidade.getPokemon(), execucao.getPokemon(), TELAS);
    }
  }

  // O Descondensar acertou (GolpesDeStatus)
  public static void descondensar(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    AplicarEfeitos.mudarUmAtributo(execucao, alvo, "evasion", -1);
    Pokemon quem = execucao.getPokemon();
    if (alvo instanceof PokemonEntity entidade) {
      tirarBarreiras(entidade.getPokemon(), quem, O_DESCONDENSAR_LEVA);
    }
    avisarArmadilhas(quem, Armadilhas.recolher(atacante, false));
    Feedback.particulas(alvo, ParticleTypes.CLOUD, PARTICULAS);
    Arena arena = Arenas.de(quem);
    Terreno terreno = arena == null ? Terreno.NENHUM : arena.campo().getTerreno();
    if (terreno != Terreno.NENHUM) {
      arena.campo().mudarOTerreno(Terreno.NENHUM, null);
      CobblemonCombatZA.LOGGER.info("DESCONDENSAR: {} desfez {}", quem.getSpecies().getName(), terreno);
      arena.avisar(quem, terreno.acabou());
    }
  }

  // O Giro Rápido e a Rotação Mortal acertaram (GolpesDeStatus)
  public static void girar(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = atacante.getPokemon();
    if (!atacante.isAlive() || pokemon.isFainted() || Habilidades.tem(pokemon, FORCA_ABSOLUTA)) {
      return;
    }
    Move prendia = Preso.livrar(atacante);
    Arena arena = Arenas.de(pokemon);
    if (prendia != null && arena != null) {
      arena.avisar(pokemon, Component.translatable("cobblemon_combat_za.status.se_livrou", pokemon.getDisplayName(false),
          prendia.getDisplayName()));
    }
    avisarArmadilhas(pokemon, Armadilhas.recolher(atacante, true));
  }

  // O golpe sem alvo (GolpesDeStatus.semAlvo). true = era o Arrumar
  public static boolean arrumar(Execucao execucao, PokemonEntity atacante) {
    if (!ARRUMAR.equals(execucao.getGolpe().getName())) {
      return false;
    }
    Pokemon pokemon = execucao.getPokemon();
    Feedback.particulas(atacante, ParticleTypes.CLOUD, PARTICULAS);
    // Só a frase do fim (as das armadilhas, uma por tipo, ficariam muitas junto com as do Ataque e da Velocidade)
    List<Armadilhas.Recolhida> recolhidas = Armadilhas.recolher(atacante, false);
    Arena arena = Arenas.de(pokemon);
    if (!recolhidas.isEmpty() && arena != null) {
      arena.avisar(pokemon, Component.translatable("cobblemon.battle.activate.tidyup"));
    }
    AplicarEfeitos.mudarUmAtributo(execucao, atacante, "atk", 1);
    AplicarEfeitos.mudarUmAtributo(execucao, atacante, "spe", 1);
    return true;
  }

  // As barreiras pedidas, do lado de "doLado" (o lado dos jogadores, ou o dos selvagens), somem com a frase do fim
  private static void tirarBarreiras(Pokemon doLado, Pokemon quem, List<Barreira> quais) {
    Arena arena = Arenas.de(doLado);
    if (arena == null) {
      return;
    }
    boolean lado = CampoEBarreiras.doSeuLado(doLado);
    Map<Barreira, CampoDaBatalha.Prazo> barreiras = arena.campo().barreiras(lado);
    for (Barreira barreira : quais) {
      if (barreiras.remove(barreira) != null) {
        CobblemonCombatZA.LOGGER.info("BARREIRA: {} tirou {} ({} lado)", quem.getSpecies().getName(), barreira,
            lado ? "seu" : "dos selvagens");
        arena.avisar(quem, barreira.acabou(lado));
      }
    }
  }

  // Uma frase por tipo de armadilha e lado ("Os espinhos ao redor da sua equipe desapareceram do chão!")
  private static void avisarArmadilhas(Pokemon quem, List<Armadilhas.Recolhida> recolhidas) {
    Arena arena = Arenas.de(quem);
    if (arena == null) {
      return;
    }
    Set<String> ditas = new HashSet<>();
    for (Armadilhas.Recolhida recolhida : recolhidas) {
      // A armadilha do selvagem pega no lado dos jogadores: "ally" (quem lê é sempre um jogador)
      String chave = "cobblemon.battle.sideend." + (recolhida.doSelvagem() ? "ally." : "opponent.") + recolhida.golpe();
      if (ditas.add(chave)) {
        arena.avisar(quem, Component.translatable(chave));
      }
    }
  }
}
