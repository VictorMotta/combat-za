package dev.victor.combatza.combate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.golpes.SonoECongelado;
import dev.victor.combatza.party.Parties;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

// O chefão das raids do Cobblemon Raid Dens (o Pokémon gigante). Ele tem o aspecto "raid" (javap no PokemonEntityMixin do mod
// de raid: crd_isRaidBoss).
// 31/10 (dele: "no mod de raids a gente ataca, ele mata nossos Pokémon e não dá dano"): a vida do chefão é da raid, não do
// corpo dele, e só a batalha por turnos do mod de raid mexia nela. Primeiro a raid ficou por turnos; depois (dele: "quero que
// altere o mod dele para lutarmos usando o nosso mod"), ela virou a NOSSA batalha, em tempo real. Escolhas dele, 31/10:
// - COMEÇAR: mandar o Pokémon no chefão, como no mod de raid (ele confere se você está na raid, se o Pokémon pode lutar...),
//   e a batalha por turnos que ele ia abrir vira a nossa (CobblemonCombatZA.aoIniciarBatalha);
// - REGRAS: as do mod de raid. A vida (a barra no alto), o tempo, as vidas de cada jogador, a vitória, as recompensas e a
//   captura continuam com ele (PonteDaRaid); todos os seus Pokémon desmaiados = uma derrota (perdeu);
// - ALVO: o chefão bate em quem mais o feriu nos últimos segundos (alvoDoChefe);
// - ENTRAR: quem é da party do dono da raid entra sem ele precisar aceitar (aceitarAParty).
// Quem está na raid luta junto, mesmo fora da sua party (GerenciarCombates, Lados)
public final class Raids {
  private static final String ASPECTO_DO_CHEFE = "raid";
  // A raiva do chefão: o dano de cada jogador nele, que cai pela metade a cada 5 segundos
  private static final double MEIA_VIDA_DA_RAIVA = 5.0 * SharedConstants.TICKS_PER_SECOND;
  // A arena da raid é grande e o chefão é gigante: só a 32 blocos dele você fugiu (dos selvagens, 16: Combate)
  public static final float LONGE_DO_CHEFE = 32.0f;
  private static final int A_CADA_TICKS = SharedConstants.TICKS_PER_SECOND;

  private record Raiva(double valor, long tick) {
    double em(long agora) {
      return valor * Math.pow(0.5, (agora - tick) / MEIA_VIDA_DA_RAIVA);
    }
  }

  // Chefão → jogador → raiva
  private static final Map<UUID, Map<UUID, Raiva>> RAIVA = new HashMap<>();
  // O último dano no chefão, em PV: o corpo dele não perde vida, então o Vida.pvTirados pergunta aqui
  private static final Map<UUID, Double> ULTIMO_DANO = new HashMap<>();
  // Os pedidos de entrar que já aceitamos (dono:quem pediu), para não aceitar de novo a cada segundo se a raid recusar
  private static final Set<String> JA_ACEITOS = new HashSet<>();

  private Raids() {
  }

  public static boolean ehChefe(Entity entidade) {
    return entidade instanceof PokemonEntity pokemon && ehChefe(pokemon.getPokemon());
  }

  public static boolean ehChefe(Pokemon pokemon) {
    return pokemon != null && (pokemon.getAspects().contains(ASPECTO_DO_CHEFE)
        || pokemon.getForcedAspects().contains(ASPECTO_DO_CHEFE));
  }

  // A batalha por turnos que vai começar é a de uma raid (algum lado tem o chefão)
  public static boolean daRaid(PokemonBattle batalha) {
    for (BattleActor ator : batalha.getActors()) {
      for (BattlePokemon pokemon : ator.getPokemonList()) {
        if (ehChefe(pokemon.getOriginalPokemon()) || ehChefe(pokemon.getEffectedPokemon())) {
          return true;
        }
      }
    }
    return false;
  }

  // A raid do chefão, se ele é o chefão de uma raid valendo; null nos outros casos
  private static Object raidDoChefe(Entity entidade) {
    return entidade instanceof PokemonEntity chefe && ehChefe(chefe) ? PonteDaRaid.raidDe(chefe) : null;
  }

  // Pode lutar com o chefão: está na raid (o mod de raid conferiu quando ele entrou) e não perdeu todas as vidas
  public static boolean podeLutar(ServerPlayer jogador, PokemonEntity chefe) {
    Object raid = raidDoChefe(chefe);
    return jogador != null && raid != null && PonteDaRaid.estaNela(raid, jogador) && !PonteDaRaid.saiu(raid, jogador);
  }

  // A nossa batalha com o chefão começou para este jogador: ele entra na raid (se ainda não estava) e ela fica "em andamento"
  public static void comecou(ServerPlayer jogador, PokemonEntity chefe) {
    Object raid = raidDoChefe(chefe);
    if (raid != null) {
      PonteDaRaid.entrar(raid, jogador);
    }
  }

  // O dano no chefão (o golpe, a queimadura, o recuo dele...) vai para a vida da raid, e o corpo não perde nada. true = foi
  // para a raid (DanoNoChefeDaRaidMixin: o hurt para aqui). Só na nossa batalha com ele, e só o dano do nosso mod (os golpes,
  // o status, o clima, as armadilhas): o soco do jogador continua sem ferir Pokémon, e o resto (a lava, a queda) fica com o
  // mod de raid
  public static boolean desviarODano(PokemonEntity chefe, DamageSource fonte, float dano) {
    if (chefe.level().isClientSide() || !ehChefe(chefe) || !doNossoMod(fonte)
        || GerenciarCombates.combateDe(chefe.getPokemon()) == null) {
      return false;
    }
    Object raid = PonteDaRaid.raidDe(chefe);
    if (raid == null) {
      return false;
    }
    double pv = dano * chefe.getPokemon().getMaxHealth() / chefe.getMaxHealth();
    ULTIMO_DANO.put(chefe.getUUID(), Math.max(0, pv));
    if (pv <= 0) {
      return true;
    }
    long agora = chefe.getServer() == null ? 0 : chefe.getServer().getTickCount();
    ServerPlayer quem = quemFeriu(chefe, fonte);
    if (quem != null) {
      RAIVA.computeIfAbsent(chefe.getUUID(), uuid -> new HashMap<>()).merge(quem.getUUID(), new Raiva(pv, agora),
          (antes, nova) -> new Raiva(antes.em(agora) + nova.valor(), agora));
    }
    PonteDaRaid.ferir(raid, quem, (float) pv);
    // O piscar vermelho e o som de apanhar, como num dano de verdade; e o sono do golpe acaba, como em qualquer selvagem
    chefe.level().broadcastDamageEvent(chefe, fonte);
    SonoECongelado.aoLevarDano(chefe);
    if (CobblemonCombatZA.LOGGER.isInfoEnabled()) {
      CobblemonCombatZA.LOGGER.info("RAID: {} PV no chefão {} (de {}) | vida da raid {}/{}", String.format("%.1f", pv),
          chefe.getPokemon().getSpecies().getName(), quem == null ? "?" : quem.getName().getString(),
          String.format("%.0f", PonteDaRaid.vida(raid)), String.format("%.0f", PonteDaRaid.vidaMaxima(raid)));
    }
    return true;
  }

  private static boolean doNossoMod(DamageSource fonte) {
    return fonte.typeHolder().unwrapKey()
        .map(tipo -> CobblemonCombatZA.MOD_ID.equals(tipo.location().getNamespace())).orElse(false);
  }

  // De quem é o dano: o dono do Pokémon que bateu (ou o jogador). A queimadura, o veneno e o recuo do próprio chefão contam
  // para quem bateu nele por último; ninguém ainda, para o primeiro da batalha
  private static ServerPlayer quemFeriu(PokemonEntity chefe, DamageSource fonte) {
    Entity atacante = fonte.getEntity();
    if (atacante instanceof ServerPlayer jogador) {
      return jogador;
    }
    if (atacante instanceof PokemonEntity pokemon && !pokemon.getPokemon().isWild()
        && pokemon.getPokemon().getOwnerPlayer() != null) {
      return pokemon.getPokemon().getOwnerPlayer();
    }
    Map<UUID, Raiva> raivas = RAIVA.get(chefe.getUUID());
    if (raivas != null && chefe.getServer() != null) {
      UUID ultimo = null;
      long quando = Long.MIN_VALUE;
      for (Map.Entry<UUID, Raiva> raiva : raivas.entrySet()) {
        if (raiva.getValue().tick() > quando) {
          quando = raiva.getValue().tick();
          ultimo = raiva.getKey();
        }
      }
      ServerPlayer jogador = ultimo == null ? null : chefe.getServer().getPlayerList().getPlayer(ultimo);
      if (jogador != null) {
        return jogador;
      }
    }
    List<ServerPlayer> naLuta = GerenciarCombates.jogadoresNoCombateDe(chefe);
    return naLuta.isEmpty() ? null : naLuta.get(0);
  }

  // O PV que o último dano tirou do chefão (Vida.pvTirados: a Drenagem, a Orbe da Vida, o histórico...). null = o último dano
  // não foi num chefão
  public static Double ultimoDano(Entity alvo) {
    return alvo instanceof PokemonEntity ? ULTIMO_DANO.remove(alvo.getUUID()) : null;
  }

  // O PV do chefão para as contas do nosso mod (a cura dele abaixo da metade, o painel da batalha...): a mesma fração da vida
  // da raid. null = não é o chefão de uma raid valendo
  public static Integer pvDoChefe(Pokemon pokemon) {
    Object raid = ehChefe(pokemon) ? raidDoChefe(pokemon.getEntity()) : null;
    float maxima = raid == null ? 0 : PonteDaRaid.vidaMaxima(raid);
    if (maxima <= 0) {
      return null;
    }
    return (int) Math.ceil(pokemon.getMaxHealth() * PonteDaRaid.vida(raid) / maxima);
  }

  // A cura do chefão (a Recuperação, a baga, o dreno...) volta para a vida da raid. Devolve quanto curou; null = não é o chefão
  // de uma raid valendo
  public static Integer curarOChefe(PokemonEntity chefe, double pv) {
    Object raid = raidDoChefe(chefe);
    if (raid == null) {
      return null;
    }
    float antes = PonteDaRaid.vida(raid);
    PonteDaRaid.curar(raid, (float) pv);
    return Math.round(PonteDaRaid.vida(raid) - antes);
  }

  // Todos os seus Pokémon desmaiaram (ou você caiu) na batalha com o chefão: é a derrota da raid, como na batalha por turnos
  // dela (perde uma vida; sem vidas, fica de fora; todos de fora, a raid falha)
  public static void perdeu(ServerPlayer jogador, List<PokemonEntity> selvagens) {
    for (PokemonEntity selvagem : selvagens) {
      Object raid = raidDoChefe(selvagem);
      if (raid != null && PonteDaRaid.estaNela(raid, jogador)) {
        CobblemonCombatZA.LOGGER.info("RAID: {} perdeu para o chefão {}", jogador.getName().getString(),
            selvagem.getPokemon().getSpecies().getName());
        PonteDaRaid.perdeu(raid, jogador);
      }
    }
  }

  // O alvo do chefão: o jogador (e o lutador dele) que mais o feriu nos últimos segundos. null = não é o chefão, ou ninguém
  // feriu ainda (aí vale o mais perto, como em todo selvagem: IaDoSelvagem)
  public static ServerPlayer alvoDoChefe(PokemonEntity chefe, List<ServerPlayer> jogadores) {
    Map<UUID, Raiva> raivas = RAIVA.get(chefe.getUUID());
    if (raivas == null || chefe.getServer() == null) {
      return null;
    }
    long agora = chefe.getServer().getTickCount();
    ServerPlayer alvo = null;
    double maior = 0;
    for (ServerPlayer jogador : jogadores) {
      Raiva raiva = raivas.get(jogador.getUUID());
      if (raiva != null && jogador.isAlive() && !jogador.isSpectator() && jogador.level() == chefe.level()
          && raiva.em(agora) > maior) {
        maior = raiva.em(agora);
        alvo = jogador;
      }
    }
    return alvo;
  }

  // A cada segundo: esquece a raiva dos chefões que saíram da batalha e aceita os pedidos da party
  public static void atualizar(MinecraftServer servidor) {
    if (servidor.getTickCount() % A_CADA_TICKS != 0 || !PonteDaRaid.instalado()) {
      return;
    }
    Set<UUID> naLuta = new HashSet<>();
    for (Combate combate : GerenciarCombates.todos()) {
      for (PokemonEntity selvagem : combate.getSelvagens()) {
        naLuta.add(selvagem.getUUID());
      }
    }
    RAIVA.keySet().retainAll(naLuta);
    ULTIMO_DANO.keySet().retainAll(naLuta);
    aceitarAParty(servidor);
  }

  // Dele, 31/10 ("a nossa party, quando entra, precisa ficar aceitando meus amigos"; escolheu "party entra direto"): o pedido de
  // quem é da party do dono da raid é aceito sozinho, como se o dono tivesse clicado em aceitar
  private static void aceitarAParty(MinecraftServer servidor) {
    Set<String> esperando = new HashSet<>();
    for (Object numero : PonteDaRaid.donosComPedidos()) {
      ServerPlayer dono = numero instanceof UUID uuid ? servidor.getPlayerList().getPlayer(uuid) : null;
      if (dono == null) {
        continue;
      }
      for (ServerPlayer amigo : Parties.outrosOnline(dono)) {
        String chave = dono.getUUID() + ":" + amigo.getUUID();
        if (!PonteDaRaid.pediu(dono.getUUID(), amigo)) {
          continue;
        }
        esperando.add(chave);
        if (JA_ACEITOS.add(chave)) {
          CobblemonCombatZA.LOGGER.info("RAID: {} é da party de {}: entra sem precisar aceitar",
              amigo.getName().getString(), dono.getName().getString());
          PonteDaRaid.aceitar(dono, amigo);
        }
      }
    }
    JA_ACEITOS.retainAll(esperando);
  }

  public static void limpar() {
    RAIVA.clear();
    ULTIMO_DANO.clear();
    JA_ACEITOS.clear();
  }
}
