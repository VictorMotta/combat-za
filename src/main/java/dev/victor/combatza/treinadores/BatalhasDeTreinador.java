package dev.victor.combatza.treinadores;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.ia.Andar;
import dev.victor.combatza.rede.PerguntaDoTreinadorPayload;
import dev.victor.combatza.rede.RespostaDoTreinadorPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// 21/10 (treinadores): as batalhas em tempo real contra os treinadores do RCT (cada uma é uma BatalhaDeTreinador). Daqui
// saem as perguntas que o resto do mod faz sobre elas ("este Pokémon é de um treinador?", "esta batalha é contra um
// treinador?"), com os ganchos pequenos em Combate, GerenciarCombates, IaDoSelvagem, Lados, ChanceDeCaptura e Deteccao. O
// Pokémon do treinador é uma CÓPIA do time dele nos dados do RCT, sem dono (um "selvagem" para o Cobblemon); ela leva a
// marca MARCA e some do mundo no fim da batalha. Se o servidor fechar no meio, ela some antes de salvar; se mesmo assim uma
// cópia voltar do disco (o servidor caiu), ela some quando carregar
public final class BatalhasDeTreinador {
  static final String MARCA = "cobblemon_combat_za.do_treinador";
  // A morte do Pokémon do treinador é vista ANTES do desmaio no chão dos selvagens (captura.Desmaio)
  private static final ResourceLocation ANTES_DO_DESMAIO = ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID,
      "treinadores");
  private static final String FRASE = "cobblemon_combat_za.treinador.";
  // O Pokémon que caiu fica deitado 1 s; depois, o feixe vermelho da bola até o treinador (1,5 s, como o do Cobblemon)
  private static final int TICKS_DEITADO = 20;
  private static final int TICKS_DO_FEIXE = 30;
  private static final int FEIXE_RECOLHENDO = 3;
  private static final int NUVENS_AO_SUMIR = 12;
  // Escolheu "Tempo real" já longe do treinador (a telinha fica aberta até 30 s): a mesma distância da desistência
  private static final double LONGE_PARA_COMECAR = 24;

  // Uma cópia do treinador no mundo, com a batalha dela
  private record Registro(PokemonEntity entidade, BatalhaDeTreinador batalha) {
  }

  // A volta para a bola: a partir de "feixeNoTick", o feixe; em "someNoTick", some
  private record Volta(PokemonEntity entidade, LivingEntity treinador, long feixeNoTick, long someNoTick) {
  }

  private static final Map<UUID, BatalhaDeTreinador> POR_JOGADOR = new HashMap<>();
  private static final Map<UUID, Registro> POR_ENTIDADE = new HashMap<>();
  private static final Map<UUID, Volta> VOLTANDO = new HashMap<>();
  private static final List<Entity> SOBRAS = new ArrayList<>();

  private BatalhasDeTreinador() {
  }

  public static void registrar() {
    PayloadTypeRegistry.playS2C().register(PerguntaDoTreinadorPayload.TIPO, PerguntaDoTreinadorPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(RespostaDoTreinadorPayload.TIPO, RespostaDoTreinadorPayload.STREAM_CODEC);
    EscolhaDoModo.registrar();
    ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(ANTES_DO_DESMAIO, Event.DEFAULT_PHASE);
    ServerLivingEntityEvents.ALLOW_DEATH.register(ANTES_DO_DESMAIO, (entidade, fonte, dano) ->
        !(entidade instanceof PokemonEntity pokemon) || !desmaiouAgora(pokemon, fonte));
    // Saindo da bola, na animação da Mega, caído ou voltando para a bola, ele não leva dano (como o seu na Mega)
    ServerLivingEntityEvents.ALLOW_DAMAGE.register((entidade, fonte, dano) -> !(entidade instanceof PokemonEntity pokemon
        && foraDaLuta(pokemon)) || fonte.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
    // Uma cópia marcada que carrega do disco (não é a que a batalha conhece) é uma sobra: some no próximo tick
    ServerEntityEvents.ENTITY_LOAD.register((entidade, nivel) -> {
      Registro registro = POR_ENTIDADE.get(entidade.getUUID());
      if (entidade.getTags().contains(MARCA) && (registro == null || registro.entidade() != entidade)) {
        SOBRAS.add(entidade);
      }
    });
    ServerTickEvents.END_SERVER_TICK.register(BatalhasDeTreinador::atualizar);
    ServerLifecycleEvents.SERVER_STOPPING.register(servidor -> encerrarTodas());
    ServerLifecycleEvents.SERVER_STOPPED.register(servidor -> limpar());
  }

  // ---- O começo ("Tempo real" na telinha: EscolhaDoModo) ----

  public static boolean estaEmBatalha(ServerPlayer jogador) {
    return POR_JOGADOR.containsKey(jogador.getUUID());
  }

  static void comecar(ServerPlayer jogador, LivingEntity treinador, List<Pokemon> modelos) {
    Component nome = treinador.getDisplayName();
    if (!treinador.isAlive() || treinador.isRemoved() || jogador.level() != treinador.level()
        || jogador.distanceTo(treinador) > LONGE_PARA_COMECAR) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "longe", nome));
      return;
    }
    if (GerenciarCombates.estaEmCombate(jogador) || estaEmBatalha(jogador)
        || BattleRegistry.getBattleByParticipatingPlayer(jogador) != null) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "ja_em_batalha"));
      return;
    }
    // A mesma conferência do RCT antes de qualquer batalha (a espera do treinador, a série, o limite de nível, um Pokémon
    // apto)
    if (!PonteDoRct.podeBatalhar(treinador, jogador)) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "nao_pode", nome));
      return;
    }
    MinecraftServer servidor = jogador.server;
    List<Pokemon> time = new ArrayList<>();
    for (Pokemon modelo : modelos) {
      Pokemon copia = modelo.clone(true, servidor.registryAccess());
      copia.heal();
      time.add(copia);
    }
    PonteDoRct.DadosDoTime dados = PonteDoRct.dadosDoTime(treinador);
    CristalZ.trocarPelaReserva(time, dados);
    // O RCT anota a batalha como dele. Se não der (o RCT mudou), a batalha é a por turnos de sempre
    if (!PonteDoRct.comecou(treinador, jogador)) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "por_turnos_entao"));
      EscolhaDoModo.porTurnos(jogador, treinador);
      return;
    }
    long agora = servidor.getTickCount();
    BatalhaDeTreinador batalha = new BatalhaDeTreinador(jogador, treinador, time, dados, agora);
    POR_JOGADOR.put(jogador.getUUID(), batalha);
    CobblemonCombatZA.LOGGER.info("TREINADOR: batalha em TEMPO REAL de {} contra {} ({} Pokémon; bolsa {})",
        jogador.getGameProfile().getName(), PonteDoRct.id(treinador), time.size(), dados == null ? "?" : dados.bolsa());
    batalha.comecar(jogador, agora);
  }

  // A cópia acabou de nascer (antes de entrar no mundo): a marca e o registro. Sem dono, o Cobblemon faria ela sumir sozinha
  // quando você se afasta um pouco (o CobblemonAgingDespawner dos selvagens); quem tira ela do mundo é a batalha
  static void registrarEntidade(PokemonEntity entidade, BatalhaDeTreinador batalha) {
    entidade.addTag(MARCA);
    entidade.setPersistenceRequired();
    POR_ENTIDADE.put(entidade.getUUID(), new Registro(entidade, batalha));
  }

  // ---- A cada tick ----

  private static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    EscolhaDoModo.atualizar(agora);
    for (Entity sobra : SOBRAS) {
      CobblemonCombatZA.LOGGER.info("TREINADOR: uma cópia de Pokémon de treinador que ficou no mundo sumiu ({})",
          sobra.getName().getString());
      sobra.discard();
    }
    SOBRAS.clear();
    for (BatalhaDeTreinador batalha : List.copyOf(POR_JOGADOR.values())) {
      if (batalha.atualizar(servidor, agora)) {
        POR_JOGADOR.remove(batalha.jogador());
      }
    }
    andarAsVoltas(agora);
    // A cópia que já sumiu sai do registro quando a batalha dela acaba (até lá, as armadilhas que ela deixou, como a Rocha
    // Furtiva, continuam pegando só os seus Pokémon)
    POR_ENTIDADE.values().removeIf(registro -> registro.entidade().isRemoved()
        && POR_JOGADOR.get(registro.batalha().jogador()) != registro.batalha());
  }

  // O Pokémon do treinador volta para a bola: "caiu" = desmaiou (fica deitado antes); senão, o feixe já começa
  static void voltarParaABola(PokemonEntity entidade, LivingEntity treinador, long agora, boolean caiu) {
    entidade.setNoAi(true);
    Andar.parar(entidade);
    entidade.setDeltaMovement(Vec3.ZERO);
    if (caiu) {
      entidade.setHealth(Vida.naVida(entidade, 1));
      entidade.setEnablePoseTypeRecalculation(false);
      entidade.getEntityData().set(PokemonEntity.getPOSE_TYPE(), PoseType.SLEEP);
    }
    long feixe = caiu ? agora + TICKS_DEITADO : agora;
    VOLTANDO.put(entidade.getUUID(), new Volta(entidade, treinador, feixe, feixe + TICKS_DO_FEIXE));
  }

  private static void andarAsVoltas(long agora) {
    VOLTANDO.values().removeIf(volta -> {
      PokemonEntity entidade = volta.entidade();
      if (entidade.isRemoved()) {
        return true;
      }
      // O Gambito Final e o recuo zeram a vida depois do desmaio (Vida.desmaiar): ele não morre no meio da volta
      if (entidade.isDeadOrDying()) {
        entidade.setHealth(Vida.naVida(entidade, 1));
      }
      if (agora >= volta.someNoTick()) {
        if (entidade.getBeamMode() != FEIXE_RECOLHENDO) {
          Feedback.particulas(entidade, ParticleTypes.POOF, NUVENS_AO_SUMIR);
        }
        entidade.discard();
        return true;
      }
      if (agora >= volta.feixeNoTick() && entidade.getBeamMode() != FEIXE_RECOLHENDO) {
        comecarOFeixe(entidade, volta.treinador());
      }
      return false;
    });
  }

  // O feixe vermelho até o treinador, como o recallWithAnimation do Cobblemon (javap: a marca do feixe aponta para quem
  // chama, modo 3 = voltando, o som da bola); sem o treinador por perto, ele só some em fumaça no fim
  private static void comecarOFeixe(PokemonEntity entidade, LivingEntity treinador) {
    if (treinador == null || treinador.isRemoved() || treinador.level() != entidade.level()) {
      return;
    }
    entidade.setPhasingTargetId(treinador.getId());
    entidade.setBeamMode(FEIXE_RECOLHENDO);
    entidade.setNoGravity(true);
    entidade.level().playSound(null, entidade.blockPosition(), CobblemonSounds.POKE_BALL_RECALL, SoundSource.NEUTRAL, 0.6f,
        1f);
  }

  // A morte do Pokémon do treinador vira o desmaio da batalha dele. O vazio e o /kill matam de verdade (a batalha vê que ele
  // sumiu). true = a morte foi cancelada aqui
  private static boolean desmaiouAgora(PokemonEntity entidade, DamageSource fonte) {
    Registro registro = POR_ENTIDADE.get(entidade.getUUID());
    if (registro == null || VOLTANDO.containsKey(entidade.getUUID()) || fonte.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
        || entidade.getServer() == null) {
      return false;
    }
    entidade.setHealth(Vida.naVida(entidade, 1));
    registro.batalha().desmaiou(entidade, entidade.getServer().getTickCount(), registro.batalha().estaLutando(entidade));
    return true;
  }

  // ---- Os ganchos do resto do mod ----

  // O Pokémon (a entidade) é de um treinador numa batalha em tempo real (ou foi: a marca fica nele, e o que ele deixou no
  // mundo depois da batalha não pega em ninguém)
  public static boolean ehDoTreinador(Entity entidade) {
    return entidade != null && (POR_ENTIDADE.containsKey(entidade.getUUID()) || entidade.getTags().contains(MARCA));
  }

  // Caído ou voltando para a bola: fora da batalha (Combate tira, os golpes passam)
  public static boolean desmaiou(PokemonEntity entidade) {
    return VOLTANDO.containsKey(entidade.getUUID());
  }

  // Do treinador, mas ainda não lutando (saindo da bola, na Mega) ou já fora (caído, voltando): os golpes passam e ele não
  // leva dano
  public static boolean foraDaLuta(PokemonEntity entidade) {
    Registro registro = POR_ENTIDADE.get(entidade.getUUID());
    return registro != null && (VOLTANDO.containsKey(entidade.getUUID()) || !registro.batalha().estaLutando(entidade));
  }

  // Lados: o Pokémon do treinador só tem como alvo os Pokémon de quem está batalhando com ele (nunca o jogador, nem NPC, nem
  // os Pokémon de outra pessoa)
  public static boolean ehAlvoDoTreinador(PokemonEntity atacante, Entity entidade) {
    Registro registro = POR_ENTIDADE.get(atacante.getUUID());
    return registro != null && entidade instanceof PokemonEntity alvo && !alvo.getPokemon().isWild()
        && registro.batalha().jogador().equals(alvo.getPokemon().getOwnerUUID());
  }

  // Lados: o seu golpe só pega o Pokémon do treinador se a batalha é sua e ele está lutando
  public static boolean ehOAdversario(ServerPlayer dono, PokemonEntity doTreinador) {
    Registro registro = POR_ENTIDADE.get(doTreinador.getUUID());
    return registro != null && registro.batalha().ehDe(dono) && !foraDaLuta(doTreinador);
  }

  // GerenciarCombates.entrarEmCombate: a batalha contra um treinador é só sua e dele. true = este jogador e este selvagem
  // NÃO entram juntos numa batalha (outro jogador no Pokémon do treinador; você, durante o duelo, num selvagem qualquer)
  public static boolean foraDoDuelo(ServerPlayer jogador, PokemonEntity selvagem) {
    Registro registro = POR_ENTIDADE.get(selvagem.getUUID());
    if (registro != null) {
      return !registro.batalha().ehDe(jogador) || !registro.batalha().estaLutando(selvagem);
    }
    return estaEmBatalha(jogador);
  }

  // IaDoSelvagem: sem nenhum Pokémon seu fora, ele espera parado, de olho em você
  public static void esperar(PokemonEntity doTreinador, ServerPlayer adversario) {
    Andar.parar(doTreinador);
    doTreinador.getLookControl().setLookAt(adversario, 30f, 30f);
  }

  public static boolean ehDeTreinador(Combate combate) {
    return doCombate(combate) != null;
  }

  // Combate.terminou: entre um Pokémon do treinador e o próximo, a batalha continua
  public static boolean aindaVemPokemon(Combate combate) {
    BatalhaDeTreinador batalha = doCombate(combate);
    return batalha != null && batalha.aindaVemPokemon();
  }

  // Combate.jogadorFugiu, numa batalha de treinador: você sai quando ela se decide ou quando desiste
  public static boolean saiu(Combate combate, ServerPlayer jogador) {
    BatalhaDeTreinador batalha = doCombate(combate);
    return batalha != null && batalha.saiu(jogador);
  }

  // Combate.avisarOFim: o aviso de fim da batalha de treinador. true = o aviso foi dado aqui
  public static boolean avisarOFim(Combate combate, ServerPlayer jogador) {
    BatalhaDeTreinador batalha = doCombate(combate);
    return batalha != null && batalha.ehDe(jogador) && batalha.avisarOFim(jogador);
  }

  private static BatalhaDeTreinador doCombate(Combate combate) {
    for (BatalhaDeTreinador batalha : POR_JOGADOR.values()) {
      if (batalha.combate() == combate) {
        return batalha;
      }
    }
    return null;
  }

  // ---- O fim do servidor ----

  // O servidor vai fechar: as batalhas não valem (o RCT esquece delas) e as cópias somem antes de o mundo ser salvo
  private static void encerrarTodas() {
    for (BatalhaDeTreinador batalha : POR_JOGADOR.values()) {
      PonteDoRct.cancelar(batalha.treinador(), batalha.jogador());
    }
    for (Registro registro : POR_ENTIDADE.values()) {
      registro.entidade().discard();
    }
    limpar();
  }

  private static void limpar() {
    POR_JOGADOR.clear();
    POR_ENTIDADE.clear();
    VOLTANDO.clear();
    SOBRAS.clear();
    EscolhaDoModo.limpar();
  }
}
