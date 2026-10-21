package dev.victor.combatza.combate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.party.Parties;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.golpes.PrimeiroTurno;
import dev.victor.combatza.ia.Bandos;
import dev.victor.combatza.ia.Parceiro;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.mega.Mega;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import dev.victor.combatza.pvp.Duelos;
import net.minecraft.server.level.ServerPlayer;

public class GerenciarCombates {
  private GerenciarCombates() {
  }

  private static final List<Combate> combates = new ArrayList<>();
  private static final int TICKS_DA_PARTY = 20;
  private static int ticksDaParty = 0;

  public static Combate encontrarCombate(ServerPlayer jogador, PokemonEntity selvagem) {
    for (Combate combate : combates) {
      if (combate.getJogadores().contains(jogador) || combate.getSelvagens().contains(selvagem)) {
        return combate;
      }
    }
    return null;
  }

  // quemComecou: o Pokémon do jogador que acertou o selvagem ou que ele atacou (null se foi o jogador): vira o lutador
  public static void entrarEmCombate(ServerPlayer jogador, PokemonEntity selvagem, Pokemon quemComecou) {
    // Etapa 8: caído no chão, ele não volta para batalha nenhuma (só espera a bola). 21/10 (PvP): quem está num duelo também
    // não entra em batalha com selvagem até o duelo acabar (pvp.Duelos)
    if (Desmaio.esta(selvagem) || Duelos.estaEmDuelo(jogador)) {
      return;
    }
    // 21/10 (treinadores): a batalha contra um treinador do RCT é só sua e dele (ninguém entra, nenhum selvagem se mistura)
    if (BatalhasDeTreinador.foraDoDuelo(jogador, selvagem)) {
      return;
    }
    Combate combate = encontrarCombate(jogador, selvagem);
    // Party: estranho não entra na batalha de outra pessoa
    if (combate != null && !combate.getJogadores().contains(jogador) && !temAliado(combate, jogador)) {
      CobblemonCombatZA.LOGGER.info("PARTY: {} não entra na batalha de outra pessoa", jogador.getName().getString());
      return;
    }
    boolean selvagemNovo = true;
    if (combate == null) {
      combate = new Combate(jogador, selvagem);
      combates.add(combate);
      CobblemonCombatZA.LOGGER.info("Novo combate! combates ativos: {}", combates.size());
    } else {
      boolean jogadorNovo = combate.adicionarJogador(jogador);
      selvagemNovo = combate.adicionarSelvagem(selvagem);
      for (ServerPlayer quemJaLutava : combate.getJogadores()) {
        if (selvagemNovo && quemJaLutava != jogador) {
          AvisoDaBatalha.entrou(quemJaLutava, selvagem);
        }
      }
      if (selvagemNovo && !jogadorNovo) {
        AvisoDaBatalha.entrou(jogador, selvagem);
      }
    }
    CobblemonCombatZA.LOGGER.info("Combate: {} jogador(es) vs {}", combate.getJogadores().size(),
        combate.getSelvagens().size());
    // 7.9: com "todos de guarda" ligado, a batalha desliga o modo, e quem luta é o que ele escolhe (Parceiro)
    Pokemon doModo = Parceiro.desligarNaBatalha(jogador);
    Lutadores.comecou(jogador, doModo != null ? doModo : quemComecou);
    // 7.5: quem acabou de entrar chama o bando dele (Bandos)
    if (selvagemNovo) {
      Bandos.chamar(jogador, selvagem);
    }
    // Party: quem é da sua party e está perto entra junto
    puxarAParty(jogador, selvagem);
  }

  private static boolean temAliado(Combate combate, ServerPlayer jogador) {
    for (ServerPlayer quemLuta : combate.getJogadores()) {
      if (Parties.saoAliados(jogador, quemLuta)) {
        return true;
      }
    }
    return false;
  }

  // Party (dele, 20/10: "a party luta junta"): quem é da party, está no mesmo mundo a até 32 blocos e não está em outra
  // batalha, entra nesta
  private static void puxarAParty(ServerPlayer jogador, PokemonEntity selvagem) {
    for (ServerPlayer amigo : Parties.outrosOnline(jogador)) {
      if (amigo.isAlive() && amigo.level() == jogador.level() && amigo.distanceTo(jogador) <= Parties.PERTO_PARA_LUTAR
          && !estaEmCombate(amigo) && !Desmaio.esta(selvagem)) {
        CobblemonCombatZA.LOGGER.info("PARTY: {} entra na batalha de {}", amigo.getName().getString(),
            jogador.getName().getString());
        entrarEmCombate(amigo, selvagem, null);
      }
    }
  }

  public static void atualizar() {
    for (Combate combate : combates) {
      combate.removerSelvagensForaDeCombate();
      combate.removerJogadoresForaDeCombate();
    }
    // Party: a cada segundo, quem da party chegou perto de uma batalha dela entra
    if (++ticksDaParty >= TICKS_DA_PARTY) {
      ticksDaParty = 0;
      for (Combate combate : List.copyOf(combates)) {
        if (!combate.getSelvagens().isEmpty()) {
          for (ServerPlayer quemLuta : List.copyOf(combate.getJogadores())) {
            puxarAParty(quemLuta, combate.getSelvagens().get(0));
          }
        }
      }
    }
    // 8.2: quem enfrenta cada selvagem, para o XP quando ele desmaiar ou for capturado
    Experiencia.anotar(combates);
    combates.removeIf(combate -> {
      if (combate.terminou()) {
        combate.zerarEstagios();
        // 6.5e (dele, 19/10: "só em batalha, sem zerar no fim"): as recargas que estavam contando terminam normalmente
        combate.getJogadores().forEach(Lutadores::esquecer);
        // A batalha acabou: o Fingimento volta, pronto para a próxima (pedido dele, 18/10)
        combate.getJogadores().forEach(PrimeiroTurno::esquecerTime);
        // 8.4: o medidor da Mega some (quem está Mega continua Mega); a próxima batalha começa com ele cheio
        combate.getJogadores().forEach(Mega::fimDaBatalha);
        // 9.1: a Cinta de Foco volta a valer e a Escolha destrava (os seus e os selvagens que ficaram)
        combate.getJogadores().forEach(ItensSegurados::fimDaBatalha);
        combate.getSelvagens().forEach(selvagem -> ItensSegurados.esquecer(selvagem.getPokemon()));
        // P5-10: o clima, o campo e as barreiras que ainda faltam voltam para o campo pessoal de cada um
        combate.getJogadores().forEach(jogador -> Arenas.saiuDaBatalha(jogador, combate));
        CobblemonCombatZA.LOGGER.info("Combate Terminou! Combates ativos agora: {}",
            combates.size() - 1);
        return true;
      }
      return false;
    });
  }

  // 21/10 (PvP): o duelo aceito pelo desafio do Cobblemon entra na lista das batalhas (pvp.Duelos)
  public static void adicionar(Combate combate) {
    combates.add(combate);
    CobblemonCombatZA.LOGGER.info("Novo combate! combates ativos: {}", combates.size());
  }

  // As batalhas que estão valendo (o PainelDaBatalha monta o painel de cada uma); só para ler
  public static List<Combate> todos() {
    return Collections.unmodifiableList(combates);
  }

  public static void encerrarTodos() {
    combates.clear();
  }

  public static boolean estaEmCombate(Pokemon pokemon) {
    ServerPlayer dono = pokemon.getOwnerPlayer();
    for (Combate combate : combates) {
      if (combate.getSelvagens().contains(pokemon.getEntity()) || combate.getJogadores().contains(dono)) {
        return true;
      }
    }
    return false;
  }

  public static boolean estaEmCombate(ServerPlayer jogador) {
    for (Combate combate : combates) {
      if (combate.getJogadores().contains(jogador)) {
        return true;
      }
    }
    return false;
  }

  // 7.2e (dele, 19/10: "quando tiver vários, o dano deles reduz também, a não ser que eu e meu amigo estejamos juntos; o
  // dano deles não pode ser monstruoso"; escolheu o "suave"): com mais selvagens que jogadores na batalha, o dano de cada
  // selvagem é multiplicado pela raiz de (jogadores ÷ selvagens). Você sozinho: 2 selvagens = 71% cada, 3 = 58%, 4 = 50%.
  // Você e um amigo contra 2: dano normal
  public static double equilibrioDoBando(PokemonEntity selvagem) {
    for (Combate combate : combates) {
      if (combate.getSelvagens().contains(selvagem)) {
        int selvagens = combate.getSelvagens().size();
        int jogadores = combate.getJogadores().size();
        return selvagens <= jogadores ? 1.0 : Math.sqrt((double) jogadores / selvagens);
      }
    }
    return 1.0;
  }

  public static List<ServerPlayer> jogadoresNoCombateDe(PokemonEntity selvagem) {
    for (Combate combate : combates) {
      if (combate.getSelvagens().contains(selvagem)) {
        return List.copyOf(combate.getJogadores());
      }
    }
    return List.of();
  }

  // P5-10: a batalha em que o jogador está; null = nenhuma
  public static Combate combateDoJogador(ServerPlayer jogador) {
    for (Combate combate : combates) {
      if (combate.getJogadores().contains(jogador)) {
        return combate;
      }
    }
    return null;
  }

  // 5b.1: a batalha de um Pokémon (o selvagem dela, ou um Pokémon de um jogador dela); null = fora de batalha
  public static Combate combateDe(Pokemon pokemon) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    for (Combate combate : combates) {
      if (dono != null ? combate.getJogadores().contains(dono) : combate.getSelvagens().contains(pokemon.getEntity())) {
        return combate;
      }
    }
    return null;
  }

  // 8.2: a batalha de um selvagem pelo UUID do Pokémon dele (na captura, o corpo já pode ter sumido); null = nenhuma
  public static Combate combateDoSelvagem(UUID pokemon) {
    for (Combate combate : combates) {
      for (PokemonEntity selvagem : combate.getSelvagens()) {
        if (selvagem.getPokemon().getUuid().equals(pokemon)) {
          return combate;
        }
      }
    }
    return null;
  }

  // Quem está do outro lado para o jogador. 21/10 (PvP): no duelo, o lutador do adversário (Combate.inimigosDe)
  public static List<PokemonEntity> selvagensNoCombateDe(ServerPlayer jogador) {
    for (Combate combate : combates) {
      if (combate.getJogadores().contains(jogador)) {
        return combate.inimigosDe(jogador);
      }
    }
    return List.of();
  }
}