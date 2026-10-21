package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.captura.Irritados;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.campo.EfeitosDoCampo;
import dev.victor.combatza.campo.LimpezaDoCampo;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.habilidades.Absorcao;
import dev.victor.combatza.ia.Guardas;
import dev.victor.combatza.ia.Parceiro;
import dev.victor.combatza.mega.Mega;
import dev.victor.combatza.habilidades.HabilidadesDeContato;
import dev.victor.combatza.habilidades.HabilidadesDeDano;
import dev.victor.combatza.habilidades.LacoFamiliar;
import dev.victor.combatza.habilidades.HabilidadesDeProtecao;
import dev.victor.combatza.habilidades.HabilidadesDePrioridade;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.habilidades.HabilidadesDeForma;
import dev.victor.combatza.itens.ItensDeContato;
import dev.victor.combatza.itens.ItensDeDano;
import dev.victor.combatza.itens.ItensDeVida;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.itens.TravaDaEscolha;
import dev.victor.combatza.itens.ItensDoAcerto;
import dev.victor.combatza.rede.UsarGolpePayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ControleDeGolpes {
  private static final int TAMANHO_DO_TIME = 6;
  public static final int MAXIMO_DE_GOLPES = 4;
  public static final float DANO_PROVISORIO = 4.0f;
  private static final String FRASE = "cobblemon_combat_za.golpe.";
  // Os motivos do "errou" (FRASE + "errou." + motivo, no arquivo de idioma)
  public static final String TEM_ALGO_CAMINHO = "tem_algo_no_caminho";

  private record Acerto(Entity alvo, double multiplicador) {
  }

  private record Chegada(Entity alvo, Vec3 ponto) {
  }

  private ControleDeGolpes() {
  }

  public static void usarGolpe(ServerPlayer jogador, UsarGolpePayload pedido) {
    int slotGolpe = pedido.slotGolpe();
    int slotPokemon = pedido.slotPokemon();
    if (slotGolpe < 0 || slotGolpe >= MAXIMO_DE_GOLPES || slotPokemon < 0 || slotPokemon >= TAMANHO_DO_TIME) {
      return;
    }
    // 7.9: com "todos de guarda", fora da batalha ninguém recebe comando (o seu jogo nem manda; isto é a garantia)
    if (Parceiro.todosDeGuarda(jogador) && !GerenciarCombates.estaEmCombate(jogador)) {
      return;
    }

    // Em batalha, o golpe é sempre do lutador, mesmo com a setinha em outro Pokémon (Lutadores)
    Pokemon pokemon = Lutadores.paraOGolpe(jogador, Cobblemon.INSTANCE.getStorage().getParty(jogador).get(slotPokemon));
    if (pokemon == null || pokemon.getEntity() == null) {
      return;
    }
    // 8.4: na animação da Mega (uns 6 segundos parado, sem IA), ele não ataca
    if (Mega.transformando(pokemon)) {
      return;
    }

    Move golpe = pokemon.getMoveSet().get(slotGolpe);
    if (golpe == null) {
      return;
    }

    // 9.1: preso num golpe pela Escolha, ou um golpe de status com o Colete de Ataque: desligado (riscado no losango)
    if (TravaDaEscolha.bloqueia(pokemon, slotGolpe, golpe)) {
      CobblemonCombatZA.LOGGER.info("ITEM: {} não pode usar {} ({})", pokemon.getSpecies().getName(), golpe.getName(),
          ItensSegurados.id(pokemon));
      Feedback.barraDeAcao(jogador, Component.literal("🚫 ").append(TravaDaEscolha.porque(pokemon, golpe)));
      return;
    }
    // 21/10: desligado pela Provocação ou pelo Bis (riscado no losango, GolpesDesligados)
    if (GolpesDesligados.recusou(jogador, pokemon, slotGolpe, golpe)) {
      return;
    }

    // Antes da recarga e sem recarga nova: o golpe gasto fica desligado até o Pokémon voltar para a bola
    if (PrimeiroTurno.passouDaHora(pokemon, golpe)) {
      CobblemonCombatZA.LOGGER.info("PRIMEIRO TURNO: {} já agiu, {} desligado", pokemon.getSpecies().getName(),
          golpe.getName());
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "so_ao_entrar", golpe.getDisplayName()));
      return;
    }

    long agora = jogador.server.getTickCount();
    // 7.4: este Pokémon é seu agora; o guarda não mexe nele por uns segundos
    Guardas.comandou(jogador, pokemon, agora);
    long restantes = Recargas.ticksRestantes(pokemon, slotGolpe, agora);
    if (restantes > 0) {
      CobblemonCombatZA.LOGGER.info("{} em recarga: faltam {} s", golpe.getName(), Recargas.emSegundos(restantes));
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "em_recarga", golpe.getDisplayName(),
          String.format("%.1f", Recargas.emSegundos(restantes))));
      return;
    }

    if (SonoECongelado.estaParado(pokemon)) {
      CobblemonCombatZA.LOGGER.info("{} está parado (dormindo ou congelado): {} ignorado",
          pokemon.getSpecies().getName(), golpe.getName());
      Feedback.historico(jogador, pokemon,
          Component.translatable(FRASE + "nao_consegue_se_mexer", pokemon.getDisplayName(false)));
      return;
    }

    if (Furia.emFuria(pokemon)) {
      CobblemonCombatZA.LOGGER.info("{} está em fúria: {} ignorado", pokemon.getSpecies().getName(), golpe.getName());
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "em_furia", pokemon.getDisplayName(false)));
      return;
    }

    if (Preso.estaPreso(pokemon)) {
      CobblemonCombatZA.LOGGER.info("{} está preso: {} ignorado", pokemon.getSpecies().getName(), golpe.getName());
      Feedback.historico(jogador, pokemon, Component.translatable(FRASE + "preso", pokemon.getDisplayName(false)));
      return;
    }

    if (AgendaDeGolpes.estaOcupado(pokemon) || Jatos.estaJorrando(pokemon)) {
      CobblemonCombatZA.LOGGER.info("{} está ocupado: {} ignorado", pokemon.getSpecies().getName(), golpe.getName());
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "ocupado"));
      return;
    }

    // O "primeiro turno" é o da batalha: fora dela (um zumbi, ou o golpe que ainda vai começar a batalha), não gasta o
    // Fingimento. O golpe que começa a batalha conta no impacto (registrarAgressao)
    if (GerenciarCombates.estaEmCombate(pokemon)) {
      PrimeiroTurno.agiu(pokemon);
    }

    if (Confusao.seFeriu(pokemon.getEntity())) {
      Recargas.iniciarRecarga(pokemon, slotGolpe, golpe, agora);
      return;
    }

    // 21/10: a paixão da Atração (metade das vezes não age), antes da paralisia, como no Showdown
    if (Atracao.naoAgiu(jogador, pokemon, slotGolpe, golpe, agora)
        || falhouPelaParalisia(jogador, pokemon, slotGolpe, golpe, agora)) {
      return;
    }

    boolean semCarga = Cargas.aoUsar(pokemon, slotGolpe, golpe);
    Execucao execucao = AgendaDeGolpes.iniciar(jogador, pokemon, golpe, slotGolpe, agora, pedido.alvoTravado(),
        semCarga, marcadorDoPedido(jogador, pokemon, golpe, pedido));
    TravaDaEscolha.usou(pokemon, slotGolpe);
    Bis.usou(pokemon, slotGolpe);
    ClimaNosGolpes.aoCarregar(execucao);
    VisualDosGolpes.aoCarregar(execucao);
    if (!Corrida.precisaCorrer(execucao)) {
      VisualDosGolpes.irAteOAlvo(execucao);
    }
    CobblemonCombatZA.LOGGER.info("Efeito de {}: {}", golpe.getName(), EfeitosDosGolpes.de(golpe));
    CobblemonCombatZA.LOGGER.info(
        "{} começou a executar {} (arquétipo {}: impacto em {} ticks, livre em {} ticks, tipo {}, alvo {})",
        pokemon.getSpecies().getName(), golpe.getName(), execucao.getArquetipo(),
        execucao.getTickImpacto() - agora, execucao.getTickFim() - agora, golpe.getType().getName(),
        golpe.getTemplate().getTarget());
    Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "carregando", golpe.getDisplayName()));
  }

  // 6.11c: o marcador que veio do seu jogo, conferido aqui (o servidor manda): só com o foco num alvo de verdade, num golpe
  // que usa marcador, sem montaria, com números de verdade, no limite do golpe em volta do alvo, no chão e a até 24 blocos
  // de você. Qualquer coisa fora disso: o golpe sai sem marcador, como sempre
  private static Vec3 marcadorDoPedido(ServerPlayer jogador, Pokemon pokemon, Move golpe, UsarGolpePayload pedido) {
    if (pedido.marcador().isEmpty() || pedido.alvoTravado() < 0) {
      return null;
    }
    Vector3f deslocamento = pedido.marcador().get();
    Entity alvo = jogador.serverLevel().getEntity(pedido.alvoTravado());
    PokemonEntity entidade = pokemon.getEntity();
    String porque = null;
    if (!(alvo instanceof LivingEntity vivo) || !vivo.isAlive()) {
      porque = "sem alvo";
    } else if (!Marcador.precisa(golpe)) {
      porque = "o golpe não usa marcador";
    } else if (entidade == null || entidade.isVehicle() || entidade.isPassenger()) {
      porque = "montado";
    } else if (!Float.isFinite(deslocamento.x()) || !Float.isFinite(deslocamento.z())) {
      porque = "números estranhos";
    }
    if (porque != null) {
      CobblemonCombatZA.LOGGER.info("MARCADOR ignorado: {}", porque);
      return null;
    }
    Vec3 noLimite = Marcador.limitar(golpe, new Vec3(deslocamento.x(), 0, deslocamento.z()));
    Vec3 ponto = Mira.noChao(jogador.serverLevel(), alvo.position().add(noLimite));
    if (ponto.distanceTo(jogador.position()) > Mira.ALCANCE) {
      CobblemonCombatZA.LOGGER.info("MARCADOR ignorado: longe de você");
      return null;
    }
    CobblemonCombatZA.LOGGER.info("MARCADOR: {} vai para {} antes de {}", pokemon.getSpecies().getName(),
        String.format("(%.1f, %.1f, %.1f)", ponto.x, ponto.y, ponto.z), golpe.getName());
    return ponto;
  }

  // 7.2: o golpe escolhido pela IA (o selvagem, IaDoSelvagem; desde a 7.4, também o guarda, Guardas), pelo mesmo caminho do
  // seu: a confusão e a paralisia, a carga, a recarga e o impacto. A IA só chama com o golpe pronto e o Pokémon livre (sem
  // dormir, congelar, ficar preso ou executar outro golpe). "quemVe" é o jogador da luta (ou o dono do guarda), que vê o
  // histórico. true = ele agiu (mesmo falhando)
  public static boolean usarGolpeDaIa(PokemonEntity atacante, int slotGolpe, Entity alvo, ServerPlayer quemVe,
      long agora) {
    Pokemon pokemon = atacante.getPokemon();
    Move golpe = pokemon.getMoveSet().get(slotGolpe);
    if (golpe == null || alvo == null || !alvo.isAlive() || TravaDaEscolha.bloqueia(pokemon, slotGolpe, golpe)
        || GolpesDesligados.bloqueia(pokemon, slotGolpe, golpe)) {
      return false;
    }
    PrimeiroTurno.agiu(pokemon);
    if (Confusao.seFeriu(atacante)) {
      Recargas.iniciarRecarga(pokemon, slotGolpe, golpe, agora);
      return true;
    }
    if (Atracao.naoAgiu(quemVe, pokemon, slotGolpe, golpe, agora)
        || falhouPelaParalisia(quemVe, pokemon, slotGolpe, golpe, agora)) {
      return true;
    }
    boolean semCarga = Cargas.aoUsar(pokemon, slotGolpe, golpe);
    Execucao execucao = AgendaDeGolpes.iniciarDaIa(quemVe, pokemon, golpe, slotGolpe, agora, alvo, semCarga);
    TravaDaEscolha.usou(pokemon, slotGolpe);
    Bis.usou(pokemon, slotGolpe);
    ClimaNosGolpes.aoCarregar(execucao);
    VisualDosGolpes.aoCarregar(execucao);
    if (!Corrida.precisaCorrer(execucao)) {
      VisualDosGolpes.irAteOAlvo(execucao);
    }
    CobblemonCombatZA.LOGGER.info("IA: {} começou {} em {} (arquétipo {}, impacto em {} ticks)",
        pokemon.getSpecies().getName(), golpe.getName(), alvo.getName().getString(), execucao.getArquetipo(),
        execucao.getTickImpacto() - agora);
    return true;
  }

  private static boolean falhouPelaParalisia(ServerPlayer quemVe, Pokemon pokemon, int slotGolpe, Move golpe,
      long agora) {
    if (!Paralisia.falhouAoUsarGolpe(pokemon.getEntity())) {
      return false;
    }
    Recargas.iniciarRecarga(pokemon, slotGolpe, golpe, agora);
    CobblemonCombatZA.LOGGER.info("PARALISIA: {} não conseguiu usar {}", pokemon.getSpecies().getName(),
        golpe.getName());
    Feedback.efeitoDoCobblemon(pokemon.getEntity(), "paralysis_actor");
    Feedback.efeitoDoCobblemon(pokemon.getEntity(), "paralysis_actorresidual");
    Feedback.historico(quemVe, pokemon, Component.translatable(FRASE + "paralisado", pokemon.getDisplayName(false)));
    return true;
  }

  public static void aplicarImpacto(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    PokemonEntity atacante = pokemon.getEntity();

    if (atacante == null) {
      CobblemonCombatZA.LOGGER.info("{} foi recolhido antes do impacto: {} perdido",
          pokemon.getSpecies().getName(), golpe.getName());
      return;
    }
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon.battle.used_move", pokemon.getDisplayName(false), golpe.getDisplayName()));
    // 21/10 (habilidades, lote da entrada e das imunidades): a Umidade e os climas fortes fazem o golpe falhar
    if (HabilidadesDeProtecao.golpeFalhou(execucao, atacante)) {
      return;
    }
    EfeitosDeVida.pagarComVida(execucao, atacante);

    FormaDoGolpe forma = FormaDoGolpe.de(golpe);
    if (forma == FormaDoGolpe.ARMADILHA) {
      Armadilhas.colocar(execucao, atacante);
      return;
    }
    if (forma == FormaDoGolpe.SEM_ALVO) {
      usouSemAlvo(execucao, atacante);
      return;
    }

    // 5.2: o golpe que voa vira um projétil de verdade. Sai agora; o acerto vem quando ele chegar (projetilChegou)
    if (forma != FormaDoGolpe.AREA_TODOS && execucao.getArquetipo().voa()) {
      Projetil.lancar(execucao, atacante);
      return;
    }

    List<Acerto> acertos = quemFoiAtingido(execucao, atacante, forma);
    if (acertos.isEmpty()) {
      return;
    }
    if (forma == FormaDoGolpe.UNICO) {
      VisualDosGolpes.raio(execucao, atacante, acertos.get(0).alvo());
    }
    aplicarNosAtingidos(execucao, atacante, acertos);
  }

  // 5.2: o projétil chegou (Projetil): em alguém, ou num ponto (um bloco, ou o fim do caminho mirando no chão). Daqui em
  // diante é o golpe de sempre: a área em volta do ponto, ou o acerto em quem ele pegou
  public static void projetilChegou(Execucao execucao, PokemonEntity atacante, Entity alvo, Vec3 ponto) {
    FormaDoGolpe forma = FormaDoGolpe.de(execucao.getGolpe());
    // 5.4a: o jato (Revólver d'Água, Raio Congelante...) acerta em pedaços enquanto encosta no alvo
    if (alvo != null && forma == FormaDoGolpe.UNICO && Jatos.ehJato(execucao.getGolpe())
        && execucao.getGolpe().getDamageCategory() != DamageCategories.INSTANCE.getSTATUS()) {
      Jatos.comecar(execucao, atacante, alvo);
      return;
    }
    boolean emBatalha = GerenciarCombates.estaEmCombate(execucao.getPokemon());
    List<Acerto> acertos = naChegada(execucao, atacante, forma, new Chegada(alvo, ponto), emBatalha);
    if (!acertos.isEmpty()) {
      aplicarNosAtingidos(execucao, atacante, acertos);
    }
  }

  public static void projetilErrou(Execucao execucao, String motivo) {
    errou(execucao, motivo);
  }

  private static void aplicarNosAtingidos(Execucao execucao, PokemonEntity atacante, List<Acerto> acertos) {
    Pokemon pokemon = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    if (golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) {
      CobblemonCombatZA.LOGGER.info("{} usou {} em {} alvo(s)",
          pokemon.getSpecies().getName(), golpe.getName(), acertos.size());
      for (Acerto acerto : acertos) {
        // Party: o Pokémon de um estranho e o selvagem da batalha de outra pessoa ficam de fora
        if (Lados.foraDaSuaLuta(execucao, acerto.alvo())) {
          continue;
        }
        registrarAgressao(execucao, atacante, acerto.alvo());
        // 21/10 (habilidades, lote das reações): o golpe de status da Travessura não pega no inimigo do tipo Sombrio
        if (HabilidadesDePrioridade.falhaNoSombrio(execucao, acerto.alvo())) {
          continue;
        }
        // 5.3: a Hipnose (60%) também pode ser desviada. 21/10: as habilidades que seguram o golpe (Ouro de Qualidade...)
        if (!Absorcao.absorveu(execucao, atacante, acerto.alvo())
            && !HabilidadesDeProtecao.bloqueou(execucao, atacante, acerto.alvo())
            && !Precisao.desviou(execucao, atacante, acerto.alvo())) {
          AplicarEfeitos.depoisDeAcertar(execucao, atacante, acerto.alvo());
        }
      }
      return;
    }

    for (Acerto acerto : acertos) {
      acertar(execucao, atacante, acerto.alvo(), acerto.multiplicador());
    }
  }

  // Em batalha, os monstros ficam de fora da área e do caminho do projétil (Lados.entraNaLuta); a mira do começo do golpe
  // já passou por eles (Execucao)
  private static List<Acerto> quemFoiAtingido(Execucao execucao, PokemonEntity atacante, FormaDoGolpe forma) {
    boolean emBatalha = GerenciarCombates.estaEmCombate(execucao.getPokemon());
    if (forma == FormaDoGolpe.AREA_TODOS) {
      Vec3 centro = atacante.getBoundingBox().getCenter();

      Feedback.particulas(execucao.getJogador().serverLevel(), ParticleTypes.EXPLOSION, centro, 1);
      // 7.2: o Terremoto do selvagem pega você também. 7.4b: o do guarda só pega monstros
      boolean doSelvagem = execucao.getPokemon().isWild();
      List<Entity> naZona = Area.emVolta(atacante, centro, Balanceamento.RAIO_EM_VOLTA_DO_POKEMON,
          e -> execucao.ehDoGuarda() ? Lados.pegaNoGolpe(execucao, atacante, e, emBatalha)
              : (doSelvagem || !(e instanceof Player)) && Lados.entraNaLutaDe(atacante, e, emBatalha));
      return comDistancia(execucao, naZona, centro, Balanceamento.RAIO_EM_VOLTA_DO_POKEMON);
    }

    Chegada chegada = chegadaDireta(execucao, atacante);
    if (chegada == null) {
      return List.of();
    }
    return naChegada(execucao, atacante, forma, chegada, emBatalha);
  }

  // Onde o golpe chegou (direto, ou o projétil): a área em volta do ponto, ou só quem ele pegou
  private static List<Acerto> naChegada(Execucao execucao, PokemonEntity atacante, FormaDoGolpe forma, Chegada chegada,
      boolean emBatalha) {
    if (forma == FormaDoGolpe.AREA_INIMIGOS) {
      // 5.6d: a Bolha enche o círculo de bolhas, no lugar da explosão
      if (CampoDeBolhas.ehCampo(execucao.getGolpe())) {
        CampoDeBolhas.comecar(execucao.getJogador().serverLevel(), chegada.ponto(), Balanceamento.RAIO_NO_IMPACTO);
      } else {
        Feedback.particulas(execucao.getJogador().serverLevel(), ParticleTypes.EXPLOSION, chegada.ponto(), 1);
      }
      List<Entity> naZona = Area.emVolta(atacante, chegada.ponto(), Balanceamento.RAIO_NO_IMPACTO,
          e -> e == chegada.alvo() || Lados.pegaNoGolpe(execucao, atacante, e, emBatalha));
      if (naZona.isEmpty()) {
        caiuNoLocal(execucao, chegada.ponto());
      }

      return comDistancia(execucao, naZona, chegada.ponto(), Balanceamento.RAIO_NO_IMPACTO);
    }

    if (chegada.alvo() == null) {
      caiuNoLocal(execucao, chegada.ponto());
      return List.of();
    }

    return List.of(new Acerto(chegada.alvo(), 1.0));
  }

  private static List<Acerto> comDistancia(Execucao execucao, List<Entity> naZona, Vec3 centro, double raio) {
    boolean danoCheio = AreaSemQueda.inclui(execucao.getGolpe());
    List<Acerto> acertos = new ArrayList<>();
    for (Entity alvo : naZona) {
      double multiplicador = danoCheio ? 1.0 : Area.multiplicadorPelaDistancia(alvo, centro, raio);
      acertos.add(new Acerto(alvo, multiplicador));
    }
    return acertos;
  }

  private static void acertar(Execucao execucao, PokemonEntity atacante, Entity alvo, double multiplicadorDeArea) {
    // 21/10 (habilidades, lote da conta do dano): pegou, e o Laço Familiar bate de novo (o jato não passa por aqui)
    if (acertar(execucao, atacante, alvo, multiplicadorDeArea, true, true)) {
      LacoFamiliar.agendar(execucao, atacante, alvo, multiplicadorDeArea);
    }
  }

  // 5.4a: um pedaço do jato (Jatos), com a fração do dano. Devolve se o golpe pegou: imune ou absorvido, o jato para.
  // 5.4f: "comVisual" diz se este pedaço traz o som, o impacto e a animação de apanhar
  public static boolean acertarPedaco(Execucao execucao, PokemonEntity atacante, Entity alvo, double fracao,
      boolean primeiro, boolean comVisual) {
    return acertar(execucao, atacante, alvo, fracao, primeiro, comVisual);
  }

  // As frases de efetividade e os efeitos extras só no primeiro acerto do golpe (um jato acerta várias vezes)
  private static boolean acertar(Execucao execucao, PokemonEntity atacante, Entity alvo, double multiplicadorDeArea,
      boolean primeiro, boolean comVisual) {
    // Party: o Pokémon de um estranho e o selvagem da batalha de outra pessoa: o golpe passa (nem dano, nem batalha). E nenhum
    // golpe (seu, do guarda ou do selvagem) pega num NPC treinador (Lados.ehNpc), nem a área
    if (Lados.foraDaSuaLuta(execucao, alvo) || Lados.ehNpc(alvo)) {
      return false;
    }
    registrarAgressao(execucao, atacante, alvo);
    // 21/10 (habilidades, lote da entrada e das imunidades): Antirruído, Blindado, Defesa Mágica, Presença Régia...
    if (Absorcao.absorveu(execucao, atacante, alvo) || CampoEBarreiras.bloqueiaPrioridade(execucao, alvo)
        || HabilidadesDeProtecao.bloqueou(execucao, atacante, alvo)) {
      return false;
    }
    Pokemon pokemon = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    // 21/10: Quebra-telha, Caninos Psíquicos e Investida Furiosa quebram as telas do alvo antes do dano (LimpezaDoCampo)
    if (primeiro) {
      LimpezaDoCampo.antesDoDano(execucao, alvo);
    }
    Dano.Resultado resultado = Dano.calcular(pokemon, golpe, alvo, atacante.getRandom());
    if (resultado.efetividade() <= 0) {
      CobblemonCombatZA.LOGGER.info("IMUNE: {} não afeta {}", golpe.getName(), alvo.getName().getString());
      Feedback.historico(execucao.getJogador(), alvo, Component.translatable("cobblemon.battle.immune", alvo.getName()));
      return false;
    }
    // 5.3: o sorteio do desvio vem depois da imunidade do tipo, como no Showdown
    if (primeiro && Precisao.desviou(execucao, atacante, alvo)) {
      return false;
    }

    // 7.2e: o golpe do selvagem num bando tira menos (em você e nos seus Pokémon)
    double doBando = execucao.getPokemon().isWild() ? GerenciarCombates.equilibrioDoBando(atacante) : 1.0;
    // Fogo amigo (dele, 20/10: "dar apenas 75% no aliado"): a área em volta (Terremoto, Surfar) pega os Pokémon do seu lado
    double doAmigo = Lados.fogoAmigo(execucao, alvo);
    // 21/10 (lote das formas): o Disfarce e a Cara de Gelo seguram o golpe (dano 0)
    float danoCheio = HabilidadesDeForma.antesDoDano(execucao, alvo,
        (float) (resultado.dano() * multiplicadorDeArea * doBando * doAmigo));
    float depoisDaRobustez = HabilidadesDeDano.robustez(alvo, danoCheio);
    boolean robustezAgiu = depoisDaRobustez < danoCheio;
    // 9.1: a Cinta de Foco, se a Robustez não segurou; lote 3: a Faixa de Foco (10%), se nada segurou
    float comCinta = ItensDeDano.cintaDeFoco(alvo, depoisDaRobustez, robustezAgiu);
    float dano = ItensDoAcerto.faixaDeFoco(alvo, comCinta, robustezAgiu || comCinta < depoisDaRobustez);

    float vidaAntes = Vida.vidaDe(alvo);
    boolean pegou = alvo.hurt(Dano.fonte(atacante, alvo), dano);
    semRevideEmBatalha(execucao, atacante, alvo);
    // 8.4: o golpe do seu Pokémon que acerta um selvagem adianta o medidor da Mega (uma vez por golpe: o jato acerta
    // várias). 21/10 (PvP): no duelo, o acerto no Pokémon do adversário também (Lados.ehInimigoDe)
    if (pegou && primeiro && !pokemon.isWild() && alvo instanceof PokemonEntity selvagem
        && Lados.ehInimigoDe(atacante, selvagem)) {
      Mega.acertou(execucao.getJogador());
    }
    double pvTirados = Vida.pvTirados(alvo, vidaAntes);
    // Etapa 8: o dano dos seus Pokémon acalma o selvagem irritado
    if (!pokemon.isWild()) {
      Irritados.acertou(execucao.getJogador(), alvo, pvTirados);
    }
    if (CobblemonCombatZA.LOGGER.isInfoEnabled()) {
      CobblemonCombatZA.LOGGER.info(
          "ACERTOU: {} usou {} em {} | dano {} (efetividade x{}, crítico? {}, área x{}, bando x{}, amigo x{} | aplicado? {} "
              + "| PV tirados {}",
          pokemon.getSpecies().getName(), golpe.getName(), alvo.getName().getString(), String.format("%.1f", dano),
          resultado.efetividade(), resultado.critico(), multiplicadorDeArea, String.format("%.2f", doBando), doAmigo, pegou,
          String.format("%.1f", pvTirados));
    }
    if (golpe.getType() == ElementalTypes.FIRE) {
      SonoECongelado.descongelarComFogo(alvo);
    }
    if (comVisual) {
      VisualDosGolpes.aoAcertar(golpe, alvo);
    }
    if (primeiro) {
      efetividade(execucao, alvo, resultado);
    }
    if (robustezAgiu) {
      Feedback.historico(execucao.getJogador(), alvo,
          Component.translatable("cobblemon.battle.ability.sturdy", alvo.getName()));
    } else if (comCinta < depoisDaRobustez) {
      Feedback.historico(execucao.getJogador(), alvo,
          Component.translatable("cobblemon_combat_za.item.cinta", alvo.getName()));
    } else if (dano < comCinta && alvo instanceof PokemonEntity comFaixa) {
      Feedback.historico(execucao.getJogador(), alvo, Component.translatable("cobblemon_combat_za.item.faixa",
          alvo.getName(), ItensSegurados.nome(comFaixa.getPokemon())));
    }
    EfeitosDeVida.depoisDoDano(execucao, atacante, alvo, pvTirados);
    ItensDeVida.orbeDaVida(execucao, atacante, pvTirados);
    if (primeiro) {
      AplicarEfeitos.depoisDeAcertar(execucao, atacante, alvo);
      Armadilhas.depoisDoAcerto(execucao, alvo);
    }
    HabilidadesDeContato.reagir(execucao, atacante, alvo, pvTirados);
    ItensDeContato.reagir(execucao, atacante, alvo, pvTirados);
    // 21/10 (lote das formas): o Disfarce cai, o Míssil Glutão cospe, Múmia e companhia, Troca de Cor, Forte Vínculo
    HabilidadesDeForma.depoisDoAcerto(execucao, atacante, alvo, pvTirados);
    // Lotes 2 e 3 dos itens: a gema e a baga de resistência são gastas; o Balão, as Políticas, o Sino, o Cartão Vermelho...
    if (pegou) {
      ItensDoAcerto.depoisDoAcerto(execucao, atacante, alvo, resultado, pvTirados, primeiro);
    }
    // 21/10 (habilidades, lote das reações): Estamina, Armadura Frágil, Corpo Maldito, Furto, Arrogância, Resultado...
    HabilidadesQueReagem.depoisDoAcerto(execucao, atacante, alvo, resultado, pvTirados, primeiro);
    // 5b.2: a Lançareia chama a Areia
    long agora = atacante.getServer() == null ? 0 : atacante.getServer().getTickCount();
    ClimaNosGolpes.depoisDoAcerto(alvo, pvTirados, agora);
    CampoEBarreiras.depoisDoAcerto(alvo, pvTirados, agora);
    return true;
  }

  private static Chegada chegadaDireta(Execucao execucao, PokemonEntity atacante) {
    double alcance = Mira.alcanceDo(execucao.getGolpe());
    if (!(execucao.getMirada() instanceof EntityHitResult acertoEntidade)) {
      Vec3 ponto = execucao.getMirada().getLocation();
      // A mira sai do olho do jogador: só os golpes de alcance curto (5.4c) conferem a distância até o ponto
      if (alcance < Mira.ALCANCE && atacante.getEyePosition().distanceTo(ponto) > alcance) {
        errou(execucao, "fora_de_alcance");
        return null;
      }
      if (!Mira.chegaAte(atacante, ponto)) {
        errou(execucao, TEM_ALGO_CAMINHO);
        return null;
      }
      return new Chegada(null, ponto);
    }

    Entity alvo = acertoEntidade.getEntity();
    if (!alvo.isAlive()) {
      errou(execucao, "alvo_sumiu");
      return null;
    }
    if (atacante.distanceTo(alvo) > alcance) {
      errou(execucao, "fora_de_alcance");
      return null;
    }
    if (!atacante.hasLineOfSight(alvo)) {
      errou(execucao, TEM_ALGO_CAMINHO);
      return null;
    }
    return new Chegada(alvo, alvo.getBoundingBox().getCenter());
  }

  private static void caiuNoLocal(Execucao execucao, Vec3 ponto) {
    CobblemonCombatZA.LOGGER.info("MIRA: {} caiu no local marcado", execucao.getGolpe().getName());
    Feedback.particulas(execucao.getJogador().serverLevel(), ParticleTypes.POOF, ponto, 10);
    Feedback.historico(execucao.getJogador(), execucao.getPokemon(), Component.translatable("cobblemon.battle.missed"));
  }

  private static void errou(Execucao execucao, String motivo) {
    Component porque = Component.translatable(FRASE + "errou." + motivo);
    CobblemonCombatZA.LOGGER.info("ERROU: {} usou {}: {}",
        execucao.getPokemon().getSpecies().getName(), execucao.getGolpe().getName(), porque.getString());
    Feedback.historico(execucao.getJogador(), execucao.getPokemon(),
        Component.translatable("cobblemon.battle.missed").append(" (").append(porque).append(")"));
  }

  // Como no Z-A e no log de batalha do Cobblemon: depois do "usou", só aparece o que não é neutro, falando do alvo
  private static void efetividade(Execucao execucao, Entity alvo, Dano.Resultado resultado) {
    if (resultado.efetividade() > 1) {
      Feedback.historico(execucao.getJogador(), alvo,
          Component.translatable("cobblemon.battle.superEffective_spread", alvo.getName()));
    } else if (resultado.efetividade() < 1) {
      Feedback.historico(execucao.getJogador(), alvo,
          Component.translatable("cobblemon.battle.resisted_spread", alvo.getName()));
    }
    if (resultado.critico()) {
      Feedback.historico(execucao.getJogador(), alvo,
          Component.translatable("cobblemon.battle.crit_spread", alvo.getName()));
    }
  }

  private static void registrarAgressao(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (alvo instanceof LivingEntity alvoVivo) {
      alvoVivo.setLastHurtByMob(atacante);
    }
    // O seu Pokémon acertando um selvagem começa (ou continua) a batalha. O golpe do selvagem não começa nada (7.2)
    if (!execucao.getPokemon().isWild() && alvo instanceof PokemonEntity alvoPokemon
        && alvoPokemon.getPokemon().isWild()) {
      GerenciarCombates.entrarEmCombate(execucao.getJogador(), alvoPokemon, execucao.getPokemon());
      PrimeiroTurno.agiu(execucao.getPokemon());
    }
    semRevideEmBatalha(execucao, atacante, alvo);
  }

  // Em batalha contra selvagem, um monstro que leva um golpe do nosso Pokémon (porque entrou na frente, por exemplo) NÃO
  // vira para ele, mesmo que o golpe pegue (pedido dele, 18/10). O Minecraft anota quem bateu (lastHurtByMob) e a IA do
  // monstro revida (HurtByTargetGoal); aqui a anotação é apagada, e o alvo dele também, se fosse o nosso Pokémon. Os
  // "neutros" (lobo, enderman, abelha...) guardam uma raiva própria (NeutralMob), que também é apagada.
  // 7.2: os Pokémon também esquecem quem bateu. O selvagem da luta tem a nossa IA (IaDoSelvagem), e o seu lutador só ataca
  // quando você manda; pela anotação, o Fight or Flight faria os dois revidarem sozinhos.
  // 7.4b: o golpe do guarda é de propósito: o monstro que ele acertou pode revidar nele
  private static void semRevideEmBatalha(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof LivingEntity vivo) || execucao.ehDoGuarda()
        || !GerenciarCombates.estaEmCombate(execucao.getPokemon())) {
      return;
    }
    vivo.setLastHurtByMob(null);
    if (vivo instanceof Mob monstro && monstro.getTarget() == atacante) {
      monstro.setTarget(null);
    }
    if (vivo instanceof NeutralMob neutro && atacante.getUUID().equals(neutro.getPersistentAngerTarget())) {
      neutro.stopBeingAngry();
    }
  }

  private static void usouSemAlvo(Execucao execucao, PokemonEntity atacante) {
    CobblemonCombatZA.LOGGER.info("SEM_ALVO: {} usou {}",
        execucao.getPokemon().getSpecies().getName(), execucao.getGolpe().getName());
    // 5b.1: os golpes de clima (Dança da Chuva, Dia Ensolarado...)
    if (EfeitosDoCampo.aoUsarGolpe(execucao, atacante) || CampoEBarreiras.aoUsarGolpe(execucao, atacante)
        || GolpesDeStatus.semAlvo(execucao, atacante)) {
      return;
    }
    Feedback.particulas(atacante, ParticleTypes.HAPPY_VILLAGER, 15);
    EfeitosDeVida.curarComGolpe(execucao, atacante);
    AplicarEfeitos.depoisDeAcertar(execucao, atacante, atacante);
  }
}
