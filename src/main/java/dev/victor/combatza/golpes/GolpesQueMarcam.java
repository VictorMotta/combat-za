package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.CampoDosGolpes;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.golpes.Marcas.Dado;
import dev.victor.combatza.golpes.Marcas.Marca;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDeEstagio;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 01/11 (as pendências, "como nos jogos"): os golpes de status que deixam uma MARCA (Marcas) no Pokémon, e as perguntas que o
// resto do mod faz a elas. Um turno dos jogos é 3,5 s:
// - riscam golpes (GolpesDesligados): Inabilitar (o último golpe do alvo, 4 turnos), Atormentar (não repete o mesmo golpe),
//   Bloqueio de Cura (5 turnos sem golpes de cura e sem curar), Aprisionar (os golpes que quem usou tem ficam riscados nos
//   inimigos); o Embargo deixa o alvo 5 turnos sem efeito de item;
// - a cada turno: Semente Sanguessuga (1/8 vai para quem plantou; Planta é imune), Pesadelo (1/4 enquanto dorme), Maldição
//   do Fantasma (paga metade, o alvo perde 1/4), Arco Aqua e Enraizar (+1/16), Chave de Polvo (Defesa e Def. Esp. −1);
//   Bocejo: dorme 1 turno depois;
// - na conta do golpe (Dano, Precisao): Focalizar Energia (crítico +2), Aguçar (o próximo golpe é crítico), Mirar e Leitura
//   Mental (o próximo golpe no alvo não erra), Carga (o próximo golpe Elétrico ×2), Minimizar (Pisotear e companhia ×2 e sem
//   errar), Espiral de Defesa (Rolagem ×2), Tiro de Piche (Fogo ×2), Levitação Magnética e Telecinese (imune a Terra; na
//   Telecinese, todo golpe acerta), Previsão, Farejador e Olho Milagroso (o Fantasma e o Sombrio perdem a imunidade, a evasão
//   não conta), Poleiro (sem o tipo Voador por 1 turno), Eletrificar (o golpe do alvo vira Elétrico por 1 turno), Pólvora
//   Explosiva (o golpe de Fogo do alvo explode nele, 1/4 da vida);
// - Laço do Destino e Rancor (até o próximo golpe): quem derrubar ele desmaia junto / perde o golpe até o fim da batalha;
// - Olhar Malvado, Bloquear, Teia de Aranha, Chave de Polvo, Sem Recuo e Enraizar: não foge (o selvagem arisco não foge, e
//   você precisa ir a 24 blocos para sair da batalha, como na Armadilharia);
// - Estocagem (até 3, Defesa e Def. Esp. +1 cada) e Engolir (cura 1/4, 1/2 ou tudo e gasta); Autotomia (−100 kg);
//   Capa Mágica e Arrebatador (1 turno: devolvem / roubam o próximo golpe de status)
public final class GolpesQueMarcam {
  private static final int TURNOS_INABILITADO = 4;
  private static final int TURNOS_SEM_CURA = 5;
  private static final int TURNOS_EMBARGO = 5;
  private static final int TURNOS_LEVITANDO = 5;
  private static final int TURNOS_TELECINESE = 3;
  private static final int TURNOS_MIRA = 2;
  private static final int TURNOS_AGUCADO = 2;
  private static final int ESTOCAGEM_MAXIMA = 3;
  private static final double SEMENTE = 1.0 / 8;
  private static final double PESADELO = 1.0 / 4;
  private static final double MALDICAO = 1.0 / 4;
  private static final double ARCO_AQUA = 1.0 / 16;
  private static final double POLVORA = 1.0 / 4;
  private static final int ESPIGAS_DE_FOCO = 2;
  // A Autotomia tira 100 kg (o Cobblemon guarda o peso em hectogramas)
  public static final int AUTOTOMIA = 1000;
  // Os golpes que dobram (e nunca erram) no Minimizado, como no Showdown (onModifyMove/basePowerCallback do "minimize")
  private static final Set<String> PISAM = Set.of("stomp", "steamroller", "bodyslam", "dragonrush", "heatcrash",
      "heavyslam", "flyingpress", "maliciousmoonsault", "supercellslam", "phantomforce", "shadowforce");
  private static final Set<String> ROLAM = Set.of("rollout", "iceball");
  private static final Set<String> NAO_LEVANTAM = Set.of("diglett", "dugtrio", "sandaconda");
  private static final String ESCUDO_MAGICO = "magicguard";
  private static final String FRASE = "cobblemon_combat_za.golpe.";

  // O Cuspir: quanto ele tinha estocado quando começou o golpe
  private static final Map<UUID, Integer> CUSPINDO = new HashMap<>();

  private GolpesQueMarcam() {
  }

  // DanoEspecial: a força do Cuspir (0 = nada estocado: falha)
  public static int cuspido(Pokemon pokemon) {
    return CUSPINDO.getOrDefault(pokemon.getUuid(), 0);
  }

  private static long turnos(int quantos) {
    return quantos * EfeitosDeStatus.ticksPorTurno();
  }

  private static long agora(Execucao execucao) {
    return execucao.getJogador().server.getTickCount();
  }

  private static void avisar(Pokemon pokemon, Component frase) {
    GolpesDeStatus.avisar(pokemon, frase);
  }

  private static void falhou(Pokemon pokemon) {
    avisar(pokemon, Component.translatable(FRASE + "falhou"));
  }

  // ---- Os golpes ----

  // No alvo (GolpesDeStatus.depoisDeAcertar). Os efeitos dos dados (estágios, status) seguem depois, como sempre
  public static void noAlvo(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity atingido) || HabilidadesQueReagem.caiu(atingido)) {
      return;
    }
    Pokemon pokemon = execucao.getPokemon();
    Pokemon doAlvo = atingido.getPokemon();
    String golpe = execucao.getGolpe().getName();
    long agora = agora(execucao);
    switch (golpe) {
      case "disable" -> inabilitar(pokemon, doAlvo, agora);
      case "torment" -> marcarUmaVez(pokemon, doAlvo, Marca.ATORMENTADO, agora, Marcas.ATE_SAIR, "cobblemon.battle.start.torment");
      case "healblock" -> marcarUmaVez(pokemon, doAlvo, Marca.SEM_CURA, agora, turnos(TURNOS_SEM_CURA),
          "cobblemon.battle.start.healblock");
      case "embargo" -> marcarUmaVez(pokemon, doAlvo, Marca.EMBARGO, agora, turnos(TURNOS_EMBARGO),
          "cobblemon.battle.start.embargo");
      case "yawn" -> {
        if (doAlvo.getStatus() != null) {
          falhou(pokemon);
        } else {
          marcarUmaVez(pokemon, doAlvo, Marca.BOCEJO, agora, turnos(1), "cobblemon.battle.start.yawn");
        }
      }
      case "nightmare" -> {
        if (!"slp".equals(EfeitosDeStatus.siglaDoStatus(doAlvo))) {
          falhou(pokemon);
        } else {
          marcarUmaVez(pokemon, doAlvo, Marca.PESADELO, agora, Marcas.ATE_SAIR, "cobblemon.battle.start.nightmare");
        }
      }
      case "leechseed" -> semente(pokemon, doAlvo, agora);
      case "lockon", "mindreader" -> {
        Marcas.marcar(pokemon, Marca.MIRA_TRAVADA, agora, turnos(TURNOS_MIRA), 0, doAlvo, golpe);
        avisar(pokemon, Component.translatable("cobblemon.battle.start.lockon", doAlvo.getDisplayName(false),
            pokemon.getDisplayName(false)));
      }
      case "curse" -> maldicao(execucao, atacante, doAlvo, agora);
      case "tarshot" -> marcarUmaVez(pokemon, doAlvo, Marca.PICHE, agora, Marcas.ATE_SAIR, "cobblemon.battle.start.tarshot");
      case "telekinesis" -> {
        if (NAO_LEVANTAM.contains(doAlvo.getSpecies().getName().toLowerCase()) || Marcas.tem(doAlvo, Marca.ENRAIZADO)) {
          falhou(pokemon);
        } else {
          marcarUmaVez(pokemon, doAlvo, Marca.TELECINESE, agora, turnos(TURNOS_TELECINESE),
              "cobblemon.battle.start.telekinesis");
        }
      }
      case "foresight", "odorsleuth" -> {
        Marcas.marcar(doAlvo, Marca.IDENTIFICADO, agora, Marcas.ATE_SAIR, 0, pokemon, golpe);
        avisar(doAlvo, Component.translatable("cobblemon.battle.start.foresight", doAlvo.getDisplayName(false)));
      }
      case "miracleeye" -> {
        Marcas.marcar(doAlvo, Marca.OLHO_MILAGROSO, agora, Marcas.ATE_SAIR, 0, pokemon, golpe);
        avisar(doAlvo, Component.translatable("cobblemon.battle.start.miracleeye", doAlvo.getDisplayName(false)));
      }
      case "electrify" -> {
        Marcas.marcar(doAlvo, Marca.ELETRIFICADO, agora, turnos(1), 0, pokemon, golpe);
        avisar(doAlvo, Component.translatable("cobblemon.battle.singleturn.electrify", doAlvo.getDisplayName(false)));
      }
      case "powder" -> {
        if (AplicarStatus.imuneAoPo(doAlvo)) {
          avisar(doAlvo, Component.translatable("cobblemon.battle.immune", doAlvo.getDisplayName(false)));
        } else {
          Marcas.marcar(doAlvo, Marca.POLVORA, agora, turnos(1), 0, pokemon, golpe);
          avisar(doAlvo, Component.translatable("cobblemon.battle.singleturn.powder", doAlvo.getDisplayName(false)));
        }
      }
      case "meanlook", "block", "spiderweb" -> prender(pokemon, doAlvo, Marca.NAO_FOGE, agora, "cobblemon.battle.activate.trapped");
      case "octolock" -> prender(pokemon, doAlvo, Marca.CHAVE_DE_POLVO, agora, "cobblemon.battle.start.octolock");
      case "spite" -> rancoroso(pokemon, doAlvo, agora);
      default -> {
        // os outros golpes não deixam marca
      }
    }
  }

  // Sem alvo (GolpesDeStatus.semAlvo). true = acabou aqui; false = os efeitos dos dados (estágios, cura) seguem
  public static boolean semAlvo(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = execucao.getPokemon();
    String golpe = execucao.getGolpe().getName();
    long agora = agora(execucao);
    switch (golpe) {
      case "aquaring" -> marcarUmaVez(pokemon, pokemon, Marca.ARCO_AQUA, agora, Marcas.ATE_SAIR,
          "cobblemon.battle.start.aquaring");
      case "ingrain" -> marcarUmaVez(pokemon, pokemon, Marca.ENRAIZADO, agora, Marcas.ATE_SAIR,
          "cobblemon.battle.start.ingrain");
      case "focusenergy" -> marcarUmaVez(pokemon, pokemon, Marca.FOCO, agora, Marcas.ATE_SAIR,
          "cobblemon.battle.start.focusenergy");
      case "laserfocus" -> marcarUmaVez(pokemon, pokemon, Marca.AGUCADO, agora, turnos(TURNOS_AGUCADO),
          "cobblemon.battle.start.laserfocus");
      case "imprison" -> {
        marcarUmaVez(pokemon, pokemon, Marca.APRISIONOU, agora, Marcas.ATE_SAIR, "cobblemon.battle.start.imprison");
        avisarOsInimigos(pokemon);
      }
      case "magiccoat" -> marcarUmaVez(pokemon, pokemon, Marca.CAPA_MAGICA, agora, turnos(1),
          "cobblemon.battle.singleturn.magiccoat");
      case "snatch" -> marcarUmaVez(pokemon, pokemon, Marca.ARREBATADOR, agora, turnos(1),
          "cobblemon.battle.singleturn.snatch");
      case "destinybond" -> {
        Marcas.marcar(pokemon, Marca.LACO_DO_DESTINO, agora, Marcas.ATE_SAIR, 0, pokemon, golpe);
        avisar(pokemon, Component.translatable("cobblemon.battle.singlemove.destinybond", pokemon.getDisplayName(false)));
      }
      case "grudge" -> {
        Marcas.marcar(pokemon, Marca.RANCOR, agora, Marcas.ATE_SAIR, 0, pokemon, golpe);
        avisar(pokemon, Component.translatable("cobblemon.battle.singlemove.grudge", pokemon.getDisplayName(false)));
      }
      case "magnetrise" -> {
        if (Marcas.tem(pokemon, Marca.ENRAIZADO)) {
          falhou(pokemon);
        } else {
          marcarUmaVez(pokemon, pokemon, Marca.LEVITANDO, agora, turnos(TURNOS_LEVITANDO),
              "cobblemon.battle.start.magnetrise");
        }
      }
      case "stockpile" -> estocar(execucao, atacante, agora);
      case "swallow" -> engolir(execucao, atacante);
      // Estes deixam a marca e os dados fazem o resto (os estágios, a cura do Poleiro)
      case "charge" -> {
        Marcas.marcar(pokemon, Marca.CARGA, agora, Marcas.ATE_SAIR, 0, pokemon, golpe);
        avisar(pokemon, Component.translatable("cobblemon.battle.start.charge", pokemon.getDisplayName(false)));
        return false;
      }
      case "minimize" -> {
        Marcas.marcar(pokemon, Marca.MINIMIZADO, agora, Marcas.ATE_SAIR, 0, pokemon, golpe);
        return false;
      }
      case "defensecurl" -> {
        Marcas.marcar(pokemon, Marca.ENROLADO, agora, Marcas.ATE_SAIR, 0, pokemon, golpe);
        return false;
      }
      case "roost" -> {
        if (AplicarStatus.temTipo(pokemon, ElementalTypes.FLYING)) {
          Marcas.marcar(pokemon, Marca.POLEIRO, agora, turnos(1), 0, pokemon, golpe);
          avisar(pokemon, Component.translatable("cobblemon.battle.singleturn.roost", pokemon.getDisplayName(false)));
        }
        return false;
      }
      case "noretreat" -> {
        Marcas.marcar(pokemon, Marca.NAO_FOGE, agora, Marcas.ATE_SAIR, 0, pokemon, golpe);
        avisar(pokemon, Component.translatable("cobblemon.battle.start.noretreat", pokemon.getDisplayName(false)));
        return false;
      }
      case "autotomize" -> {
        Dado antes = Marcas.dado(pokemon, Marca.AUTOTOMIA);
        Marcas.marcar(pokemon, Marca.AUTOTOMIA, agora, Marcas.ATE_SAIR, antes == null ? 1 : antes.valor() + 1, pokemon,
            golpe);
        return false;
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  // Marca uma vez só: já tem, falha
  private static void marcarUmaVez(Pokemon quemUsou, Pokemon quem, Marca marca, long agora, long ticks, String frase) {
    if (Marcas.tem(quem, marca)) {
      falhou(quemUsou);
      return;
    }
    Marcas.marcar(quem, marca, agora, ticks, 0, quemUsou, marca.name());
    CobblemonCombatZA.LOGGER.info("MARCA: {} ficou com {}", quem.getSpecies().getName(), marca);
    avisar(quem, Component.translatable(frase, quem.getDisplayName(false)));
    if (marca == Marca.ATORMENTADO || marca == Marca.SEM_CURA) {
      GolpesDesligados.avisarODono(quem);
    }
  }

  // Inabilitar: o último golpe do alvo (que ele ainda tem), 4 turnos
  private static void inabilitar(Pokemon pokemon, Pokemon doAlvo, long agora) {
    MoveTemplate ultimo = UsarOutroGolpe.ultimoGolpe(doAlvo);
    if (ultimo == null || Marcas.tem(doAlvo, Marca.INABILITADO) || !sabe(doAlvo, ultimo.getName())) {
      falhou(pokemon);
      return;
    }
    Marcas.marcar(doAlvo, Marca.INABILITADO, agora, turnos(TURNOS_INABILITADO), 0, pokemon, ultimo.getName());
    CobblemonCombatZA.LOGGER.info("INABILITAR: {} de {} por {} turnos", ultimo.getName(), doAlvo.getSpecies().getName(),
        TURNOS_INABILITADO);
    avisar(doAlvo, Component.translatable("cobblemon.battle.start.disable", doAlvo.getDisplayName(false),
        ultimo.getDisplayName()));
    GolpesDesligados.avisarODono(doAlvo);
  }

  private static boolean sabe(Pokemon pokemon, String golpe) {
    for (Move doPokemon : pokemon.getMoveSet().getMoves()) {
      if (doPokemon.getName().equals(golpe)) {
        return true;
      }
    }
    return false;
  }

  private static void semente(Pokemon pokemon, Pokemon doAlvo, long agora) {
    if (AplicarStatus.temTipo(doAlvo, ElementalTypes.GRASS)) {
      avisar(doAlvo, Component.translatable("cobblemon.battle.immune", doAlvo.getDisplayName(false)));
      return;
    }
    if (Marcas.tem(doAlvo, Marca.SEMENTE)) {
      falhou(pokemon);
      return;
    }
    Marcas.marcar(doAlvo, Marca.SEMENTE, agora, Marcas.ATE_SAIR, 0, pokemon, "leechseed");
    avisar(doAlvo, Component.translatable("cobblemon.battle.start.leechseed", doAlvo.getDisplayName(false)));
  }

  // Maldição: o Fantasma paga metade da vida e amaldiçoa o alvo; os outros tipos: Velocidade −1, Ataque e Defesa +1
  private static void maldicao(Execucao execucao, PokemonEntity atacante, Pokemon doAlvo, long agora) {
    Pokemon pokemon = execucao.getPokemon();
    if (!AplicarStatus.temTipo(pokemon, ElementalTypes.GHOST)) {
      AplicarEfeitos.mudarUmAtributo(execucao, atacante, "spe", -1);
      AplicarEfeitos.mudarUmAtributo(execucao, atacante, "atk", 1);
      AplicarEfeitos.mudarUmAtributo(execucao, atacante, "def", 1);
      return;
    }
    if (Marcas.tem(doAlvo, Marca.MALDICAO)) {
      falhou(pokemon);
      return;
    }
    Vida.ferir(atacante, pokemon.getMaxHealth() / 2.0);
    Marcas.marcar(doAlvo, Marca.MALDICAO, agora, Marcas.ATE_SAIR, 0, pokemon, "curse");
    avisar(doAlvo, Component.translatable("cobblemon.battle.start.curse", pokemon.getDisplayName(false),
        doAlvo.getDisplayName(false)));
  }

  // Olhar Malvado e companhia (o Fantasma sempre escapa); Chave de Polvo
  private static void prender(Pokemon pokemon, Pokemon doAlvo, Marca marca, long agora, String frase) {
    if (AplicarStatus.temTipo(doAlvo, ElementalTypes.GHOST)) {
      avisar(doAlvo, Component.translatable("cobblemon.battle.immune", doAlvo.getDisplayName(false)));
      return;
    }
    if (Marcas.tem(doAlvo, marca)) {
      falhou(pokemon);
      return;
    }
    Marcas.marcar(doAlvo, marca, agora, Marcas.ATE_SAIR, 0, pokemon, marca.name());
    avisar(doAlvo, Component.translatable(frase, doAlvo.getDisplayName(false)));
  }

  // Ataque Rancoroso: o último golpe do alvo descansa 4 s a mais (nos jogos, perde 4 PP; aqui o PP não gasta na batalha)
  private static void rancoroso(Pokemon pokemon, Pokemon doAlvo, long agora) {
    MoveTemplate ultimo = UsarOutroGolpe.ultimoGolpe(doAlvo);
    int slot = ultimo == null ? -1 : lugarDo(doAlvo, ultimo.getName());
    if (slot < 0) {
      falhou(pokemon);
      return;
    }
    Recargas.atrasar(doAlvo, slot, Math.round(4 * Balanceamento.SEGUNDOS_POR_PP * 20), agora);
    avisar(doAlvo, Component.translatable("cobblemon.battle.activate.spite", doAlvo.getDisplayName(false),
        ultimo.getDisplayName(), 4));
  }

  private static int lugarDo(Pokemon pokemon, String golpe) {
    for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
      Move doPokemon = pokemon.getMoveSet().get(slot);
      if (doPokemon != null && doPokemon.getName().equals(golpe)) {
        return slot;
      }
    }
    return -1;
  }

  private static void estocar(Execucao execucao, PokemonEntity atacante, long agora) {
    Pokemon pokemon = execucao.getPokemon();
    int antes = Marcas.valor(pokemon, Marca.ESTOCAGEM);
    if (antes >= ESTOCAGEM_MAXIMA) {
      falhou(pokemon);
      return;
    }
    Marcas.marcar(pokemon, Marca.ESTOCAGEM, agora, Marcas.ATE_SAIR, antes + 1, pokemon, "stockpile");
    avisar(pokemon, Component.translatable("cobblemon.battle.start.stockpile", pokemon.getDisplayName(false), antes + 1));
    AplicarEfeitos.mudarUmAtributo(execucao, atacante, "def", 1);
    AplicarEfeitos.mudarUmAtributo(execucao, atacante, "spd", 1);
  }

  // Engolir: 1 = 1/4 da vida, 2 = metade, 3 = tudo; a Estocagem acaba (e as subidas dela voltam)
  private static void engolir(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = execucao.getPokemon();
    int estocado = Marcas.valor(pokemon, Marca.ESTOCAGEM);
    if (estocado == 0 || Marcas.tem(pokemon, Marca.SEM_CURA)) {
      falhou(pokemon);
      return;
    }
    double parte = estocado == 1 ? 0.25 : estocado == 2 ? 0.5 : 1.0;
    int curou = Vida.curar(atacante, pokemon.getMaxHealth() * parte);
    avisar(pokemon, Component.translatable("cobblemon.battle.heal.generic", pokemon.getDisplayName(false))
        .append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
    gastarAEstocagem(pokemon);
  }

  // Engolir e Cuspir: a Estocagem acaba, e a Defesa e a Def. Esp. que ela subiu voltam
  public static void gastarAEstocagem(Pokemon pokemon) {
    int estocado = Marcas.valor(pokemon, Marca.ESTOCAGEM);
    if (estocado == 0) {
      return;
    }
    Marcas.tirar(pokemon, Marca.ESTOCAGEM);
    long ate = HabilidadesDeEstagio.ateQuando(pokemon);
    Estagios.mudar(pokemon, "def", -estocado, ate);
    Estagios.mudar(pokemon, "spd", -estocado, ate);
    avisar(pokemon, Component.translatable("cobblemon.battle.end.stockpile", pokemon.getDisplayName(false)));
  }

  // O Imprison mudou o que os inimigos podem usar: o losango deles é refeito
  private static void avisarOsInimigos(Pokemon pokemon) {
    Arena arena = Arenas.de(pokemon);
    if (arena == null) {
      return;
    }
    for (PokemonEntity entidade : arena.pokemons()) {
      if (!Lados.mesmoLado(entidade.getPokemon(), pokemon)) {
        GolpesDesligados.avisarODono(entidade.getPokemon());
      }
    }
  }

  // ---- Os turnos e o fim (Marcas) ----

  static void turno(Pokemon pokemon, Marca marca, Dado dado, long agora) {
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade == null || !HabilidadesQueReagem.dePe(entidade) || !GerenciarCombates.estaEmCombate(pokemon)) {
      return;
    }
    boolean semDanoIndireto = Habilidades.tem(pokemon, ESCUDO_MAGICO);
    switch (marca) {
      case SEMENTE -> {
        if (semDanoIndireto) {
          return;
        }
        int tira = (int) Math.max(1, Math.round(pokemon.getMaxHealth() * SEMENTE));
        avisar(pokemon, Component.translatable("cobblemon.battle.damage.leechseed", pokemon.getDisplayName(false)));
        HabilidadesQueReagem.ferir(entidade, tira);
        PokemonEntity plantou = dado.deQuem() == null ? null : dado.deQuem().getEntity();
        if (plantou != null && HabilidadesQueReagem.dePe(plantou) && !Marcas.tem(dado.deQuem(), Marca.SEM_CURA)) {
          Vida.curar(plantou, tira);
        }
      }
      case PESADELO -> {
        if (!"slp".equals(EfeitosDeStatus.siglaDoStatus(pokemon))) {
          Marcas.tirar(pokemon, Marca.PESADELO);
        } else if (!semDanoIndireto) {
          avisar(pokemon, Component.translatable("cobblemon.battle.damage.nightmare", pokemon.getDisplayName(false)));
          HabilidadesQueReagem.ferir(entidade, Math.max(1, Math.round(pokemon.getMaxHealth() * PESADELO)));
        }
      }
      case MALDICAO -> {
        if (!semDanoIndireto) {
          avisar(pokemon, Component.translatable("cobblemon.battle.damage.curse", pokemon.getDisplayName(false)));
          HabilidadesQueReagem.ferir(entidade, Math.max(1, Math.round(pokemon.getMaxHealth() * MALDICAO)));
        }
      }
      case ARCO_AQUA, ENRAIZADO -> {
        if (Vida.pvAtual(pokemon) < pokemon.getMaxHealth()) {
          int curou = Vida.curar(entidade, Math.max(1, pokemon.getMaxHealth() * ARCO_AQUA));
          if (curou > 0) {
            avisar(pokemon, Component.translatable("cobblemon.battle.heal.generic", pokemon.getDisplayName(false))
                .append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
          }
        }
      }
      case CHAVE_DE_POLVO -> {
        if (prendedorNaLuta(dado)) {
          HabilidadesDeEstagio.baixar(dado.deQuem(), pokemon, Map.of("def", -1, "spd", -1), false,
              nomeDoGolpe("octolock"));
        }
      }
      default -> {
        // as outras marcas não agem por turno
      }
    }
  }

  static void acabou(Pokemon pokemon, Marca marca) {
    switch (marca) {
      case BOCEJO -> {
        PokemonEntity entidade = pokemon.getEntity();
        if (entidade != null && pokemon.getStatus() == null && entidade.getServer() != null
            && GerenciarCombates.estaEmCombate(pokemon)) {
          AplicarStatus.porItem(entidade, "slp", nomeDoGolpe("yawn"),
              entidade.getServer().getTickCount());
        }
      }
      case INABILITADO -> {
        avisar(pokemon, Component.translatable("cobblemon.battle.end.disable", pokemon.getDisplayName(false)));
        GolpesDesligados.avisarODono(pokemon);
      }
      case SEM_CURA -> {
        avisar(pokemon, Component.translatable("cobblemon.battle.end.healblock", pokemon.getDisplayName(false)));
        GolpesDesligados.avisarODono(pokemon);
      }
      case EMBARGO -> avisar(pokemon, Component.translatable("cobblemon.battle.end.embargo", pokemon.getDisplayName(false)));
      case LEVITANDO -> avisar(pokemon, Component.translatable("cobblemon.battle.end.magnetrise",
          pokemon.getDisplayName(false)));
      case TELECINESE -> avisar(pokemon, Component.translatable("cobblemon.battle.end.telekinesis",
          pokemon.getDisplayName(false)));
      default -> {
        // as outras acabam em silêncio
      }
    }
  }

  // ---- As perguntas do resto do mod ----

  // Execucao: começou um golpe. O Laço do Destino e o Rancor acabam; o Aguçar e a Carga valem para ESTE golpe (o de dano / o
  // Elétrico) e acabam no próximo
  public static void comecouGolpe(Pokemon pokemon, Move golpe) {
    // O Cuspir gasta a Estocagem agora; a força dele (100 por estocado) fica guardada para a conta
    if ("spitup".equals(golpe.getName())) {
      CUSPINDO.put(pokemon.getUuid(), Marcas.valor(pokemon, Marca.ESTOCAGEM));
      gastarAEstocagem(pokemon);
    }
    Marcas.tirar(pokemon, Marca.LACO_DO_DESTINO);
    Marcas.tirar(pokemon, Marca.RANCOR);
    boolean deDano = golpe.getDamageCategory() != DamageCategories.INSTANCE.getSTATUS();
    armar(pokemon, Marca.AGUCADO, deDano);
    armar(pokemon, Marca.CARGA, deDano && ClimaNosGolpes.tipo(pokemon, golpe) == ElementalTypes.ELECTRIC);
  }

  private static void armar(Pokemon pokemon, Marca marca, boolean esteGolpeServe) {
    Dado dado = Marcas.dado(pokemon, marca);
    if (dado == null) {
      return;
    }
    if (dado.valor() > 0) {
      Marcas.tirar(pokemon, marca);
    } else if (esteGolpeServe) {
      dado.mudarValor(1);
    }
  }

  // GolpesDesligados: o golpe está riscado por uma marca?
  public static boolean bloqueia(Pokemon pokemon, Move golpe) {
    return porque(pokemon, golpe) != null;
  }

  // Por que está riscado (para a barra de ação); null = não está
  public static Component porque(Pokemon pokemon, Move golpe) {
    Component nome = pokemon.getDisplayName(false);
    Dado inabilitado = Marcas.dado(pokemon, Marca.INABILITADO);
    if (inabilitado != null && golpe.getName().equals(inabilitado.golpe())) {
      return Component.translatable("cobblemon.battle.cant.disable", nome, golpe.getDisplayName());
    }
    if (Marcas.tem(pokemon, Marca.ATORMENTADO)) {
      MoveTemplate ultimo = UsarOutroGolpe.ultimoGolpe(pokemon);
      if (ultimo != null && ultimo.getName().equals(golpe.getName()) && !"struggle".equals(golpe.getName())) {
        return Component.translatable(FRASE + "atormentado", nome, golpe.getDisplayName());
      }
    }
    if (Marcas.tem(pokemon, Marca.SEM_CURA) && EfeitosDosGolpes.temMarca(golpe, "heal")) {
      return Component.translatable("cobblemon.battle.cant.healblock", nome, golpe.getDisplayName());
    }
    if (aprisionado(pokemon, golpe)) {
      return Component.translatable("cobblemon.battle.cant.imprison", nome, golpe.getDisplayName());
    }
    // A Gravidade risca os golpes de voo e pulo (CampoDosGolpes)
    return CampoDosGolpes.bloqueia(pokemon, golpe);
  }

  private static boolean aprisionado(Pokemon pokemon, Move golpe) {
    for (Dado dado : Marcas.todas(Marca.APRISIONOU)) {
      Pokemon quem = dado.pokemon();
      if (!Lados.mesmoLado(quem, pokemon) && quem.getEntity() != null && sabe(quem, golpe.getName())) {
        return true;
      }
    }
    return false;
  }

  // Dano: a força a mais pelas marcas (Carga, Minimizar no alvo, Espiral de Defesa, Mãozinha)
  public static double poder(Pokemon atacante, Pokemon defensor, Move golpe, ElementalType tipo) {
    double multiplicador = 1.0;
    if (tipo == ElementalTypes.ELECTRIC && Marcas.valor(atacante, Marca.CARGA) > 0) {
      multiplicador *= 2;
    }
    if (PISAM.contains(golpe.getName()) && Marcas.tem(defensor, Marca.MINIMIZADO)) {
      multiplicador *= 2;
    }
    if (ROLAM.contains(golpe.getName()) && Marcas.tem(atacante, Marca.ENROLADO)) {
      multiplicador *= 2;
    }
    if (Marcas.tem(atacante, Marca.MAOZINHA)) {
      multiplicador *= 1.5;
    }
    return multiplicador;
  }

  // Dano: o estágio de crítico a mais (Focalizar Energia +2; o Bramido do Dragão de um aliado)
  public static int estagioDeCritico(Pokemon atacante) {
    return (Marcas.tem(atacante, Marca.FOCO) ? ESPIGAS_DE_FOCO : 0) + Marcas.valor(atacante, Marca.ANIMO_DO_DRAGAO);
  }

  // Dano: o golpe de dano depois do Aguçar é crítico
  public static boolean criticoCerto(Pokemon atacante) {
    return Marcas.valor(atacante, Marca.AGUCADO) > 0;
  }

  // Precisao: este golpe nesse alvo não erra (Mirar, Leitura Mental, Telecinese, Pisotear no Minimizado)
  public static boolean naoErra(Pokemon atacante, Move golpe, Pokemon alvo) {
    Dado mira = Marcas.dado(atacante, Marca.MIRA_TRAVADA);
    return mira != null && mira.deQuem() != null && mira.deQuem().getUuid().equals(alvo.getUuid())
        || Marcas.tem(alvo, Marca.TELECINESE) || PISAM.contains(golpe.getName()) && Marcas.tem(alvo, Marca.MINIMIZADO);
  }

  // Precisao: a evasão do identificado não conta
  public static boolean semEvasao(Pokemon alvo) {
    return Marcas.tem(alvo, Marca.IDENTIFICADO) || Marcas.tem(alvo, Marca.OLHO_MILAGROSO);
  }

  // Dano: o identificado perde a imunidade (Normal e Lutador no Fantasma; Psíquico no Sombrio)
  public static boolean perdeAImunidade(Pokemon defensor, ElementalType tipoDoGolpe, ElementalType tipoDoDefensor) {
    if (tipoDoDefensor == ElementalTypes.GHOST && (tipoDoGolpe == ElementalTypes.NORMAL
        || tipoDoGolpe == ElementalTypes.FIGHTING)) {
      return Marcas.tem(defensor, Marca.IDENTIFICADO);
    }
    return tipoDoDefensor == ElementalTypes.DARK && tipoDoGolpe == ElementalTypes.PSYCHIC
        && Marcas.tem(defensor, Marca.OLHO_MILAGROSO);
  }

  // Dano: no ar pela Levitação Magnética ou pela Telecinese (imune a Terra), menos o Enraizado
  public static boolean flutuando(Pokemon pokemon) {
    return (Marcas.tem(pokemon, Marca.LEVITANDO) || Marcas.tem(pokemon, Marca.TELECINESE))
        && !Marcas.tem(pokemon, Marca.ENRAIZADO) && !CampoDosGolpes.gravidade(pokemon);
  }

  // Dano: no turno do Poleiro, o tipo Voador não conta
  public static boolean semVoador(Pokemon pokemon) {
    return Marcas.tem(pokemon, Marca.POLEIRO);
  }

  // Dano: o Tiro de Piche deixa o alvo fraco a Fogo (×2 a mais)
  public static double contraFogo(Pokemon defensor, ElementalType tipoDoGolpe) {
    return tipoDoGolpe == ElementalTypes.FIRE && Marcas.tem(defensor, Marca.PICHE) ? 2.0 : 1.0;
  }

  // ClimaNosGolpes.tipo: eletrificado, o golpe dele é Elétrico (null = não)
  public static ElementalType tipoDoGolpe(Pokemon atacante) {
    return Marcas.tem(atacante, Marca.ELETRIFICADO) ? ElementalTypes.ELECTRIC : null;
  }

  // Vida.curar: o Bloqueio de Cura não deixa curar
  public static boolean semCura(Pokemon pokemon) {
    return Marcas.tem(pokemon, Marca.SEM_CURA);
  }

  // ItensSegurados: no Embargo, o item não faz nada
  public static boolean semItem(Pokemon pokemon) {
    return Marcas.tem(pokemon, Marca.EMBARGO);
  }

  // DanoEspecial: o peso que a Autotomia tirou
  public static int pesoTirado(Pokemon pokemon) {
    return Marcas.valor(pokemon, Marca.AUTOTOMIA) * AUTOTOMIA;
  }

  // Preso por um golpe (o Olhar Malvado de quem ainda está na luta, a Chave de Polvo, o Sem Recuo, o Enraizar)
  public static boolean naoFoge(Pokemon pokemon) {
    if (pokemon == null) {
      return false;
    }
    Dado olhar = Marcas.dado(pokemon, Marca.NAO_FOGE);
    Dado polvo = Marcas.dado(pokemon, Marca.CHAVE_DE_POLVO);
    return olhar != null && prendedorNaLuta(olhar) || polvo != null && prendedorNaLuta(polvo)
        || Marcas.tem(pokemon, Marca.ENRAIZADO) || CampoDosGolpes.ativo(pokemon, CampoDosGolpes.TRAVA_DE_FADA);
  }

  private static boolean prendedorNaLuta(Dado dado) {
    Pokemon quem = dado.deQuem();
    return quem == null || quem.getUuid().equals(dado.pokemon().getUuid())
        || quem.getEntity() != null && HabilidadesQueReagem.dePe(quem.getEntity());
  }

  // ControleDeGolpes.aplicarImpacto: o golpe de Fogo do coberto pela Pólvora Explosiva explode nele. true = o golpe falhou
  public static boolean explodiu(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = execucao.getPokemon();
    if (!Marcas.tem(pokemon, Marca.POLVORA)
        || ClimaNosGolpes.tipo(pokemon, execucao.getGolpe()) != ElementalTypes.FIRE) {
      return false;
    }
    Marcas.tirar(pokemon, Marca.POLVORA);
    avisar(pokemon, Component.translatable("cobblemon.battle.activate.powder"));
    if (!Habilidades.tem(pokemon, ESCUDO_MAGICO)) {
      HabilidadesQueReagem.ferir(atacante, Math.max(1, Math.round(pokemon.getMaxHealth() * POLVORA)));
    }
    return true;
  }

  // ControleDeGolpes, depois do dano: o Laço do Destino leva quem o derrubou junto; o Rancor tira o golpe de quem o derrubou
  public static void depoisDoDano(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity atingido) || !HabilidadesQueReagem.caiu(atingido)) {
      return;
    }
    Pokemon caido = atingido.getPokemon();
    Pokemon pokemon = execucao.getPokemon();
    if (Lados.mesmoLado(caido, pokemon)) {
      return;
    }
    if (Marcas.tirar(caido, Marca.LACO_DO_DESTINO) && HabilidadesQueReagem.dePe(atacante)) {
      avisar(caido, Component.translatable("cobblemon.battle.activate.destinybond", caido.getDisplayName(false)));
      CobblemonCombatZA.LOGGER.info("LAÇO DO DESTINO: {} levou {} junto", caido.getSpecies().getName(),
          pokemon.getSpecies().getName());
      HabilidadesQueReagem.ferir(atacante, Vida.pvAtual(pokemon));
    }
    if (Marcas.tirar(caido, Marca.RANCOR) && execucao.getSlot() >= 0) {
      long agora = agora(execucao);
      Marcas.marcar(pokemon, Marca.INABILITADO, agora, Marcas.ATE_SAIR, 0, caido, execucao.getGolpe().getName());
      avisar(pokemon, Component.translatable("cobblemon.battle.activate.grudge", pokemon.getDisplayName(false), "",
          execucao.getGolpe().getDisplayName()));
      GolpesDesligados.avisarODono(pokemon);
    }
  }

  // HabilidadesDeProtecao: a Capa Mágica devolve o golpe de status que o Espelho Mágico devolveria
  public static boolean capaMagica(Pokemon alvo, Move golpe) {
    return Marcas.tem(alvo, Marca.CAPA_MAGICA) && golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()
        && EfeitosDosGolpes.temMarca(golpe, "reflectable");
  }

  // ControleDeGolpes, no golpe sem alvo: o Arrebatador de um inimigo rouba o golpe que melhora quem usa (a marca "snatch").
  // Devolve quem roubou (o efeito vai para ele), ou null
  public static PokemonEntity arrebatador(Execucao execucao) {
    if (!EfeitosDosGolpes.temMarca(execucao.getGolpe(), "snatch")) {
      return null;
    }
    for (Dado dado : Marcas.todas(Marca.ARREBATADOR)) {
      Pokemon ladrao = dado.pokemon();
      PokemonEntity entidade = ladrao.getEntity();
      if (!Lados.mesmoLado(ladrao, execucao.getPokemon()) && entidade != null && HabilidadesQueReagem.dePe(entidade)) {
        Marcas.tirar(ladrao, Marca.ARREBATADOR);
        avisar(ladrao, Component.translatable("cobblemon.battle.activate.snatch", ladrao.getDisplayName(false),
            execucao.getPokemon().getDisplayName(false)));
        return entidade;
      }
    }
    return null;
  }

  // Para a frase do Bocejo e da Chave de Polvo: o nome do golpe pelo id
  static Component nomeDoGolpe(String golpe) {
    MoveTemplate modelo = Moves.getByName(golpe);
    return modelo == null ? Component.literal(golpe) : modelo.getDisplayName();
  }
}
