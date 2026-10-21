package dev.victor.combatza.mega;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonFaintedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.ia.Parceiro;
import dev.victor.combatza.rede.MedidorDaMegaPayload;
import dev.victor.combatza.rede.MegaPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// 8.4 (dele, 20/10: "a mega-evolução precisa funcionar nas nossas batalhas, igual é no Pokémon Z-A"), com as regras que ele
// ajustou depois de testar (8.4b: "ele não tem que destransformar depois que acaba a batalha, ele continua"; "se ele tá
// segurando a pedra, até fora de batalha posso transformar ele, aí ele fica ilimitado transformado, entrou na batalha começa
// a contar"; e a trava: "se eu transformo ele pela rodinha do Cobblemon, o tempo não tá contando"):
// - FORA da batalha: a tecla transforma (e destransforma) o selecionado que está fora, sem limite de tempo. A roda do
//   Cobblemon (do Mega Showdown) também continua valendo;
// - NA batalha: o medidor do Z-A, que começa cheio. Todo lutador Mega gasta ele, venha a Mega da tecla, da roda ou de antes
//   da batalha: 1 minuto, esvaziando como um relógio; recolher pausa; no fim, ele volta ao normal. Depois, enche de novo
//   (sozinho e com os acertos: "os dois"). Com o medidor vazio, o lutador não fica Mega;
// - a batalha acabou: quem está Mega continua Mega;
// - desmaiou: volta ao normal (8.4c, dele: "se o Pokémon desmaiar com Mega, ele sai do Mega, não fica direto"), na batalha ou
//   fora dela. Na batalha, o minuto dele acaba ali;
// - 8.4e (dele: "fora de batalha tem que aparecer também o negócio de Mega Evolução, quando coloca o Pokémon para fora, senão
//   não tem nenhum indicador que dá para mega evoluir"): fora da batalha, o medidor aparece cheio quando o selecionado que
//   está fora pode megar (com a tecla embaixo), e aceso quando ele já está Mega (sem relógio: fora da batalha não tem limite).
// Quem faz a Mega de verdade (a animação, a forma, os atributos) é o Mega Showdown, pela PonteDoMegaShowdown. A forma Mega
// troca o Ataque, a Defesa, o tipo e a habilidade no próprio Cobblemon, e o nosso dano (Dano) já lê tudo de lá
public class Mega {
  // Por que a Mega não sai (a PonteDoMegaShowdown responde)
  public enum Motivo {
    PODE, JA_ESTA_MEGA, JA_TEM_UM_MEGA, SEM_BRACELETE, SEM_PEDRA, PEDRA_DE_OUTRO
  }

  private static final String MOD_DA_MEGA = "mega_showdown";
  // A marca que o Mega Showdown põe no Pokémon enquanto a animação toca (uns 6 segundos parado, sem IA)
  private static final String TRANSFORMANDO = "form_changing";
  private static final int TICKS_POR_SEGUNDO = 20;
  private static final int DURACAO = 60 * TICKS_POR_SEGUNDO;
  // Escolhas minhas (vetáveis): sozinho, o medidor enche em 2 minutos; cada golpe do lutador que acerta adianta 10%
  private static final int ENCHER_SO_COM_O_TEMPO = 120 * TICKS_POR_SEGUNDO;
  private static final float POR_ACERTO = 0.10f;
  // O medidor vai para o jogo de 5 em 5 ticks (4 vezes por segundo); o desenho anda suave entre um e outro
  private static final int ENVIAR_A_CADA = 5;
  private static final String FRASE = "cobblemon_combat_za.mega.";

  // O medidor de um jogador na batalha. "megando" = o minuto está valendo; "megado" = o Pokémon que está gastando ele
  private static final class Medidor {
    private float carga = 1f;
    private boolean megando;
    private int falta;
    private UUID megado;
  }

  private static final Map<UUID, Medidor> MEDIDORES = new HashMap<>();
  // O último medidor que foi para o jogo de cada jogador, na batalha ou fora dela (só vai de novo se mudou)
  private static final Map<UUID, MedidorDaMegaPayload> ENVIADOS = new HashMap<>();

  private static boolean ponteQuebrada = false;

  private Mega() {
  }

  public static void registrar() {
    ServerPlayNetworking.registerGlobalReceiver(MegaPayload.TIPO, (pacote, contexto) -> pediu(contexto.player()));
    // Durante a animação (uns 6 segundos parado), o seu Pokémon não leva dano na batalha. Na batalha por turnos do
    // Cobblemon, o Mega Showdown até pausa a batalha (8,5 segundos)
    ServerLivingEntityEvents.ALLOW_DAMAGE.register((entidade, fonte, dano) -> !(entidade instanceof PokemonEntity corpo
        && transformandoNaBatalha(corpo.getPokemon())));
    CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.NORMAL, Mega::aoDesmaiar);
    ServerPlayConnectionEvents.DISCONNECT.register((conexao, servidor) -> {
      MEDIDORES.remove(conexao.getPlayer().getUUID());
      ENVIADOS.remove(conexao.getPlayer().getUUID());
    });
  }

  // O aviso de desmaio do Cobblemon sai sempre que a vida chega a zero (Pokemon.setCurrentHealth), na nossa batalha também
  private static void aoDesmaiar(PokemonFaintedEvent evento) {
    Pokemon pokemon = evento.getPokemon();
    try {
      if (instalado() && PonteDoMegaShowdown.ehMega(pokemon)) {
        PonteDoMegaShowdown.desmegar(pokemon);
        CobblemonCombatZA.LOGGER.info("MEGA: {} desmaiou e voltou ao normal", pokemon.getSpecies().getName());
      }
    } catch (LinkageError erro) {
      quebrou(erro);
    }
  }

  // O Mega Showdown está no jogo (e a ponte com ele funciona)? Sem ele, a tecla só avisa
  private static boolean instalado() {
    return !ponteQuebrada && FabricLoader.getInstance().isModLoaded(MOD_DA_MEGA);
  }

  // Se o Mega Showdown mudar por dentro numa versão nova (um método que a PonteDoMegaShowdown usa sumiu), a Mega se desliga com
  // um aviso no log, em vez de fechar o servidor. Tudo o que chama a ponte passa pelo pedido da tecla, pelo atualizar e pelo
  // aviso de desmaio: os três param aqui
  private static void quebrou(LinkageError erro) {
    if (!ponteQuebrada) {
      ponteQuebrada = true;
      CobblemonCombatZA.LOGGER.error("MEGA: o Mega Showdown mudou e a ponte com ele não funciona mais; a Mega fica desligada", erro);
    }
  }

  // Na animação da Mega o Pokémon fica parado: os golpes dele esperam (ControleDeGolpes)
  public static boolean transformando(Pokemon pokemon) {
    return pokemon.getPersistentData().getBoolean(TRANSFORMANDO);
  }

  private static boolean transformandoNaBatalha(Pokemon pokemon) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    return dono != null && transformando(pokemon) && GerenciarCombates.estaEmCombate(dono);
  }

  // A tecla da Mega: fora da batalha, transforma e destransforma o selecionado que está fora; na batalha, mega o lutador se
  // o medidor está cheio (quem conta o minuto é o atualizar, depois da animação)
  private static void pediu(ServerPlayer jogador) {
    try {
      pedido(jogador);
    } catch (LinkageError erro) {
      quebrou(erro);
      avisar(jogador, Component.translatable(FRASE + "sem_mod"));
    }
  }

  private static void pedido(ServerPlayer jogador) {
    if (!instalado()) {
      avisar(jogador, Component.translatable(FRASE + "sem_mod"));
      return;
    }
    boolean emBatalha = GerenciarCombates.estaEmCombate(jogador);
    Pokemon alvo = Parceiro.de(jogador);
    if (alvo == null || alvo.getEntity() == null) {
      avisar(jogador, Component.translatable(FRASE + (emBatalha ? "sem_lutador" : "sem_selecionado")));
      return;
    }
    if (transformando(alvo)) {
      return;
    }
    if (!emBatalha) {
      foraDaBatalha(jogador, alvo);
      return;
    }
    Medidor medidor = MEDIDORES.computeIfAbsent(jogador.getUUID(), uuid -> new Medidor());
    if (PonteDoMegaShowdown.ehMega(alvo)) {
      avisar(jogador, Component.translatable(FRASE + "ja_esta", alvo.getDisplayName(false),
          (medidor.falta + TICKS_POR_SEGUNDO - 1) / TICKS_POR_SEGUNDO));
      return;
    }
    if (medidor.megando) {
      avisar(jogador, Component.translatable(FRASE + "em_uso"));
      return;
    }
    if (medidor.carga < 1f) {
      avisar(jogador, Component.translatable(FRASE + "nao_encheu", Math.round(medidor.carga * 100)));
      return;
    }
    Motivo motivo = PonteDoMegaShowdown.podeMegar(jogador, alvo);
    if (motivo != Motivo.PODE) {
      avisar(jogador, porque(motivo, alvo));
      return;
    }
    PonteDoMegaShowdown.megar(jogador, alvo);
  }

  // Fora da batalha não há medidor nem relógio: Mega à vontade (como a roda do Cobblemon)
  private static void foraDaBatalha(ServerPlayer jogador, Pokemon alvo) {
    if (PonteDoMegaShowdown.ehMega(alvo)) {
      PonteDoMegaShowdown.desmegar(alvo);
      avisar(jogador, Component.translatable(FRASE + "voltou", alvo.getDisplayName(false)));
      return;
    }
    Motivo motivo = PonteDoMegaShowdown.podeMegar(jogador, alvo);
    if (motivo == Motivo.PODE) {
      PonteDoMegaShowdown.megar(jogador, alvo);
    } else {
      avisar(jogador, porque(motivo, alvo));
    }
  }

  private static Component porque(Motivo motivo, Pokemon alvo) {
    return switch (motivo) {
      case JA_ESTA_MEGA -> Component.translatable(FRASE + "ja_mega", alvo.getDisplayName(false));
      case JA_TEM_UM_MEGA -> Component.translatable(FRASE + "ja_tem_um");
      case SEM_BRACELETE -> Component.translatable(FRASE + "sem_bracelete");
      case SEM_PEDRA -> Component.translatable(FRASE + "sem_pedra", alvo.getDisplayName(false));
      case PEDRA_DE_OUTRO, PODE -> Component.translatable(FRASE + "pedra_de_outro", alvo.getDisplayName(false));
    };
  }

  // A cada tick: o medidor de quem está numa batalha anda (enche, ou esvazia com o Mega lutando). De 5 em 5 ticks, o
  // medidor vai para o jogo de cada jogador: o da batalha, ou o de fora dela (8.4e)
  public static void atualizar(MinecraftServer servidor) {
    try {
      atualizarComOMod(servidor);
    } catch (LinkageError erro) {
      quebrou(erro);
    }
  }

  private static void atualizarComOMod(MinecraftServer servidor) {
    if (!instalado()) {
      return;
    }
    for (Combate combate : GerenciarCombates.todos()) {
      for (ServerPlayer jogador : combate.getJogadores()) {
        andar(jogador, MEDIDORES.computeIfAbsent(jogador.getUUID(), uuid -> new Medidor()));
      }
    }
    if (servidor.getTickCount() % ENVIAR_A_CADA != 0) {
      return;
    }
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      Medidor medidor = GerenciarCombates.estaEmCombate(jogador) ? MEDIDORES.get(jogador.getUUID()) : null;
      enviar(jogador, medidor != null ? naBatalha(jogador, medidor) : foraDaBatalha(jogador));
    }
  }

  // A trava (dele: "pela rodinha o tempo não tá contando"): na batalha, qualquer lutador Mega gasta o medidor, venha a Mega de
  // onde vier. A animação não conta (nem enche, nem esvazia)
  private static void andar(ServerPlayer jogador, Medidor medidor) {
    Pokemon lutador = Lutadores.de(jogador);
    if (lutador != null && transformando(lutador)) {
      return;
    }
    boolean megaLutando = lutador != null && PonteDoMegaShowdown.ehMega(lutador);
    if (!medidor.megando) {
      if (!megaLutando) {
        medidor.carga = Math.min(1f, medidor.carga + 1f / ENCHER_SO_COM_O_TEMPO);
      } else if (medidor.carga >= 1f) {
        comecarOMinuto(jogador, medidor, lutador);
      } else {
        // Mega com o medidor vazio (pela roda, ou entrou Mega depois de gastar o minuto): volta ao normal
        PonteDoMegaShowdown.desmegar(lutador);
        avisar(jogador, Component.translatable(FRASE + "vazio", lutador.getDisplayName(false)));
      }
      return;
    }
    if (megaLutando) {
      // Trocou para outro Mega no meio do minuto: o relógio continua, agora com ele
      medidor.megado = lutador.getUuid();
      medidor.falta--;
      medidor.carga = (float) medidor.falta / DURACAO;
      if (medidor.falta <= 0) {
        PonteDoMegaShowdown.desmegar(lutador);
        Feedback.historico(jogador, lutador, Component.translatable(FRASE + "acabou", lutador.getDisplayName(false)));
        esvaziar(medidor);
      }
      return;
    }
    // O Mega não está lutando: na bola, o relógio para. Se ele já não é Mega (voltou ao normal pela roda), o minuto acabou
    Pokemon megado = medidor.megado == null ? null : doTime(jogador, medidor.megado);
    if (megado == null || !transformando(megado) && !PonteDoMegaShowdown.ehMega(megado)) {
      esvaziar(medidor);
    }
  }

  private static void comecarOMinuto(ServerPlayer jogador, Medidor medidor, Pokemon lutador) {
    medidor.megando = true;
    medidor.megado = lutador.getUuid();
    medidor.falta = DURACAO;
    medidor.carga = 1f;
    Feedback.historico(jogador, lutador, Component.translatable(FRASE + "valendo", lutador.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("MEGA: {} de {} está Mega na batalha (1 minuto)", lutador.getSpecies().getName(),
        jogador.getName().getString());
  }

  private static void esvaziar(Medidor medidor) {
    medidor.megando = false;
    medidor.megado = null;
    medidor.falta = 0;
    medidor.carga = 0f;
  }

  // Cada golpe do lutador que acerta um selvagem adianta o medidor (ControleDeGolpes.acertar); com o minuto valendo, não
  public static void acertou(ServerPlayer jogador) {
    Medidor medidor = MEDIDORES.get(jogador.getUUID());
    if (medidor != null && !medidor.megando) {
      medidor.carga = Math.min(1f, medidor.carga + POR_ACERTO);
    }
  }

  // O jogador saiu da batalha (venceu, fugiu, desmaiou, saiu do jogo): o medidor da batalha some, e quem está Mega continua
  // Mega (fora da batalha, sem limite). Logo depois vale o medidor de fora. A próxima batalha começa com ele cheio
  public static void fimDaBatalha(ServerPlayer jogador) {
    MEDIDORES.remove(jogador.getUUID());
  }

  // Na batalha: só aparece com o minuto valendo, ou com um lutador que pode megar (o bracelete e a pedra dele: 8.4f, dele:
  // "só aparece essa parte da Mega Evolução para Pokémon que estão segurando a pedra; estava lutando com um Pikachu que nem
  // pedra tinha"); a tecla aparece quando o medidor está cheio
  private static MedidorDaMegaPayload naBatalha(ServerPlayer jogador, Medidor medidor) {
    Pokemon lutador = Lutadores.de(jogador);
    boolean megaLutando = lutador != null && PonteDoMegaShowdown.ehMega(lutador);
    boolean lutadorPodeMegar = lutador != null && PonteDoMegaShowdown.podeMegar(jogador, lutador) == Motivo.PODE;
    boolean visivel = medidor.megando || lutadorPodeMegar;
    boolean pausado = medidor.megando && !megaLutando;
    boolean pronto = !medidor.megando && medidor.carga >= 1f && lutadorPodeMegar;
    return visivel ? new MedidorDaMegaPayload(true, medidor.carga, medidor.megando, pausado, pronto)
        : MedidorDaMegaPayload.ESCONDIDO;
  }

  // 8.4e: fora da batalha, o selecionado que está fora: Mega = aceso (cheio, sem relógio); pode megar = cheio, com a tecla
  private static MedidorDaMegaPayload foraDaBatalha(ServerPlayer jogador) {
    Pokemon selecionado = Parceiro.de(jogador);
    if (selecionado == null || selecionado.getEntity() == null) {
      return MedidorDaMegaPayload.ESCONDIDO;
    }
    if (PonteDoMegaShowdown.ehMega(selecionado)) {
      return new MedidorDaMegaPayload(true, 1f, true, false, false);
    }
    boolean pronto = !transformando(selecionado)
        && PonteDoMegaShowdown.podeMegar(jogador, selecionado) == Motivo.PODE;
    return pronto ? new MedidorDaMegaPayload(true, 1f, false, false, true) : MedidorDaMegaPayload.ESCONDIDO;
  }

  private static void enviar(ServerPlayer jogador, MedidorDaMegaPayload agora) {
    if (!ServerPlayNetworking.canSend(jogador, MedidorDaMegaPayload.TIPO)
        || agora.equals(ENVIADOS.get(jogador.getUUID()))) {
      return;
    }
    ServerPlayNetworking.send(jogador, agora);
    ENVIADOS.put(jogador.getUUID(), agora);
  }

  private static Pokemon doTime(ServerPlayer jogador, UUID pokemon) {
    return Cobblemon.INSTANCE.getStorage().getParty(jogador).get(pokemon);
  }

  private static void avisar(ServerPlayer jogador, Component mensagem) {
    Feedback.barraDeAcao(jogador, mensagem);
  }

  public static void limpar() {
    MEDIDORES.clear();
    ENVIADOS.clear();
  }
}
