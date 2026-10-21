package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 21/10 (golpes de status que faltavam): a ATRAÇÃO (Attract) e a PAIXÃO. Como nos jogos (Showdown, moves.js):
// - só pega no gênero oposto (macho e fêmea; sem gênero, nunca). A Insensível e o Véu Aromático seguram; a Erva Mental cura;
// - apaixonado, cada vez que ele tenta usar um golpe, metade das vezes não consegue ("A paixão de X impede que se mova!") e o
//   golpe vai para a recarga, como na paralisia. Corações em volta dele enquanto durar;
// - dura enquanto quem ele ama estiver na luta: acaba quando um dos dois volta para a bola, desmaia ou sai da batalha;
// - o Charme Fofo: quem encosta num Pokémon com ele (golpe de contato que tirou vida) tem 30% de se apaixonar por ele
//   (HabilidadesDeContato);
// - o Fio do Destino: quem se apaixona segurando ele apaixona quem causou, de volta
public class Atracao {
  private static final double CHANCE_DE_NAO_AGIR = 0.5;
  private static final int CHANCE_DO_CHARME_FOFO = 30;
  private static final String CHARME_FOFO = "cutecharm";
  private static final String INSENSIVEL = "oblivious";
  private static final String FIO_DO_DESTINO = "destinyknot";
  private static final int TICKS_ENTRE_CORACOES = 30;
  private static final int CORACOES = 3;

  private record Apaixonado(Pokemon pokemon, Pokemon porQuem) {
  }

  private static final Map<UUID, Apaixonado> APAIXONADOS = new HashMap<>();

  private Atracao() {
  }

  public static boolean apaixonado(Pokemon pokemon) {
    return APAIXONADOS.containsKey(pokemon.getUuid());
  }

  // Macho e fêmea (em qualquer ordem): o único par que se apaixona
  public static boolean generosOpostos(Pokemon um, Pokemon outro) {
    return um.getGender() == Gender.MALE && outro.getGender() == Gender.FEMALE
        || um.getGender() == Gender.FEMALE && outro.getGender() == Gender.MALE;
  }

  // Pega? (sem avisar: a IA pergunta antes de escolher e não tenta no mesmo gênero)
  public static boolean podePegar(Pokemon quemCausa, Pokemon alvo) {
    return !alvo.isFainted() && !apaixonado(alvo) && generosOpostos(quemCausa, alvo)
        && GolpesDeStatus.quemProtegeDaMente(alvo, true) == null;
  }

  // O golpe Atração acertou
  public static void tentar(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity entidade) || entidade.getPokemon().isFainted()) {
      return;
    }
    Pokemon pokemon = entidade.getPokemon();
    Pokemon quemCausa = atacante.getPokemon();
    if (!generosOpostos(quemCausa, pokemon)) {
      CobblemonCombatZA.LOGGER.info("ATRAÇÃO: {} ({}) e {} ({}) não combinam", quemCausa.getSpecies().getName(),
          quemCausa.getGender(), pokemon.getSpecies().getName(), pokemon.getGender());
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.immune", pokemon.getDisplayName(false)));
      return;
    }
    if (apaixonado(pokemon)) {
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
      return;
    }
    if (GolpesDeStatus.protegidoDaMente(pokemon, true)) {
      return;
    }
    apaixonar(pokemon, quemCausa, Component.empty());
  }

  // O Charme Fofo de quem apanhou (HabilidadesDeContato): 30% de quem encostou se apaixonar. Quando não pega, em silêncio
  public static void charmeFofo(PokemonEntity atacante, Pokemon defensor) {
    Pokemon pokemon = atacante.getPokemon();
    if (!Habilidades.tem(defensor, CHARME_FOFO) || atacante.getRandom().nextInt(100) >= CHANCE_DO_CHARME_FOFO
        || !podePegar(defensor, pokemon)) {
      return;
    }
    apaixonar(pokemon, defensor, Component.literal(" (").append(Component.translatable(
        "cobblemon_combat_za.habilidade.de", Habilidades.nome(defensor), defensor.getDisplayName(false))).append(")"));
  }

  private static void apaixonar(Pokemon pokemon, Pokemon porQuem, Component deOnde) {
    APAIXONADOS.put(pokemon.getUuid(), new Apaixonado(pokemon, porQuem));
    CobblemonCombatZA.LOGGER.info("ATRAÇÃO: {} se apaixonou por {}{}", pokemon.getSpecies().getName(),
        porQuem.getSpecies().getName(), deOnde.getString());
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.start.attract", pokemon.getDisplayName(false))
        .append(deOnde));
    Feedback.particulas(pokemon.getEntity(), ParticleTypes.HEART, CORACOES * 2);
    // O Fio do Destino de quem se apaixonou devolve a paixão (só se o outro ainda não estiver apaixonado)
    if (ItensSegurados.tem(pokemon, FIO_DO_DESTINO) && !apaixonado(porQuem) && !porQuem.isFainted()) {
      apaixonar(porQuem, pokemon, Component.literal(" (").append(ItensSegurados.nome(pokemon)).append(")"));
    }
    GolpesDeStatus.ervaMental(pokemon);
  }

  // ControleDeGolpes, antes de soltar o golpe (o seu e o da IA): metade das vezes ele não consegue. Como na paralisia, o
  // golpe vai para a recarga. true = não agiu
  public static boolean naoAgiu(ServerPlayer quemVe, Pokemon pokemon, int slot, Move golpe, long agora) {
    Apaixonado apaixonado = APAIXONADOS.get(pokemon.getUuid());
    PokemonEntity entidade = pokemon.getEntity();
    if (apaixonado == null || entidade == null || entidade.getRandom().nextDouble() >= CHANCE_DE_NAO_AGIR) {
      return false;
    }
    Recargas.iniciarRecarga(pokemon, slot, golpe, agora);
    CobblemonCombatZA.LOGGER.info("ATRAÇÃO: {} não usou {} (apaixonado por {})", pokemon.getSpecies().getName(),
        golpe.getName(), apaixonado.porQuem().getSpecies().getName());
    Feedback.particulas(entidade, ParticleTypes.HEART, CORACOES * 2);
    Feedback.historico(quemVe, pokemon, Component.translatable("cobblemon.battle.cant.attract",
        pokemon.getDisplayName(false)));
    return true;
  }

  public static void atualizar(long agora) {
    for (Apaixonado apaixonado : List.copyOf(APAIXONADOS.values())) {
      Pokemon pokemon = apaixonado.pokemon();
      if (!amadoNaLuta(apaixonado)) {
        acabar(pokemon, true);
      } else if (Habilidades.tem(pokemon, INSENSIVEL)) {
        // Ganhou a Insensível no meio (a Troca de Habilidades): a paixão acaba, como no Showdown
        acabar(pokemon, true);
      } else if (agora % TICKS_ENTRE_CORACOES == 0) {
        Feedback.particulas(pokemon.getEntity(), ParticleTypes.HEART, CORACOES);
      }
    }
  }

  // Quem ele ama ainda está fora da bola, de pé e na mesma batalha que ele
  private static boolean amadoNaLuta(Apaixonado apaixonado) {
    Pokemon amado = apaixonado.porQuem();
    PokemonEntity corpo = amado.getEntity();
    if (corpo == null || !corpo.isAlive() || amado.isFainted()) {
      return false;
    }
    Combate dele = GerenciarCombates.combateDe(apaixonado.pokemon());
    return dele != null && dele == GerenciarCombates.combateDe(amado);
  }

  static void semBatalha() {
    for (Apaixonado apaixonado : List.copyOf(APAIXONADOS.values())) {
      if (!GerenciarCombates.estaEmCombate(apaixonado.pokemon())) {
        acabar(apaixonado.pokemon(), false);
      }
    }
  }

  public static void acabar(Pokemon pokemon, boolean comFrase) {
    if (APAIXONADOS.remove(pokemon.getUuid()) == null) {
      return;
    }
    CobblemonCombatZA.LOGGER.info("ATRAÇÃO: {} superou a paixão", pokemon.getSpecies().getName());
    if (comFrase) {
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.end.attract", pokemon.getDisplayName(false)));
    }
  }

  public static void limpar() {
    APAIXONADOS.clear();
  }
}
