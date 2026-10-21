package dev.victor.combatza.ia;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.serialization.Codec;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Captura;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.rede.ModoDosGuardasPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.warden.Warden;

// 7.4 (decisões dele, 18 e 19/10): os GUARDAS são os seus Pokémon fora da bola que não são o parceiro (7.7: na batalha, o
// lutador; fora dela, o da setinha, que só ataca no seu comando: Parceiro). Eles não atacam os
// selvagens da sua batalha (só o lutador luta, 1 contra 1); te defendem dos MONSTROS, pelo modo que você escolhe na roda
// (RodaDosGuardas, segurando a tecla, "exatamente igual é no ARK"):
// - PASSIVO: só te segue, nunca ataca;
// - NEUTRO (o padrão): ataca os monstros que chegam perto de você, menos a aranha calma (7.4g);
// - AGRESSIVO: ataca todos os monstros por perto, até a aranha calma. Os dois, nunca os selvagens.
// Eles usam os 4 golpes pelo MESMO cérebro do selvagem (EscolhaDoGolpe, a IA única), um atrás do outro (7.4e: sem o respiro
// do selvagem), e os golpes passam pelos aliados (Lados). O alvo (7.4f): primeiro quem bateu nele, até matar; senão, o
// monstro mais perto de você. Fora de batalha, o Pokémon que você comandou com as teclas nos últimos 10 s
// (dele, 20/10: "bati num golem e meus Pokémon não fizeram nada (...) qualquer bicho que eu bater, até villager, meus Pokémon
// atacam"): no Neutro e no Agressivo, o bicho que VOCÊ acerta (com a mão, uma flecha...) vira o alvo deles até morrer, como o
// lobo do Minecraft, mesmo não sendo monstro (aldeão, golem, vaca); e quem te ataca ou ataca um deles também, monstro ou não
// é seu, e não do guarda. O modo fica guardado no seu jogador (um "anexo" do Fabric: vai junto no save e volta depois de
// morrer) e é avisado ao seu jogo, para a roda mostrar o atual
public class Guardas {
  private static final String FRASE = "cobblemon_combat_za.guardas.";

  public enum Modo {
    PASSIVO, NEUTRO, AGRESSIVO;

    // "Passivo" (a roda e a fileirinha) e "Passivo: só te seguem, nunca atacam" (a roda e a barra de ação)
    public Component nome() {
      return Component.translatable(FRASE + "modo." + name().toLowerCase(Locale.ROOT));
    }

    public Component descricao() {
      return Component.translatable(FRASE + "descricao." + name().toLowerCase(Locale.ROOT));
    }

    public static Modo de(String nome) {
      for (Modo modo : values()) {
        if (modo.name().equalsIgnoreCase(nome)) {
          return modo;
        }
      }
      return NEUTRO;
    }
  }

  public static final AttachmentType<String> MODO = AttachmentRegistry.create(
      ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "modo_dos_guardas"),
      construtor -> construtor.persistent(Codec.STRING).initializer(() -> "neutro").copyOnDeath()
          .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

  private static final int A_CADA_TICKS = 5;
  // 7.4g: até onde os guardas olham em volta de você (o Agressivo e quem está contra vocês) e a distância de você em que o
  // Neutro já ataca qualquer monstro
  private static final double ALCANCE = 16;
  private static final double PERTO_DE_VOCE = 10;
  // A regra da aranha do Minecraft (Spider.SpiderTargetGoal): com "brilho" 0,5 ou mais, ela não começa a atacar. No mundo
  // normal, é a luz 12 de 15 (contando a hora do dia)
  private static final int LUZ_DA_ARANHA_CALMA = 12;
  // Longe demais de você, o guarda não briga (ele está voltando para perto)
  private static final double LONGE_DO_DONO = 24;
  // Por quanto tempo ele lembra quem bateu em você ou nele (5 s) e de quem você comandou (10 s)
  private static final int LEMBRA_QUEM_BATEU = 100;
  private static final int COMANDADO_HA_POUCO = 200;

  private static final class Cerebro {
    private final long comecouNoTick;
    // 7.4f: o monstro que bateu nele, até morrer
    private Mob revidando;
    private final Set<String> usados = new HashSet<>();

    private Cerebro(long agora) {
      this.comecouNoTick = agora;
    }
  }

  private record Comandado(UUID pokemon, long tick) {
  }

  // O bicho que o jogador acertou e quando (o "lastHurtMobTimestamp" dele): enquanto estiver vivo e perto, é o alvo dos guardas
  private record AlvoDoDono(Mob alvo, int tick) {
  }

  private static final Map<UUID, Cerebro> CEREBROS = new HashMap<>();
  private static final Map<UUID, Comandado> COMANDADOS = new HashMap<>();
  private static final Map<UUID, AlvoDoDono> ALVOS_DOS_DONOS = new HashMap<>();

  private Guardas() {
  }

  // Na inicialização: o pacote da roda (e, ao carregar a classe, o anexo do modo, que precisa existir antes do mundo abrir)
  public static void registrar() {
    ServerPlayNetworking.registerGlobalReceiver(ModoDosGuardasPayload.TIPO,
        (pedido, contexto) -> mudar(contexto.player(), Modo.de(pedido.modo())));
    ServerLifecycleEvents.SERVER_STARTED.register(servidor -> desligarODefenderDoCobblemon());
    ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((servidor, recursos, deuCerto) -> desligarODefenderDoCobblemon());
  }

  // 7.4d (dele: "botei no Passivo, tomei porrada e ele foi atacar o bicho que estava me batendo"): o PRÓPRIO Cobblemon 1.7
  // faz o Pokémon do time defender o dono (o comportamento "defend_owner", nas espécies com "willDefendOwner", como o
  // Growlithe), por fora dos nossos modos. Quem defende são só os guardas: quando o mundo abre e depois de um /reload (as
  // espécies do Cobblemon já carregadas), o "willDefendOwner" de todas fica falso. Vale para quem sair da bola depois disso
  private static void desligarODefenderDoCobblemon() {
    int especies = 0;
    for (Species especie : PokemonSpecies.getSpecies()) {
      especie.getBehaviour().getCombat().setWillDefendOwner(false);
      for (FormData forma : especie.getForms()) {
        if (forma.getBehaviour().parent != null) {
          forma.getBehaviour().getCombat().setWillDefendOwner(false);
        }
      }
      especies++;
    }
    CobblemonCombatZA.LOGGER.info("GUARDAS: o \"defender o dono\" do Cobblemon desligado em {} espécies (quem defende são os "
        + "guardas)", especies);
  }

  public static Modo modo(ServerPlayer jogador) {
    return Modo.de(jogador.getAttachedOrElse(MODO, "neutro"));
  }

  public static void mudar(ServerPlayer jogador, Modo modo) {
    jogador.setAttached(MODO, modo.name().toLowerCase());
    Feedback.barraDeAcao(jogador, Component.translatable(FRASE + "mudou", modo.descricao()));
    CobblemonCombatZA.LOGGER.info("GUARDAS: {} escolheu {}", jogador.getGameProfile().getName(), modo);
  }

  // ControleDeGolpes.usarGolpe: este Pokémon é seu agora (fora de batalha, o guarda não mexe nele por 10 s)
  public static void comandou(ServerPlayer jogador, Pokemon pokemon, long agora) {
    COMANDADOS.put(jogador.getUUID(), new Comandado(pokemon.getUuid(), agora));
  }

  public static void limpar() {
    CEREBROS.clear();
    COMANDADOS.clear();
    ALVOS_DOS_DONOS.clear();
  }

  public static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    Set<UUID> vistos = new HashSet<>();
    Set<UUID> online = new HashSet<>();
    for (ServerPlayer jogador : servidor.getPlayerList().getPlayers()) {
      online.add(jogador.getUUID());
      Modo modo = modo(jogador);
      Mob doDono = alvoDoDono(jogador);
      if (modo == Modo.PASSIVO || !jogador.isAlive() || jogador.isSpectator()) {
        continue;
      }
      Pokemon parceiro = Parceiro.de(jogador);
      for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
        PokemonEntity guarda = pokemon.getEntity();
        if (guarda == null || !guarda.isAlive() || pokemon.isFainted() || guarda.level() != jogador.level()
            || Captura.dentroDaBola(guarda) || ocupadoComOutraCoisa(guarda) || guarda.distanceTo(jogador) > LONGE_DO_DONO
            || (parceiro != null && parceiro.getUuid().equals(pokemon.getUuid()))
            || comandadoHaPouco(jogador, pokemon, agora)) {
          continue;
        }
        vistos.add(pokemon.getUuid());
        guardar(jogador, guarda, modo, doDono, agora);
      }
    }
    CEREBROS.keySet().retainAll(vistos);
    ALVOS_DOS_DONOS.keySet().retainAll(online);
  }

  // O bicho que você acertou por último (o Minecraft guarda quem e quando, como para o lobo: Player.attack e a flecha chamam o
  // setLastHurtMob). Um acerto novo troca o alvo; ele sai quando morre, some, fica a mais de 24 blocos de você ou muda de
  // mundo. Os Pokémon ficam de fora (o selvagem é da batalha; o com dono, aliado), e os bichos mansos de alguém também
  private static Mob alvoDoDono(ServerPlayer jogador) {
    AlvoDoDono atual = ALVOS_DOS_DONOS.get(jogador.getUUID());
    int quando = jogador.getLastHurtMobTimestamp();
    if (jogador.getLastHurtMob() instanceof Mob acertado && podeSerAlvo(acertado) && !mansoDeAlguem(acertado)
        && bateuHaPouco(true, jogador.tickCount, quando) && (atual == null || atual.tick() != quando)) {
      atual = new AlvoDoDono(acertado, quando);
      ALVOS_DOS_DONOS.put(jogador.getUUID(), atual);
      CobblemonCombatZA.LOGGER.info("GUARDAS: {} bateu em {}: é o alvo dos guardas", jogador.getGameProfile().getName(),
          acertado.getName().getString());
    }
    if (atual == null) {
      return null;
    }
    Mob alvo = atual.alvo();
    if (!alvo.isAlive() || alvo.isRemoved() || alvo.level() != jogador.level() || alvo.distanceTo(jogador) > LONGE_DO_DONO) {
      ALVOS_DOS_DONOS.remove(jogador.getUUID());
      return null;
    }
    return alvo;
  }

  // Qualquer bicho vivo que não é Pokémon nem NPC treinador (os jogadores nem são "Mob")
  private static boolean podeSerAlvo(LivingEntity bicho) {
    return bicho instanceof Mob && bicho.isAlive() && !(bicho instanceof PokemonEntity) && !Lados.ehNpc(bicho);
  }

  // O lobo, o gato, o cavalo... domados por alguém
  private static boolean mansoDeAlguem(Mob bicho) {
    return bicho instanceof OwnableEntity manso && manso.getOwnerUUID() != null;
  }

  // 7.4c: numa batalha por turnos do Cobblemon (contra um treinador ou outro jogador), ocupado (evoluindo, saindo da bola...)
  // ou com alguém montado nele, ele não é guarda
  private static boolean ocupadoComOutraCoisa(PokemonEntity guarda) {
    return guarda.isBattling() || guarda.isBusy() || guarda.isVehicle();
  }

  private static boolean comandadoHaPouco(ServerPlayer jogador, Pokemon pokemon, long agora) {
    Comandado comandado = COMANDADOS.get(jogador.getUUID());
    return comandado != null && comandado.pokemon().equals(pokemon.getUuid())
        && agora - comandado.tick() < COMANDADO_HA_POUCO;
  }

  private static void guardar(ServerPlayer jogador, PokemonEntity guarda, Modo modo, Mob doDono, long agora) {
    Pokemon pokemon = guarda.getPokemon();
    Cerebro cerebro = CEREBROS.computeIfAbsent(pokemon.getUuid(), uuid -> new Cerebro(agora));
    // 7.4e (dele: "ficou ruim, o bicho não consegue fazer nada, o mob vira nele e só morre"): sem o respiro do selvagem;
    // acabou um golpe, ele já escolhe o próximo
    if (IaDoSelvagem.ocupado(pokemon) || agora % A_CADA_TICKS != 0) {
      return;
    }
    Mob alvo = alvo(jogador, guarda, modo, cerebro, doDono);
    if (alvo == null) {
      return;
    }
    int slot = EscolhaDoGolpe.melhor(guarda, alvo, cerebro.usados, cerebro.comecouNoTick, agora, true);
    if (slot < 0) {
      return;
    }
    String golpe = pokemon.getMoveSet().get(slot).getName();
    if (ControleDeGolpes.usarGolpeDaIa(guarda, slot, alvo, jogador, agora)) {
      cerebro.usados.add(golpe);
      CobblemonCombatZA.LOGGER.info("GUARDA ({}): {} foi em {} com {} ({})", modo, pokemon.getSpecies().getName(),
          alvo.getName().getString(), golpe, alvo == cerebro.revidando ? "revidando"
              : alvo == doDono ? "o que você bateu" : "o mais perto de você");
    }
  }

  // 7.4f (dele: "primeiro ele se salva: se algum bater nele, ele revida até matar, depois volta para o mais perto de mim"):
  // 1º o bicho que ele já está revidando, até morrer; 2º um bicho que bateu nele há pouco (vira o "revidando"; monstro ou
  // não, como o golem); 3º o que VOCÊ bateu, até morrer; 4º o monstro mais perto de você. Sem ver o que está revidando (um
  // bloco no meio), ele espera; sem ver o que você bateu, vai no mais perto
  private static Mob alvo(ServerPlayer jogador, PokemonEntity guarda, Modo modo, Cerebro cerebro, Mob doDono) {
    Mob revidando = cerebro.revidando;
    if (revidando != null
        && (!revidando.isAlive() || revidando.isRemoved() || revidando.distanceTo(jogador) > LONGE_DO_DONO)) {
      cerebro.revidando = null;
    }
    if (cerebro.revidando == null && guarda.getLastHurtByMob() instanceof Mob quemBateu && podeSerAlvo(quemBateu)
        && bateuHaPouco(true, guarda.tickCount, guarda.getLastHurtByMobTimestamp())
        && quemBateu.distanceTo(jogador) <= LONGE_DO_DONO) {
      cerebro.revidando = quemBateu;
    }
    if (cerebro.revidando != null) {
      return guarda.hasLineOfSight(cerebro.revidando) ? cerebro.revidando : null;
    }
    if (doDono != null && guarda.hasLineOfSight(doDono)) {
      return doDono;
    }
    return maisPertoDeVoce(jogador, guarda, modo);
  }

  // 7.4e (dele: "o guarda tem que sempre focar o mob mais perto de mim"): o monstro mais perto de VOCÊ, à vista do guarda,
  // que serve para o modo
  private static Mob maisPertoDeVoce(ServerPlayer jogador, PokemonEntity guarda, Modo modo) {
    Mob maisPerto = null;
    double menorDistancia = Double.MAX_VALUE;
    for (Mob monstro : jogador.level().getEntitiesOfClass(Mob.class, jogador.getBoundingBox().inflate(ALCANCE),
        m -> podeSerAlvo(m) && serve(m, jogador, guarda, modo) && guarda.hasLineOfSight(m))) {
      double distancia = jogador.distanceToSqr(monstro);
      if (distancia < menorDistancia) {
        menorDistancia = distancia;
        maisPerto = monstro;
      }
    }
    return maisPerto;
  }

  // 7.4g (dele: "qualquer mob ofensivo que chega perto de mim, no Neutro ou no Agressivo, eles têm que encher de porrada,
  // menos a aranha de manhã no Neutro; no Agressivo, mete a porrada até na aranha"). Monstro = os hostis do Minecraft
  // (Lados.ehMonstro), agora com o creeper:
  // - quem está contra vocês (mirando ou bateu há pouco em você ou nele), sempre, monstro ou não (o golem bravo, o lobo, a
  //   lhama que cospe);
  // - os que ficam em paz até alguém mexer (os "neutros" do Minecraft, como o enderman e o piglin zumbi; o piglin; o warden),
  //   só se estiverem contra vocês: um golpe num deles calmo deixa o grupo todo bravo (7.4b);
  // - Agressivo: qualquer outro monstro a até 16 blocos de você, até a aranha calma;
  // - Neutro: qualquer outro a até 10 blocos de você, menos a aranha calma (com luz, ela não ataca)
  private static boolean serve(Mob monstro, ServerPlayer jogador, PokemonEntity guarda, Modo modo) {
    boolean contraVoces = monstro.getTarget() == jogador || monstro.getTarget() == guarda
        || bateuHaPouco(jogador.getLastHurtByMob() == monstro, jogador.tickCount, jogador.getLastHurtByMobTimestamp())
        || bateuHaPouco(guarda.getLastHurtByMob() == monstro, guarda.tickCount, guarda.getLastHurtByMobTimestamp());
    if (contraVoces) {
      return true;
    }
    if (!Lados.ehMonstro(monstro) || monstro instanceof NeutralMob || monstro instanceof Piglin
        || monstro instanceof Warden) {
      return false;
    }
    if (modo == Modo.AGRESSIVO) {
      return true;
    }
    return !aranhaCalma(monstro) && monstro.distanceTo(jogador) <= PERTO_DE_VOCE;
  }

  private static boolean aranhaCalma(Mob monstro) {
    return monstro instanceof Spider
        && monstro.level().getMaxLocalRawBrightness(monstro.blockPosition()) >= LUZ_DA_ARANHA_CALMA;
  }

  private static boolean bateuHaPouco(boolean foiEle, int agora, int quando) {
    return foiEle && agora - quando < LEMBRA_QUEM_BATEU;
  }
}
