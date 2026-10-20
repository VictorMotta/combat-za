package dev.victor.combatza.captura;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Captura;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.Experiencia;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Mira;
import dev.victor.combatza.golpes.Vida;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

// Etapa 8, o desmaio no chão (especificado por ele na aula 2.5a, 10/10: "o diferencial de tudo"; feito em 20/10): o selvagem
// que desmaia na NOSSA batalha não some. Ele fica caído no chão (a pose de dormir do Cobblemon, com os passarinhos em volta),
// parado e sem levar dano, por 10 s, e você tem UMA tentativa de captura com a Pokébola. A bola falhou, ou o tempo acabou: ele
// some. O XP e os itens que ele solta saem na hora em que ele cai (como se tivesse morrido); capturado depois, não dá XP de novo.
// Como: o ALLOW_DEATH da Fabric deixa cancelar a morte (o corpo fica com 1 PV); só na nossa batalha (fora dela, no vazio ou
// pelo /kill, morre como sempre)
public class Desmaio {
  private static final int SEGUNDOS = 10;
  private static final int A_CADA_PASSARINHOS = 30;
  private static final int NUVENS_AO_SUMIR = 20;
  private static final ResourceLocation PASSARINHOS = ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID,
      "passarinhos_da_confusao");

  private static final class Caido {
    private final PokemonEntity entidade;
    private final long ate;
    private boolean tentou;

    private Caido(PokemonEntity entidade, long ate) {
      this.entidade = entidade;
      this.ate = ate;
    }
  }

  // Pelo UUID da entidade
  private static final Map<UUID, Caido> CAIDOS = new HashMap<>();
  // Os Pokémon (UUID do Pokémon) que já deram o XP de derrotado: capturados depois, não dão de novo
  private static final Set<UUID> JA_DERAM_XP = new HashSet<>();

  private Desmaio() {
  }

  public static void registrar() {
    ServerLivingEntityEvents.ALLOW_DEATH.register((entidade, fonte, dano) ->
        !(entidade instanceof PokemonEntity selvagem) || !cair(selvagem, fonte));
    // Caído, não leva dano (só o do vazio e o do /kill)
    ServerLivingEntityEvents.ALLOW_DAMAGE.register((entidade, fonte, dano) ->
        !(entidade instanceof PokemonEntity pokemon && esta(pokemon)) || fonte.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
  }

  private static boolean cair(PokemonEntity selvagem, DamageSource fonte) {
    Pokemon pokemon = selvagem.getPokemon();
    Combate combate = GerenciarCombates.combateDe(pokemon);
    if (!pokemon.isWild() || combate == null || esta(selvagem) || fonte.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
        || !(selvagem.level() instanceof ServerLevel nivel)) {
      return false;
    }
    long agora = nivel.getServer().getTickCount();
    selvagem.setHealth(Vida.naVida(selvagem, 1));
    pokemon.setCurrentHealth(1);
    CAIDOS.put(selvagem.getUUID(), new Caido(selvagem, agora + SEGUNDOS * (long) SharedConstants.TICKS_PER_SECOND));
    JA_DERAM_XP.add(pokemon.getUuid());
    // Antes de sair da batalha: o aviso (ele ainda está nela, o cartão aparece) e o XP de quem lutou
    for (ServerPlayer jogador : combate.getJogadores()) {
      Feedback.historico(jogador, pokemon, Component.translatable("cobblemon_combat_za.captura.desmaiou",
          pokemon.getDisplayName(false), SEGUNDOS));
    }
    Experiencia.derrotado(nivel.getServer(), pokemon);
    selvagem.getDelegate().drop(fonte);
    deitar(selvagem, nivel);
    Estagios.zerar(pokemon);
    Confusao.esquecer(pokemon);
    Irritados.esquecer(pokemon);
    CobblemonCombatZA.LOGGER.info("DESMAIO: {} caiu no chão; uma tentativa de captura em {} s",
        pokemon.getSpecies().getName(), SEGUNDOS);
    return true;
  }

  // Sem IA (não anda nem ataca), no chão (quem voava desce) e na pose de dormir do Cobblemon, que não muda sozinha
  private static void deitar(PokemonEntity selvagem, ServerLevel nivel) {
    selvagem.setNoAi(true);
    selvagem.setDeltaMovement(Vec3.ZERO);
    Vec3 chao = Mira.noChao(nivel, selvagem.position());
    selvagem.teleportTo(chao.x, chao.y, chao.z);
    selvagem.setEnablePoseTypeRecalculation(false);
    selvagem.getEntityData().set(PokemonEntity.getPOSE_TYPE(), PoseType.SLEEP);
  }

  // A cada tick: os passarinhos; quem foi capturado sai da lista; a bola falhou ou o tempo acabou: some
  public static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    List<Caido> paraSumir = new ArrayList<>();
    CAIDOS.values().removeIf(caido -> caido.entidade.isRemoved());
    for (Caido caido : CAIDOS.values()) {
      if (Captura.dentroDaBola(caido.entidade)) {
        continue;
      }
      if (caido.tentou || agora >= caido.ate) {
        paraSumir.add(caido);
      } else if (agora % A_CADA_PASSARINHOS == 0) {
        Feedback.efeito(caido.entidade, PASSARINHOS, "root");
      }
    }
    for (Caido caido : paraSumir) {
      sumir(caido);
    }
  }

  private static void sumir(Caido caido) {
    PokemonEntity entidade = caido.entidade;
    CAIDOS.remove(entidade.getUUID());
    JA_DERAM_XP.remove(entidade.getPokemon().getUuid());
    Feedback.particulas(entidade, ParticleTypes.POOF, NUVENS_AO_SUMIR);
    CobblemonCombatZA.LOGGER.info("DESMAIO: {} sumiu ({})", entidade.getPokemon().getSpecies().getName(),
        caido.tentou ? "a bola falhou" : "o tempo acabou");
    entidade.discard();
  }

  public static boolean esta(PokemonEntity entidade) {
    return CAIDOS.containsKey(entidade.getUUID());
  }

  // A bola acertou o caído (ChanceDeCaptura): era a tentativa dele. Se ela falhar, ele some quando sair da bola
  public static void tentou(PokemonEntity entidade) {
    Caido caido = CAIDOS.get(entidade.getUUID());
    if (caido != null) {
      caido.tentou = true;
      CobblemonCombatZA.LOGGER.info("DESMAIO: a tentativa em {}", entidade.getPokemon().getSpecies().getName());
    }
  }

  // Os segundos que faltam (a placa mostra); −1 = não está caído
  public static int segundos(PokemonEntity entidade, long agora) {
    Caido caido = CAIDOS.get(entidade.getUUID());
    return caido == null ? -1 : (int) Math.max(0, Math.ceil((caido.ate - agora) / (double) SharedConstants.TICKS_PER_SECOND));
  }

  // Capturado depois de cair: o XP já saiu na queda (Experiencia não dá de novo)
  public static boolean jaDeuOXp(Pokemon pokemon) {
    return JA_DERAM_XP.remove(pokemon.getUuid());
  }

  public static void limpar() {
    CAIDOS.clear();
    JA_DERAM_XP.clear();
  }
}
