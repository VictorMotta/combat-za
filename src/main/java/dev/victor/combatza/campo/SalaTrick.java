package dev.victor.combatza.campo;

import java.util.function.ToDoubleFunction;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Execucao;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 21/10 (golpes de status que faltavam): o TRUQUE DE AGILIDADE (Trick Room, a "Sala Trick"). Como nos jogos (Showdown,
// moves.js; a velocidade virada fica no getActionSpeed do sim/pokemon.ts): por 5 turnos (17,5 s), na batalha toda (os dois
// lados), os lentos agem "primeiro". No tempo real, a velocidade pesa em dois lugares, e nos dois ela VIRA DO AVESSO:
// - a RECARGA (Recargas: quanto mais rápido, menos espera) e a CARGA (Execucao: quanto mais rápido, mais cedo o golpe sai);
// - "do avesso" = um espelho entre a menor e a maior velocidade de quem está na luta: o mais lento fica com a velocidade do
//   mais rápido, e o mais rápido com a do mais lento. Você contra um selvagem: os dois TROCAM de velocidade. Sozinho (o campo
//   pessoal com um Pokémon só), nada muda;
// - a prioridade (Ataque Rápido, Fingimento...) não muda, como nos jogos. Usar de novo com a sala de pé desfaz a sala.
// Fica no campo (CampoDaBatalha), como o Campo Elétrico: vai do campo pessoal para a batalha e volta. Ícone na passiva
public class SalaTrick {
  // A etiqueta da passiva ("cobblemon_combat_za.campo.sala_trick")
  public static final String NOME = "sala_trick";
  private static final String GOLPE = "trickroom";
  private static final int TURNOS = 5;
  private static final int PARTICULAS = 12;
  private static final double ESPALHAR = 6.0;

  private SalaTrick() {
  }

  public static boolean ativa(Pokemon pokemon) {
    Arena arena = pokemon == null ? null : Arenas.de(pokemon);
    return arena != null && arena.campo().getPrazoDaSalaTrick() != null;
  }

  // O golpe sem alvo (GolpesDeStatus.semAlvo). true = era o Truque de Agilidade
  public static boolean aoUsarGolpe(Execucao execucao, PokemonEntity atacante) {
    if (!GOLPE.equals(execucao.getGolpe().getName()) || !(atacante.level() instanceof ServerLevel nivel)) {
      return false;
    }
    particulas(nivel, atacante, PARTICULAS * 3);
    Pokemon pokemon = execucao.getPokemon();
    Arena arena = Arenas.de(pokemon);
    if (arena == null) {
      return true;
    }
    CampoDaBatalha campo = arena.campo();
    if (campo.getPrazoDaSalaTrick() != null) {
      campo.mudarASalaTrick(null);
      CobblemonCombatZA.LOGGER.info("TRUQUE DE AGILIDADE: {} desfez a sala", pokemon.getSpecies().getName());
      arena.avisar(pokemon, Component.translatable("cobblemon.battle.fieldend.trickroom"));
      return true;
    }
    long agora = nivel.getServer().getTickCount();
    long duracao = TURNOS * EfeitosDeStatus.ticksPorTurno();
    campo.mudarASalaTrick(new CampoDaBatalha.Prazo(agora + duracao, duracao));
    CobblemonCombatZA.LOGGER.info("TRUQUE DE AGILIDADE: {} virou a velocidade por {} turnos ({})",
        pokemon.getSpecies().getName(), TURNOS, arena.naBatalha() ? "na batalha" : "fora da batalha");
    arena.avisar(pokemon, Component.translatable("cobblemon.battle.fieldstart.trickroom", pokemon.getDisplayName(false)));
    return true;
  }

  // A cada tick (CampoEBarreiras.atualizar): o fim da sala e, a cada segundo, as partículas em volta de quem está na arena
  public static void atualizar(Arena arena, long agora, boolean virouSegundo) {
    CampoDaBatalha campo = arena.campo();
    CampoDaBatalha.Prazo prazo = campo.getPrazoDaSalaTrick();
    if (prazo == null) {
      return;
    }
    if (agora >= prazo.fim()) {
      campo.mudarASalaTrick(null);
      CobblemonCombatZA.LOGGER.info("TRUQUE DE AGILIDADE: acabou");
      arena.avisar(null, Component.translatable("cobblemon.battle.fieldend.trickroom"));
      return;
    }
    if (virouSegundo) {
      for (ServerPlayer jogador : arena.jogadores()) {
        particulas(jogador.serverLevel(), jogador, PARTICULAS);
      }
    }
  }

  // A velocidade que vale na recarga e na carga. "velocidade" é a dele SEM a sala; "medir" é a mesma conta (a de quem
  // pergunta: Recargas ou Execucao), feita também nos outros Pokémon da arena para achar a menor e a maior
  public static double velocidade(Pokemon pokemon, double velocidade, ToDoubleFunction<Pokemon> medir) {
    Arena arena = Arenas.de(pokemon);
    if (arena == null || arena.campo().getPrazoDaSalaTrick() == null) {
      return velocidade;
    }
    double menor = velocidade;
    double maior = velocidade;
    for (PokemonEntity entidade : arena.pokemons()) {
      if (entidade.isAlive() && entidade.getPokemon() != pokemon) {
        double outra = medir.applyAsDouble(entidade.getPokemon());
        menor = Math.min(menor, outra);
        maior = Math.max(maior, outra);
      }
    }
    return menor + maior - velocidade;
  }

  // A IA (GolpesDeStatus.serveContra): sem a sala, só quem é mais lento que o alvo chama; com a sala, só o mais rápido usa
  // (para desfazer)
  public static boolean convem(Pokemon pokemon, Pokemon alvo) {
    if (alvo == null) {
      return false;
    }
    boolean maisLento = pokemon.getSpeed() < alvo.getSpeed();
    return ativa(pokemon) != maisLento;
  }

  private static void particulas(ServerLevel nivel, Entity onde, int quantidade) {
    nivel.sendParticles(ParticleTypes.REVERSE_PORTAL, onde.getX(), onde.getY() + 1.0, onde.getZ(), quantidade, ESPALHAR,
        1.0, ESPALHAR, 0.0);
  }
}
