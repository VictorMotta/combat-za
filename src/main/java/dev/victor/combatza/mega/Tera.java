package dev.victor.combatza.mega;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.elemental.ElementalTypeTeraType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.Terreno;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.habilidades.TiposNaBatalha;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// 01/11 (a Terastalização em tempo real, decisão dele: "igual à Mega"): com o Orbe Tera equipado e o medidor da Mega cheio, a
// tecla da Mega terastaliza o lutador (Mega.pedido). Uma vez por batalha, dividindo a vez com a Mega e o golpe Z. Como nos
// jogos (Showdown, conditions.js):
// - ele vira do tipo Tera (para o golpe que leva); o Estelar continua com os tipos dele;
// - o bônus de mesmo tipo vale nos tipos de antes e no Tera; o golpe do tipo Tera que também era de antes fica ×2 (×2,25
//   com Adaptabilidade); no Estelar, todo golpe ganha ×1,2, e o de mesmo tipo, ×2;
// - a Explosão Tera vira do tipo Tera;
// - a Teraforma Zero (Terapagos) apaga o clima e o campo da batalha;
// - até ele voltar para a bola, desmaiar ou a batalha acabar. A aparência é o brilho da cor do tipo, o do Mega Showdown
public final class Tera {
  private static final String ESTELAR = "stellar";
  private static final double ESTELAR_FORA_DO_TIPO = 1.2;
  private static final double DOBRO = 2.0;
  private static final double DOBRO_COM_ADAPTABILIDADE = 2.25;
  private static final int PARTICULAS = 20;

  // O terastalizado: o tipo Tera (null = Estelar) e os tipos de antes
  private record Terastalizado(ElementalType tipo, List<ElementalType> deAntes) {
  }

  private static final Map<UUID, Terastalizado> TERASTALIZADOS = new HashMap<>();

  private Tera() {
  }

  // O Orbe Tera equipado (com o Mega Showdown)
  static boolean temOOrbe(ServerPlayer jogador) {
    return Mega.instalado() && PonteDoMegaShowdown.temOOrbeTera(jogador);
  }

  public static boolean terastalizado(Pokemon pokemon) {
    return !TERASTALIZADOS.isEmpty() && TERASTALIZADOS.containsKey(pokemon.getUuid());
  }

  // Mega.pedido: terastaliza o lutador
  static void terastalizar(ServerPlayer jogador, Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    TeraType tera = pokemon.getTeraType();
    if (entidade == null || tera == null) {
      return;
    }
    boolean estelar = ESTELAR.equals(tera.showdownId());
    ElementalType tipo = tera instanceof ElementalTypeTeraType elemental ? elemental.getType() : null;
    List<ElementalType> deAntes = List.copyOf(TiposNaBatalha.de(pokemon));
    TERASTALIZADOS.put(pokemon.getUuid(), new Terastalizado(estelar ? null : tipo, deAntes));
    if (!estelar && tipo != null) {
      TiposNaBatalha.definir(pokemon, List.of(tipo));
    }
    try {
      PonteDoMegaShowdown.brilhoTera(entidade, tera.showdownId());
    } catch (LinkageError erro) {
      CobblemonCombatZA.LOGGER.warn("TERA: o brilho do Mega Showdown não funcionou: {}", erro.toString());
    }
    Feedback.particulas(entidade, ParticleTypes.END_ROD, PARTICULAS);
    CobblemonCombatZA.LOGGER.info("TERA: {} de {} terastalizou ({})", pokemon.getSpecies().getName(),
        jogador.getName().getString(), tera.showdownId());
    Feedback.historico(jogador, pokemon, Component.translatable("cobblemon.battle.terastallize",
        pokemon.getDisplayName(false), tera.getDisplayName()));
    if (Habilidades.tem(pokemon, "teraformzero")) {
      teraformaZero(pokemon);
    }
  }

  // A Teraforma Zero: o clima e o campo da batalha acabam
  private static void teraformaZero(Pokemon pokemon) {
    Arena arena = Arenas.de(pokemon);
    if (arena == null) {
      return;
    }
    arena.campo().acabarOClimaDoGolpe();
    arena.campo().mudarOTerreno(Terreno.NENHUM, null);
    CobblemonCombatZA.LOGGER.info("TERAFORMA ZERO: {} apagou o clima e o campo", pokemon.getSpecies().getName());
    arena.avisar(pokemon, Component.translatable("cobblemon_combat_za.tera.teraforma_zero", pokemon.getDisplayName(false)));
  }

  // Dano: o bônus de mesmo tipo do terastalizado. null = não está terastalizado (vale a conta de sempre)
  public static Double stab(Pokemon atacante, ElementalType tipoDoGolpe, double stabDeSempre) {
    Terastalizado tera = TERASTALIZADOS.isEmpty() ? null : TERASTALIZADOS.get(atacante.getUuid());
    if (tera == null) {
      return null;
    }
    boolean deAntes = tera.deAntes().contains(tipoDoGolpe);
    double dobro = Habilidades.tem(atacante, "adaptability") ? DOBRO_COM_ADAPTABILIDADE : DOBRO;
    if (tera.tipo() == null) {
      return deAntes ? dobro : ESTELAR_FORA_DO_TIPO;
    }
    if (tipoDoGolpe == tera.tipo()) {
      return deAntes ? dobro : stabDeSempre;
    }
    return deAntes ? stabDeSempre : 1.0;
  }

  // ClimaNosGolpes: a Explosão Tera do terastalizado é do tipo Tera. null = não muda
  public static ElementalType tipoDoGolpe(Pokemon atacante, Move golpe) {
    Terastalizado tera = TERASTALIZADOS.isEmpty() ? null : TERASTALIZADOS.get(atacante.getUuid());
    return tera != null && tera.tipo() != null && "terablast".equals(golpe.getName()) ? tera.tipo() : null;
  }

  // Voltou para a bola, desmaiou ou a batalha acabou: volta ao normal (o brilho apaga)
  public static void esquecer(Pokemon pokemon) {
    if (TERASTALIZADOS.remove(pokemon.getUuid()) == null) {
      return;
    }
    TiposNaBatalha.esquecer(pokemon);
    PokemonEntity entidade = pokemon.getEntity();
    if (entidade != null) {
      entidade.setGlowingTag(false);
    }
    CobblemonCombatZA.LOGGER.info("TERA: {} voltou ao normal", pokemon.getSpecies().getName());
  }

  public static void limpar() {
    TERASTALIZADOS.clear();
  }
}
