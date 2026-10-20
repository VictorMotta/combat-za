package dev.victor.combatza.golpes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class Estagios {
  public static final int MINIMO = -6;
  public static final int MAXIMO = 6;
  public static final ResourceLocation ID_VELOCIDADE_DE_ANDAR = ResourceLocation
      .fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "estagio_de_velocidade");

  private static final DustParticleOptions PARTICULA_SUBIU = new DustParticleOptions(
      new Vector3f(0.2f, 0.5f, 1.0f), 1.0f);
  private static final DustParticleOptions PARTICULA_CAIU = new DustParticleOptions(
      new Vector3f(1.0f, 0.2f, 0.2f), 1.0f);
  private static final int PARTICULAS_POR_ESTAGIO = 2;

  private record Estagio(int valor, long acabaNoTick) {
  }

  private record Ficha(Pokemon pokemon, Map<String, Estagio> estagios) {
  }

  private static final Map<UUID, Ficha> PLACAR = new HashMap<>();
  private static final List<String> ORDEM = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");

  private Estagios() {
  }

  public static int de(Pokemon pokemon, String atributo) {
    Ficha ficha = PLACAR.get(pokemon.getUuid());
    if (ficha == null) {
      return 0;
    }
    Estagio estagio = ficha.estagios().get(atributo);
    return estagio == null ? 0 : estagio.valor();
  }

  // Os estágios que estão valendo, sempre na mesma ordem (a placa do selvagem mostra; a mesma situação dá a mesma lista)
  public static Map<String, Integer> atuais(Pokemon pokemon) {
    Map<String, Integer> atuais = new LinkedHashMap<>();
    Ficha ficha = PLACAR.get(pokemon.getUuid());
    if (ficha != null) {
      for (String atributo : ORDEM) {
        Estagio estagio = ficha.estagios().get(atributo);
        if (estagio != null) {
          atuais.put(atributo, estagio.valor());
        }
      }
    }
    return atuais;
  }

  public static int mudar(Pokemon pokemon, String atributo, int quanto, long acabaNoTick) {
    int antes = de(pokemon, atributo);
    int depois = Math.clamp((long) antes + quanto, MINIMO, MAXIMO);
    Ficha ficha = PLACAR.computeIfAbsent(pokemon.getUuid(), uuid -> new Ficha(pokemon, new HashMap<>()));
    if (depois == 0) {
      ficha.estagios().remove(atributo);
    } else {
      ficha.estagios().put(atributo, new Estagio(depois, acabaNoTick));
    }
    if ("spe".equals(atributo)) {
      atualizarVelocidadeDeAndar(pokemon);
    }
    return depois - antes;
  }

  public static double multiplicador(Pokemon pokemon, String atributo) {
    int estagio = de(pokemon, atributo);
    return estagio >= 0 ? (2.0 + estagio) / 2.0 : 2.0 / (2.0 - estagio);
  }

  public static void zerar(Pokemon pokemon) {
    if (PLACAR.remove(pokemon.getUuid()) != null) {
      CobblemonCombatZA.LOGGER.info("ESTÁGIOS ZERADOS: {}", pokemon.getSpecies().getName());
      atualizarVelocidadeDeAndar(pokemon);
    }
  }

  public static void limpar() {
    PLACAR.clear();
  }

  public static void atualizar(long agora) {
    for (Ficha ficha : PLACAR.values()) {
      if (!GerenciarCombates.estaEmCombate(ficha.pokemon())) {
        tirarOsQueAcabaram(ficha, agora);
      }
      if (agora % SharedConstants.TICKS_PER_SECOND == 0) {
        mostrarParticulas(ficha);
      }
    }
    PLACAR.values().removeIf(ficha -> ficha.estagios().isEmpty());
  }

  private static void tirarOsQueAcabaram(Ficha ficha, long agora) {
    for (String atributo : List.copyOf(ficha.estagios().keySet())) {
      if (ficha.estagios().get(atributo).acabaNoTick() <= agora) {
        ficha.estagios().remove(atributo);
        aoVoltarAoNormal(ficha.pokemon(), atributo);
      }
    }
  }

  private static void aoVoltarAoNormal(Pokemon pokemon, String atributo) {
    String frase = nomeDoAtributo(atributo) + " de " + pokemon.getSpecies().getName() + " voltou ao normal";
    CobblemonCombatZA.LOGGER.info("ESTÁGIO ACABOU: {}", frase);
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (dono != null) {
      Feedback.historico(dono, pokemon, Component.literal(frase));
    }
    if ("spe".equals(atributo)) {
      atualizarVelocidadeDeAndar(pokemon);
    }
  }

  private static void mostrarParticulas(Ficha ficha) {
    PokemonEntity entidade = ficha.pokemon().getEntity();
    for (Estagio estagio : ficha.estagios().values()) {
      DustParticleOptions cor = estagio.valor() > 0 ? PARTICULA_SUBIU : PARTICULA_CAIU;
      Feedback.particulas(entidade, cor, PARTICULAS_POR_ESTAGIO * Math.abs(estagio.valor()));
    }
  }

  public static String nomeDoAtributo(String atributo) {
    return switch (atributo) {
      case "atk" -> "O Ataque";
      case "def" -> "A Defesa";
      case "spa" -> "O Ataque Especial";
      case "spd" -> "A Defesa Especial";
      case "spe" -> "A Velocidade";
      case "accuracy" -> "A Precisão";
      case "evasion" -> "A Evasão";
      default -> atributo;
    };
  }

  private static void atualizarVelocidadeDeAndar(Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade == null) {
      return;
    }

    AttributeInstance velocidade = entidade
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
    if (velocidade == null) {
      return;
    }
    double extra = multiplicador(pokemon, "spe") - 1.0;
    velocidade.addOrUpdateTransientModifier(new AttributeModifier(ID_VELOCIDADE_DE_ANDAR, extra,
        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
  }
}
