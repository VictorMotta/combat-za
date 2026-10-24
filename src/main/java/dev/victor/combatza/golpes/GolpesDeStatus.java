package dev.victor.combatza.golpes;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonFaintedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonRecallEvent;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.SemDerrubar;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.LimpezaDoCampo;
import dev.victor.combatza.campo.SalaTrick;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.TrocaDeHabilidade;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.rede.GolpesDesligadosPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 21/10 (golpes de status que faltavam; pedido dele: Provocação, Bis, Atração e Charme Fofo, Truque de Agilidade, Troca de
// Habilidades, Quebra-telha, Descondensar, Giro Rápido...): o ponto de encontro desses golpes com o resto do mod. Cada efeito
// mora na sua classe (Provocacao, Bis, Atracao, GolpesDesligados, habilidades.TrocaDeHabilidade, campo.SalaTrick,
// campo.LimpezaDoCampo); aqui ficam os ganchos (o acerto do golpe, o golpe sem alvo, a IA), as proteções que eles dividem (o
// Véu Aromático, a Insensível e a Erva Mental) e o relógio. Como nos jogos, os efeitos no Pokémon acabam quando ele volta para
// a bola, desmaia ou sai da batalha (e a batalha nova começa sem nenhum)
public class GolpesDeStatus {
  private static final String VEU_AROMATICO = "aromaveil";
  private static final String INSENSIVEL = "oblivious";
  private static final String ERVA_MENTAL = "mentalherb";
  private static final int CONFERIR_A_CADA_TICKS = 20;

  private GolpesDeStatus() {
  }

  // Uma linha no CobblemonCombatZA: o pacote dos golpes riscados, o relógio, o fim do mundo, a volta para a bola e o desmaio
  public static void registrar() {
    PayloadTypeRegistry.playS2C().register(GolpesDesligadosPayload.TIPO, GolpesDesligadosPayload.STREAM_CODEC);
    ServerTickEvents.END_SERVER_TICK.register(servidor -> SemDerrubar.rodar("GolpesDeStatus", () -> atualizar(servidor.getTickCount())));
    ServerLifecycleEvents.SERVER_STOPPED.register(servidor -> limpar());
    CobblemonEvents.POKEMON_RECALL_POST.subscribe(Priority.NORMAL, SemDerrubar.evento("GolpesDeStatus.POKEMON_RECALL_POST", GolpesDeStatus::aoRecolher));
    CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.NORMAL, SemDerrubar.evento("GolpesDeStatus.POKEMON_FAINTED", GolpesDeStatus::aoDesmaiar));
  }

  // Depois do acerto (AplicarEfeitos.depoisDeAcertar): os golpes de status no alvo e os giros que limpam o chão
  public static void depoisDeAcertar(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    switch (execucao.getGolpe().getName()) {
      case "taunt" -> Provocacao.tentar(execucao, alvo);
      case "encore" -> Bis.tentar(execucao, alvo);
      case "attract" -> Atracao.tentar(execucao, atacante, alvo);
      case "skillswap", "roleplay", "entrainment", "simplebeam", "worryseed", "gastroacid" ->
          TrocaDeHabilidade.peloGolpe(execucao, atacante, alvo);
      case "defog" -> LimpezaDoCampo.descondensar(execucao, atacante, alvo);
      case "rapidspin", "mortalspin" -> LimpezaDoCampo.girar(execucao, atacante);
      default -> {
        // os outros golpes seguem o caminho de sempre
      }
    }
  }

  // O golpe sem alvo (ControleDeGolpes.usouSemAlvo): o Truque de Agilidade e o Arrumar. true = era um deles
  public static boolean semAlvo(Execucao execucao, PokemonEntity atacante) {
    return SalaTrick.aoUsarGolpe(execucao, atacante) || LimpezaDoCampo.arrumar(execucao, atacante);
  }

  // A IA (EscolhaDoGolpe): o golpe de status serve contra este alvo agora? false = nota zero (não gasta a vez com a Atração
  // no mesmo gênero, com a Provocação em quem já está provocado, com o Truque de Agilidade sendo mais rápido...)
  public static boolean serveContra(PokemonEntity atacante, Move golpe, Entity alvo) {
    Pokemon pokemon = atacante.getPokemon();
    Pokemon doAlvo = alvo instanceof PokemonEntity entidade ? entidade.getPokemon() : null;
    String nome = golpe.getName();
    return switch (nome) {
      case "taunt" -> doAlvo != null && Provocacao.convemContra(doAlvo);
      case "encore" -> doAlvo != null && Bis.podePegar(doAlvo);
      case "attract" -> doAlvo != null && Atracao.podePegar(pokemon, doAlvo);
      case "skillswap", "roleplay", "entrainment", "simplebeam", "worryseed", "gastroacid" ->
          doAlvo != null && TrocaDeHabilidade.podePegar(nome, pokemon, doAlvo);
      case "trickroom" -> SalaTrick.convem(pokemon, doAlvo);
      default -> true;
    };
  }

  // Showdown: o Véu Aromático (de quem recebe ou de um aliado dele na luta) segura a Atração, a Provocação e o Bis; a
  // Insensível segura a Atração e a Provocação. Devolve quem segurou (o dono da habilidade), ou null
  static Pokemon quemProtegeDaMente(Pokemon alvo, boolean insensivelSegura) {
    if (insensivelSegura && Habilidades.tem(alvo, INSENSIVEL)) {
      return alvo;
    }
    if (Habilidades.tem(alvo, VEU_AROMATICO)) {
      return alvo;
    }
    Arena arena = Arenas.de(alvo);
    if (arena == null) {
      return null;
    }
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon aliado = entidade.getPokemon();
      if (Lados.mesmoLado(aliado, alvo) && Habilidades.tem(aliado, VEU_AROMATICO)) {
        return aliado;
      }
    }
    return null;
  }

  // O mesmo, avisando no histórico ("X não é afetado: Véu Aromático"). true = protegido
  static boolean protegidoDaMente(Pokemon alvo, boolean insensivelSegura) {
    Pokemon protetor = quemProtegeDaMente(alvo, insensivelSegura);
    if (protetor == null) {
      return false;
    }
    CobblemonCombatZA.LOGGER.info("PROTEGIDO: {} ({} de {})", alvo.getSpecies().getName(), Habilidades.atual(protetor),
        protetor.getSpecies().getName());
    avisar(alvo, alvo.getDisplayName(false).copy().append(" ")
        .append(Component.translatable("cobblemon_combat_za.status.nao_e_afetado_por", Habilidades.nome(protetor))));
    return true;
  }

  // Showdown, Erva Mental: logo que pega a Atração, a Provocação ou o Bis, quem segura come a erva e se livra de todos
  static void ervaMental(Pokemon pokemon) {
    if (!ItensSegurados.tem(pokemon, ERVA_MENTAL)) {
      return;
    }
    ItensSegurados.gastar(pokemon, ERVA_MENTAL);
    Provocacao.acabar(pokemon, false);
    Bis.acabar(pokemon, false);
    Atracao.acabar(pokemon, false);
    CobblemonCombatZA.LOGGER.info("ITEM: {} comeu a Erva Mental", pokemon.getSpecies().getName());
    avisar(pokemon, Component.translatable("cobblemon.battle.enditem.mentalherb", pokemon.getDisplayName(false)));
  }

  // Uma frase sobre este Pokémon no histórico de quem está na batalha dele (fora da batalha, só no log)
  public static void avisar(Pokemon sobreQuem, Component frase) {
    Combate combate = GerenciarCombates.combateDe(sobreQuem);
    if (combate == null) {
      return;
    }
    for (ServerPlayer jogador : combate.getJogadores()) {
      Feedback.historico(jogador, sobreQuem, frase);
    }
  }

  private static void atualizar(long agora) {
    Provocacao.atualizar(agora);
    Bis.atualizar(agora);
    Atracao.atualizar(agora);
    TrocaDeHabilidade.atualizar(agora);
    // Saiu da batalha (fugiu, a batalha acabou): os efeitos no Pokémon acabam, como numa troca
    if (agora % CONFERIR_A_CADA_TICKS == 0) {
      Provocacao.semBatalha();
      Bis.semBatalha();
      Atracao.semBatalha();
    }
  }

  // A batalha nova começou (Combate): tudo o que pesava nele acaba, sem frase
  public static void esquecer(Pokemon pokemon) {
    Provocacao.acabar(pokemon, false);
    Bis.acabar(pokemon, false);
    Atracao.acabar(pokemon, false);
    TrocaDeHabilidade.esquecer(pokemon);
  }

  // Voltou para a bola ou desmaiou: o mesmo, e o "último golpe" do Bis começa do zero
  private static void aoRecolher(PokemonRecallEvent.Post evento) {
    saiu(evento.getPokemon());
  }

  private static void aoDesmaiar(PokemonFaintedEvent evento) {
    saiu(evento.getPokemon());
  }

  private static void saiu(Pokemon pokemon) {
    esquecer(pokemon);
    Bis.esquecerOUltimo(pokemon);
  }

  public static void esquecerTime(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      esquecer(pokemon);
    }
  }

  private static void limpar() {
    Provocacao.limpar();
    Bis.limpar();
    Atracao.limpar();
    TrocaDeHabilidade.limpar();
    GolpesDesligados.limpar();
  }
}
