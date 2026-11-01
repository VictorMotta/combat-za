package dev.victor.combatza.golpes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.status.Statuses;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatus;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.habilidades.ContagemDoPerecer;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.HabilidadesDaDupla;
import dev.victor.combatza.habilidades.HabilidadesDeEstagio;
import dev.victor.combatza.habilidades.HabilidadesDosItens;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.habilidades.TiposNaBatalha;
import dev.victor.combatza.itens.ItensDeVida;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

// 01/11 (as pendências, "como nos jogos"): os golpes de status que AGEM NA HORA, sem marca:
// - estágios: Nevoeiro (zera todos na luta), Tambor Máximo (paga metade, Ataque no máximo), Carga Psíquica (copia), Troca de
//   Coração, de Força e de Defesa (trocam), Reversão (inverte), Acupressão (+2 num sorteado), Seiva Forte (cura pelo Ataque do
//   alvo, que cai 1); Alma Estridente e Descarne cobram a vida (1/3, 1/2) antes de subir;
// - vida e status: Divisão de Dor (a média), Sino da Cura e Aromaterapia (o time sem status), Refrescar, Ânimo Revigorado
//   (sem status, Atq. Esp. e Def. Esp. +1), Bênção Lunar e Cura Selvagem (1/4 e sem status, ele e os aliados), Orvalho da
//   Vida (os aliados também), Desejo (metade da vida 1 turno depois, em quem estiver lutando), Mudança de Estado (passa o
//   status), Purificação (tira o status do alvo e cura metade), Recordação (quem usa desmaia);
// - tipo: Ensopar (Água), Pólvora Mágica (Psíquico), Maldição da Floresta (+Planta), Doces ou Travessuras (+Fantasma),
//   Clonatipo (copia), Conversão (o tipo do 1º golpe), Conversão 2 (um tipo que resiste ao último golpe do alvo), Camuflagem
//   (pelo campo);
// - bagas e itens: Boca Cheia (come a baga, Defesa +2), Hora do Chá (todos comem a baga), Reciclagem (o item gasto volta);
// - Canção do Perecer: todos que ouvem desmaiam em 3 turnos (10,5 s), menos quem voltar para a bola (o selvagem fica caído
//   para a captura); Borrifada, Celebrar, Hora Extra e Dar as Mãos: "Mas nada aconteceu."
public final class GolpesNaHora {
  private static final List<String> ATRIBUTOS = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");
  private static final List<String> DE_FORCA = List.of("atk", "spa");
  private static final List<String> DE_DEFESA = List.of("def", "spd");
  private static final Set<String> CURAM_A_EQUIPE = Set.of("healbell", "aromatherapy");
  private static final String ANTIRRUIDO = "soundproof";

  // O Desejo: quem desejou (e o dono), quanto cura e quando
  private record Desejo(Pokemon pokemon, ServerPlayer dono, double pv, long quando) {
  }

  private static final List<Desejo> DESEJOS = new ArrayList<>();

  private GolpesNaHora() {
  }

  private static void avisar(Pokemon pokemon, Component frase) {
    GolpesDeStatus.avisar(pokemon, frase);
  }

  private static void falhou(Pokemon pokemon) {
    avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.falhou"));
  }

  // ---- No alvo (GolpesDeStatus.depoisDeAcertar) ----

  public static void noAlvo(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    if (!(alvo instanceof PokemonEntity atingido) || HabilidadesQueReagem.caiu(atingido)) {
      return;
    }
    Pokemon pokemon = execucao.getPokemon();
    Pokemon doAlvo = atingido.getPokemon();
    long ate = HabilidadesDeEstagio.ateQuando(pokemon);
    switch (execucao.getGolpe().getName()) {
      case "painsplit" -> dividirADor(atacante, atingido);
      case "psychup" -> {
        Estagios.definir(pokemon, Estagios.atuais(doAlvo), ate);
        avisar(pokemon, Component.translatable("cobblemon.battle.copyboost.generic", pokemon.getDisplayName(false),
            doAlvo.getDisplayName(false)));
      }
      case "heartswap" -> trocarEstagios(pokemon, doAlvo, ATRIBUTOS, "heartswap");
      case "powerswap" -> trocarEstagios(pokemon, doAlvo, DE_FORCA, "powerswap");
      case "guardswap" -> trocarEstagios(pokemon, doAlvo, DE_DEFESA, "guardswap");
      case "topsyturvy" -> {
        Map<String, Integer> invertidos = new HashMap<>();
        Estagios.atuais(doAlvo).forEach((atributo, valor) -> invertidos.put(atributo, -valor));
        if (invertidos.isEmpty()) {
          falhou(pokemon);
          return;
        }
        Estagios.definir(doAlvo, invertidos, HabilidadesDeEstagio.ateQuando(doAlvo));
        avisar(doAlvo, Component.translatable("cobblemon.battle.invertboost", doAlvo.getDisplayName(false)));
      }
      case "strengthsap" -> seivaForte(execucao, atacante, doAlvo);
      case "memento" -> {
        CobblemonCombatZA.LOGGER.info("RECORDAÇÃO: {} desmaiou", pokemon.getSpecies().getName());
        HabilidadesQueReagem.ferir(atacante, Vida.pvAtual(pokemon));
      }
      case "psychoshift" -> mudancaDeEstado(execucao, atacante, atingido);
      case "purify" -> {
        if (doAlvo.getStatus() == null) {
          falhou(pokemon);
          return;
        }
        curarStatus(doAlvo);
        curar(atacante, pokemon.getMaxHealth() / 2.0);
      }
      case "soak" -> virarTipo(pokemon, doAlvo, List.of(ElementalTypes.WATER));
      case "magicpowder" -> virarTipo(pokemon, doAlvo, List.of(ElementalTypes.PSYCHIC));
      case "forestscurse" -> somarTipo(pokemon, doAlvo, ElementalTypes.GRASS);
      case "trickortreat" -> somarTipo(pokemon, doAlvo, ElementalTypes.GHOST);
      case "reflecttype" -> virarTipo(pokemon, pokemon, new ArrayList<>(TiposNaBatalha.de(doAlvo)));
      case "conversion2" -> conversao2(atacante, pokemon, doAlvo);
      // Encharcar de Veneno: só no envenenado, Ataque, Atq. Esp. e Velocidade −1
      case "venomdrench" -> {
        String sigla = EfeitosDeStatus.siglaDoStatus(doAlvo);
        if ("psn".equals(sigla) || "tox".equals(sigla)) {
          HabilidadesDeEstagio.baixar(pokemon, doAlvo, Map.of("atk", -1, "spa", -1, "spe", -1), false,
              execucao.getGolpe().getDisplayName());
        }
      }
      default -> {
        // os outros golpes não agem aqui
      }
    }
  }

  // ---- Sem alvo (GolpesDeStatus.semAlvo). true = acabou aqui; false = os efeitos dos dados seguem ----

  public static boolean semAlvo(Execucao execucao, PokemonEntity atacante) {
    Pokemon pokemon = execucao.getPokemon();
    long agora = execucao.getJogador().server.getTickCount();
    switch (execucao.getGolpe().getName()) {
      case "haze" -> nevoeiro(pokemon);
      case "bellydrum" -> tamborMaximo(atacante, pokemon);
      case "acupressure" -> acupressao(execucao, atacante, pokemon);
      case "clangoroussoul" -> {
        return !pagar(atacante, pokemon, 3);
      }
      case "filletaway" -> {
        return !pagar(atacante, pokemon, 2);
      }
      case "healbell", "aromatherapy" -> curarAEquipe(execucao, pokemon);
      case "refresh" -> {
        String sigla = EfeitosDeStatus.siglaDoStatus(pokemon);
        if (!"psn".equals(sigla) && !"tox".equals(sigla) && !"par".equals(sigla) && !"brn".equals(sigla)) {
          falhou(pokemon);
        } else {
          curarStatus(pokemon);
        }
      }
      case "takeheart" -> {
        curarStatus(pokemon);
        AplicarEfeitos.mudarUmAtributo(execucao, atacante, "spa", 1);
        AplicarEfeitos.mudarUmAtributo(execucao, atacante, "spd", 1);
      }
      case "lunarblessing", "junglehealing" -> {
        for (PokemonEntity quem : euEOsAliados(pokemon)) {
          curar(quem, quem.getPokemon().getMaxHealth() / 4.0);
          curarStatus(quem.getPokemon());
        }
      }
      case "lifedew" -> {
        for (Pokemon aliado : HabilidadesDaDupla.aliados(pokemon)) {
          if (aliado.getEntity() != null) {
            curar(aliado.getEntity(), aliado.getMaxHealth() / 4.0);
          }
        }
        return false;
      }
      case "wish" -> {
        ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
        DESEJOS.add(new Desejo(pokemon, dono, pokemon.getMaxHealth() / 2.0, agora + EfeitosDeStatus.ticksPorTurno()));
        avisar(pokemon, Component.translatable("cobblemon_combat_za.golpe.desejou", pokemon.getDisplayName(false)));
      }
      case "conversion" -> {
        Move primeiro = pokemon.getMoveSet().get(0);
        if (primeiro == null || TiposNaBatalha.de(pokemon).equals(List.of(primeiro.getType()))) {
          falhou(pokemon);
        } else {
          virarTipo(pokemon, pokemon, List.of(primeiro.getType()));
        }
      }
      case "camouflage" -> virarTipo(pokemon, pokemon, List.of(daCamuflagem(pokemon)));
      case "stuffcheeks" -> {
        if (!ItensSegurados.id(pokemon).endsWith("berry") || !ItensDeVida.comerAgora(atacante)) {
          falhou(pokemon);
        } else {
          AplicarEfeitos.mudarUmAtributo(execucao, atacante, "def", 2);
        }
      }
      case "teatime" -> horaDoCha(pokemon);
      case "recycle" -> reciclar(pokemon);
      case "perishsong" -> cancaoDoPerecer(pokemon, atacante);
      case "splash", "holdhands" -> avisar(pokemon, Component.translatable("cobblemon.battle.nothing"));
      case "celebrate" -> avisar(pokemon, Component.translatable("cobblemon.battle.activate.celebrate",
          pokemon.getDisplayName(false)));
      case "happyhour" -> avisar(pokemon, Component.translatable("cobblemon.battle.activate.happyhour"));
      default -> {
        return false;
      }
    }
    return true;
  }

  // ---- Os de estágio ----

  private static void nevoeiro(Pokemon pokemon) {
    Arena arena = Arenas.de(pokemon);
    List<PokemonEntity> todos = arena == null ? List.of() : arena.pokemons();
    for (PokemonEntity entidade : todos) {
      Estagios.zerar(entidade.getPokemon());
    }
    if (arena == null && pokemon.getEntity() != null) {
      Estagios.zerar(pokemon);
    }
    avisar(pokemon, Component.translatable("cobblemon.battle.clearallboost"));
  }

  private static void tamborMaximo(PokemonEntity atacante, Pokemon pokemon) {
    int metade = pokemon.getMaxHealth() / 2;
    if (Vida.pvAtual(pokemon) <= metade || Estagios.de(pokemon, "atk") >= Estagios.MAXIMO) {
      falhou(pokemon);
      return;
    }
    Vida.ferir(atacante, metade);
    Map<String, Integer> estagios = new HashMap<>(Estagios.atuais(pokemon));
    estagios.put("atk", Estagios.MAXIMO);
    Estagios.definir(pokemon, estagios, HabilidadesDeEstagio.ateQuando(pokemon));
    avisar(pokemon, Component.translatable("cobblemon.battle.setboost.bellydrum", pokemon.getDisplayName(false)));
  }

  private static void acupressao(Execucao execucao, PokemonEntity atacante, Pokemon pokemon) {
    List<String> podem = ATRIBUTOS.stream().filter(a -> Estagios.de(pokemon, a) < Estagios.MAXIMO).toList();
    if (podem.isEmpty()) {
      falhou(pokemon);
      return;
    }
    AplicarEfeitos.mudarUmAtributo(execucao, atacante, podem.get(atacante.getRandom().nextInt(podem.size())), 2);
  }

  private static void trocarEstagios(Pokemon pokemon, Pokemon doAlvo, List<String> quais, String golpe) {
    Map<String, Integer> meus = new HashMap<>(Estagios.atuais(pokemon));
    Map<String, Integer> dele = new HashMap<>(Estagios.atuais(doAlvo));
    for (String atributo : quais) {
      Integer meu = meus.remove(atributo);
      Integer doOutro = dele.remove(atributo);
      if (doOutro != null) {
        meus.put(atributo, doOutro);
      }
      if (meu != null) {
        dele.put(atributo, meu);
      }
    }
    Estagios.definir(pokemon, meus, HabilidadesDeEstagio.ateQuando(pokemon));
    Estagios.definir(doAlvo, dele, HabilidadesDeEstagio.ateQuando(doAlvo));
    avisar(pokemon, Component.translatable("cobblemon.battle.swapboost." + golpe, pokemon.getDisplayName(false)));
  }

  // Seiva Forte: cura o Ataque do alvo (com os estágios dele) e baixa o Ataque dele em 1
  private static void seivaForte(Execucao execucao, PokemonEntity atacante, Pokemon doAlvo) {
    Pokemon pokemon = execucao.getPokemon();
    if (Estagios.de(doAlvo, "atk") <= Estagios.MINIMO) {
      falhou(pokemon);
      return;
    }
    double ataque = doAlvo.getAttack() * Estagios.multiplicador(doAlvo, "atk");
    HabilidadesDeEstagio.baixar(pokemon, doAlvo, Map.of("atk", -1), false, execucao.getGolpe().getDisplayName());
    curar(atacante, ataque);
  }

  // Alma Estridente (1/3) e Descarne (1/2): sem vida para pagar, falha. true = pagou (os estágios dos dados seguem)
  private static boolean pagar(PokemonEntity atacante, Pokemon pokemon, int parte) {
    int custo = pokemon.getMaxHealth() / parte;
    if (Vida.pvAtual(pokemon) <= custo) {
      falhou(pokemon);
      return false;
    }
    Vida.ferir(atacante, custo);
    avisar(pokemon, pokemon.getDisplayName(false).copy()
        .append(Component.translatable("cobblemon_combat_za.pv.perdeu", custo)));
    return true;
  }

  // ---- Os de vida e status ----

  private static void dividirADor(PokemonEntity atacante, PokemonEntity atingido) {
    Pokemon pokemon = atacante.getPokemon();
    Pokemon doAlvo = atingido.getPokemon();
    int media = (Vida.pvAtual(pokemon) + Vida.pvAtual(doAlvo)) / 2;
    acertarAVida(atacante, media);
    acertarAVida(atingido, media);
    avisar(pokemon, Component.translatable("cobblemon.battle.sethp.painsplit"));
  }

  private static void acertarAVida(PokemonEntity entidade, int pv) {
    Pokemon pokemon = entidade.getPokemon();
    int diferenca = Math.min(pv, pokemon.getMaxHealth()) - Vida.pvAtual(pokemon);
    if (diferenca > 0) {
      Vida.curar(entidade, diferenca);
    } else if (diferenca < 0) {
      Vida.ferir(entidade, -diferenca);
    }
  }

  private static void curar(PokemonEntity entidade, double pv) {
    Pokemon pokemon = entidade.getPokemon();
    if (Vida.pvAtual(pokemon) >= pokemon.getMaxHealth()) {
      return;
    }
    int curou = Vida.curar(entidade, pv);
    if (curou > 0) {
      avisar(pokemon, Component.translatable("cobblemon.battle.heal.generic", pokemon.getDisplayName(false))
          .append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
    }
  }

  private static void curarStatus(Pokemon pokemon) {
    if (pokemon.getStatus() == null) {
      return;
    }
    Component fim = Component.translatable(pokemon.getStatus().getStatus().getRemoveMessage(),
        pokemon.getDisplayName(false));
    pokemon.setStatus(null);
    avisar(pokemon, fim);
  }

  // Sino da Cura e Aromaterapia: o time inteiro (o seu: os 6; o selvagem: ele e os aliados na luta). O Antirruído não ouve o
  // sino; a Aromaterapia pega todos
  private static void curarAEquipe(Execucao execucao, Pokemon pokemon) {
    avisar(pokemon, Component.translatable("cobblemon.battle.activate." + execucao.getGolpe().getName()));
    boolean sino = "healbell".equals(execucao.getGolpe().getName());
    ServerPlayer dono = pokemon.isWild() ? null : pokemon.getOwnerPlayer();
    List<Pokemon> time = new ArrayList<>();
    if (dono != null) {
      Cobblemon.INSTANCE.getStorage().getParty(dono).forEach(time::add);
    } else {
      time.add(pokemon);
      time.addAll(HabilidadesDaDupla.aliados(pokemon));
    }
    for (Pokemon doTime : time) {
      if (!sino || !Habilidades.tem(doTime, ANTIRRUIDO) || doTime == pokemon) {
        curarStatus(doTime);
      }
    }
  }

  private static List<PokemonEntity> euEOsAliados(Pokemon pokemon) {
    List<PokemonEntity> todos = new ArrayList<>();
    if (pokemon.getEntity() != null) {
      todos.add(pokemon.getEntity());
    }
    for (Pokemon aliado : HabilidadesDaDupla.aliados(pokemon)) {
      if (aliado.getEntity() != null) {
        todos.add(aliado.getEntity());
      }
    }
    return todos;
  }

  // Mudança de Estado: o status passa dele para o alvo (que não pode ter outro), e ele fica curado
  private static void mudancaDeEstado(Execucao execucao, PokemonEntity atacante, PokemonEntity atingido) {
    Pokemon pokemon = atacante.getPokemon();
    String sigla = EfeitosDeStatus.siglaDoStatus(pokemon);
    if (sigla == null || atingido.getPokemon().getStatus() != null
        || !(Statuses.getStatus(sigla) instanceof PersistentStatus)) {
      falhou(pokemon);
      return;
    }
    AplicarStatus.tentar(execucao, atingido, sigla);
    if (atingido.getPokemon().getStatus() != null) {
      curarStatus(pokemon);
    }
  }

  // ---- Os de tipo (até voltar para a bola: TiposNaBatalha) ----

  private static void virarTipo(Pokemon quemUsou, Pokemon pokemon, List<ElementalType> tipos) {
    if (tipos.isEmpty() || TiposNaBatalha.de(pokemon).equals(tipos) || Habilidades.tem(pokemon, "multitype")
        || Habilidades.tem(pokemon, "rkssystem")) {
      falhou(quemUsou);
      return;
    }
    TiposNaBatalha.definir(pokemon, tipos);
    CobblemonCombatZA.LOGGER.info("TIPO: {} agora é {}", pokemon.getSpecies().getName(), tipos);
    avisar(pokemon, Component.translatable("cobblemon.battle.start.typechange", pokemon.getDisplayName(false),
        tipos.get(0).getDisplayName()));
  }

  private static void somarTipo(Pokemon quemUsou, Pokemon pokemon, ElementalType tipo) {
    List<ElementalType> tipos = new ArrayList<>(TiposNaBatalha.de(pokemon));
    if (tipos.contains(tipo)) {
      falhou(quemUsou);
      return;
    }
    // Só um tipo somado por vez: o de antes (da Maldição da Floresta ou das Travessuras) sai
    if (tipos.size() > 2) {
      tipos = new ArrayList<>(tipos.subList(0, 2));
    }
    tipos.add(tipo);
    TiposNaBatalha.definir(pokemon, tipos);
    avisar(pokemon, Component.translatable("cobblemon.battle.start.typeadd", pokemon.getDisplayName(false),
        tipo.getDisplayName()));
  }

  // Conversão 2: um tipo (sorteado) que resiste ao tipo do último golpe do alvo
  private static void conversao2(PokemonEntity atacante, Pokemon pokemon, Pokemon doAlvo) {
    MoveTemplate ultimo = UsarOutroGolpe.ultimoGolpe(doAlvo);
    if (ultimo == null) {
      falhou(pokemon);
      return;
    }
    List<ElementalType> resistem = new ArrayList<>();
    for (ElementalType tipo : ElementalTypes.all()) {
      if (AIUtility.INSTANCE.getDamageMultiplier(ultimo.getElementalType(), tipo) < 1
          && !TiposNaBatalha.de(pokemon).contains(tipo)) {
        resistem.add(tipo);
      }
    }
    if (resistem.isEmpty()) {
      falhou(pokemon);
      return;
    }
    virarTipo(pokemon, pokemon, List.of(resistem.get(atacante.getRandom().nextInt(resistem.size()))));
  }

  private static ElementalType daCamuflagem(Pokemon pokemon) {
    return switch (CampoEBarreiras.terreno(pokemon)) {
      case ELETRICO -> ElementalTypes.ELECTRIC;
      case PLANTAS -> ElementalTypes.GRASS;
      case ENEVOADO -> ElementalTypes.FAIRY;
      case PSIQUICO -> ElementalTypes.PSYCHIC;
      default -> ElementalTypes.NORMAL;
    };
  }

  // ---- Bagas e itens ----

  private static void horaDoCha(Pokemon pokemon) {
    Arena arena = Arenas.de(pokemon);
    boolean alguem = false;
    for (PokemonEntity entidade : arena == null ? List.<PokemonEntity>of() : arena.pokemons()) {
      if (ItensSegurados.id(entidade.getPokemon()).endsWith("berry") && HabilidadesQueReagem.dePe(entidade)) {
        alguem |= ItensDeVida.comerAgora(entidade);
      }
    }
    avisar(pokemon, Component.translatable(alguem ? "cobblemon.battle.fieldactivate.teatime"
        : "cobblemon.battle.fail.teatime"));
  }

  // Reciclagem: o último item gasto volta para a mão vazia
  private static void reciclar(Pokemon pokemon) {
    ItemStack gasto = HabilidadesDosItens.ultimoGasto(pokemon);
    if (gasto == null || !pokemon.heldItem().isEmpty()) {
      falhou(pokemon);
      return;
    }
    HabilidadesDosItens.esquecerOGasto(pokemon);
    pokemon.swapHeldItem(gasto, false, true);
    avisar(pokemon, Component.translatable("cobblemon.battle.item.recycle", pokemon.getDisplayName(false),
        gasto.getHoverName()));
  }

  // ---- Canção do Perecer ----

  private static void cancaoDoPerecer(Pokemon pokemon, PokemonEntity atacante) {
    Arena arena = Arenas.de(pokemon);
    List<PokemonEntity> ouvem = new ArrayList<>(arena == null ? List.of(atacante) : arena.pokemons());
    if (!ouvem.contains(atacante)) {
      ouvem.add(atacante);
    }
    avisar(pokemon, Component.translatable("cobblemon.battle.fieldactivate.perishsong"));
    for (PokemonEntity entidade : ouvem) {
      Pokemon quem = entidade.getPokemon();
      if (HabilidadesQueReagem.dePe(entidade) && !ContagemDoPerecer.contando(quem)
          && (entidade == atacante || !Habilidades.tem(quem, ANTIRRUIDO))) {
        ContagemDoPerecer.comecar(entidade);
      }
    }
  }

  // ---- O Desejo (a cada tick, GolpesDeStatus) ----

  public static void atualizar(long agora) {
    if (DESEJOS.isEmpty()) {
      return;
    }
    for (Desejo desejo : List.copyOf(DESEJOS)) {
      if (agora < desejo.quando()) {
        continue;
      }
      DESEJOS.remove(desejo);
      // Em quem estiver lutando no lugar dele: ele mesmo, ou o lutador do dono
      Pokemon quem = desejo.pokemon();
      PokemonEntity entidade = quem.getEntity();
      if ((entidade == null || !HabilidadesQueReagem.dePe(entidade)) && desejo.dono() != null) {
        Pokemon lutador = Lutadores.de(desejo.dono());
        entidade = lutador == null ? null : lutador.getEntity();
      }
      if (entidade == null || !HabilidadesQueReagem.dePe(entidade)) {
        continue;
      }
      avisar(entidade.getPokemon(), Component.translatable("cobblemon_combat_za.golpe.desejo_realizado",
          quem.getDisplayName(false)));
      curar(entidade, desejo.pv());
    }
  }

  public static void limpar() {
    DESEJOS.clear();
  }
}
