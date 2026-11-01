package dev.victor.combatza.mega;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.habilidades.HabilidadesDeEstagio;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 01/11 (o golpe Z em tempo real, decisão dele: "igual à Mega"): com o Anel Z equipado, o lutador segurando um Cristal Z e o
// medidor da Mega cheio, a tecla da Mega ARMA o Poder Z (Mega.pedido). O próximo golpe dele que serve ao cristal sai como golpe Z:
// - Cristal de tipo (Eletrizium Z...): qualquer golpe daquele tipo. O de dano vira o golpe Z do tipo (Gigavolt Havoc...), com o
//   poder Z dos jogos; o de status sai normal, mais o efeito Z dele (os estágios que sobem, a cura, as quedas que somem);
// - Cristal especial (Pikanium Z...): o golpe de origem da espécie certa (o Ataque Elétrico do Pikachu) vira o golpe Z dela
//   (Catastropika), com o poder e o tipo dele.
// Uma vez por batalha, dividindo a vez com a Mega e o Tera. Os números vêm do Showdown do Cobblemon (golpes_z.json, gerado por
// ferramentas/gerar-golpes-z.cjs). O golpe Z nunca erra
public final class GolpesZ {
  private static final String ARQUIVO = "/cobblemon_combat_za/golpes_z.json";
  // O golpe Z que começou e ainda não terminou vale por no máximo 15 s (o jato e a área acertam depois do começo)
  private static final long TICKS_DO_GOLPE = 15L * SharedConstants.TICKS_PER_SECOND;

  private static final class Dados {
    Map<String, Cristal> cristais = Map.of();
    Map<String, String> porTipo = Map.of();
    Map<String, Especial> especiais = Map.of();
    Map<String, Integer> poder = Map.of();
    Map<String, EfeitoZ> status = Map.of();
  }

  private static final class Cristal {
    String tipo;
    String de;
    String golpe;
    List<String> especies;
  }

  private static final class Especial {
    int poder;
    String tipo;
  }

  private static final class EfeitoZ {
    Map<String, Integer> boost;
    String effect;
  }

  // O golpe Z em andamento: de qual golpe, o nome do golpe Z, o poder, o tipo (null = o do golpe) e até quando vale
  private record EmUso(String golpe, String golpeZ, int poder, ElementalType tipo, long ateOTick) {
  }

  private static Dados dados = new Dados();
  // Quem está com o Poder Z armado (o jogador → o lutador que armou)
  private static final Map<UUID, UUID> ARMADOS = new HashMap<>();
  private static final Map<UUID, EmUso> EM_USO = new HashMap<>();

  private GolpesZ() {
  }

  public static void carregar() {
    try (InputStream entrada = GolpesZ.class.getResourceAsStream(ARQUIVO)) {
      if (entrada == null) {
        CobblemonCombatZA.LOGGER.error("Não achei o {} dentro do jar!", ARQUIVO);
        return;
      }
      dados = new Gson().fromJson(new InputStreamReader(entrada, StandardCharsets.UTF_8), Dados.class);
      CobblemonCombatZA.LOGGER.info("Golpes Z carregados: {} cristais, {} golpes com poder Z", dados.cristais.size(),
          dados.poder.size());
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.error("Falha ao ler o {}", ARQUIVO, erro);
    }
  }

  // O lutador pode armar o Poder Z: o Anel Z equipado e um Cristal Z na mão
  static boolean podeArmar(ServerPlayer jogador, Pokemon pokemon) {
    return Mega.instalado() && PonteDoMegaShowdown.temOAnelZ(jogador) && cristal(pokemon) != null;
  }

  private static Cristal cristal(Pokemon pokemon) {
    String id = PonteDoMegaShowdown.cristalZ(pokemon);
    return id == null ? null : dados.cristais.get(id);
  }

  public static boolean armado(ServerPlayer jogador) {
    return ARMADOS.containsKey(jogador.getUUID());
  }

  // Mega.pedido: o Poder Z envolve o lutador
  static void armar(ServerPlayer jogador, Pokemon pokemon) {
    ARMADOS.put(jogador.getUUID(), pokemon.getUuid());
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade != null) {
      try {
        PonteDoMegaShowdown.brilhoZ(entidade, pokemon);
      } catch (LinkageError erro) {
        CobblemonCombatZA.LOGGER.warn("GOLPE Z: o brilho do Mega Showdown não funcionou: {}", erro.toString());
      }
    }
    CobblemonCombatZA.LOGGER.info("GOLPE Z: {} de {} está com o Poder Z", pokemon.getSpecies().getName(),
        jogador.getName().getString());
    Feedback.historico(jogador, pokemon, Component.translatable("cobblemon.battle.zpower", pokemon.getDisplayName(false)));
    Feedback.barraDeAcao(jogador, Component.translatable("cobblemon_combat_za.z.armado"));
  }

  // ControleDeGolpes.usarGolpe, antes de o golpe começar: com o Poder Z armado e o golpe que serve ao cristal, ele sai como
  // golpe Z. O de status ganha o efeito Z na hora
  public static void aoUsar(ServerPlayer jogador, Pokemon pokemon, Move golpe, long agora) {
    UUID armado = ARMADOS.get(jogador.getUUID());
    if (armado == null || !armado.equals(pokemon.getUuid())) {
      return;
    }
    Cristal cristal = cristal(pokemon);
    if (cristal == null) {
      return;
    }
    String nome = golpe.getName();
    boolean deStatus = golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
    EmUso emUso = null;
    if (cristal.de != null) {
      Especial especial = dados.especiais.get(cristal.golpe);
      if (!nome.equals(cristal.de) || especial == null || !daEspecie(pokemon, cristal)) {
        return;
      }
      emUso = new EmUso(nome, cristal.golpe, especial.poder, ElementalTypes.get(especial.tipo), agora + TICKS_DO_GOLPE);
    } else {
      ElementalType tipo = ClimaNosGolpes.tipo(pokemon, golpe);
      if (tipo == null || !tipo.getName().equalsIgnoreCase(cristal.tipo)) {
        return;
      }
      if (!deStatus) {
        emUso = new EmUso(nome, dados.porTipo.get(cristal.tipo), dados.poder.getOrDefault(nome, 100), null,
            agora + TICKS_DO_GOLPE);
      }
    }
    ARMADOS.remove(jogador.getUUID());
    apagarOBrilho(pokemon);
    if (emUso != null) {
      EM_USO.put(pokemon.getUuid(), emUso);
      CobblemonCombatZA.LOGGER.info("GOLPE Z: {} usou {} (de {}, poder {})", pokemon.getSpecies().getName(), emUso.golpeZ(),
          nome, emUso.poder());
      Feedback.historico(jogador, pokemon, Component.translatable("cobblemon.battle.used_move",
          pokemon.getDisplayName(false), Component.translatable("cobblemon.move." + emUso.golpeZ())));
    } else {
      efeitoZ(jogador, pokemon, golpe);
    }
  }

  private static boolean daEspecie(Pokemon pokemon, Cristal cristal) {
    String especie = pokemon.getSpecies().getName().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return cristal.especies == null || cristal.especies.isEmpty() || cristal.especies.contains(especie);
  }

  // O efeito Z do golpe de status (Showdown, zMove.boost / zMove.effect)
  private static void efeitoZ(ServerPlayer jogador, Pokemon pokemon, Move golpe) {
    EfeitoZ efeito = dados.status.get(golpe.getName());
    CobblemonCombatZA.LOGGER.info("GOLPE Z: {} usou o Poder Z em {} ({})", pokemon.getSpecies().getName(), golpe.getName(),
        efeito == null ? "sem efeito Z" : efeito.boost != null ? efeito.boost : efeito.effect);
    if (efeito == null) {
      return;
    }
    if (efeito.boost != null) {
      long ate = HabilidadesDeEstagio.ateQuando(pokemon);
      efeito.boost.forEach((atributo, quanto) -> {
        Estagios.mudar(pokemon, atributo, quanto, ate);
        String forca = quanto >= 3 ? "severe" : quanto == 2 ? "sharp" : "slight";
        Feedback.historico(jogador, pokemon, Component.translatable("cobblemon.battle.boost." + forca + ".zeffect",
            pokemon.getDisplayName(false), Estagios.nomeDoAtributo(atributo, pokemon)));
      });
      return;
    }
    switch (efeito.effect == null ? "" : efeito.effect) {
      case "heal" -> {
        if (pokemon.getEntity() != null) {
          Vida.curar(pokemon.getEntity(), pokemon.getMaxHealth());
        }
        Feedback.historico(jogador, pokemon, Component.translatable("cobblemon.battle.heal.zeffect",
            pokemon.getDisplayName(false)));
      }
      case "clearnegativeboost" -> {
        Estagios.tirarQuedas(pokemon);
        Feedback.historico(jogador, pokemon, Component.translatable("cobblemon.battle.clearallnegativeboost.zeffect",
            pokemon.getDisplayName(false)));
      }
      default -> {
        // os outros efeitos Z (o crítico, curar quem entra, a Maldição do Fantasma, o centro das atenções) ficam de fora
      }
    }
  }

  // Dano: o poder do golpe Z em andamento (null = não é golpe Z: o poder de sempre)
  public static Integer poder(Pokemon atacante, Move golpe) {
    EmUso emUso = EM_USO.isEmpty() ? null : EM_USO.get(atacante.getUuid());
    return emUso != null && emUso.golpe().equals(golpe.getName()) ? emUso.poder() : null;
  }

  // ClimaNosGolpes: o tipo do golpe Z especial (null = o do golpe)
  public static ElementalType tipo(Pokemon atacante, Move golpe) {
    EmUso emUso = EM_USO.isEmpty() ? null : EM_USO.get(atacante.getUuid());
    return emUso != null && emUso.golpe().equals(golpe.getName()) ? emUso.tipo() : null;
  }

  // Precisao: o golpe Z nunca erra
  public static boolean emUso(Pokemon atacante, Move golpe) {
    return poder(atacante, golpe) != null;
  }

  // Execucao: começou outro golpe (ou o mesmo de novo, depois do golpe Z): o golpe Z acabou
  public static void comecouGolpe(Pokemon pokemon, Move golpe, long agora) {
    EmUso emUso = EM_USO.isEmpty() ? null : EM_USO.get(pokemon.getUuid());
    if (emUso == null) {
      return;
    }
    // O próprio golpe Z também passa por aqui ao começar (logo depois de aoUsar): esse fica
    boolean oMesmoAgora = emUso.golpe().equals(golpe.getName()) && agora <= emUso.ateOTick() - TICKS_DO_GOLPE + 1;
    if (!oMesmoAgora || agora > emUso.ateOTick()) {
      EM_USO.remove(pokemon.getUuid());
    }
  }

  // A batalha acabou para o jogador: o Poder Z armado e não usado se perde (e o brilho apaga)
  public static void fimDaBatalha(ServerPlayer jogador) {
    UUID armado = ARMADOS.remove(jogador.getUUID());
    Pokemon pokemon = armado == null ? null : com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(jogador)
        .get(armado);
    if (pokemon != null) {
      apagarOBrilho(pokemon);
    }
  }

  // O brilho do Poder Z apaga (o do Tera, se ele está terastalizado, fica)
  private static void apagarOBrilho(Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade != null && !Tera.terastalizado(pokemon)) {
      entidade.setGlowingTag(false);
    }
  }

  public static void esquecer(Pokemon pokemon) {
    EM_USO.remove(pokemon.getUuid());
  }

  public static void limpar() {
    ARMADOS.clear();
    EM_USO.clear();
  }
}
