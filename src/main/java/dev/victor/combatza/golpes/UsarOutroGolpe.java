package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.habilidades.Disfarces;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaDupla;
import dev.victor.combatza.habilidades.HabilidadesDeForma;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.habilidades.TrocaDeHabilidade;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

// 01/11 (as pendências, decisão dele: "como nos jogos"): os golpes que USAM OUTRO GOLPE ou copiam um golpe, e o Dançarino.
// O golpe chamado começa quando o que chamou acerta, sem carga, na mesma mira (Execucao.chamadoPor); a recarga no fim é a do
// lugar do golpe que chamou, pelo tempo do golpe chamado:
// - Metrônomo: um golpe sorteado entre os que o Metrônomo dos jogos pode chamar (a marca "metronome" do Showdown);
// - Imitador: o último golpe usado na batalha (por qualquer um); Ataque Espelhado: o último golpe do alvo;
// - Eu Primeiro: o golpe de dano que o alvo está carregando agora, com 1,5× de força (o alvo já soltou: falha);
// - Auxílio: um golpe sorteado dos outros Pokémon do seu time (o selvagem e o do treinador não têm time: falha);
// - Poder da Natureza: pelo campo (Elétrico: Relâmpago; de Plantas: Bola de Energia; Enevoado: Explosão Lunar; Psíquico:
//   Psíquico; sem campo: Tri-Ataque);
// - Ataque Sonâmbulo: só dormindo (a tecla funciona dormindo, como a do Ronco), um dos outros golpes dele, sorteado;
// - Instrução: o alvo repete o último golpe dele agora, de graça (sem gastar a recarga), se estiver livre;
// - Imitar: o último golpe do alvo entra no lugar do Imitar até ele voltar para a bola, desmaiar ou a batalha acabar;
// - Rascunho: o mesmo, PARA SEMPRE (como nos jogos; decisão dele);
// - Transformação: vira o alvo, como o Impostor (habilidades.Disfarces); Decalque: ele e os aliados pegam a habilidade do alvo;
// - Dançarino: quando alguém na batalha usa uma dança (a marca "dance"), quem tem Dançarino e está livre dança junto, de
//   graça (a dança que é num alvo vai em quem dançou primeiro, se for inimigo; senão no alvo dele)
public final class UsarOutroGolpe {
  private static final double PODER_DO_EU_PRIMEIRO = 1.5;
  private static final String DANCARINO = "dancer";
  private static final Set<String> NAO_RASCUNHA = Set.of("sketch", "struggle", "chatter");

  // O último golpe de cada Pokémon (a mira dele e o lugar, para a Instrução)
  private record Ultimo(MoveTemplate modelo, int slot, HitResult mira) {
  }

  private static final Map<UUID, Ultimo> ULTIMO = new HashMap<>();
  // O último golpe usado em cada batalha (o Imitador)
  private static final Map<Combate, MoveTemplate> ULTIMO_NA_LUTA = new WeakHashMap<>();
  // Os golpes do Eu Primeiro (1,5× de força)
  private static final Set<Move> DO_EU_PRIMEIRO = Collections.newSetFromMap(new WeakHashMap<>());
  private static List<MoveTemplate> doMetronomo;

  private UsarOutroGolpe() {
  }

  // AgendaDeGolpes, no impacto: o golpe que ele acabou de usar
  public static void usou(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    MoveTemplate modelo = execucao.getGolpe().getTemplate();
    ULTIMO.put(pokemon.getUuid(), new Ultimo(modelo, execucao.getSlot(), execucao.getMirada()));
    Combate combate = GerenciarCombates.combateDe(pokemon);
    if (combate != null) {
      ULTIMO_NA_LUTA.put(combate, modelo);
    }
    // O Atormentar risca o golpe que ele acabou de usar
    if (Marcas.tem(pokemon, Marcas.Marca.ATORMENTADO)) {
      GolpesDesligados.avisarODono(pokemon);
    }
  }

  // O último golpe que ele usou (Inabilitar, Atormentar, Ataque Rancoroso); null = nenhum desde que entrou
  public static MoveTemplate ultimoGolpe(Pokemon pokemon) {
    Ultimo ultimo = ULTIMO.get(pokemon.getUuid());
    return ultimo == null ? null : ultimo.modelo();
  }

  // ControleDeGolpes.usarGolpe: dormindo, a tecla do Ataque Sonâmbulo e a do Ronco funcionam
  public static boolean usaDormindo(Pokemon pokemon, Move golpe) {
    String nome = golpe.getName();
    return ("sleeptalk".equals(nome) || "snore".equals(nome)) && dormindo(pokemon);
  }

  private static boolean dormindo(Pokemon pokemon) {
    return "slp".equals(EfeitosDeStatus.siglaDoStatus(pokemon)) || HabilidadesDeForma.contaComoDormindo(pokemon);
  }

  // Dano: o golpe do Eu Primeiro sai 1,5× mais forte
  public static double poder(Move golpe) {
    return !DO_EU_PRIMEIRO.isEmpty() && DO_EU_PRIMEIRO.contains(golpe) ? PODER_DO_EU_PRIMEIRO : 1.0;
  }

  // O golpe sem alvo (GolpesDeStatus.semAlvo). true = era um destes
  public static boolean semAlvo(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = execucao.getPokemon();
    MoveTemplate escolhido;
    switch (execucao.getGolpe().getName()) {
      case "metronome" -> escolhido = sortear(atacante, metronomo());
      case "copycat" -> {
        Combate combate = GerenciarCombates.combateDe(pokemon);
        MoveTemplate ultimo = combate == null ? null : ULTIMO_NA_LUTA.get(combate);
        escolhido = ultimo == null || EfeitosDosGolpes.comMarca("failcopycat").contains(ultimo.getName()) ? null : ultimo;
      }
      case "assist" -> escolhido = sortear(atacante, doTime(pokemon));
      case "naturepower" -> escolhido = Moves.getByName(peloCampo(pokemon));
      case "sleeptalk" -> escolhido = dormindo(pokemon) ? sortear(atacante, dormindoPodeUsar(pokemon)) : null;
      default -> {
        return false;
      }
    }
    chamarOuFalhar(execucao, escolhido, execucao.getMirada());
    return true;
  }

  // O golpe no alvo (GolpesDeStatus.depoisDeAcertar). true = era um destes
  public static boolean noAlvo(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    String nome = execucao.getGolpe().getName();
    if (!Set.of("mirrormove", "mefirst", "instruct", "mimic", "sketch", "transform", "doodle").contains(nome)) {
      return false;
    }
    if (!(alvo instanceof PokemonEntity atingido) || HabilidadesQueReagem.caiu(atingido)) {
      falhou(execucao.getPokemon());
      return true;
    }
    Pokemon doAlvo = atingido.getPokemon();
    Ultimo ultimo = ULTIMO.get(doAlvo.getUuid());
    HitResult noAlvo = new EntityHitResult(atingido, atingido.getBoundingBox().getCenter());
    switch (nome) {
      case "mirrormove" -> chamarOuFalhar(execucao, ultimo == null || !EfeitosDosGolpes.comMarca("mirror")
          .contains(ultimo.modelo().getName()) ? null : ultimo.modelo(), noAlvo);
      case "mefirst" -> euPrimeiro(execucao, doAlvo, noAlvo);
      case "instruct" -> instrucao(execucao, atingido, ultimo);
      case "mimic", "sketch" -> copiarOGolpe(execucao, ultimo, "sketch".equals(nome));
      case "transform" -> {
        if (!Disfarces.transformarEm(atacante, atingido)) {
          falhou(execucao.getPokemon());
        }
      }
      default -> decalque(execucao.getPokemon(), doAlvo);
    }
    return true;
  }

  private static void chamarOuFalhar(Execucao execucao, MoveTemplate modelo, HitResult mira) {
    if (modelo == null) {
      falhou(execucao.getPokemon());
      return;
    }
    Move chamado = modelo.create();
    execucao.chamar(chamado, mira);
    CobblemonCombatZA.LOGGER.info("CHAMOU: {} usou {} pelo {}", execucao.getPokemon().getSpecies().getName(),
        modelo.getName(), execucao.getGolpe().getName());
  }

  private static MoveTemplate sortear(PokemonEntity atacante, List<MoveTemplate> opcoes) {
    return opcoes.isEmpty() ? null : opcoes.get(atacante.getRandom().nextInt(opcoes.size()));
  }

  private static List<MoveTemplate> metronomo() {
    if (doMetronomo == null || doMetronomo.isEmpty()) {
      List<MoveTemplate> lista = new ArrayList<>();
      for (String nome : EfeitosDosGolpes.comMarca("metronome")) {
        MoveTemplate modelo = Moves.getByName(nome);
        if (modelo != null) {
          lista.add(modelo);
        }
      }
      doMetronomo = lista;
    }
    return doMetronomo;
  }

  // Auxílio: os golpes dos outros Pokémon do time do dono (sem os marcados "noassist")
  private static List<MoveTemplate> doTime(Pokemon pokemon) {
    List<MoveTemplate> golpes = new ArrayList<>();
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    if (dono == null) {
      return golpes;
    }
    Set<String> proibidos = EfeitosDosGolpes.comMarca("noassist");
    for (Pokemon doTime : Cobblemon.INSTANCE.getStorage().getParty(dono)) {
      if (doTime.getUuid().equals(pokemon.getUuid())) {
        continue;
      }
      for (Move golpe : doTime.getMoveSet().getMoves()) {
        if (!proibidos.contains(golpe.getName())) {
          golpes.add(golpe.getTemplate());
        }
      }
    }
    return golpes;
  }

  private static String peloCampo(Pokemon pokemon) {
    return switch (CampoEBarreiras.terreno(pokemon)) {
      case ELETRICO -> "thunderbolt";
      case PLANTAS -> "energyball";
      case ENEVOADO -> "moonblast";
      case PSIQUICO -> "psychic";
      default -> "triattack";
    };
  }

  // Ataque Sonâmbulo: os outros golpes dele, menos os que carregam um turno e os marcados "nosleeptalk"
  private static List<MoveTemplate> dormindoPodeUsar(Pokemon pokemon) {
    List<MoveTemplate> golpes = new ArrayList<>();
    for (Move golpe : pokemon.getMoveSet().getMoves()) {
      String nome = golpe.getName();
      if (!EfeitosDosGolpes.comMarca("nosleeptalk").contains(nome) && !EfeitosDosGolpes.comMarca("charge").contains(nome)) {
        golpes.add(golpe.getTemplate());
      }
    }
    return golpes;
  }

  // Eu Primeiro: o golpe de dano que o alvo carrega agora (ainda não soltou)
  private static void euPrimeiro(Execucao execucao, Pokemon doAlvo, HitResult noAlvo) {
    Execucao doOutro = AgendaDeGolpes.emExecucao(doAlvo);
    Move golpe = doOutro == null || doOutro.jaImpactou() ? null : doOutro.getGolpe();
    if (golpe == null || golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()
        || EfeitosDosGolpes.comMarca("failmefirst").contains(golpe.getName())) {
      falhou(execucao.getPokemon());
      return;
    }
    Move copia = golpe.getTemplate().create();
    DO_EU_PRIMEIRO.add(copia);
    execucao.chamar(copia, noAlvo);
    CobblemonCombatZA.LOGGER.info("EU PRIMEIRO: {} usa {} de {} antes dele", execucao.getPokemon().getSpecies().getName(),
        golpe.getName(), doAlvo.getSpecies().getName());
  }

  // Instrução: o alvo repete o último golpe dele, de graça, na mesma mira de antes (o alvo que sumiu: falha)
  private static void instrucao(Execucao execucao, PokemonEntity atingido, Ultimo ultimo) {
    Pokemon doAlvo = atingido.getPokemon();
    boolean miraSumiu = ultimo != null && ultimo.mira() instanceof EntityHitResult acerto && !acerto.getEntity().isAlive();
    if (ultimo == null || miraSumiu || EfeitosDosGolpes.comMarca("failinstruct").contains(ultimo.modelo().getName())
        || SonoECongelado.estaParado(doAlvo) || atingido.getServer() == null) {
      falhou(execucao.getPokemon());
      return;
    }
    ServerPlayer quemVe = !doAlvo.isWild() && doAlvo.getOwnerPlayer() != null ? doAlvo.getOwnerPlayer()
        : execucao.getJogador();
    Execucao repetida = AgendaDeGolpes.iniciarDeGraca(quemVe, doAlvo, ultimo.modelo().create(), ultimo.slot(),
        atingido.getServer().getTickCount(), ultimo.mira(), Execucao.Origem.DE_GRACA);
    if (repetida == null) {
      falhou(execucao.getPokemon());
      return;
    }
    GolpesDeStatus.avisar(doAlvo, Component.translatable("cobblemon_combat_za.golpe.instrucao",
        doAlvo.getDisplayName(false), execucao.getPokemon().getDisplayName(false)));
  }

  // Imitar e Rascunho: o último golpe do alvo no lugar do golpe usado (o Rascunho, para sempre)
  private static void copiarOGolpe(Execucao execucao, Ultimo ultimo, boolean paraSempre) {
    Pokemon pokemon = execucao.getPokemon();
    int slot = lugarDo(pokemon, execucao.getGolpe().getName());
    MoveTemplate modelo = ultimo == null ? null : ultimo.modelo();
    boolean proibido = modelo == null || (paraSempre ? NAO_RASCUNHA.contains(modelo.getName())
        : EfeitosDosGolpes.comMarca("failmimic").contains(modelo.getName()));
    if (slot < 0 || proibido || jaTem(pokemon, modelo) || paraSempre && Disfarces.comGolpesTrocados(pokemon)) {
      falhou(pokemon);
      return;
    }
    if (paraSempre) {
      pokemon.getMoveSet().setMove(slot, modelo.create());
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.activate.sketch",
          pokemon.getDisplayName(false), "", modelo.getDisplayName()));
    } else {
      Disfarces.imitar(pokemon, slot, modelo);
      GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.start.mimic", pokemon.getDisplayName(false),
          modelo.getDisplayName()));
    }
    CobblemonCombatZA.LOGGER.info("{}: {} aprendeu {}", paraSempre ? "RASCUNHO" : "IMITAR", pokemon.getSpecies().getName(),
        modelo.getName());
  }

  private static int lugarDo(Pokemon pokemon, String nome) {
    for (int slot = 0; slot < ControleDeGolpes.MAXIMO_DE_GOLPES; slot++) {
      Move golpe = pokemon.getMoveSet().get(slot);
      if (golpe != null && nome.equals(golpe.getName())) {
        return slot;
      }
    }
    return -1;
  }

  private static boolean jaTem(Pokemon pokemon, MoveTemplate modelo) {
    return lugarDo(pokemon, modelo.getName()) >= 0;
  }

  // Decalque: ele e os aliados ficam com a habilidade do alvo (até voltarem para a bola)
  private static void decalque(Pokemon pokemon, Pokemon doAlvo) {
    String habilidade = Habilidades.atual(doAlvo);
    if (!TrocaDeHabilidade.podeSerCopiada(habilidade)) {
      falhou(pokemon);
      return;
    }
    List<Pokemon> recebem = new ArrayList<>(HabilidadesDaDupla.aliados(pokemon));
    recebem.add(0, pokemon);
    for (Pokemon quem : recebem) {
      if (!habilidade.equals(Habilidades.atual(quem))) {
        TrocaDeHabilidade.definir(quem, habilidade);
        GolpesDeStatus.avisar(quem, Component.translatable("cobblemon_combat_za.habilidade.virou", quem.getDisplayName(false),
            TrocaDeHabilidade.nomeDe(habilidade)));
      }
    }
  }

  // AgendaDeGolpes, depois do impacto: uma dança faz quem tem Dançarino na batalha dançar junto (não a dança copiada)
  public static void dancarino(Execucao execucao, long agora) {
    if (execucao.getOrigem() == Execucao.Origem.DANCARINO || !EfeitosDosGolpes.temMarca(execucao.getGolpe(), "dance")) {
      return;
    }
    Pokemon quemDancou = execucao.getPokemon();
    Arena arena = Arenas.de(quemDancou);
    if (arena == null || !arena.naBatalha()) {
      return;
    }
    for (PokemonEntity entidade : List.copyOf(arena.pokemons())) {
      Pokemon pokemon = entidade.getPokemon();
      if (pokemon.getUuid().equals(quemDancou.getUuid()) || !Habilidades.tem(pokemon, DANCARINO)
          || !HabilidadesQueReagem.dePe(entidade) || SonoECongelado.estaParado(pokemon)) {
        continue;
      }
      HitResult mira = miraDoDancarino(execucao, entidade);
      ServerPlayer quemVe = !pokemon.isWild() && pokemon.getOwnerPlayer() != null ? pokemon.getOwnerPlayer()
          : execucao.getJogador();
      Execucao danca = AgendaDeGolpes.iniciarDeGraca(quemVe, pokemon, execucao.getGolpe().getTemplate().create(), -1, agora,
          mira, Execucao.Origem.DANCARINO);
      if (danca != null) {
        CobblemonCombatZA.LOGGER.info("DANÇARINO: {} dançou {} junto com {}", pokemon.getSpecies().getName(),
            execucao.getGolpe().getName(), quemDancou.getSpecies().getName());
        GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon.battle.ability.generic",
            pokemon.getDisplayName(false), Habilidades.nome(pokemon)));
      }
    }
  }

  private static HitResult miraDoDancarino(Execucao execucao, PokemonEntity dancarino) {
    PokemonEntity quemDancou = execucao.getPokemon().getEntity();
    Entity alvo = quemDancou != null && Lados.ehInimigoDe(dancarino, quemDancou) ? quemDancou : execucao.alvoMirado();
    if (alvo == null || alvo == dancarino) {
      alvo = dancarino;
    }
    return new EntityHitResult(alvo, alvo.getBoundingBox().getCenter());
  }

  private static void falhou(Pokemon pokemon) {
    CobblemonCombatZA.LOGGER.info("FALHOU: o golpe de {} não tinha o que usar", pokemon.getSpecies().getName());
    GolpesDeStatus.avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
  }

  // Voltou para a bola ou desmaiou (GolpesDeStatus): o "último golpe" dele começa do zero
  public static void esquecer(Pokemon pokemon) {
    ULTIMO.remove(pokemon.getUuid());
  }

  public static void limpar() {
    ULTIMO.clear();
    ULTIMO_NA_LUTA.clear();
    DO_EU_PRIMEIRO.clear();
  }
}
