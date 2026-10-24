package dev.victor.combatza.combate;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.party.Parties;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import dev.victor.combatza.pvp.Duelos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;

public class Lados {
  private Lados() {
  }

  // Os NPCs (dele, 21/10: "meu Pokémon tá atacando NPC, isso não pode acontecer, mesmo se eu mirar nele"; 23/10: "adiciona
  // para os Pokémon nunca poder dar dano nesses NPCs do jogo", escolheu "todo NPC, aldeão incluído"): tudo que o Minecraft
  // marca como NPC (a interface Npc: o aldeão, o mercador ambulante, o mercador do CobbleDollars, o treinador do RCT), os
  // NPCs do próprio Cobblemon e tudo do mod rctmod (a Associação dos Treinadores). A mira passa por eles e nenhum golpe pega
  // neles (Mira, ControleDeGolpes, Armadilhas), nem os guardas vão neles (Guardas). O golem não é NPC: continua atacável
  private static final String MOD_DOS_TREINADORES = "rctmod";

  public static boolean ehNpc(Entity entidade) {
    return entidade instanceof Npc || entidade instanceof NPCEntity
        || MOD_DOS_TREINADORES.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entidade.getType()).getNamespace());
  }

  // 21/10 (PvP): os dois Pokémon estão do mesmo lado da batalha? Selvagem com selvagem (o do treinador também é "selvagem")
  // e com dono com dono; no duelo, cada jogador é um lado (pvp.Duelos). As habilidades e os golpes que olham "inimigo" e
  // "aliado" (Intimidação, Download, Ruínas, Presença Régia, as armadilhas...) perguntam aqui
  public static boolean mesmoLado(Pokemon um, Pokemon outro) {
    Boolean noDuelo = um.isWild() || outro.isWild() ? null : Duelos.mesmoLadoNoDuelo(um, outro);
    return noDuelo != null ? noDuelo : um.isWild() == outro.isWild();
  }

  public static boolean ehAliado(Entity entidade) {
    return entidade instanceof Player || (entidade instanceof PokemonEntity pokemon && !pokemon.getPokemon().isWild());
  }

  // 21/10 (PvP): quem a mira do jogador atravessa. Fora do duelo, os aliados (ehAliado); no duelo, tudo menos os Pokémon do
  // adversário (pvp.Duelos)
  public static boolean miraAtravessa(ServerPlayer jogador, Entity entidade) {
    Boolean noDuelo = Duelos.miraAtravessa(jogador, entidade);
    return noDuelo != null ? noDuelo : ehAliado(entidade);
  }

  // Etapa 8: o selvagem caído no chão (Desmaio) não é mais inimigo: os golpes passam por ele. 21/10 (treinadores): nem o
  // Pokémon do treinador que ainda está saindo da bola ou que já caiu (BatalhasDeTreinador)
  public static boolean ehInimigo(Entity entidade) {
    return entidade instanceof Enemy || (entidade instanceof PokemonEntity pokemon && pokemon.getPokemon().isWild()
        && !Desmaio.esta(pokemon) && !BatalhasDeTreinador.foraDaLuta(pokemon));
  }

  // 7.2: do ponto de vista de quem ataca. Para o SELVAGEM, o inimigo é o lado de cá: os jogadores e os Pokémon deles (os
  // outros selvagens são aliados dele). Para o seu Pokémon, o de sempre (ehInimigo)
  public static boolean ehInimigoDe(PokemonEntity atacante, Entity entidade) {
    // 21/10 (treinadores, dele: "no duelo, os Pokémon do treinador não atacam o jogador nem NPC"): só os Pokémon de quem
    // batalha com ele
    if (atacante != null && BatalhasDeTreinador.ehDoTreinador(atacante)) {
      return BatalhasDeTreinador.ehAlvoDoTreinador(atacante, entidade);
    }
    // 21/10 (PvP): no duelo, o inimigo do seu Pokémon é só o Pokémon do adversário (pvp.Duelos)
    Boolean noDuelo = atacante == null ? null : Duelos.ehInimigoNoDuelo(atacante.getPokemon(), entidade);
    if (noDuelo != null) {
      return noDuelo;
    }
    if (atacante != null && atacante.getPokemon().isWild()) {
      return (entidade instanceof Player jogador && !jogador.isSpectator())
          || (entidade instanceof PokemonEntity pokemon && !pokemon.getPokemon().isWild());
    }
    return ehInimigo(entidade);
  }

  // 7.2: no golpe do selvagem, você também entra na luta (pode levar o golpe); os monstros continuam de fora
  public static boolean entraNaLutaDe(PokemonEntity atacante, Entity entidade, boolean emBatalha) {
    // 21/10 (treinadores): o golpe do Pokémon do treinador (até o Terremoto, que pega todos em volta) nunca pega você
    if (atacante != null && BatalhasDeTreinador.ehDoTreinador(atacante)) {
      return BatalhasDeTreinador.ehAlvoDoTreinador(atacante, entidade);
    }
    if (atacante != null && atacante.getPokemon().isWild()) {
      return !emBatalha || entidade instanceof PokemonEntity || entidade instanceof Player;
    }
    return entraNaLuta(entidade, emBatalha);
  }

  // Fogo amigo (dele, 20/10: "pode colocar para dar menos 25% de dano, dar apenas 75% no aliado"): o golpe de um Pokémon com
  // dono num outro Pokémon com dono (o seu guarda, o Pokémon de outro jogador) tira 75%. Só a área em volta chega neles (os
  // outros golpes atravessam os aliados)
  private static final double DANO_NO_ALIADO = 0.75;

  // 21/10 (PvP): o Pokémon do adversário no duelo não é aliado: leva o dano cheio
  public static double fogoAmigo(Execucao execucao, Entity alvo) {
    return !execucao.getPokemon().isWild() && alvo instanceof PokemonEntity atingido
        && !atingido.getPokemon().isWild() && atingido.getPokemon() != execucao.getPokemon()
        && !Boolean.TRUE.equals(Duelos.ehInimigoNoDuelo(execucao.getPokemon(), alvo)) ? DANO_NO_ALIADO : 1.0;
  }

  // Party (dele, 20/10: "estranhos não entram na sua batalha, e os seus golpes não pegam neles nem nos Pokémon deles"): o golpe
  // de um Pokémon com dono passa pelo Pokémon de um estranho (o dono fora da sua party) e pelo selvagem que está na batalha de
  // outra pessoa (sem ninguém da sua party nela)
  public static boolean foraDaSuaLuta(Execucao execucao, Entity alvo) {
    Pokemon pokemon = execucao.getPokemon();
    // 21/10 (PvP): o duelo é só dos dois: o golpe de quem duela só pega nos Pokémon do adversário, e os Pokémon do duelo só
    // levam o golpe dele (pvp.Duelos)
    Boolean doDuelo = Duelos.foraDoDuelo(pokemon, alvo);
    if (doDuelo != null) {
      return doDuelo;
    }
    if (pokemon.isWild() || !(alvo instanceof PokemonEntity atingido)) {
      return false;
    }
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (!atingido.getPokemon().isWild()) {
      return !Parties.saoAliados(dono, atingido.getPokemon().getOwnerPlayer());
    }
    // 21/10 (treinadores): o Pokémon de um treinador só leva o golpe de quem está batalhando com ele (nem a party entra)
    if (BatalhasDeTreinador.ehDoTreinador(atingido)) {
      return !BatalhasDeTreinador.ehOAdversario(dono, atingido);
    }
    return daBatalhaDeOutro(dono, atingido);
  }

  // O selvagem está numa batalha em que não há ninguém da sua party
  public static boolean daBatalhaDeOutro(ServerPlayer jogador, PokemonEntity selvagem) {
    Combate combate = GerenciarCombates.combateDe(selvagem.getPokemon());
    if (jogador == null || combate == null || combate.getJogadores().isEmpty()) {
      return false;
    }
    for (ServerPlayer quemLuta : combate.getJogadores()) {
      if (Parties.saoAliados(jogador, quemLuta)) {
        return false;
      }
    }
    return true;
  }

  // 7.4b: monstro = os hostis do Minecraft (Enemy), que não são Pokémon
  public static boolean ehMonstro(Entity entidade) {
    return entidade instanceof Enemy && !(entidade instanceof PokemonEntity);
  }

  // Quem o golpe pega além do alvo marcado (no caminho do projétil e do jato, e na área do ponto de chegada).
  // 7.4b: o golpe do GUARDA (o seu Pokémon pela IA, Guardas) só pega MONSTROS, dentro e fora da batalha: atravessa os
  // selvagens (a luta é do lutador, e um acerto sem querer começaria outra), você e os aliados. E o alvo dele, mesmo não
  // sendo monstro (o golem ou o aldeão que você bateu), também na área em volta do Pokémon
  public static boolean pegaNoGolpe(Execucao execucao, PokemonEntity atacante, Entity entidade, boolean emBatalha) {
    // 23/10 (dele: "não dar dano não quer dizer que não é para atravessar: no meio da luta, se ele se enfia na frente, vai
    // atrapalhar"): o NPC nunca para o golpe. O projétil e o jato passam por ele e seguem até o alvo
    if (ehNpc(entidade)) {
      return false;
    }
    if (execucao.ehDoGuarda()) {
      return ehMonstro(entidade) || entidade == execucao.alvoMirado();
    }
    return ehInimigoDe(atacante, entidade) && entraNaLutaDe(atacante, entidade, emBatalha);
  }

  // Em batalha contra selvagem, os golpes do seu Pokémon atravessam os monstros e os bichos (zumbi, aranha, vaca...): só
  // Pokémon entram na luta, então o golpe não mira neles, não para neles e não pega neles (pedido dele, 18/10). Fora de
  // batalha, todo mundo entra, como antes
  public static boolean entraNaLuta(Entity entidade, boolean emBatalha) {
    return !emBatalha || entidade instanceof PokemonEntity;
  }
}
