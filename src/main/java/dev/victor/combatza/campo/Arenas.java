package dev.victor.combatza.campo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.pvp.Duelo;
import net.minecraft.server.level.ServerPlayer;

// P5-10 (veto dele, 20/10: "fora de batalha pode adicionar também a mesma coisa de dentro"): fora da batalha, cada jogador
// tem o campo pessoal dele, onde os golpes de clima, de campo e de barreira dos Pokémon dele valem do mesmo jeito. Entrou numa
// batalha: o que está valendo vai junto para ela (dá para preparar o Vento de Cauda antes de atacar). Saiu: o que ainda falta
// volta para o campo pessoal e continua contando
public class Arenas {
  private static final Map<UUID, CampoDaBatalha> PESSOAIS = new HashMap<>();

  private Arenas() {
  }

  // Onde este Pokémon está: a batalha dele, ou o campo pessoal do dono. O selvagem fora da batalha não tem (null)
  public static Arena de(Pokemon pokemon) {
    Combate combate = GerenciarCombates.combateDe(pokemon);
    if (combate != null) {
      return daBatalha(combate);
    }
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    return dono == null ? null : doJogador(dono);
  }

  public static Arena daBatalha(Combate combate) {
    return new Arena(combate.getCampo(), combate.getJogadores(), naLuta(combate), true);
  }

  public static Arena doJogador(ServerPlayer jogador) {
    return new Arena(PESSOAIS.computeIfAbsent(jogador.getUUID(), uuid -> new CampoDaBatalha()), List.of(jogador),
        foraDaBola(jogador), false);
  }

  // Os Pokémon da batalha: os selvagens e o lutador de cada jogador
  private static List<PokemonEntity> naLuta(Combate combate) {
    List<PokemonEntity> todos = new ArrayList<>(combate.getSelvagens());
    for (ServerPlayer jogador : combate.getJogadores()) {
      Pokemon lutador = Lutadores.de(jogador);
      if (lutador != null && lutador.getEntity() != null) {
        todos.add(lutador.getEntity());
      }
    }
    return todos;
  }

  // Fora da batalha: todos os Pokémon dele fora da bola (o parceiro e os guardas)
  private static List<PokemonEntity> foraDaBola(ServerPlayer jogador) {
    List<PokemonEntity> fora = new ArrayList<>();
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      PokemonEntity entidade = pokemon.getEntity();
      if (entidade != null && entidade.isAlive()) {
        fora.add(entidade);
      }
    }
    return fora;
  }

  // Entrou numa batalha: o que vale no campo pessoal vai para ela
  public static void entrouNaBatalha(ServerPlayer jogador, Combate combate) {
    CampoDaBatalha pessoal = PESSOAIS.get(jogador.getUUID());
    if (pessoal == null || !pessoal.temEfeitoDeGolpe()) {
      return;
    }
    combate.getCampo().receber(pessoal, true, ladoNaBatalha(jogador, combate));
    pessoal.esvaziarOsGolpes();
    CobblemonCombatZA.LOGGER.info("CAMPO: o que valia para {} entrou na batalha", jogador.getName().getString());
  }

  // Saiu da batalha: o que ainda falta volta para o campo pessoal
  public static void saiuDaBatalha(ServerPlayer jogador, Combate combate) {
    if (jogador.hasDisconnected()) {
      return;
    }
    PESSOAIS.computeIfAbsent(jogador.getUUID(), uuid -> new CampoDaBatalha()).receber(combate.getCampo(),
        ladoNaBatalha(jogador, combate), true);
  }

  // O lado do jogador no campo da batalha: o dos jogadores; no duelo, cada um tem o seu (pvp.Duelo)
  private static boolean ladoNaBatalha(ServerPlayer jogador, Combate combate) {
    return !(combate instanceof Duelo duelo) || duelo.ladoDe(jogador);
  }

  public static void esquecer(ServerPlayer jogador) {
    PESSOAIS.remove(jogador.getUUID());
  }

  public static void limpar() {
    PESSOAIS.clear();
  }
}
