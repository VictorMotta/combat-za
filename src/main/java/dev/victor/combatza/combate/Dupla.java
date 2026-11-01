package dev.victor.combatza.combate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Mira;
import dev.victor.combatza.pvp.Duelo;
import dev.victor.combatza.pvp.Duelos;
import dev.victor.combatza.rede.AlternarNaDuplaPayload;
import dev.victor.combatza.rede.FocoPayload;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// A batalha em DUPLA em tempo real (decisões dele, 31/10): os treinadores do RCT em dupla e o duelo PvP em dupla. Cada jogador
// tem dois Pokémon na luta (as duas vagas ficam em Lutadores):
// - você comanda um com as teclas de sempre; o outro (o SEGUNDO) luta sozinho, com a mesma IA dos selvagens, "para não ficar
//   apelão" (IaDoSelvagem.lutarDoLado), e ataca quem está no seu foco (sem foco, o inimigo mais perto dele);
// - a tecla "Trocar o controle na dupla" (ou o R no segundo) troca os dois: as suas teclas passam para o outro;
// - quem desmaia dá lugar ao próximo do time em 2 s, sozinho (um que já está fora entra na hora, sem passar pela bola).
// Daqui saem também o foco (o seu jogo avisa quando ele muda: FocoPayload) e o alvo do segundo
public final class Dupla {
  // Escolha dele: 2 s entre um Pokémon desmaiar e o próximo entrar
  private static final int TICKS_ATE_O_PROXIMO = 2 * SharedConstants.TICKS_PER_SECOND;
  private static final int SEM_FOCO = -1;
  private static final double NA_FRENTE_DE_VOCE = 2.0;
  private static final String FRASE = "cobblemon_combat_za.dupla.";
  // A tecla "Trocar o controle na dupla" (client.TeclasDoTime)
  public static final String TECLA = "key.cobblemon_combat_za.alternar_na_dupla";

  // Em quem o foco de cada jogador está (o número da entidade)
  private static final Map<UUID, Integer> FOCO = new HashMap<>();
  // Desde quando o jogador está com uma vaga sobrando na dupla
  private static final Map<UUID, Long> VAGA_DESDE = new HashMap<>();

  private Dupla() {
  }

  public static void registrar() {
    PayloadTypeRegistry.playC2S().register(AlternarNaDuplaPayload.TIPO, AlternarNaDuplaPayload.STREAM_CODEC);
    PayloadTypeRegistry.playC2S().register(FocoPayload.TIPO, FocoPayload.STREAM_CODEC);
    ServerPlayNetworking.registerGlobalReceiver(AlternarNaDuplaPayload.TIPO,
        (pedido, contexto) -> alternar(contexto.player()));
    ServerPlayNetworking.registerGlobalReceiver(FocoPayload.TIPO,
        (pedido, contexto) -> FOCO.put(contexto.player().getUUID(), pedido.alvo()));
  }

  private static void alternar(ServerPlayer jogador) {
    if (!Lutadores.emDupla(jogador) || !GerenciarCombates.estaEmCombate(jogador)) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "so_na_dupla"));
      return;
    }
    if (!Lutadores.alternar(jogador)) {
      Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "falta_um"));
      return;
    }
    Pokemon lutador = Lutadores.de(jogador);
    Pokemon segundo = Lutadores.segundo(jogador);
    Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "trocou", lutador.getDisplayName(false),
        segundo.getDisplayName(false)));
  }

  // A batalha em dupla começou (BatalhaDeTreinador, Duelo): no chat, como funciona, com a tecla que está nos Controles dele
  // (o Component.keybind mostra a tecla do jogo de quem lê)
  public static void avisarOComeco(ServerPlayer jogador) {
    jogador.sendSystemMessage(Component.translatable(FRASE + "comecou", Component.keybind(TECLA))
        .withStyle(ChatFormatting.GOLD));
  }

  // O alvo do segundo: o do seu foco, se ele é um inimigo da batalha; senão, o inimigo mais perto dele. null = nenhum
  public static Entity alvoDoSegundo(PokemonEntity segundo, ServerPlayer dono) {
    List<PokemonEntity> inimigos = GerenciarCombates.selvagensNoCombateDe(dono);
    Integer foco = FOCO.get(dono.getUUID());
    Entity focado = foco == null || foco == SEM_FOCO ? null : dono.serverLevel().getEntity(foco);
    if (focado instanceof PokemonEntity inimigo && inimigos.contains(inimigo) && podeSerAlvo(segundo, inimigo)) {
      return inimigo;
    }
    PokemonEntity maisPerto = null;
    for (PokemonEntity inimigo : inimigos) {
      if (podeSerAlvo(segundo, inimigo)
          && (maisPerto == null || segundo.distanceToSqr(inimigo) < segundo.distanceToSqr(maisPerto))) {
        maisPerto = inimigo;
      }
    }
    return maisPerto;
  }

  private static boolean podeSerAlvo(PokemonEntity segundo, PokemonEntity inimigo) {
    return inimigo.isAlive() && !inimigo.isRemoved() && inimigo.level() == segundo.level()
        && !Captura.dentroDaBola(inimigo) && !BatalhasDeTreinador.foraDaLuta(inimigo) && Lados.ehInimigoDe(segundo, inimigo);
  }

  // A cada tick: na dupla, com uma vaga sobrando, um do time que já está fora entra na hora; senão, passados 2 s, o próximo
  // do time sai da bola sozinho
  public static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      if (!Lutadores.emDupla(jogador) || !GerenciarCombates.estaEmCombate(jogador) || !jogador.isAlive()
          || Lutadores.naLuta(jogador).size() >= 2) {
        VAGA_DESDE.remove(jogador.getUUID());
        continue;
      }
      if (entrouQuemEstaFora(jogador)) {
        VAGA_DESDE.remove(jogador.getUUID());
        continue;
      }
      long desde = VAGA_DESDE.computeIfAbsent(jogador.getUUID(), uuid -> agora);
      if (agora - desde >= TICKS_ATE_O_PROXIMO) {
        // Quem sai da bola leva 1,5 s até entrar na vaga: a contagem recomeça daqui
        VAGA_DESDE.put(jogador.getUUID(), agora);
        mandarOProximo(jogador);
      }
    }
  }

  private static boolean entrouQuemEstaFora(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      PokemonEntity entidade = pokemon.getEntity();
      if (entidade != null && podeEntrar(jogador, pokemon) && entidade.level() == jogador.level()
          && entidade.distanceTo(jogador) <= Mira.ALCANCE && Lutadores.preencherAVaga(jogador, pokemon)) {
        return true;
      }
    }
    return false;
  }

  private static void mandarOProximo(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (pokemon.getEntity() == null && podeEntrar(jogador, pokemon)) {
        CobblemonCombatZA.LOGGER.info("DUPLA: a vaga de {} ficou livre; {} entra sozinho",
            jogador.getGameProfile().getName(), pokemon.getSpecies().getName());
        Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "entrou_sozinho", pokemon.getDisplayName(false)));
        SoltarNoSelvagem.jogar(jogador, pokemon, ondeSoltar(jogador));
        return;
      }
    }
  }

  // Com vida e, no duelo, um dos escolhidos
  private static boolean podeEntrar(ServerPlayer jogador, Pokemon pokemon) {
    Duelo duelo = Duelos.dueloDe(jogador);
    return !pokemon.isFainted() && (duelo == null || duelo.doTime(pokemon));
  }

  // No caminho até o inimigo mais perto (como o R num selvagem); sem inimigo, na sua frente
  private static Vec3 ondeSoltar(ServerPlayer jogador) {
    PokemonEntity maisPerto = null;
    for (PokemonEntity inimigo : GerenciarCombates.selvagensNoCombateDe(jogador)) {
      if (inimigo.level() == jogador.level()
          && (maisPerto == null || jogador.distanceToSqr(inimigo) < jogador.distanceToSqr(maisPerto))) {
        maisPerto = inimigo;
      }
    }
    Vec3 onde = maisPerto == null ? null : SoltarNoSelvagem.onde(jogador, maisPerto);
    if (onde != null) {
      return onde;
    }
    Vec3 frente = jogador.getLookAngle().multiply(1, 0, 1);
    frente = frente.lengthSqr() > 1.0E-4 ? frente.normalize() : new Vec3(0, 0, 1);
    return Mira.noChao(jogador.serverLevel(), jogador.position().add(frente.scale(NA_FRENTE_DE_VOCE)));
  }

  public static void esquecer(ServerPlayer jogador) {
    FOCO.remove(jogador.getUUID());
    VAGA_DESDE.remove(jogador.getUUID());
  }

  public static void limpar() {
    FOCO.clear();
    VAGA_DESDE.clear();
  }
}
