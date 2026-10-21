package dev.victor.combatza.golpes;

import java.util.LinkedHashSet;
import java.util.List;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.pvp.Duelos;
import dev.victor.combatza.rede.HistoricoPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class Feedback {
  private static final double DISTANCIA_DOS_EFEITOS = 32.0;

  private Feedback() {
  }

  // Avisos da interface (recarga, carregando, ocupado): ficam na barra de ação, em cima da barra de itens
  public static void barraDeAcao(ServerPlayer jogador, Component mensagem) {
    jogador.displayClientMessage(mensagem, true);
  }

  // Um acontecimento da luta vai para o histórico da batalha (os cartões da direita, como no Z-A). "sobreQuem" é de quem
  // a frase fala: dele saem o rosto e a cor do cartão (vermelho = selvagem, azul = seu).
  // 6.4d (dele, 19/10: "esse histórico na esquerda só aparece em combate, e não qualquer habilidade que eles taquem"): o
  // histórico é da BATALHA. Fora dela, nenhum cartão; nela, só os Pokémon da luta: os selvagens dela e o seu lutador. Os
  // guardas (e os golpes deles nos monstros) ficam de fora
  public static void historico(ServerPlayer jogador, Pokemon sobreQuem, Component mensagem) {
    // 21/10 (PvP): no duelo, a frase sobre um Pokémon dele vai para os dois jogadores, uma vez só (pvp.Duelos)
    List<ServerPlayer> doDuelo = Duelos.quemVeNoHistorico(jogador, sobreQuem, mensagem);
    if (doDuelo != null) {
      doDuelo.forEach(quem -> enviarHistorico(quem, sobreQuem, mensagem));
      return;
    }
    if (daBatalha(jogador, sobreQuem)) {
      enviarHistorico(jogador, sobreQuem, mensagem);
    }
  }

  // Quando a frase fala de uma entidade: se é Pokémon, o mesmo de cima. Um monstro (ou você) não faz parte da batalha: sem
  // cartão (6.4d)
  public static void historico(ServerPlayer jogador, Entity sobreQuem, Component mensagem) {
    if (sobreQuem instanceof PokemonEntity pokemon) {
      historico(jogador, pokemon.getPokemon(), mensagem);
    }
  }

  // A troca e a saída do lutador (Lutadores): na batalha, sempre, mesmo falando do Pokémon que acabou de deixar de ser o
  // lutador ("X, volte!")
  public static void historicoDoLutador(ServerPlayer jogador, Pokemon sobreQuem, Component mensagem) {
    if (jogador != null && GerenciarCombates.estaEmCombate(jogador)) {
      enviarHistorico(jogador, sobreQuem, mensagem);
    }
  }

  // 5b.1: uma frase da batalha toda (o clima, o campo) para um jogador dela. Com "quem" (quem chamou), o cartão leva o rosto
  // dele; sem (a chuva do mundo começou), vai sem rosto
  public static void historicoDaBatalha(ServerPlayer jogador, Pokemon quem, Component mensagem) {
    if (quem != null) {
      enviarHistorico(jogador, quem, mensagem);
    } else {
      enviarHistorico(jogador, "", List.of(), false, mensagem);
    }
  }

  private static boolean daBatalha(ServerPlayer jogador, Pokemon sobreQuem) {
    if (jogador == null || !GerenciarCombates.estaEmCombate(jogador)) {
      return false;
    }
    if (sobreQuem.isWild()) {
      return GerenciarCombates.selvagensNoCombateDe(jogador).contains(sobreQuem.getEntity());
    }
    Pokemon lutador = Lutadores.de(jogador);
    return lutador != null && lutador.getUuid().equals(sobreQuem.getUuid());
  }

  // 21/10 (PvP): o cartão do Pokémon do adversário do duelo é vermelho, como o do selvagem
  private static void enviarHistorico(ServerPlayer jogador, Pokemon sobreQuem, Component mensagem) {
    enviarHistorico(jogador, sobreQuem.getSpecies().getResourceIdentifier().toString(),
        List.copyOf(sobreQuem.getAspects()), sobreQuem.isWild() || Duelos.ehDoAdversario(jogador, sobreQuem), mensagem);
  }

  // Sem o nosso mod no cliente (um servidor com alguém de fora), a frase cai na barra de ação como antes
  private static void enviarHistorico(ServerPlayer jogador, String especie, List<String> aspectos, boolean doSelvagem,
      Component mensagem) {
    if (jogador == null) {
      return;
    }
    if (ServerPlayNetworking.canSend(jogador, HistoricoPayload.TIPO)) {
      ServerPlayNetworking.send(jogador, new HistoricoPayload(mensagem, especie, aspectos, doSelvagem));
    } else {
      barraDeAcao(jogador, mensagem);
    }
  }

  public static void particulas(Entity entidade, ParticleOptions tipo, int quantidade) {
    if (entidade != null && entidade.level() instanceof ServerLevel nivel) {
      nivel.sendParticles(tipo, entidade.getX(), entidade.getY() + entidade.getBbHeight() / 2, entidade.getZ(),
          quantidade, 0.4, 0.4, 0.4, 0.05);
    }
  }

  public static void particulas(ServerLevel nivel, ParticleOptions tipo, Vec3 ponto, int quantidade) {
    nivel.sendParticles(tipo, ponto.x, ponto.y, ponto.z, quantidade, 0.4, 0.4, 0.4, 0.05);
  }

  public static BossEvent.BossBarColor corDaBarra(Move golpe) {
    return switch (golpe.getType().getName()) {
      case "Water" -> BossEvent.BossBarColor.BLUE; // Água
      case "Fire" -> BossEvent.BossBarColor.RED; // Fogo
      case "Grass" -> BossEvent.BossBarColor.GREEN; // Planta
      case "Electric" -> BossEvent.BossBarColor.YELLOW; // Elétrico
      case "Psychic", "Ghost" -> BossEvent.BossBarColor.PURPLE; // Psíquico, Fantasma
      case "Fairy" -> BossEvent.BossBarColor.PINK; // Fada
      default -> BossEvent.BossBarColor.WHITE; // o resto
    };
  }

  public static void efeitoDoCobblemon(Entity entidade, String efeito) {
    efeitoDoCobblemon(entidade, efeito, "middle");
  }

  public static void efeitoDoCobblemon(Entity entidade, String efeito, String ponto) {
    efeito(entidade, ResourceLocation.fromNamespaceAndPath("cobblemon", efeito), ponto);
  }

  public static void efeito(Entity entidade, ResourceLocation efeito, String ponto) {
    if (entidade != null && entidade.level() instanceof ServerLevel nivel) {
      SpawnSnowstormEntityParticlePacket pacote = new SpawnSnowstormEntityParticlePacket(efeito, entidade.getId(),
          List.of(ponto), null, List.of());
      pacote.sendToPlayersAround(entidade.getX(), entidade.getY(), entidade.getZ(), DISTANCIA_DOS_EFEITOS,
          nivel.dimension(), jogador -> false);
    }
  }

  // O cliente toca a PRIMEIRA animação da lista que o modelo do Pokémon tiver (por isso a ordem importa)
  public static void animacao(Entity entidade, List<String> nomes) {
    if (entidade != null && entidade.level() instanceof ServerLevel nivel) {
      PlayPosableAnimationPacket pacote = new PlayPosableAnimationPacket(entidade.getId(), new LinkedHashSet<>(nomes),
          List.of());
      pacote.sendToPlayersAround(entidade.getX(), entidade.getY(), entidade.getZ(), DISTANCIA_DOS_EFEITOS,
          nivel.dimension(), jogador -> false);
    }
  }

  public static void som(Entity entidade, ResourceLocation som) {
    if (entidade != null) {
      entidade.level().playSound(null, entidade.getX(), entidade.getY(), entidade.getZ(),
          SoundEvent.createVariableRangeEvent(som), SoundSource.NEUTRAL, 1.0f, 1.0f);
    }
  }

  public static void avisarQuemImporta(Pokemon pokemon, Component mensagem) {
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (dono != null) {
      barraDeAcao(dono, mensagem);
      return;
    }
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade == null) {
      return;
    }
    for (ServerPlayer jogador : GerenciarCombates.jogadoresNoCombateDe(entidade)) {
      barraDeAcao(jogador, mensagem);
    }
  }
}
