package dev.victor.combatza.combate;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Mira;
import dev.victor.combatza.ia.Parceiro;
import dev.victor.combatza.rede.SoltarNoSelvagemPayload;
import kotlin.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// O R com um selvagem envolvido (o travado no G, ou o que está na mira) joga o Pokémon da setinha no meio do caminho até
// o selvagem e começa a NOSSA batalha; já em batalha, é a troca (Lutadores). Nunca a batalha por turnos do Cobblemon: o
// cliente manda o nosso pacote no lugar do pedido de batalha (RDoCobblemonMixin). Pedido dele, 18/10: "o r usa para
// iniciar a luta no cobblemon, então preciso que tire isso de vez"
public class SoltarNoSelvagem {
  private static final int TAMANHO_DO_TIME = 6;
  // No meio do caminho até o selvagem, entre 2 e 12 blocos (12 é o alcance do R do Cobblemon), e nunca em cima dele
  private static final double METADE_DO_CAMINHO = 0.5;
  private static final double SOLTAR_MAIS_PERTO = 2.0;
  private static final double SOLTAR_MAIS_LONGE = 12.0;
  private static final double FOLGA_DO_SELVAGEM = 1.5;
  private static final double ANTES_DA_PAREDE = 0.5;
  // O chão é procurado de 3 blocos acima do ponto até 5 abaixo (um morro ou um buraco no meio do caminho)
  private static final int ACIMA = 3;
  private static final int ABAIXO = 5;

  private SoltarNoSelvagem() {
  }

  public static void pediu(ServerPlayer jogador, SoltarNoSelvagemPayload pedido) {
    if (pedido.slotPokemon() < 0 || pedido.slotPokemon() >= TAMANHO_DO_TIME) {
      return;
    }
    Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(jogador).get(pedido.slotPokemon());
    Entity alvo = jogador.serverLevel().getEntity(pedido.selvagem());
    if (pokemon == null || pokemon.getEntity() != null || !(alvo instanceof PokemonEntity selvagem) || !selvagem.isAlive()
        || !selvagem.getPokemon().isWild()) {
      return;
    }
    if (pokemon.isFainted()) {
      aviso(jogador, pokemon, " está desmaiado!");
      return;
    }
    if (jogador.distanceTo(selvagem) > Mira.ALCANCE) {
      aviso(jogador, pokemon, ": o selvagem está longe demais");
      return;
    }
    Vec3 onde = onde(jogador, selvagem);
    if (onde == null) {
      aviso(jogador, pokemon, ": não tem chão para ele cair");
      return;
    }
    // Primeiro a batalha, depois a bola: quando ele sair, o Lutadores já sabe que é batalha (lutador novo, ou a troca).
    // 7.9: o "todos de guarda" desliga antes, sem escolher ninguém: quem luta é este que você jogou
    Parceiro.desligar(jogador);
    GerenciarCombates.entrarEmCombate(jogador, selvagem, null);
    jogar(jogador, pokemon, onde);
    CobblemonCombatZA.LOGGER.info("R num selvagem: {} jogado contra {}", pokemon.getSpecies().getName(),
        selvagem.getPokemon().getSpecies().getName());
  }

  // Na linha entre você e o selvagem, no meio do caminho (e não para onde o corpo está virado, que no Better Third Person
  // pode ser outro lado). Com uma parede no meio, cai antes dela. Lá, o chão: um bloco firme (ou água) embaixo e dois
  // blocos livres para o Pokémon. null = não tem onde cair
  public static Vec3 onde(ServerPlayer jogador, PokemonEntity selvagem) {
    ServerLevel nivel = jogador.serverLevel();
    Vec3 caminho = selvagem.position().subtract(jogador.position());
    double total = caminho.length();
    if (total < 0.01) {
      return null;
    }
    double distancia = Math.min(Math.clamp(total * METADE_DO_CAMINHO, SOLTAR_MAIS_PERTO, SOLTAR_MAIS_LONGE),
        Math.max(0, total - FOLGA_DO_SELVAGEM));
    Vec3 ponto = jogador.position().add(caminho.scale(distancia / total));
    BlockHitResult parede = nivel.clip(new ClipContext(jogador.getEyePosition(), ponto.add(0, 1, 0),
        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, jogador));
    if (parede.getType() == HitResult.Type.BLOCK) {
      ponto = parede.getLocation().subtract(caminho.normalize().scale(ANTES_DA_PAREDE));
    }
    BlockPos.MutableBlockPos pos = BlockPos.containing(ponto.x, ponto.y + ACIMA, ponto.z).mutable();
    for (int i = 0; i <= ACIMA + ABAIXO; i++, pos.move(Direction.DOWN)) {
      if (livre(nivel, pos) && livre(nivel, pos.above()) && firme(nivel, pos.below())) {
        return Vec3.atBottomCenterOf(pos);
      }
    }
    return null;
  }

  // A mesma bola do R do Cobblemon (sendOutWithAnimation: o arremesso, o grito e o "saiu da bola")
  public static void jogar(ServerPlayer jogador, Pokemon pokemon, Vec3 onde) {
    pokemon.sendOutWithAnimation(jogador, jogador.serverLevel(), onde, null, true, null, entidade -> Unit.INSTANCE);
  }

  private static boolean livre(ServerLevel nivel, BlockPos pos) {
    return nivel.getBlockState(pos).getCollisionShape(nivel, pos).isEmpty() && nivel.getFluidState(pos).isEmpty();
  }

  private static boolean firme(ServerLevel nivel, BlockPos pos) {
    return !nivel.getBlockState(pos).getCollisionShape(nivel, pos).isEmpty() || !nivel.getFluidState(pos).isEmpty();
  }

  private static void aviso(ServerPlayer jogador, Pokemon pokemon, String motivo) {
    Feedback.barraDeAcao(jogador, Component.literal("🚫 ").append(pokemon.getDisplayName(false)).append(motivo));
  }
}
