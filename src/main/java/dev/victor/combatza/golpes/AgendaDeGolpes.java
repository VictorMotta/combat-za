package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.habilidades.HabilidadesDaBatalha;
import dev.victor.combatza.habilidades.LacoFamiliar;
import dev.victor.combatza.habilidades.QuebraMolde;
import dev.victor.combatza.habilidades.HabilidadesDeForma;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AgendaDeGolpes {
  private static final Map<UUID, Execucao> EM_EXECUCAO = new HashMap<>();

  private AgendaDeGolpes() {
  }

  public static boolean estaOcupado(Pokemon pokemon) {
    return EM_EXECUCAO.containsKey(pokemon.getUuid());
  }

  // O golpe que ele está executando agora (null = nenhum)
  public static Execucao emExecucao(Pokemon pokemon) {
    return EM_EXECUCAO.get(pokemon.getUuid());
  }

  public static Execucao iniciar(ServerPlayer jogador, Pokemon pokemon, Move golpe, int slot, long agora,
      int alvoTravado, boolean semCarga) {
    return iniciar(jogador, pokemon, golpe, slot, agora, alvoTravado, semCarga, null);
  }

  // 6.11c: com o marcador, ele primeiro vai até lá (correndo de frente para o caminho): virar para o alvo fica para a chegada
  public static Execucao iniciar(ServerPlayer jogador, Pokemon pokemon, Move golpe, int slot, long agora,
      int alvoTravado, boolean semCarga, Vec3 marcador) {
    Execucao execucao = new Execucao(jogador, pokemon, golpe, slot, agora, alvoTravado, semCarga);
    if (marcador != null) {
      execucao.definirMarcador(marcador);
    }
    EM_EXECUCAO.put(pokemon.getUuid(), execucao);
    execucao.mostrarBarra();
    if (marcador == null) {
      VisualDosGolpes.alinharNoCliente(execucao, agora);
    }
    // 21/10 (lote das formas): Proteico e Líbero (o tipo), Troca de Postura (a forma), Míssil Glutão (a presa)
    HabilidadesDeForma.aoComecarGolpe(execucao);
    return execucao;
  }

  // 7.2 e 7.4: o golpe da IA (o selvagem, o guarda), no alvo que ela escolheu
  public static Execucao iniciarDaIa(ServerPlayer quemVe, Pokemon pokemon, Move golpe, int slot, long agora, Entity alvo,
      boolean semCarga) {
    Execucao execucao = Execucao.daIa(quemVe, pokemon, golpe, slot, agora, alvo, semCarga);
    EM_EXECUCAO.put(pokemon.getUuid(), execucao);
    VisualDosGolpes.alinharNoCliente(execucao, agora);
    AvisoDoGolpeDoSelvagem.comecou(execucao, agora);
    HabilidadesDeForma.aoComecarGolpe(execucao);
    return execucao;
  }

  public static void atualizar(long agora) {
    for (Execucao execucao : new ArrayList<>(EM_EXECUCAO.values())) {
      // 23/10 (um erro num golpe derrubou o servidor dedicado com todo mundo): o golpe que der erro é largado e o erro fica
      // no log; a batalha e o servidor continuam
      try {
        atualizarExecucao(execucao, agora);
      } catch (RuntimeException erro) {
        CobblemonCombatZA.LOGGER.error("GOLPE COM ERRO: {} de {} foi largado", execucao.getGolpe().getName(),
            execucao.getPokemon().getSpecies().getName(), erro);
        EM_EXECUCAO.remove(execucao.getPokemon().getUuid(), execucao);
      }
    }
    // 21/10 (habilidades, lote da conta do dano): a segunda pancada do Laço Familiar
    LacoFamiliar.atualizar(agora);
  }

  public static boolean cancelar(Pokemon pokemon, long agora) {
    Execucao execucao = EM_EXECUCAO.get(pokemon.getUuid());
    if (execucao == null || execucao.jaImpactou()) {
      return false;
    }
    execucao.esconderBarra();
    AvisoDoGolpeDoSelvagem.acabou(execucao);
    EM_EXECUCAO.remove(pokemon.getUuid());
    recarregar(execucao, agora);
    CobblemonCombatZA.LOGGER.info("CANCELADO: {} perdeu o {} durante a carga",
        pokemon.getSpecies().getName(), execucao.getGolpe().getName());
    Feedback.particulas(execucao.getEntidade(), ParticleTypes.SMOKE, 20);
    Feedback.historico(execucao.getJogador(), pokemon,
        Component.translatable("cobblemon_combat_za.golpe.cancelado", execucao.getGolpe().getDisplayName()));
    return true;
  }

  // 7.7: o guarda que entra na luta pela troca sem bola larga o golpe que carregava, sem recarga e sem aviso (o golpe não
  // saiu, e quem o escolheu não foi você). Depois do impacto, ele termina normalmente
  public static void largar(Pokemon pokemon) {
    Execucao execucao = EM_EXECUCAO.get(pokemon.getUuid());
    if (execucao == null || execucao.jaImpactou()) {
      return;
    }
    execucao.esconderBarra();
    AvisoDoGolpeDoSelvagem.acabou(execucao);
    EM_EXECUCAO.remove(pokemon.getUuid());
    if (execucao.getEntidade() != null) {
      execucao.getEntidade().getNavigation().stop();
    }
    CobblemonCombatZA.LOGGER.info("LARGOU: {} largou o {} para entrar na luta", pokemon.getSpecies().getName(),
        execucao.getGolpe().getName());
  }

  public static void limpar() {
    EM_EXECUCAO.values().forEach(Execucao::esconderBarra);
    EM_EXECUCAO.clear();
  }

  private static void naoAlcancou(Execucao execucao, long agora) {
    naoAlcancou(execucao, agora, "nao_alcancou");
  }

  private static void naoAlcancou(Execucao execucao, long agora, String motivo) {
    execucao.esconderBarra();
    AvisoDoGolpeDoSelvagem.acabou(execucao);
    EM_EXECUCAO.remove(execucao.getPokemon().getUuid());
    execucao.getEntidade().getNavigation().stop();
    // 01/11: o seu golpe de longe que não achou de onde ver o alvo não gasta a vez (ele não saiu)
    if (EfeitosDosGolpes.fazContato(execucao.getGolpe()) || execucao.ehDaIa()) {
      recarregar(execucao, agora);
    }
    CobblemonCombatZA.LOGGER.info("NÃO ALCANÇOU: {} não chegou até o alvo com {}",
        execucao.getPokemon().getSpecies().getName(), execucao.getGolpe().getName());
    Feedback.historico(execucao.getJogador(), execucao.getPokemon(),
        Component.translatable("cobblemon.battle.missed").append(" (")
            .append(Component.translatable("cobblemon_combat_za.golpe.errou." + motivo)).append(")"));
  }

  private static void atualizarExecucao(Execucao execucao, long agora) {
    UUID uuid = execucao.getPokemon().getUuid();

    if (EM_EXECUCAO.get(uuid) != execucao) {
      return;
    }

    if (!execucao.jaImpactou() && execucao.foiRecolhido()) {
      cancelar(execucao.getPokemon(), agora);
      return;
    }
    // 6.11c: com o marcador, antes de tudo ele vai até lá; só depois olha o alvo e solta
    if (!execucao.jaImpactou() && execucao.indoAoMarcador()) {
      Corrida.irAoMarcador(execucao, agora);
      if (execucao.indoAoMarcador()) {
        execucao.atualizarBarra(agora);
        return;
      }
    }
    VisualDosGolpes.olharParaOAlvo(execucao);

    if (!execucao.jaImpactou()) {
      execucao.atualizarBarra(agora);
    }

    if (!execucao.jaImpactou() && agora % 2 == 0) {
      Feedback.particulas(execucao.getPokemon().getEntity(), ParticleTypes.ENCHANT, 3);
    }

    if (!execucao.jaImpactou() && Corrida.precisaCorrer(execucao)) {
      if (Corrida.desistiu(execucao, agora)) {
        naoAlcancou(execucao, agora);
        return;
      }
      // 21/10: quem não voa, num alvo de contato a mais de 3 blocos do chão
      if (Corrida.altoDemais(execucao)) {
        naoAlcancou(execucao, agora, "alto_demais");
        return;
      }
      Corrida.atualizar(execucao, agora);
    }

    if (!execucao.jaImpactou() && agora >= execucao.getTickImpacto()) {
      execucao.marcarImpacto();
      AvisoDoGolpeDoSelvagem.acabou(execucao);
      // 21/10 (habilidades, lote da conta do dano): ele agiu (o Analisar de quem bater nele agora) e o impacto acontece
      // "durante o golpe" (o Quebra-Molde e o Infiltrado valem nele)
      HabilidadesDaBatalha.agiu(execucao.getPokemon(), agora);
      // 01/11: o último golpe de cada um (o Imitador, o Ataque Espelhado, a Instrução... leem: UsarOutroGolpe)
      UsarOutroGolpe.usou(execucao);
      QuebraMolde.durante(execucao, () -> ControleDeGolpes.aplicarImpacto(execucao));
      execucao.esconderBarra();
      // 01/11: o Dançarino de quem está na batalha copia a dança
      UsarOutroGolpe.dancarino(execucao, agora);
      // 01/11: o golpe chamou outro (Metrônomo, Imitador...): o chamado começa agora, no lugar dele
      if (execucao.getChamado() != null && EM_EXECUCAO.get(uuid) == execucao) {
        Execucao chamado = Execucao.chamadoPor(execucao, agora);
        EM_EXECUCAO.put(uuid, chamado);
        comecar(chamado, agora);
        return;
      }
    }

    if (agora >= execucao.getTickFim()) {
      EM_EXECUCAO.remove(uuid);
      // 01/11: o golpe chamado ou de graça não tem fúria nem cargas guardadas
      if (execucao.getOrigem() != Execucao.Origem.NORMAL) {
        recarregar(execucao, agora);
        return;
      }
      // 5.4d: a fúria ataca de novo sozinha (Furia); 5.4b: com cargas sobrando, a recarga espera a última (Cargas)
      if (!Furia.continuar(execucao, agora) && !Cargas.guardarParaDepois(execucao, agora)) {
        Recargas.iniciarRecarga(execucao.getPokemon(), execucao.getSlot(), execucao.getGolpe(), agora);
      }
    }
  }

  private static void recarregar(Execucao execucao, long agora) {
    if (execucao.temRecarga()) {
      Recargas.iniciarRecarga(execucao.getPokemon(), execucao.getSlot(), execucao.getGolpe(), agora);
    }
  }

  // 01/11: o golpe de graça (a Instrução, o Dançarino: UsarOutroGolpe), se ele estiver livre. null = não começou
  public static Execucao iniciarDeGraca(ServerPlayer quemVe, Pokemon pokemon, Move golpe, int slot, long agora,
      HitResult mira, Execucao.Origem origem) {
    if (estaOcupado(pokemon) || pokemon.getEntity() == null) {
      return null;
    }
    Execucao execucao = Execucao.deGraca(quemVe, pokemon, golpe, slot, agora, mira, origem);
    EM_EXECUCAO.put(pokemon.getUuid(), execucao);
    comecar(execucao, agora);
    return execucao;
  }

  // O começo de um golpe que não passou pela sua tecla nem pela IA: a barra, o visual, a carga e a corrida
  private static void comecar(Execucao execucao, long agora) {
    execucao.mostrarBarra();
    VisualDosGolpes.alinharNoCliente(execucao, agora);
    if (execucao.ehDaIa()) {
      AvisoDoGolpeDoSelvagem.comecou(execucao, agora);
    }
    HabilidadesDeForma.aoComecarGolpe(execucao);
    ClimaNosGolpes.aoCarregar(execucao);
    VisualDosGolpes.aoCarregar(execucao);
    if (!Corrida.precisaCorrer(execucao)) {
      VisualDosGolpes.irAteOAlvo(execucao);
    }
  }
}
