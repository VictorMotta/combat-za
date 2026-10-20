package dev.victor.combatza.ia;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.serialization.Codec;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.golpes.AgendaDeGolpes;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Furia;
import dev.victor.combatza.golpes.Jatos;
import dev.victor.combatza.golpes.Preso;
import dev.victor.combatza.golpes.SonoECongelado;
import dev.victor.combatza.rede.SetinhaPayload;
import dev.victor.combatza.rede.TodosDeGuardaPayload;
import dev.victor.combatza.rede.TrocarOLadoPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

// 7.7 (dele, 19/10): "o pokemon que eu selecionar que está do lado de fora sempre anda perto de mim, na minha frente do
// lado direito (podendo mudar o lado dele); ele vai lá na frente no comando e, se não tiver um comando em alguns segundos,
// volta para a posição". O PARCEIRO é quem as suas teclas comandam: na batalha, o lutador; fora dela, o Pokémon da
// setinha, se ele está fora da bola. Escolhas dele: fora da batalha, o parceiro não é guarda (só ataca no seu comando; os
// outros que estão fora te defendem, Guardas); o lado se troca com uma tecla SEM tecla padrão (Controles → "Trocar o lado
// do Pokémon"). A posição é a decidida na 4.3: > "na frente à direita, meio que inclinado ao personagem"
public class Parceiro {
  private static final String DIREITA = "direita";
  private static final String ESQUERDA = "esquerda";

  // O lado fica guardado no seu jogador, como o modo dos guardas (um anexo do Fabric: vai no save e volta depois de morrer)
  public static final AttachmentType<String> LADO = AttachmentRegistry.create(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "lado_do_parceiro"),
      construtor -> construtor.persistent(Codec.STRING).initializer(() -> DIREITA).copyOnDeath());

  // 7.9 (dele, 20/10: "uma opção de não ter pokemon selecionado e todos ficarem de guarda, aí some os comandos da tela";
  // "só vai dar para usar fora de combate"): TODOS DE GUARDA. Ligado, fora da batalha ninguém é o parceiro: todos os que
  // estão fora são guardas, e o seu jogo esconde o losango dos golpes e o painel grande (por isso o anexo vai para o seu
  // jogo, como o modo dos guardas). A setinha continua andando entre os Pokémon. Desliga: o mesmo botão (um toque no U), ou
  // uma batalha que começa (desligarNaBatalha). Só existe ligado: desligado, o anexo sai do jogador
  public static final AttachmentType<Boolean> TODOS_DE_GUARDA = AttachmentRegistry.create(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "todos_de_guarda"),
      construtor -> construtor.persistent(Codec.BOOL).copyOnDeath()
          .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

  // O lugar, contado da casca dele e da sua (o Pokémon grande fica mais longe): uns 2 blocos à frente e 1,5 para o lado
  private static final double FRENTE = 1.4;
  private static final double PARA_O_LADO = 0.9;
  // 8.6 (dele, 20/10: "bota o Pokémon nosso para andar um pouco mais à frente da gente, porque os grandes, como o Mega
  // Charizard, fica até difícil de enxergar"; escolheu "pelo tamanho dele"): cada bloco de altura acima de 1 leva ele 1 bloco
  // mais para a frente. O Pikachu fica como antes; o Charizard (2,65 de altura), uns 4 blocos à frente
  private static final double ALTURA_SEM_AFASTAR = 1.0;
  private static final double FRENTE_POR_ALTURA = 1.0;
  // Com você parado, ele chega a menos de 1,5 bloco do lugar e para; só volta a andar quando o lugar fica a mais de 2,5
  // (sem ficar tremendo no lugar)
  private static final double CHEGOU = 1.5;
  private static final double SAIU_DO_LUGAR = 2.5;
  // Você andando: ele mira um pouco à frente do lugar (o seu passo de 6 ticks), para não ficar parando e andando
  private static final double VOCE_ANDANDO = 0.05;
  private static final double PASSO_DE_TELEPORTE = 1.0;
  private static final int TICKS_DE_ANTECIPACAO = 6;
  // Depois de um golpe (o seu comando) ou de sair da bola, ele fica onde está por 3 s antes de voltar para o lugar
  private static final int ESPERA_DEPOIS_DO_COMANDO = 60;
  // O caminho é refeito quando o destino muda mais de 1 bloco, ou a cada meio segundo
  private static final double DESTINO_MUDOU = 1.0;
  private static final int REFAZ_O_CAMINHO = 10;
  // A velocidade: perto, andando; longe, mais rápido, para acompanhar você correndo
  private static final float PERTO = 1.2f;
  private static final float APRESSADO = 1.6f;
  private static final float CORRENDO = 2.0f;
  private static final double LONGE = 4;
  private static final double MUITO_LONGE = 10;
  // O chão do lugar: de 2 blocos acima até 4 abaixo (um degrau, um morro)
  private static final int ACIMA = 2;
  private static final int ABAIXO = 4;
  // Parado na batalha, ele vira para o selvagem aos poucos (graus por tick)
  private static final float GIRO_POR_TICK = 15f;
  // O feixe da bola do Cobblemon: 0 = nenhum; senão ele está saindo da bola ou voltando para ela (1,5 s)
  private static final int SEM_FEIXE = 0;
  private static final int TAMANHO_DO_TIME = 6;

  private static final class Estado {
    // O último tick em que ele estava ocupado (um golpe, saindo da bola...)
    private long ocupadoNoTick = -ESPERA_DEPOIS_DO_COMANDO;
    private boolean andando;
    private boolean noLugar;
    private Vec3 destino;
    private long caminhoNoTick;
    private Vec3 jogadorAntes;
    // O lugar que ele não alcança (atrás de uma parede, do outro lado de um buraco): ele fica onde chegou até o lugar mudar
    private Vec3 desistiuDe;
  }

  private static final Map<UUID, Integer> SETINHA = new HashMap<>();
  private static final Map<UUID, Estado> ESTADOS = new HashMap<>();
  // O parceiro de cada jogador no tick anterior (o log conta só quando ele muda)
  private static final Map<UUID, UUID> ULTIMO = new HashMap<>();

  private Parceiro() {
  }

  // Na inicialização: os pacotes da setinha e da tecla do lado (e, ao carregar a classe, o anexo do lado, que precisa
  // existir antes do mundo abrir)
  public static void registrar() {
    ServerPlayNetworking.registerGlobalReceiver(SetinhaPayload.TIPO,
        (pedido, contexto) -> setinha(contexto.player(), pedido.vaga()));
    ServerPlayNetworking.registerGlobalReceiver(TrocarOLadoPayload.TIPO,
        (pedido, contexto) -> trocarOLado(contexto.player()));
    ServerPlayNetworking.registerGlobalReceiver(TodosDeGuardaPayload.TIPO,
        (pedido, contexto) -> alternarTodosDeGuarda(contexto.player()));
  }

  // Quem as suas teclas comandam: na batalha, o lutador (com o lugar vago, ninguém); fora dela, o da setinha, se ele está
  // fora da bola (com todos de guarda, ninguém). null = nenhum
  public static Pokemon de(ServerPlayer jogador) {
    if (GerenciarCombates.estaEmCombate(jogador)) {
      return Lutadores.de(jogador);
    }
    if (todosDeGuarda(jogador)) {
      return null;
    }
    Integer vaga = SETINHA.get(jogador.getUUID());
    if (vaga == null) {
      return null;
    }
    Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(jogador).get(vaga);
    return pokemon != null && pokemon.getEntity() != null && !pokemon.isFainted() ? pokemon : null;
  }

  public static boolean todosDeGuarda(ServerPlayer jogador) {
    return jogador.getAttachedOrElse(TODOS_DE_GUARDA, false);
  }

  // O botão (um toque no U, RodaDosGuardas): liga e desliga, só fora da batalha
  private static void alternarTodosDeGuarda(ServerPlayer jogador) {
    if (GerenciarCombates.estaEmCombate(jogador)) {
      Feedback.barraDeAcao(jogador,
          Component.literal("🛡 Na batalha, quem luta é o lutador: \"todos de guarda\" é só fora dela"));
      return;
    }
    if (todosDeGuarda(jogador)) {
      desligar(jogador);
      Feedback.barraDeAcao(jogador, Component.literal("🛡 Todos de guarda: desligado"));
      return;
    }
    jogador.setAttached(TODOS_DE_GUARDA, true);
    Feedback.barraDeAcao(jogador, Component.literal("🛡 Todos de guarda: ninguém selecionado, todos te defendem"));
    CobblemonCombatZA.LOGGER.info("PARCEIRO: {} ligou o \"todos de guarda\"", jogador.getGameProfile().getName());
  }

  public static void desligar(ServerPlayer jogador) {
    if (jogador.removeAttached(TODOS_DE_GUARDA) != null) {
      CobblemonCombatZA.LOGGER.info("PARCEIRO: {} desligou o \"todos de guarda\"", jogador.getGameProfile().getName());
    }
  }

  // 7.9 (dele: "se um pokemon bravo te ataca, automaticamente desliga isso, e o que estiver selecionado entra para a briga;
  // se nenhum que tiver no chão estiver selecionado, ele escolhe o primeiro da lista que tiver solto no chão, na ordem do
  // Cobblemon; sem pokemon, desliga mesmo assim"): começou uma batalha com todos de guarda (GerenciarCombates). O modo
  // desliga, e quem entra na briga é o da setinha, se está fora; senão, o primeiro do time que está fora. null = o modo não
  // estava ligado, ou ninguém está fora
  public static Pokemon desligarNaBatalha(ServerPlayer jogador) {
    if (!todosDeGuarda(jogador)) {
      return null;
    }
    desligar(jogador);
    Integer vaga = SETINHA.get(jogador.getUUID());
    Pokemon daSetinha = vaga == null ? null : Cobblemon.INSTANCE.getStorage().getParty(jogador).get(vaga);
    if (podeLutar(daSetinha)) {
      return daSetinha;
    }
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (podeLutar(pokemon)) {
        return pokemon;
      }
    }
    return null;
  }

  private static boolean podeLutar(Pokemon pokemon) {
    return pokemon != null && pokemon.getEntity() != null && pokemon.getEntity().isAlive() && !pokemon.isFainted();
  }

  // Saiu da bola (o R, ou a troca): ele fica onde caiu por 3 s antes de vir para o seu lado, como depois de um comando
  public static void saiuDaBola(Pokemon pokemon, long agora) {
    ESTADOS.computeIfAbsent(pokemon.getUuid(), uuid -> new Estado()).ocupadoNoTick = agora;
  }

  public static void esquecer(ServerPlayer jogador) {
    SETINHA.remove(jogador.getUUID());
    ULTIMO.remove(jogador.getUUID());
  }

  public static void limpar() {
    SETINHA.clear();
    ESTADOS.clear();
    ULTIMO.clear();
  }

  public static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    Set<UUID> vistos = new HashSet<>();
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      Pokemon pokemon = de(jogador);
      contarNoLog(jogador, pokemon);
      PokemonEntity entidade = pokemon == null ? null : pokemon.getEntity();
      if (entidade == null || !entidade.isAlive() || entidade.level() != jogador.level() || !jogador.isAlive()
          || jogador.isSpectator()) {
        continue;
      }
      vistos.add(pokemon.getUuid());
      acompanhar(jogador, pokemon, entidade, agora);
    }
    ESTADOS.keySet().retainAll(vistos);
  }

  private static void acompanhar(ServerPlayer jogador, Pokemon pokemon, PokemonEntity entidade, long agora) {
    Estado estado = ESTADOS.computeIfAbsent(pokemon.getUuid(), uuid -> new Estado());
    Vec3 passo = estado.jogadorAntes == null ? Vec3.ZERO
        : jogador.position().subtract(estado.jogadorAntes).multiply(1, 0, 1);
    estado.jogadorAntes = jogador.position();
    if (passo.length() > PASSO_DE_TELEPORTE) {
      passo = Vec3.ZERO;
    }
    if (ocupado(pokemon, entidade)) {
      // O caminho até o lugar sai da memória: o golpe de contato corre por conta própria (Corrida), sem o cérebro do
      // Cobblemon puxando de volta para o lugar
      if (estado.andando) {
        Andar.parar(entidade);
      }
      estado.andando = false;
      estado.noLugar = false;
      estado.ocupadoNoTick = agora;
      return;
    }
    if (agora - estado.ocupadoNoTick < ESPERA_DEPOIS_DO_COMANDO || SonoECongelado.estaParado(pokemon)
        || Preso.estaPreso(pokemon)) {
      return;
    }
    Vec3 lugar = lugar(jogador, entidade);
    double distancia = entidade.position().distanceTo(lugar);
    boolean voceAndando = passo.length() > VOCE_ANDANDO;
    if (voceAndando) {
      estado.desistiuDe = null;
    } else if (distancia <= (estado.noLugar ? SAIU_DO_LUGAR : CHEGOU) || naoChegaMaisPerto(estado, entidade, lugar)) {
      ficarNoLugar(jogador, entidade, estado);
      return;
    }
    estado.noLugar = false;
    Vec3 destino = voceAndando ? lugar.add(passo.scale(TICKS_DE_ANTECIPACAO)) : lugar;
    if (!estado.andando || estado.destino == null || estado.destino.distanceTo(destino) > DESTINO_MUDOU
        || agora - estado.caminhoNoTick >= REFAZ_O_CAMINHO) {
      Andar.ate(entidade, destino, velocidade(distancia));
      estado.andando = true;
      estado.destino = destino;
      estado.caminhoNoTick = agora;
    }
  }

  // Ocupado: executando um golpe, em fúria ou jorrando (o seu comando); saindo da bola ou voltando para ela (o feixe: o
  // Cobblemon não marca como "ocupado" e ainda deixa andar); numa batalha por turnos, ocupado no Cobblemon (evoluindo), com
  // alguém montado nele, ou montado em algo
  private static boolean ocupado(Pokemon pokemon, PokemonEntity entidade) {
    return AgendaDeGolpes.estaOcupado(pokemon) || Jatos.estaJorrando(pokemon) || Furia.emFuria(pokemon)
        || entidade.getBeamMode() != SEM_FEIXE || entidade.isBattling() || entidade.isBusy() || entidade.isVehicle()
        || entidade.isPassenger();
  }

  // O lugar não tem caminho (uma parede, um buraco): o último caminho até ele acabou e ele ainda está longe. Ele fica onde
  // chegou, sem refazer o caminho a cada tick, até o lugar mudar (você andar ou virar)
  private static boolean naoChegaMaisPerto(Estado estado, PokemonEntity entidade, Vec3 lugar) {
    if (estado.desistiuDe != null && estado.desistiuDe.distanceTo(lugar) <= DESTINO_MUDOU) {
      return true;
    }
    if (estado.andando && estado.destino != null && estado.destino.distanceTo(lugar) <= DESTINO_MUDOU
        && entidade.getNavigation().isDone()) {
      estado.desistiuDe = lugar;
      return true;
    }
    estado.desistiuDe = null;
    return false;
  }

  // No lugar: ele para (e larga o passeio do Cobblemon, que o tiraria do lugar) e, na batalha, olha para o selvagem
  private static void ficarNoLugar(ServerPlayer jogador, PokemonEntity entidade, Estado estado) {
    if (!estado.noLugar || estado.andando || entidade.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
      Andar.parar(entidade);
    }
    estado.noLugar = true;
    estado.andando = false;
    PokemonEntity selvagem = selvagemMaisPerto(jogador, entidade);
    if (selvagem != null) {
      virarPara(entidade, selvagem);
    }
  }

  // Na frente e para o lado de onde o seu personagem está virado (no Better Third Person, para onde ele anda). A largura
  // dele e a sua contam: o Pokémon grande fica mais longe
  private static Vec3 lugar(ServerPlayer jogador, PokemonEntity entidade) {
    double giro = Math.toRadians(jogador.getYRot());
    Vec3 frente = new Vec3(-Math.sin(giro), 0, Math.cos(giro));
    Vec3 direita = new Vec3(-Math.cos(giro), 0, -Math.sin(giro));
    double cascas = (entidade.getBbWidth() + jogador.getBbWidth()) / 2;
    double pelaAltura = Math.max(0, entidade.getBbHeight() - ALTURA_SEM_AFASTAR) * FRENTE_POR_ALTURA;
    double lado = naEsquerda(jogador) ? -1 : 1;
    Vec3 ponto = jogador.position().add(frente.scale(FRENTE + cascas + pelaAltura))
        .add(direita.scale(lado * (PARA_O_LADO + cascas)));
    return noChao(jogador.serverLevel(), ponto, entidade);
  }

  // O chão no lugar: um bloco firme (ou água) embaixo e espaço livre para a altura dele. Sem chão (uma parede, um buraco
  // fundo), o próprio ponto: o caminho chega o mais perto que der
  private static Vec3 noChao(ServerLevel nivel, Vec3 ponto, PokemonEntity entidade) {
    int altura = Math.max(1, Mth.ceil(entidade.getBbHeight()));
    BlockPos.MutableBlockPos pos = BlockPos.containing(ponto.x, ponto.y + ACIMA, ponto.z).mutable();
    for (int i = 0; i <= ACIMA + ABAIXO; i++, pos.move(Direction.DOWN)) {
      if (firme(nivel, pos.below()) && livre(nivel, pos, altura)) {
        return new Vec3(ponto.x, pos.getY(), ponto.z);
      }
    }
    return ponto;
  }

  private static boolean livre(ServerLevel nivel, BlockPos pos, int altura) {
    for (int i = 0; i < altura; i++) {
      BlockPos acima = pos.above(i);
      if (!nivel.getBlockState(acima).getCollisionShape(nivel, acima).isEmpty()
          || !nivel.getFluidState(acima).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static boolean firme(ServerLevel nivel, BlockPos pos) {
    return !nivel.getBlockState(pos).getCollisionShape(nivel, pos).isEmpty() || !nivel.getFluidState(pos).isEmpty();
  }

  private static float velocidade(double distancia) {
    if (distancia > MUITO_LONGE) {
      return CORRENDO;
    }
    return distancia > LONGE ? APRESSADO : PERTO;
  }

  // Fora da batalha, a lista vem vazia: ele olha em volta como sempre
  private static PokemonEntity selvagemMaisPerto(ServerPlayer jogador, PokemonEntity entidade) {
    PokemonEntity maisPerto = null;
    double menorDistancia = Double.MAX_VALUE;
    for (PokemonEntity selvagem : GerenciarCombates.selvagensNoCombateDe(jogador)) {
      if (!selvagem.isAlive() || selvagem.level() != entidade.level()) {
        continue;
      }
      double distancia = selvagem.distanceToSqr(entidade);
      if (distancia < menorDistancia) {
        menorDistancia = distancia;
        maisPerto = selvagem;
      }
    }
    return maisPerto;
  }

  // O mesmo giro do golpe (VisualDosGolpes.olharParaOAlvo), só que aos poucos; e o olhar do cérebro no selvagem, para os
  // passeios de olhar do Cobblemon não virarem a cabeça para outro lado
  private static void virarPara(PokemonEntity entidade, PokemonEntity selvagem) {
    double dx = selvagem.getX() - entidade.getX();
    double dz = selvagem.getZ() - entidade.getZ();
    if (dx * dx + dz * dz < 0.01) {
      return;
    }
    float giro = Mth.approachDegrees(entidade.getYRot(), (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f,
        GIRO_POR_TICK);
    entidade.setYRot(giro);
    entidade.setYBodyRot(giro);
    entidade.setYHeadRot(giro);
    entidade.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(selvagem, true));
  }

  private static void setinha(ServerPlayer jogador, int vaga) {
    if (vaga >= 0 && vaga < TAMANHO_DO_TIME) {
      SETINHA.put(jogador.getUUID(), vaga);
    } else {
      SETINHA.remove(jogador.getUUID());
    }
  }

  private static void trocarOLado(ServerPlayer jogador) {
    boolean esquerda = !naEsquerda(jogador);
    jogador.setAttached(LADO, esquerda ? ESQUERDA : DIREITA);
    Feedback.barraDeAcao(jogador, Component.literal("⇄ O seu Pokémon anda " + comoFala(esquerda)));
    CobblemonCombatZA.LOGGER.info("PARCEIRO: {} pôs o Pokémon {}", jogador.getGameProfile().getName(),
        comoFala(esquerda));
  }

  private static boolean naEsquerda(ServerPlayer jogador) {
    return ESQUERDA.equals(jogador.getAttachedOrElse(LADO, DIREITA));
  }

  private static String comoFala(boolean esquerda) {
    return esquerda ? "à sua esquerda" : "à sua direita";
  }

  private static void contarNoLog(ServerPlayer jogador, Pokemon pokemon) {
    UUID agora = pokemon == null ? null : pokemon.getUuid();
    if (Objects.equals(agora, ULTIMO.get(jogador.getUUID()))) {
      return;
    }
    if (agora == null) {
      ULTIMO.remove(jogador.getUUID());
      return;
    }
    ULTIMO.put(jogador.getUUID(), agora);
    CobblemonCombatZA.LOGGER.info("PARCEIRO: {} é o parceiro de {} (anda {})", pokemon.getSpecies().getName(),
        jogador.getGameProfile().getName(), naEsquerda(jogador) ? "à esquerda" : "à direita");
  }
}
