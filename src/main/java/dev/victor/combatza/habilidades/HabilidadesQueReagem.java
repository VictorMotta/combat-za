package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.golpes.AplicarEfeitos;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.Armadilhas;
import dev.victor.combatza.golpes.Dano;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.itens.ItensDeEstagio;
import dev.victor.combatza.itens.ItensSegurados;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 21/10 (habilidades, lote das reações; dele: "que habilidades são essas? Como assim só 94 funcionam?"): as habilidades que
// REAGEM a alguma coisa na batalha em tempo real, com os números do Showdown (o abilities.js que vem dentro do Cobblemon):
// - quem APANHOU de um golpe que tirou vida (ControleDeGolpes.acertar chama depoisDoAcerto): Estamina (Defesa +1), Armadura
//   Frágil (golpe físico: Defesa −1 e Velocidade +2), Hidrorreforço (Água: Defesa +2), Motor a Vapor (Fogo ou Água:
//   Velocidade +6), Justiceiro (Sombrio: Ataque +1), Covardia (Sombrio, Inseto ou Fantasma: Velocidade +1; intimidado
//   também), Ponto de Fúria (crítico: Ataque no máximo), Carapaça da Ira e Furioso (a vida passou da metade para baixo),
//   Pegajoso e Cabelo Bagunçado (contato: a Velocidade de quem bateu cai 1), Dispersar Algodão (a Velocidade de todos os
//   outros cai 1), Corpo Maldito (30%: o golpe que bateu fica desabilitado por 4 turnos, 14 s), Electromorfose e Energia
//   Eólica (golpe de vento) deixam "carregado" (o próximo golpe Elétrico ×2), Corpo Perecido (contato: os dois desmaiam em 3
//   turnos, ContagemDoPerecer), Furto (contato: rouba o item de quem bateu) e Detrito Tóxico (golpe físico: Espinhos Tóxicos
//   embaixo de quem bateu);
// - quem BATEU: Toque Venenoso (contato: 30% de envenenar), Corrente Tóxica (30% de envenenar muito) e Mágico (rouba o
//   item);
// - quem DERRUBOU um Pokémon com um golpe: Arrogância e Relincho Branco (Ataque +1), Relincho Negro (Ataque Especial +1) e
//   Ultraimpulso (o maior atributo +1); e quem CAIU: Resultado (contato: quem bateu perde 1/4 da vida) e Probóscide (quem
//   bateu perde a vida que ele ainda tinha);
// - Oportunista (o inimigo subiu atributos: ele sobe os mesmos; ItensDeEstagio.mudaram avisa).
// O roubo e o Alígero ficam em HabilidadesDosItens; as de cada turno (Acelerador, Mau-Humor...) e a Coralma, em
// HabilidadesDoTurno; as de prioridade, em HabilidadesDePrioridade. Aqui ficam também as ajudas que elas usam (mudar um
// atributo com a frase, avisar a batalha, os Pokémon de pé na batalha)
public class HabilidadesQueReagem {
  private static final String FRASE = "cobblemon_combat_za.habilidade.";
  // De quem apanhou
  private static final String ESTAMINA = "stamina";
  private static final String ARMADURA_FRAGIL = "weakarmor";
  private static final String HIDRORREFORCO = "watercompaction";
  private static final String MOTOR_A_VAPOR = "steamengine";
  private static final String JUSTICEIRO = "justified";
  private static final String COVARDIA = "rattled";
  private static final String PONTO_DE_FURIA = "angerpoint";
  private static final String CARAPACA_DA_IRA = "angershell";
  private static final String FURIOSO = "berserk";
  private static final String PEGAJOSO = "gooey";
  private static final String CABELO_BAGUNCADO = "tanglinghair";
  private static final String DISPERSAR_ALGODAO = "cottondown";
  private static final String CORPO_MALDITO = "cursedbody";
  private static final String ELETROMORFOSE = "electromorphosis";
  private static final String ENERGIA_EOLICA = "windpower";
  private static final String CORPO_PERECIDO = "perishbody";
  private static final String FURTO = "pickpocket";
  private static final String DETRITO_TOXICO = "toxicdebris";
  // De quem bateu
  private static final String TOQUE_VENENOSO = "poisontouch";
  private static final String CORRENTE_TOXICA = "toxicchain";
  private static final String MAGICO = "magician";
  // De quem derrubou e de quem caiu
  private static final String ARROGANCIA = "moxie";
  private static final String RELINCHO_BRANCO = "chillingneigh";
  private static final String RELINCHO_NEGRO = "grimneigh";
  // A Sinergia Equina (As One) do Calyrex montado: o Relincho do cavalo dele
  private static final String SINERGIA_GLASTRIER = "asoneglastrier";
  private static final String SINERGIA_SPECTRIER = "asonespectrier";
  private static final String ULTRAIMPULSO = "beastboost";
  private static final String RESULTADO = "aftermath";
  private static final String PROBOSCIDE = "innardsout";
  // As outras
  private static final String OPORTUNISTA = "opportunist";
  // As dos outros lotes que estas consultam (como no Showdown): Umidade segura o Resultado; Escudo Mágico segura o dano que
  // não é de golpe
  private static final String UMIDADE = "damp";
  private static final String ESCUDO_MAGICO = "magicguard";

  private static final int CHANCE_DE_DESABILITAR = 30;
  private static final int CHANCE_DO_VENENO = 30;
  private static final int TURNOS_DESABILITADO = 4;
  private static final int RESULTADO_TIRA = 4;
  private static final String ESPINHOS_TOXICOS = "toxicspikes";
  // Fora da batalha, a mudança de atributo dura 30 s (na batalha, até ela acabar), como a dos itens
  private static final long SEGUNDOS_FORA_DA_BATALHA = 30;
  private static final List<String> ORDEM_DOS_ESTAGIOS = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");
  // O Ultraimpulso: o maior atributo (sem os estágios), e no empate o primeiro desta ordem, como no Showdown
  private static final List<String> ATRIBUTOS = List.of("atk", "def", "spa", "spd", "spe");

  // O golpe desabilitado pelo Corpo Maldito (até "ate"; voltou para a bola, acabou)
  private record Desabilitado(String golpe, PokemonEntity entidade, long ate) {
  }

  private static final Map<UUID, Desabilitado> DESABILITADOS = new HashMap<>();
  // "Carregado" (Electromorfose, Energia Eólica): o próximo golpe Elétrico ×2. Vale para a entidade (voltou para a bola,
  // acabou)
  private static final Map<UUID, PokemonEntity> CARREGADOS = new HashMap<>();
  // O golpe Elétrico que gastou a carga (vale para todos os acertos dele, até o próximo uso)
  private static final Map<UUID, Move> GOLPE_CARREGADO = new HashMap<>();

  private HabilidadesQueReagem() {
  }

  // ControleDeGolpes.acertar, depois do dano e dos itens. "primeiro": o primeiro acerto do golpe (o jato acerta várias
  // vezes; as reações valem uma vez por golpe, mas o golpe que derruba pode ser qualquer pedaço)
  public static void depoisDoAcerto(Execucao execucao, PokemonEntity atacante, Entity alvo, Dano.Resultado resultado,
      double pvTirados, boolean primeiro) {
    if (pvTirados <= 0 || !(alvo instanceof PokemonEntity atingido) || atingido == atacante) {
      return;
    }
    if (caiu(atingido)) {
      derrubou(execucao, atacante, atingido, pvTirados);
      return;
    }
    if (!primeiro) {
      return;
    }
    deQuemApanhou(execucao, atacante, atingido, resultado, pvTirados);
    if (atacante.isAlive() && !caiu(atacante)) {
      deQuemBateu(execucao, atacante, atingido, resultado);
    }
  }

  // Showdown, onDamagingHit (e o onHit do Ponto de Fúria, o onAfterMoveSecondary do Furioso, da Carapaça e do Furto)
  private static void deQuemApanhou(Execucao execucao, PokemonEntity atacante, PokemonEntity atingido,
      Dano.Resultado resultado, double pvTirados) {
    Pokemon defensor = atingido.getPokemon();
    Pokemon doAtacante = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    ElementalType tipo = ClimaNosGolpes.tipo(doAtacante, golpe);
    boolean fisico = golpe.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
    boolean contato = ItensSegurados.fazContato(doAtacante, golpe) && atacante.isAlive();
    Component deOnde = deOnde(defensor);
    switch (Habilidades.atual(defensor)) {
      case ESTAMINA -> mudar(defensor, Map.of("def", 1), deOnde);
      case ARMADURA_FRAGIL -> {
        if (fisico) {
          mudar(defensor, Map.of("def", -1, "spe", 2), deOnde);
        }
      }
      case HIDRORREFORCO -> {
        if (tipo == ElementalTypes.WATER) {
          mudar(defensor, Map.of("def", 2), deOnde);
        }
      }
      case MOTOR_A_VAPOR -> {
        if (tipo == ElementalTypes.WATER || tipo == ElementalTypes.FIRE) {
          mudar(defensor, Map.of("spe", 6), deOnde);
        }
      }
      case JUSTICEIRO -> {
        if (tipo == ElementalTypes.DARK) {
          mudar(defensor, Map.of("atk", 1), deOnde);
        }
      }
      case COVARDIA -> {
        if (tipo == ElementalTypes.DARK || tipo == ElementalTypes.BUG || tipo == ElementalTypes.GHOST) {
          mudar(defensor, Map.of("spe", 1), deOnde);
        }
      }
      case PONTO_DE_FURIA -> {
        if (resultado.critico()) {
          pontoDeFuria(defensor);
        }
      }
      case CARAPACA_DA_IRA -> {
        if (passouDaMetade(defensor, pvTirados)) {
          mudar(defensor, Map.of("atk", 1, "spa", 1, "spe", 1, "def", -1, "spd", -1), deOnde);
        }
      }
      case FURIOSO -> {
        if (passouDaMetade(defensor, pvTirados)) {
          mudar(defensor, Map.of("spa", 1), deOnde);
        }
      }
      case PEGAJOSO, CABELO_BAGUNCADO -> {
        if (contato) {
          baixarDeOutro(defensor, doAtacante, "spe", -1, deOnde);
        }
      }
      case DISPERSAR_ALGODAO -> dispersarAlgodao(atingido, deOnde);
      case CORPO_MALDITO -> corpoMaldito(execucao, atacante, deOnde);
      case ELETROMORFOSE -> carregar(atingido, deOnde);
      case ENERGIA_EOLICA -> {
        if (EfeitosDosGolpes.temMarca(golpe, "wind")) {
          carregar(atingido, deOnde);
        }
      }
      case CORPO_PERECIDO -> {
        if (contato) {
          ContagemDoPerecer.corpoPerecido(atingido, atacante, deOnde);
        }
      }
      case FURTO -> {
        if (contato) {
          HabilidadesDosItens.roubar(defensor, doAtacante, deOnde);
        }
      }
      case DETRITO_TOXICO -> {
        if (fisico) {
          detritoToxico(execucao, atingido, atacante);
        }
      }
      default -> {
        // as outras habilidades não reagem ao golpe
      }
    }
  }

  // Showdown, onSourceDamagingHit (Toque Venenoso, Corrente Tóxica) e onAfterMoveSecondarySelf (Mágico)
  private static void deQuemBateu(Execucao execucao, PokemonEntity atacante, PokemonEntity atingido,
      Dano.Resultado resultado) {
    Pokemon doAtacante = execucao.getPokemon();
    switch (Habilidades.atual(doAtacante)) {
      case TOQUE_VENENOSO -> {
        if (ItensSegurados.fazContato(doAtacante, execucao.getGolpe())) {
          envenenar(execucao, atacante, atingido, "psn");
        }
      }
      case CORRENTE_TOXICA -> envenenar(execucao, atacante, atingido, "tox");
      case MAGICO -> {
        // a mão ficou vazia porque a Gema acabou de ser gasta neste golpe: não rouba (como no Showdown)
        if (!resultado.gema()) {
          HabilidadesDosItens.roubar(doAtacante, atingido.getPokemon(), deOnde(doAtacante));
        }
      }
      default -> {
        // as outras habilidades não reagem ao próprio golpe
      }
    }
  }

  // Showdown, onSourceAfterFaint (quem derrubou) e onDamagingHit com a vida zerada (quem caiu)
  private static void derrubou(Execucao execucao, PokemonEntity atacante, PokemonEntity atingido, double pvTirados) {
    Pokemon caido = atingido.getPokemon();
    Pokemon doAtacante = execucao.getPokemon();
    String habilidade = Habilidades.atual(caido);
    if (RESULTADO.equals(habilidade) && ItensSegurados.fazContato(doAtacante, execucao.getGolpe())
        && !temUmidade(caido)) {
      ferirQuemBateu(atacante, caido, (double) doAtacante.getMaxHealth() / RESULTADO_TIRA);
    } else if (PROBOSCIDE.equals(habilidade)) {
      // a vida que ele tinha: o que o golpe tirou (o selvagem caído no chão fica com 1 PV, Desmaio)
      ferirQuemBateu(atacante, caido, pvTirados + (Desmaio.esta(atingido) ? 1 : 0));
    }
    if (!atacante.isAlive() || caiu(atacante)) {
      return;
    }
    Component deOnde = deOnde(doAtacante);
    switch (Habilidades.atual(doAtacante)) {
      case ARROGANCIA, RELINCHO_BRANCO, SINERGIA_GLASTRIER -> mudar(doAtacante, Map.of("atk", 1), deOnde);
      case RELINCHO_NEGRO, SINERGIA_SPECTRIER -> mudar(doAtacante, Map.of("spa", 1), deOnde);
      case ULTRAIMPULSO -> mudar(doAtacante, Map.of(melhorAtributo(doAtacante), 1), deOnde);
      default -> {
        // as outras habilidades não reagem a derrubar
      }
    }
  }

  // A Carapaça da Ira e o Furioso: antes do golpe, mais da metade da vida; depois, a metade ou menos
  private static boolean passouDaMetade(Pokemon pokemon, double pvTirados) {
    double metade = pokemon.getMaxHealth() / 2.0;
    double depois = Vida.pvAtual(pokemon);
    return depois <= metade && depois + pvTirados > metade;
  }

  // Ponto de Fúria: o crítico deixa o Ataque no máximo, com a frase do Cobblemon ("maximizou seu Ataque")
  private static void pontoDeFuria(Pokemon pokemon) {
    int mudou = Estagios.mudar(pokemon, "atk", Estagios.MAXIMO - Estagios.MINIMO, quandoAcaba(pokemon));
    avisar(pokemon, Component.translatable("cobblemon.battle.setboost.angerpoint", pokemon.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} levou um crítico e maximizou o Ataque (Ponto de Fúria)",
        pokemon.getSpecies().getName());
    if (mudou != 0) {
      ItensDeEstagio.mudaram(pokemon, Map.of("atk", mudou));
    }
  }

  // Dispersar Algodão: a Velocidade de todos os outros da batalha cai 1 (até a dos aliados, como no Showdown)
  private static void dispersarAlgodao(PokemonEntity dono, Component deOnde) {
    Combate combate = GerenciarCombates.combateDe(dono.getPokemon());
    if (combate == null) {
      return;
    }
    for (PokemonEntity outro : naLuta(combate)) {
      if (outro != dono) {
        baixarDeOutro(dono.getPokemon(), outro.getPokemon(), "spe", -1, deOnde);
      }
    }
  }

  // Corpo Maldito: 30% de desabilitar o golpe que bateu por 4 turnos (14 s); quem já tem um golpe desabilitado, não
  private static void corpoMaldito(Execucao execucao, PokemonEntity atacante, Component deOnde) {
    Pokemon doAtacante = execucao.getPokemon();
    Desabilitado antes = DESABILITADOS.get(doAtacante.getUuid());
    if (!atacante.isAlive() || antes != null && antes.entidade() == atacante
        || atacante.getRandom().nextInt(100) >= CHANCE_DE_DESABILITAR) {
      return;
    }
    Move golpe = execucao.getGolpe();
    long ate = agora(doAtacante) + TURNOS_DESABILITADO * EfeitosDeStatus.ticksPorTurno();
    DESABILITADOS.put(doAtacante.getUuid(), new Desabilitado(golpe.getName(), atacante, ate));
    avisar(doAtacante, Component.translatable("cobblemon.battle.start.disable", doAtacante.getDisplayName(false),
        golpe.getDisplayName()).append(" (").append(deOnde).append(")"));
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} de {} desabilitado por {} turnos (Corpo Maldito)", golpe.getName(),
        doAtacante.getSpecies().getName(), TURNOS_DESABILITADO);
  }

  // O golpe está desabilitado agora? (TravaDaEscolha.bloqueia: o seu, o do selvagem e o do guarda)
  public static boolean desabilitado(Pokemon pokemon, Move golpe) {
    Desabilitado desabilitado = DESABILITADOS.get(pokemon.getUuid());
    return desabilitado != null && desabilitado.entidade() == pokemon.getEntity()
        && desabilitado.golpe().equals(golpe.getName());
  }

  // Electromorfose e Energia Eólica: "carregado" (frase do Cobblemon: "começou a carregar energia")
  private static void carregar(PokemonEntity entidade, Component deOnde) {
    Pokemon pokemon = entidade.getPokemon();
    CARREGADOS.put(pokemon.getUuid(), entidade);
    avisar(pokemon, Component.translatable("cobblemon.battle.start.charge", pokemon.getDisplayName(false)).append(" (")
        .append(deOnde).append(")"));
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} ficou carregado ({})", pokemon.getSpecies().getName(),
        Habilidades.atual(pokemon));
  }

  // Energia Eólica: o Vento de Cauda começou do lado dela (CampoEBarreiras)
  public static void ventoDeCauda(List<PokemonEntity> pokemons, boolean ladoDoJogador) {
    for (PokemonEntity entidade : pokemons) {
      Pokemon pokemon = entidade.getPokemon();
      if (CampoEBarreiras.doSeuLado(pokemon) == ladoDoJogador && Habilidades.tem(pokemon, ENERGIA_EOLICA)) {
        carregar(entidade, deOnde(pokemon));
      }
    }
  }

  // Um golpe começou (Execucao; a primeira carga, não cada acerto). Carregado e golpe Elétrico (até o de status, como no
  // Showdown): a carga passa para este golpe. O golpe que gastou a carga perde o ×2 quando é usado de novo
  public static void comecouGolpe(Pokemon pokemon, Move golpe) {
    UUID uuid = pokemon.getUuid();
    if (GOLPE_CARREGADO.get(uuid) == golpe) {
      GOLPE_CARREGADO.remove(uuid);
    }
    PokemonEntity carregado = CARREGADOS.get(uuid);
    if (carregado != null && carregado == pokemon.getEntity()
        && ClimaNosGolpes.tipo(pokemon, golpe) == ElementalTypes.ELECTRIC) {
      CARREGADOS.remove(uuid);
      GOLPE_CARREGADO.put(uuid, golpe);
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} gastou a carga em {} (×2)", pokemon.getSpecies().getName(),
          golpe.getName());
    }
  }

  // HabilidadesDeDano.poder: o golpe Elétrico que gastou a carga sai ×2
  public static double carga(Pokemon atacante, Move golpe) {
    return GOLPE_CARREGADO.get(atacante.getUuid()) == golpe ? 2.0 : 1.0;
  }

  // Toque Venenoso e Corrente Tóxica: 30%; o Escudo de Poeira e a Capa Furtiva seguram (em silêncio, como no Showdown)
  private static void envenenar(Execucao execucao, PokemonEntity atacante, PokemonEntity atingido, String sigla) {
    Pokemon defensor = atingido.getPokemon();
    if (Habilidades.tem(defensor, Habilidades.ESCUDO_DE_POEIRA) || ItensSegurados.tem(defensor, ItensSegurados.CAPA_FURTIVA)
        || atacante.getRandom().nextInt(100) >= CHANCE_DO_VENENO) {
      return;
    }
    AplicarStatus.pelaHabilidade(execucao, atingido, sigla, deOnde(execucao.getPokemon()));
  }

  // Detrito Tóxico: Espinhos Tóxicos embaixo de quem bateu, do lado do dono da habilidade (até 2 camadas: veneno forte). A
  // armadilha precisa de um golpe "de quem colocou": uma Execucao de Espinhos Tóxicos do dono, sem carga (só para isso)
  private static void detritoToxico(Execucao execucao, PokemonEntity dono, PokemonEntity atacante) {
    MoveTemplate modelo = Moves.getByName(ESPINHOS_TOXICOS);
    if (modelo == null) {
      return;
    }
    Pokemon pokemon = dono.getPokemon();
    Execucao daHabilidade = Execucao.daIa(execucao.getJogador(), pokemon, modelo.create(), 0, agora(pokemon), atacante,
        true);
    avisar(pokemon, Component.translatable("cobblemon.battle.ability.generic", pokemon.getDisplayName(false),
        Habilidades.nome(pokemon)));
    Armadilhas.toxicosEmbaixo(daHabilidade, atacante);
  }

  // ItensDeEstagio.mudaram: "quemSubiu" subiu atributos (não numa cópia da Erva Imitadora ou do próprio Oportunista). Cada
  // inimigo com Oportunista sobe os mesmos (e isso não faz ninguém copiar de volta)
  public static void oportunista(Pokemon quemSubiu, Map<String, Integer> subidas) {
    Combate combate = GerenciarCombates.combateDe(quemSubiu);
    if (combate == null) {
      return;
    }
    for (PokemonEntity entidade : naLuta(combate)) {
      Pokemon pokemon = entidade.getPokemon();
      if (!Lados.mesmoLado(pokemon, quemSubiu) && Habilidades.tem(pokemon, OPORTUNISTA)) {
        ItensDeEstagio.semCopiar(() -> mudar(pokemon, subidas, deOnde(pokemon)));
      }
    }
  }

  // Intimidacao: a Covardia sobe a Velocidade de quem foi intimidado (Geração 8 em diante)
  public static void intimidado(Pokemon vitima) {
    if (Habilidades.tem(vitima, COVARDIA)) {
      mudar(vitima, Map.of("spe", 1), deOnde(vitima));
    }
  }

  // A habilidade mexeu nos atributos de "pokemon": muda e avisa como os golpes, com a habilidade no fim ("A Defesa de
  // Mudsdale subiu! (Estamina de Mudsdale)"). As reações aos atributos (Erva Branca, Erva Imitadora, Oportunista) veem tudo
  // junto
  public static void mudar(Pokemon pokemon, Map<String, Integer> pedidos, Component deOnde) {
    long ate = quandoAcaba(pokemon);
    Map<String, Integer> mudaram = new LinkedHashMap<>();
    for (String atributo : ORDEM_DOS_ESTAGIOS) {
      Integer quanto = pedidos.get(atributo);
      if (quanto == null) {
        continue;
      }
      int mudou = Estagios.mudar(pokemon, atributo, quanto, ate);
      avisar(pokemon, Component.translatable(Estagios.FRASE + AplicarEfeitos.comoMudou(quanto, mudou),
          Estagios.nomeDoAtributo(atributo, pokemon)).append(" (").append(deOnde).append(")"));
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} {} {} (agora {}; {})", pokemon.getSpecies().getName(), atributo,
          quanto > 0 ? "+" + quanto : quanto, Estagios.de(pokemon, atributo), deOnde.getString());
      if (mudou != 0) {
        mudaram.put(atributo, mudou);
      }
    }
    if (!mudaram.isEmpty()) {
      ItensDeEstagio.mudaram(pokemon, mudaram);
    }
  }

  // A queda que vem da habilidade de OUTRO Pokémon (Pegajoso, Cabelo Bagunçado, Dispersar Algodão): a mesma de todas as
  // habilidades (HabilidadesDeEstagio.baixar: o Corpo Claro, o Amuleto Purificador, a Névoa, a Armadura Espelhada que devolve,
  // e o Competitivo e o Desafiador de quem caiu)
  private static void baixarDeOutro(Pokemon dono, Pokemon vitima, String atributo, int quanto, Component deOnde) {
    HabilidadesDeEstagio.baixar(dono, vitima, Map.of(atributo, quanto), false, deOnde);
  }

  // Ultraimpulso: o maior atributo, sem os estágios
  private static String melhorAtributo(Pokemon pokemon) {
    String melhor = ATRIBUTOS.get(0);
    int maior = -1;
    for (String atributo : ATRIBUTOS) {
      int valor = switch (atributo) {
        case "atk" -> pokemon.getAttack();
        case "def" -> pokemon.getDefence();
        case "spa" -> pokemon.getSpecialAttack();
        case "spd" -> pokemon.getSpecialDefence();
        default -> pokemon.getSpeed();
      };
      if (valor > maior) {
        maior = valor;
        melhor = atributo;
      }
    }
    return melhor;
  }

  // Resultado e Probóscide: quem bateu perde vida (o Escudo Mágico segura: não é dano de golpe)
  private static void ferirQuemBateu(PokemonEntity atacante, Pokemon dono, double pv) {
    Pokemon vitima = atacante.getPokemon();
    if (!atacante.isAlive() || caiu(atacante) || Habilidades.tem(vitima, ESCUDO_MAGICO)) {
      return;
    }
    int perde = (int) Math.max(1, Math.round(pv));
    avisar(vitima, Component.translatable(FRASE + "feriu", vitima.getDisplayName(false), deOnde(dono), perde));
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} perdeu {} PV ({})", vitima.getSpecies().getName(), perde,
        Habilidades.atual(dono));
    ferir(atacante, perde);
  }

  // A Umidade de qualquer Pokémon da batalha segura o Resultado
  private static boolean temUmidade(Pokemon caido) {
    Combate combate = GerenciarCombates.combateDe(caido);
    if (combate == null) {
      return false;
    }
    for (PokemonEntity entidade : naLuta(combate)) {
      if (Habilidades.tem(entidade.getPokemon(), UMIDADE)) {
        return true;
      }
    }
    return false;
  }

  // Dano de habilidade (Resultado, Probóscide, Pesadelos, Corpo Perecido) em PV exato (o selvagem que cai na nossa batalha
  // fica caído no chão: Vida.ferir)
  public static void ferir(PokemonEntity entidade, double pv) {
    Vida.ferir(entidade, pv);
  }

  // Os Pokémon de pé na batalha: os selvagens dela e o lutador de cada jogador (os guardas não contam)
  public static List<PokemonEntity> naLuta(Combate combate) {
    List<PokemonEntity> todos = new ArrayList<>();
    for (PokemonEntity selvagem : combate.getSelvagens()) {
      if (dePe(selvagem)) {
        todos.add(selvagem);
      }
    }
    for (ServerPlayer jogador : combate.getJogadores()) {
      Pokemon lutador = Lutadores.de(jogador);
      if (lutador != null && lutador.getEntity() != null && dePe(lutador.getEntity())) {
        todos.add(lutador.getEntity());
      }
    }
    return todos;
  }

  public static boolean dePe(PokemonEntity entidade) {
    return entidade.isAlive() && !entidade.isRemoved() && !caiu(entidade);
  }

  // Desmaiou (o seu) ou caiu no chão (o selvagem da nossa batalha, Desmaio), ou morreu
  public static boolean caiu(PokemonEntity entidade) {
    // O Pokémon do treinador derrubado não morre nem fica no chão: volta para a bola (BatalhasDeTreinador)
    return entidade.getPokemon().isFainted() || Desmaio.esta(entidade) || entidade.isDeadOrDying()
        || BatalhasDeTreinador.desmaiou(entidade);
  }

  // "Estamina de Mudsdale" (a habilidade e o dono)
  public static Component deOnde(Pokemon pokemon) {
    return Component.translatable(FRASE + "de", Habilidades.nome(pokemon), pokemon.getDisplayName(false));
  }

  // A frase vai para o histórico de quem está na batalha dele (fora de uma batalha, nenhuma)
  public static void avisar(Pokemon pokemon, Component frase) {
    Combate combate = GerenciarCombates.combateDe(pokemon);
    if (combate == null) {
      return;
    }
    for (ServerPlayer jogador : combate.getJogadores()) {
      Feedback.historico(jogador, pokemon, frase);
    }
  }

  // A cada tick (HabilidadesDoTurno): o golpe desabilitado volta depois de 4 turnos; quem voltou para a bola perde o
  // desabilitado e a carga, como nos jogos
  public static void atualizar(long agora) {
    Iterator<Desabilitado> desabilitados = DESABILITADOS.values().iterator();
    while (desabilitados.hasNext()) {
      Desabilitado desabilitado = desabilitados.next();
      PokemonEntity entidade = desabilitado.entidade();
      Pokemon pokemon = entidade.getPokemon();
      if (pokemon.getEntity() != entidade || entidade.isRemoved()) {
        desabilitados.remove();
      } else if (agora >= desabilitado.ate()) {
        desabilitados.remove();
        avisar(pokemon, Component.translatable("cobblemon.battle.end.disable", pokemon.getDisplayName(false)));
        CobblemonCombatZA.LOGGER.info("HABILIDADE: {} pode usar {} de novo", pokemon.getSpecies().getName(),
            desabilitado.golpe());
      }
    }
    CARREGADOS.values().removeIf(entidade -> entidade.isRemoved() || entidade.getPokemon().getEntity() != entidade);
  }

  private static long quandoAcaba(Pokemon pokemon) {
    return agora(pokemon) + SEGUNDOS_FORA_DA_BATALHA * SharedConstants.TICKS_PER_SECOND;
  }

  private static long agora(Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    return entidade != null && entidade.getServer() != null ? entidade.getServer().getTickCount() : 0;
  }

  // ItensSegurados.esquecer: a batalha começou ou acabou para ele (ou o selvagem saiu dela). O item roubado volta
  // (HabilidadesDosItens)
  public static void esquecer(Pokemon pokemon) {
    UUID uuid = pokemon.getUuid();
    DESABILITADOS.remove(uuid);
    CARREGADOS.remove(uuid);
    GOLPE_CARREGADO.remove(uuid);
    ContagemDoPerecer.esquecer(pokemon);
    HabilidadesDosItens.esquecer(pokemon);
  }

  public static void limpar() {
    DESABILITADOS.clear();
    CARREGADOS.clear();
    GOLPE_CARREGADO.clear();
    ContagemDoPerecer.limpar();
    HabilidadesDosItens.limpar();
  }
}
