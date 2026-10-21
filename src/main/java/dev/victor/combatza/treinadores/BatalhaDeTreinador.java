package dev.victor.combatza.treinadores;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.AvisoDaBatalha;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.Experiencia;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.SoltarNoSelvagem;
import dev.victor.combatza.golpes.AgendaDeGolpes;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Mira;
import dev.victor.combatza.golpes.PrimeiroTurno;
import dev.victor.combatza.golpes.StatusNossos;
import dev.victor.combatza.ia.Parceiro;
import dev.victor.combatza.mega.Mega;
import kotlin.Unit;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// 21/10 (treinadores, decisões dele): UMA batalha em tempo real contra um treinador do RCT, 1 contra 1.
// - O treinador manda UM Pokémon de cada vez, perto dele (a bola sai da mão dele, a do Cobblemon); a nossa IA dos selvagens
//   controla esse Pokémon (IaDoSelvagem), que só luta com os SEUS Pokémon: nunca vai em você nem em NPC (Lados), não foge e
//   espera quando você não tem ninguém fora. Ele cai: o XP sai na hora (Experiencia), ele fica deitado 1 s e volta para a
//   bola do treinador (sem os 10 s caído do selvagem, Desmaio), e 2 s depois sai o próximo, na ordem do time;
// - para o resto do mod ele é um "selvagem" da batalha (Combate): os golpes, o dano, os itens segurados, as habilidades, o
//   clima e o painel valem igual. O que muda: não se captura (ChanceDeCaptura), os guardas não lutam com ele, nenhum outro
//   jogador nem selvagem entra na batalha (GerenciarCombates) e ninguém de fora acerta ele (Lados);
// - o FIM vai para o RCT como numa batalha por turnos (PonteDoRct): todos os dele caíram = vitória (a série, o limite de
//   nível, o prêmio); todos os seus caíram = derrota; você se afastou 24 blocos do treinador = desistiu = derrota (aos 16, o
//   aviso). Saiu do jogo ou morreu: quem conta é o próprio RCT (o treinador vê o adversário fora e conta a vitória dele,
//   como faz na batalha por turnos);
// - a parte 2: a bolsa do RCT (BolsaDoTreinador), a Mega (MegaDoTreinador) e o item reserva no lugar do Cristal Z (CristalZ)
final class BatalhaDeTreinador {
  // Como o Pokémon do treinador que está fora está agora: saindo da bola (o feixe), na animação da Mega ou lutando
  enum Fase {
    SAINDO_DA_BOLA, MEGANDO, LUTANDO
  }

  // Como a batalha acabou
  enum Fim {
    VENCEU, PERDEU, DESISTIU, SAIU_DO_JOGO, TREINADOR_SUMIU, NAO_COMECOU
  }

  // Escolhas minhas, "como nos jogos" (vetáveis): o aviso a 16 blocos do treinador, a desistência a 24 (a decisão dele);
  // depois do aviso, ele só volta a avisar se você chegar a 14 blocos e se afastar de novo
  private static final double LONGE_PARA_AVISAR = 16;
  private static final double LONGE_PARA_DESISTIR = 24;
  private static final double PERTO_DE_NOVO = 14;
  // 2 s entre um Pokémon do treinador cair e o próximo sair (a decisão dele); o feixe da bola do Cobblemon dura 1,5 s
  private static final int TICKS_ATE_O_PROXIMO = 2 * SharedConstants.TICKS_PER_SECOND;
  private static final int TICKS_DO_FEIXE = 30;
  private static final int TICKS_MINIMOS_DA_MEGA = SharedConstants.TICKS_PER_SECOND;
  // A animação da Mega dura uns 6,3 s; se a marca dela não sair em 10 s (o Mega Showdown mudou), ele entra na luta igual
  private static final int TICKS_MAXIMOS_DA_MEGA = 10 * SharedConstants.TICKS_PER_SECOND;
  // O primeiro Pokémon do treinador tem 15 s para entrar na luta (o feixe e a Mega cabem com folga); senão, não começou
  private static final int TICKS_PARA_COMECAR = 15 * SharedConstants.TICKS_PER_SECOND;
  // Onde o Pokémon do treinador cai: entre ele e você, a 40% do caminho, de 1,5 a 4 blocos dele
  private static final double FRACAO_DO_CAMINHO = 0.4;
  private static final double SOLTAR_MAIS_PERTO = 1.5;
  private static final double SOLTAR_MAIS_LONGE = 4.0;
  private static final String FRASE = "cobblemon_combat_za.treinador.";

  private final UUID jogador;
  private final String nomeDoJogador;
  private final LivingEntity treinador;
  private final Component nome;
  private final List<Pokemon> time;
  private final BolsaDoTreinador bolsa;
  private final long comecouNoTick;
  private final Set<UUID> desmaiados = new HashSet<>();
  private int proximo;
  private PokemonEntity atual;
  private Fase fase;
  private long faseDesde;
  private long proximoNoTick = -1;
  private Combate combate;
  private boolean megou;
  private boolean avisouLonge;
  private Fim fim;

  BatalhaDeTreinador(ServerPlayer jogador, LivingEntity treinador, List<Pokemon> time, PonteDoRct.DadosDoTime dados,
      long agora) {
    this.jogador = jogador.getUUID();
    this.nomeDoJogador = jogador.getGameProfile().getName();
    this.treinador = treinador;
    this.nome = treinador.getDisplayName();
    this.time = time;
    this.bolsa = new BolsaDoTreinador(dados);
    this.comecouNoTick = agora;
  }

  UUID jogador() {
    return jogador;
  }

  LivingEntity treinador() {
    return treinador;
  }

  Combate combate() {
    return combate;
  }

  boolean ehDe(ServerPlayer quem) {
    return quem != null && jogador.equals(quem.getUUID());
  }

  // O Pokémon dele que está lutando agora (não o que está saindo da bola, na animação da Mega ou caído)
  boolean estaLutando(PokemonEntity entidade) {
    return entidade == atual && fase == Fase.LUTANDO;
  }

  boolean todosDesmaiaram() {
    return desmaiados.size() >= time.size();
  }

  boolean treinadorSumiu() {
    return !treinador.isAlive() || treinador.isRemoved();
  }

  boolean longeDemais(ServerPlayer quem) {
    return quem.level() != treinador.level() || quem.distanceTo(treinador) > LONGE_PARA_DESISTIR;
  }

  // Combate: entre um Pokémon dele e o próximo, a batalha continua
  boolean aindaVemPokemon() {
    return fim == null && !todosDesmaiaram() && !treinadorSumiu();
  }

  // Combate: você "sai" da batalha quando ela se decide (todos os dele caíram), quando desiste (longe) ou se ele sumiu
  boolean saiu(ServerPlayer quem) {
    return todosDesmaiaram() || treinadorSumiu() || longeDemais(quem);
  }

  // O começo: o primeiro Pokémon dele sai da bola, e o seu também, se você não tem nenhum fora (como nos jogos, os dois
  // juntos)
  void comecar(ServerPlayer quem, long agora) {
    Feedback.historicoDaBatalha(quem, null, Component.translatable(FRASE + "comecou", nome));
    soltarOProximo(quem, agora);
    if (atual != null && seuPokemonFora(quem) == null) {
      Pokemon seu = primeiroApto(quem);
      Vec3 onde = seu == null ? null : SoltarNoSelvagem.onde(quem, atual);
      if (onde != null) {
        Parceiro.desligar(quem);
        SoltarNoSelvagem.jogar(quem, seu, onde);
      }
    }
  }

  // A cada tick. true = a batalha acabou (e o RCT já sabe)
  boolean atualizar(MinecraftServer servidor, long agora) {
    ServerPlayer quem = servidor.getPlayerList().getPlayer(jogador);
    if (fim == null) {
      fim = conferirOFim(quem, agora);
    }
    if (fim != null) {
      finalizar(quem, agora);
      return true;
    }
    acompanharOAtual(quem, agora);
    if (atual == null && proximoNoTick >= 0 && agora >= proximoNoTick && fim == null) {
      proximoNoTick = -1;
      soltarOProximo(quem, agora);
    }
    avisarSeEstaLonge(quem);
    if (atual != null && fase == Fase.LUTANDO) {
      bolsa.talvezUsar(quem, nome, atual, agora);
    }
    return false;
  }

  // Depois de começar a luta, quem decide o fim é o Combate (ele tira você e chama avisarOFim). Antes do primeiro Pokémon
  // entrar na luta, é aqui
  private Fim conferirOFim(ServerPlayer quem, long agora) {
    if (combate != null) {
      boolean valendo = GerenciarCombates.todos().contains(combate) && quem != null && combate.getJogadores().contains(quem);
      return valendo ? null : porQueSaiu(quem);
    }
    if (quem == null || !quem.isAlive()) {
      return Fim.SAIU_DO_JOGO;
    }
    if (treinadorSumiu()) {
      return Fim.TREINADOR_SUMIU;
    }
    if (longeDemais(quem)) {
      return Fim.DESISTIU;
    }
    return agora - comecouNoTick > TICKS_PARA_COMECAR ? Fim.NAO_COMECOU : null;
  }

  Fim porQueSaiu(ServerPlayer quem) {
    if (quem == null || !quem.isAlive() || quem.isRemoved()) {
      return Fim.SAIU_DO_JOGO;
    }
    if (todosDesmaiaram()) {
      return Fim.VENCEU;
    }
    if (Combate.timeTodoDesmaiado(quem)) {
      return Fim.PERDEU;
    }
    return treinadorSumiu() ? Fim.TREINADOR_SUMIU : Fim.DESISTIU;
  }

  // Combate.avisarOFim: você saiu da batalha. O aviso de fim é o da batalha de treinador (com o XP de cada um, como no
  // selvagem). Saiu do jogo ou morreu: o aviso de sempre. true = o aviso foi dado aqui
  boolean avisarOFim(ServerPlayer quem) {
    fim = porQueSaiu(quem);
    String texto = switch (fim) {
      case VENCEU -> "venceu";
      case PERDEU -> "perdeu";
      case DESISTIU -> "desistiu";
      case TREINADOR_SUMIU -> "sumiu";
      default -> null;
    };
    if (texto == null) {
      return false;
    }
    String tipo = switch (fim) {
      case VENCEU -> "venceu";
      case PERDEU, DESISTIU -> "perdeu";
      default -> "acabou";
    };
    AvisoDaBatalha.doTreinador(quem, Component.translatable(FRASE + texto, nome), tipo, time);
    return true;
  }

  // O fim: o RCT fica sabendo e os Pokémon dele que ainda estão fora voltam para a bola
  private void finalizar(ServerPlayer quem, long agora) {
    switch (fim) {
      case VENCEU -> PonteDoRct.terminou(jogador, true);
      case PERDEU, DESISTIU -> PonteDoRct.terminou(jogador, false);
      // O próprio RCT conta: o treinador vê o adversário morto ou fora do jogo e anota a vitória dele (TrainerMob.tick)
      case SAIU_DO_JOGO -> {
      }
      case TREINADOR_SUMIU, NAO_COMECOU -> PonteDoRct.cancelar(treinador, jogador);
    }
    // Antes de o primeiro Pokémon entrar na luta não existe Combate para dar o aviso de fim
    if (combate == null && quem != null && fim != Fim.SAIU_DO_JOGO) {
      Feedback.barraDeAcao(quem, Component.translatable(FRASE + (fim == Fim.DESISTIU ? "desistiu" : "nao_comecou"), nome));
    }
    if (atual != null && !atual.isRemoved()) {
      BatalhasDeTreinador.voltarParaABola(atual, treinador, agora, false);
    }
    atual = null;
    CobblemonCombatZA.LOGGER.info("TREINADOR: a batalha de {} contra {} acabou: {} ({} de {} Pokémon dele caíram)",
        nomeDoJogador, PonteDoRct.id(treinador), fim, desmaiados.size(), time.size());
  }

  // O feixe da bola acabou: a Mega (uma por batalha, como nos jogos) ou direto para a luta. Caiu ou sumiu sem passar pelo
  // desmaio (o Gambito Final zera a vida sem golpe; o /kill e o vazio matam de verdade): conta como desmaiado. O XP só sai
  // aqui se ninguém deu antes (a morte de verdade já deu o XP pelo AFTER_DEATH da Experiencia)
  private void acompanharOAtual(ServerPlayer quem, long agora) {
    if (atual == null) {
      return;
    }
    if (atual.isRemoved() || !atual.isAlive() || atual.isDeadOrDying()) {
      DamageSource ultima = atual.getLastDamageSource();
      boolean morteDeVerdade = ultima != null && ultima.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
      desmaiou(atual, agora, !atual.isRemoved() && fase == Fase.LUTANDO && !morteDeVerdade);
      return;
    }
    if (fase == Fase.SAINDO_DA_BOLA && agora - faseDesde >= TICKS_DO_FEIXE && atual.getBeamMode() == 0) {
      Pokemon pokemon = atual.getPokemon();
      if (!megou && MegaDoTreinador.podeMegar(pokemon) && MegaDoTreinador.megar(pokemon)) {
        megou = true;
        fase = Fase.MEGANDO;
        faseDesde = agora;
        Feedback.historicoDaBatalha(quem, pokemon,
            Component.translatable("cobblemon.battle.mega", pokemon.getDisplayName(false)));
        CobblemonCombatZA.LOGGER.info("TREINADOR: {} mega-evoluiu", pokemon.getSpecies().getName());
        return;
      }
      entrarNaLuta(quem);
    } else if (fase == Fase.MEGANDO && agora - faseDesde >= TICKS_MINIMOS_DA_MEGA
        && (!Mega.transformando(atual.getPokemon()) || agora - faseDesde >= TICKS_MAXIMOS_DA_MEGA)) {
      entrarNaLuta(quem);
    }
  }

  // O primeiro abre a batalha (o mesmo caminho de quando você bate num selvagem); os outros entram na que já existe
  private void entrarNaLuta(ServerPlayer quem) {
    fase = Fase.LUTANDO;
    if (quem == null) {
      return;
    }
    if (combate == null) {
      GerenciarCombates.entrarEmCombate(quem, atual, seuPokemonFora(quem));
      // A batalha do jogador tem de ser esta, com o Pokémon do treinador nela (um duelo no meio do caminho não serve)
      Combate dele = GerenciarCombates.combateDoJogador(quem);
      combate = dele != null && dele.getSelvagens().contains(atual) ? dele : null;
      if (combate == null) {
        fim = Fim.NAO_COMECOU;
      }
      return;
    }
    combate.adicionarSelvagem(atual);
  }

  // A bola sai da mão do treinador (sendOutWithAnimation, com o grito), no caminho entre ele e você
  private void soltarOProximo(ServerPlayer quem, long agora) {
    if (proximo >= time.size() || !(treinador.level() instanceof ServerLevel nivel)) {
      return;
    }
    Pokemon pokemon = time.get(proximo++);
    pokemon.sendOutWithAnimation(treinador, nivel, ondeSoltar(quem), null, true, null, entidade -> {
      BatalhasDeTreinador.registrarEntidade(entidade, this);
      return Unit.INSTANCE;
    });
    atual = pokemon.getEntity();
    fase = Fase.SAINDO_DA_BOLA;
    faseDesde = agora;
    if (atual == null) {
      CobblemonCombatZA.LOGGER.warn("TREINADOR: {} não conseguiu sair da bola", pokemon.getSpecies().getName());
      desmaiados.add(pokemon.getUuid());
      proximoNoTick = agora + TICKS_ATE_O_PROXIMO;
      return;
    }
    if (quem != null) {
      Feedback.historicoDaBatalha(quem, pokemon,
          Component.translatable("cobblemon.battle.switch.other", nome, pokemon.getDisplayName(false)));
    }
    CobblemonCombatZA.LOGGER.info("TREINADOR: {} mandou {} Nv. {} ({} de {})", PonteDoRct.id(treinador),
        pokemon.getSpecies().getName(), pokemon.getLevel(), proximo, time.size());
  }

  private Vec3 ondeSoltar(ServerPlayer quem) {
    Vec3 deLa = treinador.position();
    if (quem == null || quem.level() != treinador.level()) {
      return deLa;
    }
    Vec3 caminho = quem.position().subtract(deLa).multiply(1, 0, 1);
    double total = caminho.length();
    if (total < 0.01) {
      return deLa;
    }
    double distancia = Math.min(Math.clamp(total * FRACAO_DO_CAMINHO, SOLTAR_MAIS_PERTO, SOLTAR_MAIS_LONGE),
        Math.max(0, total - SOLTAR_MAIS_PERTO));
    return Mira.noChao(treinador.level(), deLa.add(caminho.scale(distancia / total)));
  }

  // O Pokémon dele desmaiou (BatalhasDeTreinador, no lugar da morte; ou visto aqui): o XP sai agora, ele deita e volta para
  // a bola, e o próximo sai em 2 s. "darXp" = ele estava lutando e caiu (sumir do mundo não dá XP)
  void desmaiou(PokemonEntity entidade, long agora, boolean darXp) {
    Pokemon pokemon = entidade.getPokemon();
    if (!desmaiados.add(pokemon.getUuid())) {
      return;
    }
    MinecraftServer servidor = treinador.getServer();
    ServerPlayer quem = servidor == null ? null : servidor.getPlayerList().getPlayer(jogador);
    if (quem != null) {
      Feedback.historicoDaBatalha(quem, pokemon, Component.translatable("cobblemon.battle.fainted",
          pokemon.getDisplayName(false)));
    }
    if (darXp && servidor != null) {
      Experiencia.derrotado(servidor, pokemon);
    }
    AgendaDeGolpes.largar(pokemon);
    Estagios.zerar(pokemon);
    StatusNossos.esquecer(pokemon);
    EfeitosDeStatus.esquecer(pokemon);
    Confusao.esquecer(pokemon);
    PrimeiroTurno.esquecer(pokemon);
    if (entidade == atual) {
      atual = null;
    }
    if (!entidade.isRemoved()) {
      BatalhasDeTreinador.voltarParaABola(entidade, treinador, agora, true);
    }
    if (!todosDesmaiaram()) {
      proximoNoTick = agora + TICKS_ATE_O_PROXIMO;
    }
    CobblemonCombatZA.LOGGER.info("TREINADOR: {} de {} desmaiou ({} de {}){}", pokemon.getSpecies().getName(),
        PonteDoRct.id(treinador), desmaiados.size(), time.size(), darXp ? "" : " sem XP");
  }

  // Aos 16 blocos do treinador, o aviso (uma vez; de novo só depois de voltar para perto)
  private void avisarSeEstaLonge(ServerPlayer quem) {
    if (quem == null) {
      return;
    }
    double distancia = quem.level() == treinador.level() ? quem.distanceTo(treinador) : Double.MAX_VALUE;
    if (distancia > LONGE_PARA_AVISAR && !avisouLonge) {
      avisouLonge = true;
      Component aviso = Component.translatable(FRASE + "volte", nome, (int) LONGE_PARA_DESISTIR);
      Feedback.barraDeAcao(quem, aviso);
      Feedback.historicoDaBatalha(quem, null, aviso);
    } else if (distancia <= PERTO_DE_NOVO) {
      avisouLonge = false;
    }
  }

  // O seu Pokémon que já está fora: o selecionado (a setinha), ou o primeiro do time fora da bola
  private static Pokemon seuPokemonFora(ServerPlayer quem) {
    if (quem == null) {
      return null;
    }
    Pokemon selecionado = Parceiro.de(quem);
    if (selecionado != null && selecionado.getEntity() != null && !selecionado.isFainted()) {
      return selecionado;
    }
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(quem)) {
      if (pokemon.getEntity() != null && !pokemon.isFainted()) {
        return pokemon;
      }
    }
    return null;
  }

  private static Pokemon primeiroApto(ServerPlayer quem) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(quem)) {
      if (!pokemon.isFainted() && pokemon.getEntity() == null) {
        return pokemon;
      }
    }
    return null;
  }
}
