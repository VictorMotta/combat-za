package dev.victor.combatza.combate;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSeenEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.campo.CampoDaBatalha;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.GolpesDeStatus;
import dev.victor.combatza.golpes.PrimeiroTurno;
import dev.victor.combatza.golpes.StatusNossos;
import dev.victor.combatza.habilidades.HabilidadesDeFuga;
import dev.victor.combatza.habilidades.Intimidacao;
import dev.victor.combatza.ia.Deteccao;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.itens.ItensDoCampo;
import dev.victor.combatza.mega.Mega;
import dev.victor.combatza.party.Parties;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
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

  // 21/10 (PvP): a batalha sem selvagem, o duelo entre dois jogadores (pvp.Duelo). Quem entra nela, o duelo mesmo põe
  protected Combate(long comecouNoTick) {
    this.comecouNoTick = comecouNoTick;
  }

  // true se ele entrou agora (o aviso de começo é para quem entrou)
  public boolean adicionarJogador(ServerPlayer jogador) {
    if (jogadores.contains(jogador)) {
      return false;
    }
    jogadores.add(jogador);
    for (PokemonEntity selvagem : selvagens) {
      viu(jogador, selvagem);
    }
    // P5-10: o que vale no campo pessoal dele (o Vento de Cauda de antes de atacar) entra na batalha
    Arenas.entrouNaBatalha(jogador, this);
    zerarEstagiosDoTime(jogador);
    PrimeiroTurno.esquecerTime(jogador);
    // 21/10: a Provocação, o Bis, a paixão e a habilidade trocada de uma luta antiga não vêm junto
    GolpesDeStatus.esquecerTime(jogador);
    // Lotes 2 e 3 dos itens: a batalha começa com os itens de uma vez valendo e sem nada guardado (o Metrônomo do zero)
    ItensSegurados.fimDaBatalha(jogador);
    Intimidacao.doTime(jogador, selvagens);
    return true;
  }

  public boolean adicionarSelvagem(PokemonEntity selvagem) {
    if (selvagens.contains(selvagem)) {
      return false;
    }
    selvagens.add(selvagem);
    for (ServerPlayer jogador : jogadores) {
      viu(jogador, selvagem);
    }
    Estagios.zerar(selvagem.getPokemon());
    StatusNossos.acordarSeDormeDeCansaco(selvagem);
    PrimeiroTurno.esquecer(selvagem.getPokemon());
    ItensSegurados.esquecer(selvagem.getPokemon());
    GolpesDeStatus.esquecer(selvagem.getPokemon());
    Intimidacao.doSelvagem(selvagem, jogadores);
    return true;
  }

  // A Pokédex (dele, 21/10: "um selvagem, quando lutamos com ele no Cobblemon, libera o Pokémon na Pokédex; na nossa luta não
  // acontece, fica com ??? em cima"): como na batalha do Cobblemon, quem está na batalha VIU cada selvagem dela. O evento é o
  // do próprio Cobblemon (POKEMON_SEEN): a Pokédex dele marca o Pokémon como encontrado (PokedexHandler.onPokemonSeen →
  // PokedexManager.encounter), e quem mais escuta esse evento também fica sabendo
  private static void viu(ServerPlayer jogador, PokemonEntity selvagem) {
    CobblemonEvents.POKEMON_SEEN.emit(new PokemonSeenEvent(jogador.getUUID(), selvagem.getPokemon()));
  }

  public List<ServerPlayer> getJogadores() {
    return jogadores;
  }

  public List<PokemonEntity> getSelvagens() {
    return selvagens;
  }

  // Quem está do outro lado para este jogador: os selvagens. 21/10 (PvP): no duelo, o lutador do adversário (pvp.Duelo)
  public List<PokemonEntity> inimigosDe(ServerPlayer jogador) {
    return List.copyOf(selvagens);
  }

  public long getComecouNoTick() {
    return comecouNoTick;
  }

  public CampoDaBatalha getCampo() {
    return campo;
  }

  // 21/10 (treinadores): contra um treinador do RCT, entre um Pokémon dele e o próximo a batalha continua
  public boolean terminou() {
    return (selvagens.isEmpty() && !BatalhasDeTreinador.aindaVemPokemon(this)) || jogadores.isEmpty();
  }

  public void removerSelvagensForaDeCombate() {
    selvagens.removeIf(selvagem -> {
      // Etapa 8: o que desmaiou e ficou caído no chão (Desmaio) sai como derrotado. 21/10: o do treinador que caiu também
      boolean caido = Desmaio.esta(selvagem) || BatalhasDeTreinador.desmaiou(selvagem);
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
    // 21/10 (treinadores): contra um treinador, o aviso é o da batalha de treinador (venceu, perdeu ou desistiu)
    if (BatalhasDeTreinador.avisarOFim(this, jogador)) {
      return;
    }
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

  private boolean estaoPerto(ServerPlayer jogador, PokemonEntity selvagem, float distancia) {
    return jogador.level() == selvagem.level() && jogador.distanceTo(selvagem) <= distancia;
  }

  private boolean jogadorFugiu(ServerPlayer jogador) {
    // 21/10 (treinadores): contra um treinador, você sai quando a batalha se decide ou quando se afasta 24 blocos dele
    if (BatalhasDeTreinador.ehDeTreinador(this)) {
      return BatalhasDeTreinador.saiu(this, jogador);
    }
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

  // Lote 3 dos itens: com a Bola de Fumaça no time e todos na bola, a distância para fugir cai para 8 blocos (só a sua fuga;
  // o selvagem sai da batalha na distância de sempre)
  // 21/10 (habilidades, lote da entrada e das imunidades): preso pela Armadilharia, Fio de Sombra ou Magnetismo, 24 blocos
  private boolean pertoDeAlgumSelvagem(ServerPlayer jogador) {
    float distancia = HabilidadesDeFuga.distanciaDeFuga(jogador, this,
        ItensDoCampo.distanciaDeFuga(jogador, DISTANCIA_FUGA));
    for (PokemonEntity selvagem : selvagens) {
      if (estaoPerto(jogador, selvagem, distancia)) {
        return true;
      }
    }
    return false;
  }

  // 21/10: o selvagem que prende o lutador de alguém só fica para trás a 24 blocos (a mesma distância de quem ele prende)
  private boolean selvagemFicouParaTras(PokemonEntity selvagem) {
    // 21/10 (treinadores): o Pokémon do treinador não "fica para trás" (quem se afasta é você, e aí desiste)
    if (BatalhasDeTreinador.ehDoTreinador(selvagem)) {
      return false;
    }
    float distancia = HabilidadesDeFuga.distanciaDoSelvagem(selvagem, this, DISTANCIA_FUGA);
    for (ServerPlayer jogador : jogadores) {
      if (estaoPerto(jogador, selvagem, distancia)) {
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