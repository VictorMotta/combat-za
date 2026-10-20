package dev.victor.combatza.combate;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.campo.CampoDaBatalha;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.PrimeiroTurno;
import dev.victor.combatza.golpes.StatusNossos;
import dev.victor.combatza.habilidades.Intimidacao;
import dev.victor.combatza.ia.Deteccao;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.mega.Mega;
import dev.victor.combatza.party.Parties;
import net.minecraft.server.level.ServerPlayer;

public class Combate {
  // 8.6 (dele, 20/10: "está impossível fugir deles correndo"; escolheu 16, era 24): a 16 blocos de todos os selvagens, você
  // fugiu (e o selvagem que fica a 16 blocos de todos os jogadores sai da batalha)
  private static final float DISTANCIA_FUGA = 16.0f;
  private final List<ServerPlayer> jogadores = new ArrayList<>();
  private final List<PokemonEntity> selvagens = new ArrayList<>();
  // Para o aviso de fim (AvisoDaBatalha): quem saiu derrotado (ou capturado) e quem fugiu
  private final List<Pokemon> derrotados = new ArrayList<>();
  private final List<Pokemon> fugiram = new ArrayList<>();
  // 8.7: quando a batalha começou (a Bola Rápida vale nos primeiros 3,5 s; a Bola Tempo melhora com o tempo)
  private final long comecouNoTick;
  // 5b.1: o clima (e, na 5b.3, o campo e as barreiras) desta batalha
  private final CampoDaBatalha campo = new CampoDaBatalha();

  public Combate(ServerPlayer primeiroJogador, PokemonEntity primeiroSelvagem) {
    comecouNoTick = primeiroJogador.server.getTickCount();
    adicionarJogador(primeiroJogador);
    adicionarSelvagem(primeiroSelvagem);
    // o jogador entrou antes de existir selvagem: só agora a Intimidação do time dele tem em quem pegar
    Intimidacao.doTime(primeiroJogador, selvagens);
  }

  // true se ele entrou agora (o aviso de começo é para quem entrou)
  public boolean adicionarJogador(ServerPlayer jogador) {
    if (jogadores.contains(jogador)) {
      return false;
    }
    jogadores.add(jogador);
    // P5-10: o que vale no campo pessoal dele (o Vento de Cauda de antes de atacar) entra na batalha
    Arenas.entrouNaBatalha(jogador, this);
    zerarEstagiosDoTime(jogador);
    PrimeiroTurno.esquecerTime(jogador);
    Intimidacao.doTime(jogador, selvagens);
    return true;
  }

  public boolean adicionarSelvagem(PokemonEntity selvagem) {
    if (selvagens.contains(selvagem)) {
      return false;
    }
    selvagens.add(selvagem);
    Estagios.zerar(selvagem.getPokemon());
    StatusNossos.acordarSeDormeDeCansaco(selvagem);
    PrimeiroTurno.esquecer(selvagem.getPokemon());
    Intimidacao.doSelvagem(selvagem, jogadores);
    return true;
  }

  public List<ServerPlayer> getJogadores() {
    return jogadores;
  }

  public List<PokemonEntity> getSelvagens() {
    return selvagens;
  }

  public long getComecouNoTick() {
    return comecouNoTick;
  }

  public CampoDaBatalha getCampo() {
    return campo;
  }

  public boolean terminou() {
    return selvagens.isEmpty() || jogadores.isEmpty();
  }

  public void removerSelvagensForaDeCombate() {
    selvagens.removeIf(selvagem -> {
      // Etapa 8: o que desmaiou e ficou caído no chão (Desmaio) sai como derrotado
      boolean caido = Desmaio.esta(selvagem);
      boolean sai = !selvagem.isAlive() || selvagem.isRemoved() || caido || selvagemFicouParaTras(selvagem);
      if (sai) {
        (selvagem.isAlive() && !selvagem.isRemoved() && !caido ? fugiram : derrotados).add(selvagem.getPokemon());
        CobblemonCombatZA.LOGGER.info("Selvagem {} saiu do combate!", selvagem.getName().getString());
        Estagios.zerar(selvagem.getPokemon());
        ItensSegurados.esquecer(selvagem.getPokemon());
      }
      return sai;
    });
  }

  public void removerJogadoresForaDeCombate() {
    jogadores.removeIf(jogador -> {
      boolean sai = !jogador.isAlive() || jogador.isRemoved() || jogadorFugiu(jogador) || timeTodoDesmaiado(jogador);
      if (sai) {
        CobblemonCombatZA.LOGGER.info("Jogador {} saiu do combate!", jogador.getName().getString());
        zerarEstagiosDoTime(jogador);
        Lutadores.esquecer(jogador);
        PrimeiroTurno.esquecerTime(jogador);
        // 9.1: a Cinta de Foco volta a valer e a Escolha destrava
        ItensSegurados.fimDaBatalha(jogador);
        // P5-10: o que ainda falta do clima, do campo e das barreiras volta para o campo pessoal dele
        Arenas.saiuDaBatalha(jogador, this);
        // 8.4: o medidor da Mega some (quem está Mega continua Mega, 8.4b)
        Mega.fimDaBatalha(jogador);
        avisarOFim(jogador);
      }
      return sai;
    });
  }

  // Por que a batalha acabou para ele. Sem selvagens, quem decide são eles (todos derrotados = venceu); com selvagens
  // ainda na luta, foi ele que saiu: desmaiou ou fugiu (saiu do jogo: sem aviso)
  private void avisarOFim(ServerPlayer jogador) {
    if (!jogador.isAlive()) {
      AvisoDaBatalha.terminou(jogador, AvisoDaBatalha.Fim.DESMAIOU, pokemonsNaLuta());
    } else if (timeTodoDesmaiado(jogador)) {
      // 8.6: todos os seus Pokémon desmaiaram. A batalha acaba, e quem lutava com você te deixa em paz por 1 minuto
      AvisoDaBatalha.terminou(jogador, AvisoDaBatalha.Fim.TIME_DESMAIOU, pokemonsNaLuta());
      Deteccao.perderOInteresse(jogador, selvagens);
    } else if (selvagens.isEmpty() && fugiram.isEmpty()) {
      AvisoDaBatalha.terminou(jogador, AvisoDaBatalha.Fim.VENCEU, derrotados);
    } else if (selvagens.isEmpty() && derrotados.isEmpty()) {
      AvisoDaBatalha.terminou(jogador, AvisoDaBatalha.Fim.SELVAGEM_FUGIU, fugiram);
    } else if (selvagens.isEmpty()) {
      List<Pokemon> todos = new ArrayList<>(derrotados);
      todos.addAll(fugiram);
      AvisoDaBatalha.terminou(jogador, AvisoDaBatalha.Fim.ACABOU, todos);
    } else if (!jogador.isRemoved()) {
      AvisoDaBatalha.terminou(jogador, AvisoDaBatalha.Fim.FUGIU, pokemonsNaLuta());
      // 8.6: você fugiu: quem lutava com você te deixa em paz por 1 minuto
      Deteccao.perderOInteresse(jogador, selvagens);
    }
  }

  // 8.6 (dele: "se meus Pokémon morrerem, ferrou, eles vão me seguir eternamente"): ninguém do seu time pode lutar
  public static boolean timeTodoDesmaiado(ServerPlayer jogador) {
    boolean temAlguem = false;
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      temAlguem = true;
      if (!pokemon.isFainted()) {
        return false;
      }
    }
    return temAlguem;
  }

  private List<Pokemon> pokemonsNaLuta() {
    return selvagens.stream().map(PokemonEntity::getPokemon).toList();
  }

  private boolean estaoPerto(ServerPlayer jogador, PokemonEntity selvagem) {
    return jogador.level() == selvagem.level() && jogador.distanceTo(selvagem) <= DISTANCIA_FUGA;
  }

  private boolean jogadorFugiu(ServerPlayer jogador) {
    if (pertoDeAlgumSelvagem(jogador)) {
      return false;
    }
    // Party: alguém da party dele que está perto dos selvagens, a até 32 blocos dele, segura ele na batalha
    for (ServerPlayer outro : jogadores) {
      if (outro != jogador && Parties.saoAliados(jogador, outro) && outro.level() == jogador.level()
          && outro.distanceTo(jogador) <= Parties.PERTO_PARA_LUTAR && pertoDeAlgumSelvagem(outro)) {
        return false;
      }
    }
    return true;
  }

  private boolean pertoDeAlgumSelvagem(ServerPlayer jogador) {
    for (PokemonEntity selvagem : selvagens) {
      if (estaoPerto(jogador, selvagem)) {
        return true;
      }
    }
    return false;
  }

  private boolean selvagemFicouParaTras(PokemonEntity selvagem) {
    for (ServerPlayer jogador : jogadores) {
      if (estaoPerto(jogador, selvagem)) {
        return false;
      }
    }
    return true;
  }

  public void zerarEstagios() {
    for (PokemonEntity selvagem : selvagens) {
      Estagios.zerar(selvagem.getPokemon());
    }
    for (ServerPlayer jogador : jogadores) {
      zerarEstagiosDoTime(jogador);
    }
  }

  private static void zerarEstagiosDoTime(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      Estagios.zerar(pokemon);
    }
  }

}