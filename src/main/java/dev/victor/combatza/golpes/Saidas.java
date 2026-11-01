package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Raids;
import dev.victor.combatza.golpes.Marcas.Dado;
import dev.victor.combatza.golpes.Marcas.Marca;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDeEstagio;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.ia.IaDoSelvagem;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import kotlin.Unit;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// 01/11 (as pendências, decisões dele: "como nos jogos"): os golpes que TIRAM um Pokémon da luta:
// - Rugido e Ataque de Vento: o selvagem foge; o Pokémon do treinador volta para a bola e o próximo dele sai; o seu volta para a
//   bola e um Pokémon sorteado do seu time sai no lugar (Ventosas, Cão de Guarda, Enraizar e o chefão da raid seguram);
// - Teletransporte: o selvagem foge; o seu volta para a bola (você manda o próximo); o do treinador troca;
// - Passar o Bastão: quem usa volta para a bola, e o próximo que entrar do mesmo lado (em até 1 minuto) recebe os estágios, o
//   Substituto e as marcas (Focalizar Energia, Arco Aqua, Enraizar, Semente, Maldição, Olhar Malvado...);
// - Cauda Descendente: paga metade da vida, volta para a bola, e quem entrar ganha um Substituto (1/4 da vida de quem usou);
// - Tiro Divisor (Ataque e Atq. Esp. do alvo −1) e Recepção Gelada (a neve): depois, quem usou volta para a bola;
// - Desejo de Cura e Dança Lunar: quem usa desmaia, e quem entrar do mesmo lado entra com a vida cheia e sem status;
// - Bênção Ressuscitadora: o primeiro Pokémon desmaiado do seu time volta com metade da vida (sem time: falha).
// O selvagem não tem time: o Passar o Bastão, a Cauda Descendente e os desejos falham nele
public final class Saidas {
  private static final long VALE_POR = 60L * SharedConstants.TICKS_PER_SECOND;
  private static final Set<String> NAO_SAEM_A_FORCA = Set.of("suctioncups", "guarddog");
  private static final List<Marca> PASSAM = List.of(Marca.FOCO, Marca.ARCO_AQUA, Marca.ENRAIZADO, Marca.SEMENTE,
      Marca.MALDICAO, Marca.NAO_FOGE, Marca.CHAVE_DE_POLVO, Marca.LEVITANDO, Marca.SEM_CURA, Marca.EMBARGO);

  private record Marcada(Marca marca, int valor, Pokemon deQuem, String golpe) {
  }

  // O que fica para quem entrar do lado: os estágios, o boneco, as marcas e a cura do desejo
  private record Heranca(long ate, Map<String, Integer> estagios, Double boneco, List<Marcada> marcas, boolean cura) {
  }

  // Por lado: o dono (o jogador, ou o treinador)
  private static final Map<UUID, Heranca> HERANCAS = new HashMap<>();

  private Saidas() {
  }

  // ---- Os golpes ----

  // No alvo (GolpesDeStatus.depoisDeAcertar)
  public static void noAlvo(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity atingido) || HabilidadesQueReagem.caiu(atingido)) {
      return;
    }
    switch (execucao.getGolpe().getName()) {
      case "roar", "whirlwind" -> forcarASaida(execucao, atingido);
      case "partingshot" -> {
        HabilidadesDeEstagio.baixar(execucao.getPokemon(), atingido.getPokemon(), Map.of("atk", -1, "spa", -1), false,
            execucao.getGolpe().getDisplayName());
        sair(execucao, atacante);
      }
      default -> {
        // os outros golpes não tiram ninguém
      }
    }
  }

  // ControleDeGolpes, no começo do golpe sem alvo: a Recepção Gelada chama a neve (EfeitosDoCampo) e sai
  public static void antesDoSemAlvo(Execucao execucao, PokemonEntity atacante) {
    if ("chillyreception".equals(execucao.getGolpe().getName())) {
      sair(execucao, atacante);
    }
  }

  // Sem alvo (GolpesDeStatus.semAlvo). true = era um destes
  public static boolean semAlvo(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = execucao.getPokemon();
    long agora = execucao.getJogador().server.getTickCount();
    switch (execucao.getGolpe().getName()) {
      case "teleport" -> sair(execucao, atacante);
      case "batonpass" -> {
        if (!temQuemEntre(atacante)) {
          falhou(pokemon);
          return true;
        }
        List<Marcada> marcas = new ArrayList<>();
        for (Marca marca : PASSAM) {
          Dado dado = Marcas.dado(pokemon, marca);
          if (dado != null) {
            marcas.add(new Marcada(marca, dado.valor(), dado.deQuem(), dado.golpe()));
          }
        }
        deixar(atacante, new Heranca(agora + VALE_POR, Estagios.atuais(pokemon), Substituto.tirar(pokemon), marcas,
            false));
        sair(execucao, atacante);
      }
      case "shedtail" -> {
        int custo = (int) Math.ceil(pokemon.getMaxHealth() / 2.0);
        if (!temQuemEntre(atacante) || Vida.pvAtual(pokemon) <= custo) {
          falhou(pokemon);
          return true;
        }
        Vida.ferir(atacante, custo);
        GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.start.shedtail",
            pokemon.getDisplayName(false)));
        deixar(atacante, new Heranca(agora + VALE_POR, Map.of(), pokemon.getMaxHealth() / 4.0, List.of(), false));
        sair(execucao, atacante);
      }
      case "healingwish", "lunardance" -> {
        if (!temQuemEntre(atacante)) {
          falhou(pokemon);
          return true;
        }
        deixar(atacante, new Heranca(agora + VALE_POR, Map.of(), null, List.of(), true));
        CobblemonCombatZA.LOGGER.info("SAÍDA: {} desmaiou pelo {}", pokemon.getSpecies().getName(),
            execucao.getGolpe().getName());
        HabilidadesQueReagem.ferir(atacante, Vida.pvAtual(pokemon));
      }
      case "revivalblessing" -> reviver(execucao, pokemon);
      default -> {
        return false;
      }
    }
    return true;
  }

  // ---- Sair e entrar ----

  // Rugido e Ataque de Vento: o alvo sai da luta à força
  private static void forcarASaida(Execucao execucao, PokemonEntity atingido) {
    Pokemon doAlvo = atingido.getPokemon();
    long agora = execucao.getJogador().server.getTickCount();
    if (Raids.ehChefe(atingido) || Marcas.tem(doAlvo, Marca.ENRAIZADO)
        || NAO_SAEM_A_FORCA.contains(Habilidades.atual(doAlvo))) {
      falhou(execucao.getPokemon());
      return;
    }
    if (BatalhasDeTreinador.ehDoTreinador(atingido)) {
      if (!BatalhasDeTreinador.trocarAForca(atingido, agora)) {
        falhou(execucao.getPokemon());
      }
      return;
    }
    if (doAlvo.isWild()) {
      fugir(atingido, execucao.getGolpe().getName());
      return;
    }
    trocarOSeu(execucao, atingido);
  }

  // Quem usou sai da luta por conta própria (Teletransporte, Passar o Bastão, Tiro Divisor...)
  private static void sair(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = atacante.getPokemon();
    long agora = execucao.getJogador().server.getTickCount();
    if (Raids.ehChefe(atacante)) {
      return;
    }
    if (BatalhasDeTreinador.ehDoTreinador(atacante)) {
      BatalhasDeTreinador.trocarAForca(atacante, agora);
      return;
    }
    if (pokemon.isWild()) {
      fugir(atacante, execucao.getGolpe().getName());
      return;
    }
    CobblemonCombatZA.LOGGER.info("SAÍDA: {} voltou para a bola ({})", pokemon.getSpecies().getName(),
        execucao.getGolpe().getName());
    atacante.recallWithAnimation();
  }

  private static void fugir(PokemonEntity selvagem, String porque) {
    IaDoSelvagem.fazerFugir(selvagem, porque);
    GolpesDeStatus.avisar(selvagem.getPokemon(), Component.translatable("cobblemon_combat_za.batalha.fugindo",
        selvagem.getPokemon().getDisplayName(false)));
  }

  // O seu (ou o do outro jogador no duelo): volta para a bola e um sorteado do time sai no lugar dele
  private static void trocarOSeu(Execucao execucao, PokemonEntity atingido) {
    Pokemon pokemon = atingido.getPokemon();
    ServerPlayer dono = pokemon.getOwnerPlayer();
    List<Pokemon> podemEntrar = dono == null ? List.of() : naBola(dono, pokemon);
    if (podemEntrar.isEmpty() || !(atingido.level() instanceof ServerLevel nivel)) {
      falhou(execucao.getPokemon());
      return;
    }
    Pokemon novo = podemEntrar.get(atingido.getRandom().nextInt(podemEntrar.size()));
    Vec3 onde = atingido.position();
    CobblemonCombatZA.LOGGER.info("SAÍDA: {} foi arrastado; {} entra", pokemon.getSpecies().getName(),
        novo.getSpecies().getName());
    atingido.recallWithAnimation().whenComplete((recolhido, erro) -> dono.server.execute(() -> {
      if (novo.getEntity() == null && !novo.isFainted() && dono.isAlive()) {
        novo.sendOutWithAnimation(dono, nivel, onde, null, true, null, entidade -> Unit.INSTANCE);
        GolpesDeStatus.avisar(novo, Component.translatable("cobblemon.battle.dragged_out", novo.getDisplayName(false)));
      }
    }));
  }

  // Os do time na bola, de pé (menos ele)
  private static List<Pokemon> naBola(ServerPlayer dono, Pokemon menos) {
    List<Pokemon> naBola = new ArrayList<>();
    for (Pokemon doTime : Cobblemon.INSTANCE.getStorage().getParty(dono)) {
      if (!doTime.getUuid().equals(menos.getUuid()) && !doTime.isFainted() && doTime.getEntity() == null) {
        naBola.add(doTime);
      }
    }
    return naBola;
  }

  // Tem alguém do mesmo lado para entrar? (o seu time, ou o próximo do treinador; o selvagem não tem ninguém)
  private static boolean temQuemEntre(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if (BatalhasDeTreinador.ehDoTreinador(entidade)) {
      return BatalhasDeTreinador.temProximo(entidade);
    }
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    if (dono == null) {
      return false;
    }
    for (Pokemon doTime : Cobblemon.INSTANCE.getStorage().getParty(dono)) {
      if (!doTime.getUuid().equals(pokemon.getUuid()) && !doTime.isFainted()) {
        return true;
      }
    }
    return false;
  }

  // O lado do Pokémon: o jogador dono, ou o treinador (null = selvagem)
  private static UUID lado(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if (BatalhasDeTreinador.ehDoTreinador(entidade)) {
      return BatalhasDeTreinador.treinadorDe(entidade);
    }
    return pokemon.isWild() ? null : pokemon.getOwnerUUID();
  }

  private static void deixar(PokemonEntity entidade, Heranca heranca) {
    UUID lado = lado(entidade);
    if (lado != null) {
      HERANCAS.put(lado, heranca);
    }
  }

  // HabilidadesDeEntrada: entrou na batalha. Recebe o que ficou para o lado dele
  public static void aoEntrar(PokemonEntity entidade) {
    UUID lado = HERANCAS.isEmpty() ? null : lado(entidade);
    Heranca heranca = lado == null ? null : HERANCAS.remove(lado);
    long agora = entidade.getServer() == null ? 0 : entidade.getServer().getTickCount();
    if (heranca == null || agora > heranca.ate()) {
      return;
    }
    Pokemon pokemon = entidade.getPokemon();
    CobblemonCombatZA.LOGGER.info("SAÍDA: {} recebeu o que ficou ({} estágios, boneco {}, {} marcas, cura {})",
        pokemon.getSpecies().getName(), heranca.estagios().size(), heranca.boneco(), heranca.marcas().size(),
        heranca.cura());
    if (!heranca.estagios().isEmpty()) {
      Estagios.definir(pokemon, heranca.estagios(), HabilidadesDeEstagio.ateQuando(pokemon));
    }
    if (heranca.boneco() != null) {
      Substituto.dar(pokemon, heranca.boneco());
    }
    for (Marcada marcada : heranca.marcas()) {
      Marcas.marcar(pokemon, marcada.marca(), agora, Marcas.ATE_SAIR, marcada.valor(), marcada.deQuem(), marcada.golpe());
    }
    if (heranca.cura()) {
      Vida.curar(entidade, pokemon.getMaxHealth());
      if (pokemon.getStatus() != null) {
        pokemon.setStatus(null);
      }
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.entrou_curado",
          pokemon.getDisplayName(false)));
    }
  }

  // Bênção Ressuscitadora (dele, 01/11: "revive quem tá selecionado"): o Pokémon desmaiado da setinha volta com metade da
  // vida; a setinha num Pokémon de pé, falha (e a barra de ação diz por quê). O golpe da IA (o segundo da dupla, o guarda),
  // sem setinha, revive o primeiro desmaiado do time
  private static void reviver(Execucao execucao, Pokemon pokemon) {
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    Pokemon revivido = null;
    if (dono != null) {
      var time = Cobblemon.INSTANCE.getStorage().getParty(dono);
      int setinha = execucao.getSetinha();
      if (setinha >= 0) {
        Pokemon daSetinha = time.get(setinha);
        revivido = daSetinha != null && daSetinha.isFainted() ? daSetinha : null;
        if (revivido == null) {
          Feedback.barraDeAcao(dono, Component.translatable("cobblemon_combat_za.golpe.reviver_qual"));
        }
      } else {
        for (Pokemon doTime : time) {
          if (doTime.isFainted()) {
            revivido = doTime;
            break;
          }
        }
      }
    }
    if (revivido == null) {
      falhou(pokemon);
      return;
    }
    revivido.setCurrentHealth(Math.max(1, revivido.getMaxHealth() / 2));
    CobblemonCombatZA.LOGGER.info("BÊNÇÃO RESSUSCITADORA: {} voltou", revivido.getSpecies().getName());
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.reviveu",
        revivido.getDisplayName(false)));
  }

  private static void falhou(Pokemon pokemon) {
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
  }

  public static void limpar() {
    HERANCAS.clear();
  }
}
