package dev.victor.combatza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonFaintedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonRecallEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.captura.ChanceDeCaptura;
import dev.victor.combatza.combate.Experiencia;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.combate.PainelDaBatalha;
import dev.victor.combatza.combate.PlacasDosSelvagens;
import dev.victor.combatza.combate.Rolamento;
import dev.victor.combatza.combate.SoltarNoSelvagem;
import dev.victor.combatza.config.ComandoCombatZA;
import dev.victor.combatza.config.Configuracao;
import dev.victor.combatza.golpes.AgendaDeGolpes;
import dev.victor.combatza.golpes.AnimacoesDeAtaque;
import dev.victor.combatza.golpes.AnimacoesDeBatalha;
import dev.victor.combatza.golpes.AreaSemQueda;
import dev.victor.combatza.golpes.Armadilhas;
import dev.victor.combatza.golpes.CampoDeBolhas;
import dev.victor.combatza.golpes.Cargas;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Furia;
import dev.victor.combatza.golpes.Jatos;
import dev.victor.combatza.golpes.Paralisia;
import dev.victor.combatza.golpes.ParticulasDosGolpes;
import dev.victor.combatza.golpes.Preso;
import dev.victor.combatza.golpes.PrimeiroTurno;
import dev.victor.combatza.golpes.Projetil;
import dev.victor.combatza.golpes.Recargas;
import dev.victor.combatza.golpes.SonoECongelado;
import dev.victor.combatza.golpes.StatusNossos;
import dev.victor.combatza.golpes.TempoRealDoStatus;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.habilidades.Absorcao;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDeEntrada;
import dev.victor.combatza.habilidades.HabilidadesDeForma;
import dev.victor.combatza.habilidades.Intimidacao;
import dev.victor.combatza.ia.Deteccao;
import dev.victor.combatza.ia.Guardas;
import dev.victor.combatza.ia.IaDoSelvagem;
import dev.victor.combatza.ia.Parceiro;
import dev.victor.combatza.rede.AlinharPayload;
import dev.victor.combatza.rede.AvisoDaBatalhaPayload;
import dev.victor.combatza.rede.CargasPayload;
import dev.victor.combatza.rede.EntrarNaLutaPayload;
import dev.victor.combatza.rede.GolpeDoSelvagemPayload;
import dev.victor.combatza.rede.HistoricoPayload;
import dev.victor.combatza.rede.JaAgiuPayload;
import dev.victor.combatza.rede.LutadorPayload;
import dev.victor.combatza.rede.ModoDosGuardasPayload;
import dev.victor.combatza.rede.PainelDaBatalhaPayload;
import dev.victor.combatza.rede.PlacasPayload;
import dev.victor.combatza.rede.RecargaPayload;
import dev.victor.combatza.rede.RolarPayload;
import dev.victor.combatza.rede.RolouPayload;
import dev.victor.combatza.rede.SetinhaPayload;
import dev.victor.combatza.rede.SinaisPayload;
import dev.victor.combatza.rede.SoltarNoSelvagemPayload;
import dev.victor.combatza.rede.TodosDeGuardaPayload;
import dev.victor.combatza.rede.TrocarOLadoPayload;
import dev.victor.combatza.rede.MedidorDaMegaPayload;
import dev.victor.combatza.rede.MegaPayload;
import dev.victor.combatza.mega.Mega;
import dev.victor.combatza.combate.EterEElixir;
import dev.victor.combatza.combate.Mochila;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.party.ComandoParty;
import dev.victor.combatza.party.Parties;
import dev.victor.combatza.rede.PartyPayload;
import dev.victor.combatza.captura.Irritados;
import dev.victor.combatza.itens.ItensDeVida;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.EfeitosDoCampo;
import dev.victor.combatza.campo.Passivas;
import dev.victor.combatza.rede.PassivasPayload;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.itens.TravaDaEscolha;
import dev.victor.combatza.rede.TravaDaEscolhaPayload;
import dev.victor.combatza.golpes.CorridaMontada;
import dev.victor.combatza.golpes.GolpesDeStatus;
import dev.victor.combatza.rede.CorridaMontadaPayload;
import dev.victor.combatza.party.PontosDaParty;
import dev.victor.combatza.pvp.Duelos;
import dev.victor.combatza.itens.EstadoDosItens;
import dev.victor.combatza.rede.MeusPontosPayload;
import dev.victor.combatza.rede.PontosDaPartyPayload;
import dev.victor.combatza.rede.UsarGolpePayload;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class CobblemonCombatZA implements ModInitializer {

  public static final String MOD_ID = "cobblemon_combat_za";
  public static final String CHAVE_SILENCIO = "cobblemon_combat_za.silencio";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  // O vigia de travamento do servidor (VigiaDeTravamento): ligado enquanto o mundo está aberto
  private static VigiaDeTravamento vigiaDoServidor;

  /**
   * Runs the mod initializer.
   */
  @Override
  public void onInitialize() {
    LOGGER.info("Cobblemon Combat ZA carregou!");
    EfeitosDosGolpes.carregar();
    Configuracao.carregar();
    ComandoCombatZA.registrar();
    ComandoParty.registrar();
    AreaSemQueda.carregar();
    AnimacoesDeAtaque.carregar();
    ParticulasDosGolpes.carregar();
    AnimacoesDeBatalha.carregar();
    Projetil.registrar();
    CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, this::aoIniciarBatalha);
    CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.NORMAL, this::aoDesmaiar);
    CobblemonEvents.POKEMON_RECALL_POST.subscribe(Priority.NORMAL, this::aoRecolher);
    CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL, this::aoSoltar);
    CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.NORMAL, this::antesDeSoltar);
    ChanceDeCaptura.registrar();
    Experiencia.registrar();
    Mochila.registrar();
    Desmaio.registrar();
    ServerLivingEntityEvents.AFTER_DAMAGE.register(this::aoReceberDano);
    ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::podeLevarDano);
    ServerLifecycleEvents.SERVER_STARTED.register(servidor -> {
      Cobblemon.INSTANCE.getConfig().setPlayerDamagePokemon(true);
      LOGGER.info("Dano do jogador em Pokémon LIBERADO (playerDamagePokemon = true)");
      vigiaDoServidor = VigiaDeTravamento.comecar("servidor", servidor::isPaused);
      // A party guardada no mundo volta (quem sai do jogo continua nela)
      Parties.carregar(servidor);
    });
    // Salvar o mundo ao sair pode demorar: o vigia desliga antes
    ServerLifecycleEvents.SERVER_STOPPING.register(servidor -> {
      if (vigiaDoServidor != null) {
        vigiaDoServidor.parar();
        vigiaDoServidor = null;
      }
    });
    ServerTickEvents.END_SERVER_TICK.register(servidor -> {
      if (vigiaDoServidor != null) {
        vigiaDoServidor.sinal();
      }
      GerenciarCombates.atualizar();
      IaDoSelvagem.atualizar(servidor);
      Guardas.atualizar(servidor);
      Parceiro.atualizar(servidor);
      AgendaDeGolpes.atualizar(servidor.getTickCount());
      CorridaMontada.atualizar(servidor);
      Jatos.atualizar(servidor.getTickCount());
      Cargas.atualizar(servidor.getTickCount());
      Preso.atualizar(servidor.getTickCount());
      Armadilhas.atualizar(servidor.getTickCount());
      CampoDeBolhas.atualizar(servidor.getTickCount());
      Deteccao.atualizar(servidor);
      EfeitosDeStatus.atualizar(servidor, servidor.getTickCount());
      TempoRealDoStatus.atualizar(servidor, servidor.getTickCount());
      SonoECongelado.atualizar(servidor.getTickCount());
      Paralisia.atualizar();
      Confusao.atualizar(servidor.getTickCount());
      Estagios.atualizar(servidor.getTickCount());
      PlacasDosSelvagens.atualizar(servidor);
      PainelDaBatalha.atualizar(servidor);
      Mega.atualizar(servidor);
      Desmaio.atualizar(servidor);
      Parties.atualizar(servidor);
      PontosDaParty.atualizar(servidor);
      Irritados.atualizar(servidor.getTickCount());
      ItensDeVida.atualizar(servidor.getTickCount());
      EfeitosDoCampo.atualizar(servidor, servidor.getTickCount());
      // 21/10 (habilidades, lote da entrada e das imunidades): climas fortes, Paradoxo, Ruminante, Fuga Automática...
      HabilidadesDeEntrada.atualizar(servidor, servidor.getTickCount());
      Passivas.atualizar(servidor);
    });
    ServerLifecycleEvents.SERVER_STOPPED.register(servidor -> {
      GerenciarCombates.encerrarTodos();
      Experiencia.limpar();
      Mega.limpar();
      Mochila.limpar();
      Desmaio.limpar();
      Parties.limpar();
      PontosDaParty.limpar();
      Irritados.limpar();
      ItensSegurados.limpar();
      Arenas.limpar();
      EterEElixir.limpar();
      Passivas.limpar();
      CorridaMontada.limpar();
      Lutadores.limpar();
      AgendaDeGolpes.limpar();
      Jatos.limpar();
      Cargas.limpar();
      Preso.limpar();
      Furia.limpar();
      Armadilhas.limpar();
      CampoDeBolhas.limpar();
      Deteccao.limpar();
      IaDoSelvagem.limpar();
      Guardas.limpar();
      Parceiro.limpar();
      Estagios.limpar();
      EfeitosDeStatus.limpar();
      StatusNossos.limpar();
      SonoECongelado.limpar();
      Paralisia.limpar();
      PrimeiroTurno.limpar();
      Confusao.limpar();
      Absorcao.limpar();
      HabilidadesDeEntrada.limpar();
      Recargas.limpar();
      PlacasDosSelvagens.limpar();
      Rolamento.limpar();
      PainelDaBatalha.limpar();
    });
    PayloadTypeRegistry.playC2S().register(UsarGolpePayload.TIPO, UsarGolpePayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(RolarPayload.TIPO, RolarPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(SoltarNoSelvagemPayload.TIPO, SoltarNoSelvagemPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(ModoDosGuardasPayload.TIPO, ModoDosGuardasPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(SetinhaPayload.TIPO, SetinhaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(TodosDeGuardaPayload.TIPO, TodosDeGuardaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(TrocarOLadoPayload.TIPO, TrocarOLadoPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(EntrarNaLutaPayload.TIPO, EntrarNaLutaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(MegaPayload.TIPO, MegaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(MeusPontosPayload.TIPO, MeusPontosPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(RecargaPayload.TIPO, RecargaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(PlacasPayload.TIPO, PlacasPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(HistoricoPayload.TIPO, HistoricoPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(LutadorPayload.TIPO, LutadorPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(RolouPayload.TIPO, RolouPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(JaAgiuPayload.TIPO, JaAgiuPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(AvisoDaBatalhaPayload.TIPO, AvisoDaBatalhaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(PainelDaBatalhaPayload.TIPO, PainelDaBatalhaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(CargasPayload.TIPO, CargasPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(AlinharPayload.TIPO, AlinharPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(SinaisPayload.TIPO, SinaisPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(GolpeDoSelvagemPayload.TIPO, GolpeDoSelvagemPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(MedidorDaMegaPayload.TIPO, MedidorDaMegaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(CorridaMontadaPayload.TIPO, CorridaMontadaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(TravaDaEscolhaPayload.TIPO, TravaDaEscolhaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(PassivasPayload.TIPO, PassivasPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(PartyPayload.TIPO, PartyPayload.STREAM_CODEC);
    PayloadTypeRegistry.playS2C().register(PontosDaPartyPayload.TIPO, PontosDaPartyPayload.STREAM_CODEC);
    ServerPlayConnectionEvents.DISCONNECT.register((conexao, servidor) -> {
      PlacasDosSelvagens.esquecer(conexao.getPlayer());
      Rolamento.esquecer(conexao.getPlayer());
      PainelDaBatalha.esquecer(conexao.getPlayer());
      Deteccao.esquecer(conexao.getPlayer());
      Parceiro.esquecer(conexao.getPlayer());
      Experiencia.esquecer(conexao.getPlayer());
      CorridaMontada.esquecer(conexao.getPlayer());
      Arenas.esquecer(conexao.getPlayer());
      Passivas.esquecer(conexao.getPlayer());
      Parties.aoDesconectar(conexao.getPlayer());
      PontosDaParty.esquecer(conexao.getPlayer());
    });
    ServerPlayNetworking.registerGlobalReceiver(UsarGolpePayload.TIPO,
        (payload, contexto) -> ControleDeGolpes.usarGolpe(contexto.player(), payload));
    ServerPlayNetworking.registerGlobalReceiver(MeusPontosPayload.TIPO,
        (payload, contexto) -> PontosDaParty.receber(contexto.player(), payload));
    ServerPlayNetworking.registerGlobalReceiver(RolarPayload.TIPO,
        (payload, contexto) -> Rolamento.pediu(contexto.player(), payload));
    ServerPlayNetworking.registerGlobalReceiver(SoltarNoSelvagemPayload.TIPO,
        (payload, contexto) -> SoltarNoSelvagem.pediu(contexto.player(), payload));
    ServerPlayNetworking.registerGlobalReceiver(EntrarNaLutaPayload.TIPO,
        (payload, contexto) -> Lutadores.entrarQuemEstaFora(contexto.player(), payload.vaga()));
    Guardas.registrar();
    Parceiro.registrar();
    Mega.registrar();
    // 21/10 (golpes de status que faltavam): Provocação, Bis, Atração, Troca de Habilidades (o relógio, a volta para a bola
    // e o pacote dos golpes riscados)
    GolpesDeStatus.registrar();
    // 21/10 (habilidades, lote das formas): as que mudam a forma, o tipo ou a habilidade na batalha
    HabilidadesDeForma.registrar();
    // 21/10: a batalha em tempo real contra os treinadores do RCT (a escolha, os payloads, o tick e o fim)
    BatalhasDeTreinador.registrar();
    // 21/10 (PvP): o duelo em tempo real pelo desafio de batalha do Cobblemon (pvp.Duelos)
    Duelos.registrar();
  }

  private void aoIniciarBatalha(BattleStartedEvent.Pre event) {
    PokemonBattle battle = event.getBattle();
    LOGGER.info("Batalha começando! Selvagem? {} | NPC? {} | PvP? {}",
        battle.isPvW(), battle.isPvN(), battle.isPvP());

    if (battle.isPvW()) {
      event.cancel();
      event.setReason(Component.translatable(CHAVE_SILENCIO));

      LOGGER.info("Batalha contra selvagem BLOQUEADA: combate em tempo real!");

      PokemonEntity selvagem = encontrarSelvagem(battle);
      if (selvagem != null) {
        for (ServerPlayer jogador : battle.getPlayers()) {
          GerenciarCombates.entrarEmCombate(jogador, selvagem, null);
        }
        soltarQuemIaLutar(battle, selvagem);
      }
    }
  }

  // Uma batalha antiga contra selvagem que ainda chegue até aqui (o R não pede mais: SoltarNoSelvagem) é cancelada acima;
  // o Pokémon que ia lutar sai da bola do mesmo jeito do R, no meio do caminho até o selvagem
  private void soltarQuemIaLutar(PokemonBattle battle, PokemonEntity selvagem) {
    for (BattleActor ator : battle.getActors()) {
      if (!(ator instanceof PlayerBattleActor atorJogador) || ator.getPokemonList().isEmpty()) {
        continue;
      }
      ServerPlayer jogador = atorJogador.getEntity();
      BattlePokemon primeiro = ator.getPokemonList().get(0);
      Pokemon pokemon = primeiro.getOriginalPokemon();
      if (jogador == null || pokemon.isFainted() || pokemon.getEntity() != null) {
        continue;
      }
      Vec3 onde = SoltarNoSelvagem.onde(jogador, selvagem);
      if (onde != null) {
        SoltarNoSelvagem.jogar(jogador, pokemon, onde);
        LOGGER.info("R num selvagem: {} saiu da bola para a nossa batalha", pokemon.getSpecies().getName());
      }
    }
  }

  private PokemonEntity encontrarSelvagem(PokemonBattle battle) {
    for (BattleActor ator : battle.getActors()) {
      if (ator instanceof PokemonBattleActor atorSelvagem) {
        return atorSelvagem.getEntity();
      }
    }
    return null;
  }

  private void aoReceberDano(LivingEntity vitima, DamageSource fonte, float danoBase, float danoRecebido,
      boolean bloqueado) {
    SonoECongelado.aoLevarDano(vitima);
    Vida.guardarNoPv(vitima);
    Entity atacante = fonte.getEntity();

    if (atacante == null) {
      return;
    }

    if (!(vitima instanceof PokemonEntity || atacante instanceof PokemonEntity)) {
      return;
    }

    verificarGatilho(atacante, vitima);
    verificarGatilho(vitima, atacante);
  }

  private boolean podeLevarDano(LivingEntity vitima, DamageSource fonte, float dano) {
    if (vitima instanceof PokemonEntity pokemon && fonte.getEntity() instanceof Player jogador) {
      provocar(jogador, pokemon);
      return false;
    }
    return !EfeitosDeStatus.ehFogoDaQueimadura(vitima, fonte) && !Rolamento.estaDesviando(vitima, fonte);
  }

  // Dele, 18/10: "num combate não podemos dar dano no Pokémon selvagem nem do inimigo na mão" (e escolheu: qualquer dano;
  // fora do combate, bater começa a batalha sem dano). O jogador nunca machuca Pokémon, nem com soco, espada ou flecha (a
  // flecha conta como dele: fonte.getEntity() é quem atirou). A luta é só entre Pokémon. Bater num selvagem só provoca
  private void provocar(Player jogador, PokemonEntity pokemon) {
    if (jogador instanceof ServerPlayer quemBateu && pokemon.getPokemon().isWild()) {
      LOGGER.info("PROVOCOU: {} bateu em {} (sem dano)", quemBateu.getGameProfile().getName(),
          pokemon.getPokemon().getSpecies().getName());
      GerenciarCombates.entrarEmCombate(quemBateu, pokemon, null);
    }
  }

  private void verificarGatilho(Entity ladoDoJogador, Entity outroLado) {
    ServerPlayer jogador = jogadorResponsavel(ladoDoJogador);
    PokemonEntity selvagem = comoSelvagem(outroLado);

    if (jogador != null && selvagem != null) {
      Pokemon quemComecou = ladoDoJogador instanceof PokemonEntity pokemon ? pokemon.getPokemon() : null;
      GerenciarCombates.entrarEmCombate(jogador, selvagem, quemComecou);
    }
  }

  private ServerPlayer jogadorResponsavel(Entity entidade) {
    if (entidade instanceof ServerPlayer jogador) {
      return jogador;
    }
    if (entidade instanceof PokemonEntity pokemon) {
      return pokemon.getPokemon().getOwnerPlayer();
    }
    return null;
  }

  private PokemonEntity comoSelvagem(Entity entidade) {
    if (entidade instanceof PokemonEntity pokemon && pokemon.getPokemon().isWild()) {
      return pokemon;
    }
    return null;
  }

  private void aoDesmaiar(PokemonFaintedEvent evento) {
    Estagios.zerar(evento.getPokemon());
    StatusNossos.esquecer(evento.getPokemon());
    PrimeiroTurno.esquecer(evento.getPokemon());
    Confusao.esquecer(evento.getPokemon());
    Absorcao.esquecer(evento.getPokemon());
    TravaDaEscolha.esquecer(evento.getPokemon());
    Lutadores.saiu(evento.getPokemon());
  }

  private void aoRecolher(PokemonRecallEvent.Post evento) {
    Pokemon pokemon = evento.getPokemon();
    Estagios.zerar(pokemon);
    EfeitosDeStatus.esquecer(pokemon);
    PrimeiroTurno.esquecer(pokemon);
    Confusao.esquecer(pokemon);
    Absorcao.esquecer(pokemon);
    // 9.1: voltou para a bola, a Escolha destrava (como nos jogos; a Cinta de Foco gasta continua gasta até o fim da batalha)
    TravaDaEscolha.esquecer(pokemon);
    // Lotes 2 e 3 dos itens: o que estava guardado (Lansat, Micle, Custap, Metrônomo) acaba, como nos jogos
    EstadoDosItens.esquecer(pokemon);
    // 21/10 (habilidades, lote da entrada e das imunidades): a Regeneração (1/3 da vida para quem lutou nesta batalha)
    HabilidadesDeEntrada.aoRecolher(pokemon);
    Lutadores.saiu(pokemon);
    if (Habilidades.tem(pokemon, Habilidades.CURA_NATURAL) && pokemon.getStatus() != null) {
      pokemon.setStatus(null);
      LOGGER.info("CURA NATURAL: {} voltou para a bola e se curou", pokemon.getSpecies().getName());
      Feedback.avisarQuemImporta(pokemon,
          Component.translatable("cobblemon.battle.curestatus.naturalcure", pokemon.getDisplayName(false)));
    }
  }

  // Vai sair da bola com o dono em batalha e o lutador fora: primeiro o lutador volta (Lutadores.segurarATroca)
  private void antesDeSoltar(PokemonSentEvent.Pre evento) {
    if (Lutadores.segurarATroca(evento.getPokemon(), evento.getLevel(), evento.getPosition())) {
      evento.cancel();
    }
  }

  // Saiu da bola com o dono em combate: é a troca do lutador (Lutadores) e a "entrada" da Intimidação. 7.7: ele fica onde
  // caiu por uns segundos antes de vir para o seu lado (Parceiro)
  private void aoSoltar(PokemonSentEvent.Post evento) {
    Lutadores.entrou(evento.getPokemon());
    ServerPlayer dono = evento.getPokemon().getOwnerPlayer();
    if (dono != null) {
      Intimidacao.aoEntrar(evento.getPokemonEntity(), GerenciarCombates.selvagensNoCombateDe(dono));
      Parceiro.saiuDaBola(evento.getPokemon(), dono.server.getTickCount());
    }
  }
}
