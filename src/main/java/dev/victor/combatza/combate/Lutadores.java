package dev.victor.combatza.combate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.AgendaDeGolpes;
import dev.victor.combatza.golpes.Balanceamento;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Recargas;
import dev.victor.combatza.rede.LutadorPayload;
import kotlin.Unit;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// Em batalha, cada jogador tem UM lutador: o Pokémon que luta. O losango e o Z/X/C/V são dele, mesmo com a setinha em
// outro Pokémon; os outros que estão fora são os de guarda (pedido dele, 18/10: "mudo na setinha ele some as
// possibilidades de tacar habilidade do pokemon que estava brigando"). Fora de batalha não há lutador: vale a setinha.
// - Quem vira o lutador: o primeiro Pokémon do jogador a agir na batalha (o que acertou o selvagem, o que o selvagem
//   atacou, o que foi jogado nele com o R) ou, com o lugar vago, o próximo que sair da bola ou que o jogador comandar.
//   7.2b: sem lutador, o selvagem vai no seu Pokémon fora mais perto dele, e esse vira o lutador (IaDoSelvagem)
// - Trocar (com o R, escolha dele): em batalha, jogar outro Pokémon da bola recolhe o lutador, e o novo vira o lutador.
//   Quem entra trocando fica Balanceamento.SEGUNDOS_DA_TROCA sem atacar: a vez perdida na troca dos jogos. 7.7: o R num
//   Pokémon que já está fora faz dele o lutador ali mesmo, sem voltar para a bola (entrarQuemEstaFora)
// - Lugar vago por desmaio: quem entra chega pronto, como nos jogos. Vago porque o jogador recolheu o lutador: quem
//   entra espera do mesmo jeito, para a espera da troca não ter atalho
public class Lutadores {
  private enum Vago {
    POR_DESMAIO, POR_RECOLHER
  }

  private static final int TAMANHO_DO_TIME = 6;
  // O feixe da bola do Cobblemon: 0 = nenhum; senão o Pokémon está saindo da bola ou voltando para ela (1,5 s)
  private static final int SEM_FEIXE = 0;
  private static final Map<UUID, UUID> LUTADOR_DO_JOGADOR = new HashMap<>();
  private static final Map<UUID, Vago> LUGAR_VAGO = new HashMap<>();
  // A troca em dois tempos: os jogadores com o lutador voltando, e os Pokémon cuja saída já é a segunda metade da troca
  private static final Set<UUID> TROCANDO = new HashSet<>();
  private static final Set<UUID> PODE_SAIR = new HashSet<>();

  private Lutadores() {
  }

  // O lutador do jogador, se ele tem um e o lutador está fora da bola
  public static Pokemon de(ServerPlayer jogador) {
    UUID lutador = LUTADOR_DO_JOGADOR.get(jogador.getUUID());
    if (lutador == null) {
      return null;
    }
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (pokemon.getUuid().equals(lutador) && pokemon.getEntity() != null) {
        return pokemon;
      }
    }
    return null;
  }

  // GerenciarCombates.entrarEmCombate: no começo da batalha, quem começou vira o lutador. Depois do começo, nada muda
  // por aqui (um guarda que o selvagem atacar continua de guarda)
  public static void comecou(ServerPlayer jogador, Pokemon quemComecou) {
    UUID dono = jogador.getUUID();
    if (quemComecou != null && quemComecou.getEntity() != null && !LUTADOR_DO_JOGADOR.containsKey(dono)
        && !LUGAR_VAGO.containsKey(dono)) {
      definir(jogador, quemComecou, false);
    }
  }

  // Antes de um Pokémon sair da bola (o POKEMON_SENT_PRE do Cobblemon, antes até da bola ser jogada): com o dono em batalha
  // e o lutador fora, PRIMEIRO o lutador volta (com a animação do R) e só quando a volta termina o novo sai, no mesmo lugar
  // (pedido dele, 18/10: "deve ter que puxar primeiro depois taca, se não fica 2 pokemons"). true = esta saída foi
  // segurada (o evento é cancelado). Um R a mais durante a volta é ignorado
  public static boolean segurarATroca(Pokemon novo, ServerLevel nivel, Vec3 onde) {
    if (PODE_SAIR.remove(novo.getUuid())) {
      return false;
    }
    ServerPlayer dono = novo.getOwnerPlayer();
    if (dono == null || !GerenciarCombates.estaEmCombate(dono)) {
      return false;
    }
    Pokemon antigo = de(dono);
    if (antigo == null || antigo.getUuid().equals(novo.getUuid())) {
      return false;
    }
    if (!TROCANDO.add(dono.getUUID())) {
      return true;
    }
    antigo.getEntity().recallWithAnimation().whenComplete((recolhido, erro) -> dono.server.execute(() -> {
      TROCANDO.remove(dono.getUUID());
      if (novo.getEntity() == null && !novo.isFainted() && dono.isAlive()) {
        PODE_SAIR.add(novo.getUuid());
        novo.sendOutWithAnimation(dono, nivel, onde, null, true, null, entidade -> Unit.INSTANCE);
      }
    }));
    return true;
  }

  // Saiu da bola (o R do Cobblemon, ou o R mirando no selvagem): com o dono em batalha, é a troca. Desde a 6.10 o lutador
  // já voltou antes (segurarATroca); se ainda estiver fora (uma saída que não passou por lá), ele volta agora
  public static void entrou(Pokemon novo) {
    ServerPlayer dono = novo.getOwnerPlayer();
    if (dono == null || !GerenciarCombates.estaEmCombate(dono)) {
      return;
    }
    Pokemon antigo = de(dono);
    if (antigo != null && antigo.getUuid().equals(novo.getUuid())) {
      return;
    }
    boolean trocou = antigo != null;
    definir(dono, novo, trocou || LUGAR_VAGO.get(dono.getUUID()) == Vago.POR_RECOLHER);
    if (trocou) {
      Feedback.historicoDoLutador(dono, antigo,
          Component.translatable("cobblemon.battle.withdraw.self", antigo.getDisplayName(false)));
      antigo.getEntity().recallWithAnimation();
    }
    Feedback.historicoDoLutador(dono, novo,
        Component.translatable("cobblemon.battle.switch.self", novo.getDisplayName(false)));
  }

  // 7.7 (dele, 19/10: "puxo meu atual e o que está de fora só vira para bater no pokemon selvagem e entra no combate; não
  // precisa tirar ele e colocar de novo, se não vai ser 2 animações"): na batalha, o R no Pokémon da setinha que JÁ ESTÁ
  // FORA (um guarda) faz dele o lutador ali mesmo. O lutador de antes volta para a bola (a única animação), e quem entra
  // espera a troca, como no R da bola. Com o lugar vago, ele só entra. O golpe que ele carregava de guarda é largado, sem
  // recarga: ele vem para o seu lado e vira para o selvagem (ia.Parceiro)
  public static void entrarQuemEstaFora(ServerPlayer jogador, int vaga) {
    if (vaga < 0 || vaga >= TAMANHO_DO_TIME) {
      return;
    }
    Pokemon novo = Cobblemon.INSTANCE.getStorage().getParty(jogador).get(vaga);
    PokemonEntity entidade = novo == null ? null : novo.getEntity();
    if (entidade == null || !entidade.isAlive() || novo.isFainted()) {
      return;
    }
    // A batalha acabou agora há pouco e o seu jogo ainda não sabia (o painel da batalha chega a cada 5 ticks): o R de
    // sempre do Cobblemon, que recolhe
    if (!GerenciarCombates.estaEmCombate(jogador)) {
      if (entidade.getBeamMode() == SEM_FEIXE) {
        entidade.recallWithAnimation();
      }
      return;
    }
    // Com uma bola no ar (alguém do time saindo ou voltando, 1,5 s), esta troca se misturaria com a outra: o que voltava
    // viraria o lutador, ou quem chega da bola recolheria quem acabou de entrar
    if (TROCANDO.contains(jogador.getUUID()) || bolaNoAr(jogador) || entidade.isBusy()) {
      Feedback.barraDeAcao(jogador, Component.literal("⏳ Espere a troca terminar"));
      return;
    }
    Pokemon antigo = de(jogador);
    if (antigo != null && antigo.getUuid().equals(novo.getUuid())) {
      return;
    }
    AgendaDeGolpes.largar(novo);
    boolean trocou = antigo != null;
    definir(jogador, novo, trocou || LUGAR_VAGO.get(jogador.getUUID()) == Vago.POR_RECOLHER);
    if (trocou) {
      Feedback.historicoDoLutador(jogador, antigo,
          Component.translatable("cobblemon.battle.withdraw.self", antigo.getDisplayName(false)));
      antigo.getEntity().recallWithAnimation();
    }
    Feedback.historicoDoLutador(jogador, novo,
        Component.translatable("cobblemon.battle.switch.self", novo.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("LUTADOR: {} entrou na luta sem passar pela bola (R com a setinha nele){}",
        novo.getSpecies().getName(), trocou ? "; " + antigo.getSpecies().getName() + " voltou para a bola" : "");
  }

  // Alguém do time saindo da bola ou voltando para ela (o feixe da bola do Cobblemon)
  private static boolean bolaNoAr(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      PokemonEntity entidade = pokemon.getEntity();
      if (entidade != null && entidade.getBeamMode() != SEM_FEIXE) {
        return true;
      }
    }
    return false;
  }

  // Voltou para a bola ou desmaiou: se era o lutador, o lugar fica vago (a volta da troca não passa daqui: quando ela
  // chega, o lutador já é o novo)
  public static void saiu(Pokemon pokemon) {
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (dono == null || !pokemon.getUuid().equals(LUTADOR_DO_JOGADOR.get(dono.getUUID()))) {
      return;
    }
    LUTADOR_DO_JOGADOR.remove(dono.getUUID());
    LUGAR_VAGO.put(dono.getUUID(), pokemon.isFainted() ? Vago.POR_DESMAIO : Vago.POR_RECOLHER);
    avisar(dono, null);
    if (!pokemon.isFainted()) {
      Feedback.historicoDoLutador(dono, pokemon,
          Component.translatable("cobblemon.battle.withdraw.self", pokemon.getDisplayName(false)));
    }
    CobblemonCombatZA.LOGGER.info("LUTADOR: {} saiu ({}); o lugar está vago", pokemon.getSpecies().getName(),
        pokemon.isFainted() ? "desmaiou" : "voltou para a bola");
  }

  // 7.2b (dele: "qualquer um seu fora"): o seu Pokémon fora mais perto do selvagem, até o alcance; null = nenhum fora
  public static Pokemon oMaisPertoDe(ServerPlayer jogador, Entity selvagem, double alcance) {
    Pokemon maisPerto = null;
    double menorDistancia = alcance * alcance;
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      PokemonEntity entidade = pokemon.getEntity();
      if (entidade == null || !entidade.isAlive() || pokemon.isFainted() || entidade.level() != selvagem.level()) {
        continue;
      }
      double distancia = entidade.distanceToSqr(selvagem);
      if (distancia <= menorDistancia) {
        menorDistancia = distancia;
        maisPerto = pokemon;
      }
    }
    return maisPerto;
  }

  // 7.2b: o selvagem foi para cima dele sem haver lutador: ele vira o lutador. No meio de uma troca, não: quem chega é o
  // novo. Vago porque o jogador recolheu o lutador: espera como na troca, igual a quem ele comandar
  public static void atacadoPeloSelvagem(ServerPlayer jogador, Pokemon pokemon) {
    if (de(jogador) == null && !TROCANDO.contains(jogador.getUUID()) && pokemon.getEntity() != null
        && !pokemon.isFainted()) {
      definir(jogador, pokemon, LUGAR_VAGO.get(jogador.getUUID()) == Vago.POR_RECOLHER);
    }
  }

  // ControleDeGolpes.usarGolpe: em batalha, o golpe é sempre do lutador. Com o lugar vago, quem o jogador comandar (o
  // da setinha, fora da bola) vira o lutador
  public static Pokemon paraOGolpe(ServerPlayer jogador, Pokemon pedido) {
    if (!GerenciarCombates.estaEmCombate(jogador)) {
      return pedido;
    }
    Pokemon lutador = de(jogador);
    if (lutador != null) {
      return lutador;
    }
    // Desmaiado ainda tem corpo durante a animação do desmaio: não vira lutador (visto no log de 19/10)
    if (pedido != null && pedido.getEntity() != null && !pedido.isFainted()) {
      definir(jogador, pedido, LUGAR_VAGO.get(jogador.getUUID()) == Vago.POR_RECOLHER);
    }
    return pedido;
  }

  // O combate acabou para o jogador: sem lutador, a HUD volta a seguir a setinha
  public static void esquecer(ServerPlayer jogador) {
    boolean tinhaLutador = LUTADOR_DO_JOGADOR.remove(jogador.getUUID()) != null;
    boolean tinhaLugarVago = LUGAR_VAGO.remove(jogador.getUUID()) != null;
    if (tinhaLutador || tinhaLugarVago) {
      avisar(jogador, null);
    }
  }

  public static void limpar() {
    LUTADOR_DO_JOGADOR.clear();
    LUGAR_VAGO.clear();
    TROCANDO.clear();
    PODE_SAIR.clear();
  }

  private static void definir(ServerPlayer jogador, Pokemon pokemon, boolean esperar) {
    LUTADOR_DO_JOGADOR.put(jogador.getUUID(), pokemon.getUuid());
    LUGAR_VAGO.remove(jogador.getUUID());
    if (esperar) {
      Recargas.esperarAoEntrar(pokemon, jogador.server.getTickCount());
    }
    avisar(jogador, pokemon.getUuid());
    CobblemonCombatZA.LOGGER.info("LUTADOR: {} luta por {}{}", pokemon.getSpecies().getName(),
        jogador.getName().getString(),
        esperar ? " (entrou trocando: " + Balanceamento.SEGUNDOS_DA_TROCA + " s sem atacar)" : "");
  }

  // A HUD do dono mostra o losango e o painel grande do lutador (LutadorPayload); vazio = sem lutador
  private static void avisar(ServerPlayer jogador, UUID lutador) {
    if (ServerPlayNetworking.canSend(jogador, LutadorPayload.TIPO)) {
      ServerPlayNetworking.send(jogador, new LutadorPayload(Optional.ofNullable(lutador)));
    }
  }
}
