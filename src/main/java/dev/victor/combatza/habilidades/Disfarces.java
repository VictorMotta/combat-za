package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.effects.IllusionEffect;
import com.cobblemon.mod.common.entity.pokemon.effects.TransformEffect;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.combate.Raids;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// 01/11 (as habilidades que faltavam, decisões dele: "Impostor" e "Ilusão"): os dois disfarces, com os efeitos do próprio
// Cobblemon para a aparência (os da batalha por turnos dele: TransformEffect e IllusionEffect).
// - ILUSÃO (Zoroark): ao entrar na batalha, ele aparece como o último Pokémon do seu time (o selvagem: como outro selvagem
//   que está por perto) até levar o primeiro golpe de dano; aí a ilusão cai, como nos jogos (quem desfaz é o próprio
//   Cobblemon, no hurt fora da batalha por turnos). Só a aparência: os golpes e a conta são os dele;
// - IMPOSTOR (Ditto): ao entrar, vira o inimigo mais perto dele: a aparência, os golpes (no seu losango), os atributos (menos a
//   vida), os tipos, a habilidade e os estágios. Até voltar para a bola, desmaiar ou a batalha acabar. Os golpes de verdade
//   ficam guardados nos dados do Pokémon (persistentData) e voltam sozinhos, mesmo se o servidor cair no meio (ao entrar no
//   jogo de novo)
public final class Disfarces {
  private static final String CHAVE = "cobblemon_combat_za_impostor";
  private static final double PERTO_PARA_ILUDIR = 32;

  // O Ditto transformado (o Pokémon) e os atributos do alvo, copiados na hora
  private record Transformado(Pokemon pokemon, Map<String, Integer> atributos) {
  }

  private static final Map<UUID, Transformado> TRANSFORMADOS = new HashMap<>();
  // O Zoroark iludido (o Pokémon e a entidade)
  private static final Map<UUID, PokemonEntity> ILUDIDOS = new HashMap<>();
  // Quem está com um golpe do Imitar
  private static final Map<UUID, Pokemon> IMITANDO = new HashMap<>();

  private Disfarces() {
  }

  // HabilidadesDeEntrada: entrou na batalha
  public static void aoEntrar(PokemonEntity entidade) {
    switch (Habilidades.atual(entidade.getPokemon())) {
      case "illusion" -> iludir(entidade);
      case "imposter" -> transformar(entidade);
      default -> {
        // sem disfarce
      }
    }
  }

  // ---- A Ilusão ----

  private static void iludir(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    Pokemon disfarce = disfarceDe(entidade);
    if (disfarce == null || ILUDIDOS.containsKey(pokemon.getUuid()) || entidade.getEffects().getMockEffect() != null) {
      return;
    }
    new IllusionEffect(disfarce).start(entidade);
    ILUDIDOS.put(pokemon.getUuid(), entidade);
    CobblemonCombatZA.LOGGER.info("ILUSÃO: {} aparece como {}", pokemon.getSpecies().getName(),
        disfarce.getSpecies().getName());
  }

  // O último do seu time (com vida, fora ele); o selvagem: outro selvagem que está por perto
  private static Pokemon disfarceDe(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if (!pokemon.isWild()) {
      ServerPlayer dono = pokemon.getOwnerPlayer();
      Pokemon ultimo = null;
      if (dono != null) {
        for (Pokemon doTime : Cobblemon.INSTANCE.getStorage().getParty(dono)) {
          if (!doTime.getUuid().equals(pokemon.getUuid()) && !doTime.isFainted()) {
            ultimo = doTime;
          }
        }
      }
      return ultimo;
    }
    PokemonEntity maisPerto = null;
    for (PokemonEntity outro : entidade.level().getEntitiesOfClass(PokemonEntity.class,
        entidade.getBoundingBox().inflate(PERTO_PARA_ILUDIR), outro -> outro != entidade && outro.getPokemon().isWild()
            && !Raids.ehChefe(outro) && !BatalhasDeTreinador.ehDoTreinador(outro))) {
      if (maisPerto == null || entidade.distanceToSqr(outro) < entidade.distanceToSqr(maisPerto)) {
        maisPerto = outro;
      }
    }
    return maisPerto == null ? null : maisPerto.getPokemon();
  }

  // ---- O Impostor ----

  private static void transformar(PokemonEntity entidade) {
    PokemonEntity alvo = alvoDoImpostor(entidade);
    if (alvo != null) {
      transformarEm(entidade, alvo);
    }
  }

  // 01/11: também o golpe Transformação (golpes.UsarOutroGolpe). false = não deu (já transformado, ou o alvo disfarçado)
  public static boolean transformarEm(PokemonEntity entidade, PokemonEntity alvo) {
    Pokemon ditto = entidade.getPokemon();
    if (TRANSFORMADOS.containsKey(ditto.getUuid()) || entidade.getEffects().getMockEffect() != null
        || alvo.getEffects().getMockEffect() != null) {
      return false;
    }
    Pokemon copiado = alvo.getPokemon();
    guardarOsGolpes(ditto);
    ditto.getMoveSet().doWithoutEmitting(() -> {
      for (int i = 0; i < 4; i++) {
        Move doAlvo = copiado.getMoveSet().get(i);
        ditto.getMoveSet().setMove(i, doAlvo == null ? null : doAlvo.getTemplate().create());
      }
      return kotlin.Unit.INSTANCE;
    });
    ditto.getMoveSet().update();
    Map<String, Integer> atributos = new HashMap<>();
    atributos.put("atk", copiado.getAttack());
    atributos.put("def", copiado.getDefence());
    atributos.put("spa", copiado.getSpecialAttack());
    atributos.put("spd", copiado.getSpecialDefence());
    atributos.put("spe", copiado.getSpeed());
    TRANSFORMADOS.put(ditto.getUuid(), new Transformado(ditto, atributos));
    TiposNaBatalha.definir(ditto, new ArrayList<>(TiposNaBatalha.de(copiado)));
    TrocaDeHabilidade.definir(ditto, Habilidades.atual(copiado));
    Estagios.zerar(ditto);
    long ate = HabilidadesDeEstagio.ateQuando(ditto);
    Estagios.atuais(copiado).forEach((atributo, valor) -> Estagios.mudar(ditto, atributo, valor, ate));
    new TransformEffect(copiado, true).start(entidade);
    CobblemonCombatZA.LOGGER.info("IMPOSTOR: {} virou {}", ditto.getSpecies().getName(), copiado.getSpecies().getName());
    HabilidadesQueReagem.avisar(ditto, Component.translatable("cobblemon.battle.transform", ditto.getDisplayName(false),
        copiado.getDisplayName(false)));
    return true;
  }

  // 01/11: o golpe Imitar (golpes.UsarOutroGolpe): o golpe copiado fica no lugar do Imitar até ele voltar para a bola, desmaiar
  // ou a batalha acabar (os golpes de verdade ficam guardados como os do Impostor)
  public static void imitar(Pokemon pokemon, int slot, MoveTemplate modelo) {
    if (!pokemon.getPersistentData().contains(CHAVE)) {
      guardarOsGolpes(pokemon);
    }
    pokemon.getMoveSet().doWithoutEmitting(() -> {
      pokemon.getMoveSet().setMove(slot, modelo.create());
      return kotlin.Unit.INSTANCE;
    });
    pokemon.getMoveSet().update();
    IMITANDO.put(pokemon.getUuid(), pokemon);
  }

  // Os golpes dele estão trocados agora (Impostor, Transformação ou Imitar)? O Rascunho não mexe neles
  public static boolean comGolpesTrocados(Pokemon pokemon) {
    return pokemon.getPersistentData().contains(CHAVE);
  }

  // O inimigo da batalha mais perto dele (que não esteja disfarçado)
  private static PokemonEntity alvoDoImpostor(PokemonEntity entidade) {
    Pokemon ditto = entidade.getPokemon();
    List<PokemonEntity> inimigos = new ArrayList<>();
    if (ditto.isWild()) {
      var combate = GerenciarCombates.combateDe(ditto);
      if (combate != null) {
        for (ServerPlayer jogador : combate.getJogadores()) {
          for (Pokemon pokemon : Lutadores.naLuta(jogador)) {
            if (pokemon.getEntity() != null) {
              inimigos.add(pokemon.getEntity());
            }
          }
        }
      }
    } else if (ditto.getOwnerPlayer() != null) {
      inimigos.addAll(GerenciarCombates.selvagensNoCombateDe(ditto.getOwnerPlayer()));
    }
    PokemonEntity maisPerto = null;
    for (PokemonEntity inimigo : inimigos) {
      if (inimigo.isAlive() && inimigo.getEffects().getMockEffect() == null && Lados.ehInimigoDe(entidade, inimigo)
          && (maisPerto == null || entidade.distanceToSqr(inimigo) < entidade.distanceToSqr(maisPerto))) {
        maisPerto = inimigo;
      }
    }
    return maisPerto;
  }

  // Os golpes de verdade vão para os dados do Pokémon (salvos com ele) antes da cópia
  private static void guardarOsGolpes(Pokemon ditto) {
    ListTag golpes = new ListTag();
    for (int i = 0; i < 4; i++) {
      Move golpe = ditto.getMoveSet().get(i);
      CompoundTag guardado = new CompoundTag();
      if (golpe != null) {
        guardado.putString("nome", golpe.getName());
        guardado.putInt("pp", golpe.getCurrentPp());
        guardado.putInt("aumentos", golpe.getRaisedPpStages());
      }
      golpes.add(guardado);
    }
    ditto.getPersistentData().put(CHAVE, golpes);
  }

  // Os golpes de verdade voltam (se estavam guardados)
  private static void devolverOsGolpes(Pokemon pokemon) {
    if (!pokemon.getPersistentData().contains(CHAVE)) {
      return;
    }
    ListTag golpes = pokemon.getPersistentData().getList(CHAVE, Tag.TAG_COMPOUND);
    pokemon.getMoveSet().doWithoutEmitting(() -> {
      for (int i = 0; i < 4; i++) {
        CompoundTag guardado = i < golpes.size() ? golpes.getCompound(i) : new CompoundTag();
        MoveTemplate modelo = guardado.contains("nome") ? Moves.getByName(guardado.getString("nome")) : null;
        pokemon.getMoveSet().setMove(i,
            modelo == null ? null : new Move(modelo, guardado.getInt("pp"), guardado.getInt("aumentos")));
      }
      return kotlin.Unit.INSTANCE;
    });
    pokemon.getMoveSet().update();
    pokemon.getPersistentData().remove(CHAVE);
    CobblemonCombatZA.LOGGER.info("IMPOSTOR: {} voltou aos golpes dele", pokemon.getSpecies().getName());
  }

  // Dano e Recargas: o atributo do Ditto transformado é o do alvo (a vida não muda). 1 = não está transformado
  public static double atributo(Pokemon pokemon, String atributo) {
    Transformado transformado = TRANSFORMADOS.isEmpty() ? null : TRANSFORMADOS.get(pokemon.getUuid());
    if (transformado == null) {
      return 1.0;
    }
    int dele = switch (atributo) {
      case "atk" -> pokemon.getAttack();
      case "def" -> pokemon.getDefence();
      case "spa" -> pokemon.getSpecialAttack();
      case "spd" -> pokemon.getSpecialDefence();
      default -> pokemon.getSpeed();
    };
    return dele <= 0 ? 1.0 : transformado.atributos().getOrDefault(atributo, dele) / (double) dele;
  }

  // Voltou para a bola (CobblemonCombatZA.aoRecolher): o Ditto volta a ser ele
  public static void aoRecolher(Pokemon pokemon) {
    ILUDIDOS.remove(pokemon.getUuid());
    IMITANDO.remove(pokemon.getUuid());
    if (TRANSFORMADOS.remove(pokemon.getUuid()) != null || pokemon.getPersistentData().contains(CHAVE)) {
      devolverOsGolpes(pokemon);
      TrocaDeHabilidade.esquecer(pokemon);
      TiposNaBatalha.esquecer(pokemon);
    }
  }

  // Entrou no jogo: um Ditto que ficou transformado (o servidor caiu no meio da batalha) volta aos golpes dele
  public static void aoEntrarNoJogo(ServerPlayer jogador) {
    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      if (pokemon.getPersistentData().contains(CHAVE)) {
        devolverOsGolpes(pokemon);
      }
    }
  }

  // A cada segundo: a ilusão que caiu (o golpe de dano) avisa; quem saiu da batalha (desmaiou, a batalha acabou) volta ao normal
  public static void atualizar(MinecraftServer servidor) {
    if (servidor.getTickCount() % SharedConstants.TICKS_PER_SECOND != 0) {
      return;
    }
    ILUDIDOS.values().removeIf(entidade -> {
      Pokemon pokemon = entidade.getPokemon();
      boolean caiu = entidade.getEffects().getMockEffect() == null;
      boolean saiu = entidade.isRemoved() || !entidade.isAlive() || GerenciarCombates.combateDe(pokemon) == null;
      if (caiu && !entidade.isRemoved()) {
        HabilidadesQueReagem.avisar(pokemon, Component.translatable("cobblemon.battle.end.illusion",
            pokemon.getDisplayName(false)));
      } else if (saiu && !caiu && !entidade.isRemoved()) {
        entidade.getEffects().getMockEffect().end(entidade);
      }
      return caiu || saiu;
    });
    TRANSFORMADOS.values().removeIf(transformado -> {
      Pokemon pokemon = transformado.pokemon();
      PokemonEntity entidade = pokemon.getEntity();
      boolean saiu = entidade == null || entidade.isRemoved() || !entidade.isAlive() || pokemon.isFainted()
          || GerenciarCombates.combateDe(pokemon) == null;
      if (saiu) {
        if (entidade != null && !entidade.isRemoved() && entidade.getEffects().getMockEffect() instanceof TransformEffect) {
          entidade.getEffects().getMockEffect().end(entidade);
        }
        devolverOsGolpes(pokemon);
        TrocaDeHabilidade.esquecer(pokemon);
        TiposNaBatalha.esquecer(pokemon);
      }
      return saiu;
    });
    IMITANDO.values().removeIf(pokemon -> {
      PokemonEntity entidade = pokemon.getEntity();
      boolean saiu = entidade == null || entidade.isRemoved() || pokemon.isFainted()
          || GerenciarCombates.combateDe(pokemon) == null;
      if (saiu && !TRANSFORMADOS.containsKey(pokemon.getUuid())) {
        devolverOsGolpes(pokemon);
      }
      return saiu;
    });
  }

  // O servidor vai fechar: todo Ditto volta aos golpes dele antes de salvar
  public static void limpar() {
    for (Transformado transformado : TRANSFORMADOS.values()) {
      devolverOsGolpes(transformado.pokemon());
    }
    IMITANDO.values().forEach(Disfarces::devolverOsGolpes);
    TRANSFORMADOS.clear();
    ILUDIDOS.clear();
    IMITANDO.clear();
  }
}
