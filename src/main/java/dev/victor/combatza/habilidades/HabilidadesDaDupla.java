package dev.victor.combatza.habilidades;

import java.util.ArrayList;
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

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.golpes.AgendaDeGolpes;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.ia.Andar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

// 01/11 (as habilidades de dupla, pedido dele: "depois a 2"): as 19 habilidades que só valem com um ALIADO na luta. Agora que
// existe a dupla (combate.Dupla), elas têm em quem agir. Aliado = outro Pokémon do mesmo lado na batalha: o seu segundo na
// dupla, o outro Pokémon do treinador em dupla, outro selvagem do bando, o lutador de um amigo da party na mesma batalha. Os
// números são os do Showdown (data/abilities.js do Cobblemon):
// - na conta do dano: Bateria (golpe especial do aliado ×1,3), Fonte de Energia (golpe do aliado ×1,3), Espírito Férreo
//   (golpe de Aço dele e do aliado ×1,5), Mais e Menos (Ataque Especial ×1,5 com um aliado de Mais ou Menos), Proteção
//   Amiga (o aliado leva ×0,75) e Estrela Vitória (precisão dele e do aliado ×1,1);
// - o que não pega: Véu Doce (ninguém do lado dorme), Véu de Flores (o Pokémon de Planta do lado não pega status nem tem
//   atributo baixado por outro), Telepatia (o golpe do aliado não pega nele); o Véu Aromático já protegia o lado (GolpesDeStatus);
// - ao entrar: Dupla Dinâmica (copia os estágios do aliado), Remédio Estranho (zera os estágios dos aliados), Hospitalidade
//   (cura 1/4 da vida do aliado) e Comandante (o Tatsugiri entra na boca do Dondozo aliado: some, não apanha e não age,
//   e o Dondozo ganha +2 em tudo, até ele sair da luta);
// - a cada turno: Cura (30% de chance de curar o status de um aliado);
// - quando um aliado cai: Reação Química e Receptor (pegam a habilidade dele);
// - quando o aliado gasta o item: Simbiose (passa o próprio item para ele)
public final class HabilidadesDaDupla {
  private static final double UM_VIRGULA_TRES = 5325.0 / 4096;
  private static final double UM_E_MEIO = 1.5;
  private static final double PROTECAO_AMIGA = 0.75;
  private static final double ESTRELA_VITORIA = 1.1;
  private static final float CHANCE_DA_CURA = 0.3f;
  private static final int COMANDANTE = 2;
  private static final String[] TODOS_OS_ATRIBUTOS = { "atk", "def", "spa", "spd", "spe" };
  // O Comandante dura enquanto o Dondozo estiver na luta: os estágios dele não acabam sozinhos
  private static final long PARA_SEMPRE = Long.MAX_VALUE / 4;
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  // O Tatsugiri na boca do Dondozo (a entidade dele → a do Dondozo)
  private record Comando(PokemonEntity tatsugiri, PokemonEntity dondozo) {
  }

  private static final Map<UUID, Comando> COMANDANDO = new HashMap<>();

  private HabilidadesDaDupla() {
  }

  // Os aliados de pé na batalha: os do mesmo lado na arena dela, menos ele. Fora da batalha, ninguém
  public static List<Pokemon> aliados(Pokemon pokemon) {
    List<Pokemon> aliados = new ArrayList<>();
    Arena arena = GerenciarCombates.combateDe(pokemon) == null ? null : Arenas.de(pokemon);
    if (arena == null || !arena.naBatalha()) {
      return aliados;
    }
    for (PokemonEntity entidade : arena.pokemons()) {
      Pokemon outro = entidade.getPokemon();
      if (!outro.getUuid().equals(pokemon.getUuid()) && entidade.isAlive() && !HabilidadesQueReagem.caiu(entidade)
          && Lados.mesmoLado(outro, pokemon)) {
        aliados.add(outro);
      }
    }
    return aliados;
  }

  private static boolean aliadoTem(Pokemon pokemon, String... habilidades) {
    for (Pokemon aliado : aliados(pokemon)) {
      for (String habilidade : habilidades) {
        if (Habilidades.tem(aliado, habilidade)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean eleOuAliadoTem(Pokemon pokemon, String habilidade) {
    return Habilidades.tem(pokemon, habilidade) || aliadoTem(pokemon, habilidade);
  }

  // ---- A conta do dano (HabilidadesDaConta) ----

  // Showdown, onAllyBasePower: a Bateria (golpe especial), a Fonte de Energia (qualquer golpe) e o Espírito Férreo (golpe de
  // Aço, também o dele)
  public static double poder(Pokemon atacante, Move golpe, ElementalType tipo) {
    double multiplicador = 1.0;
    if (golpe.getDamageCategory() == DamageCategories.INSTANCE.getSPECIAL() && aliadoTem(atacante, "battery")) {
      multiplicador *= UM_VIRGULA_TRES;
    }
    if (aliadoTem(atacante, "powerspot")) {
      multiplicador *= UM_VIRGULA_TRES;
    }
    if (tipo == ElementalTypes.STEEL && eleOuAliadoTem(atacante, "steelyspirit")) {
      multiplicador *= UM_E_MEIO;
    }
    return multiplicador;
  }

  // Showdown, onModifySpA: Mais e Menos, com um aliado de Mais ou de Menos
  public static double ataque(Pokemon atacante, boolean fisico) {
    boolean temMaisOuMenos = Habilidades.tem(atacante, "plus") || Habilidades.tem(atacante, "minus");
    return !fisico && temMaisOuMenos && aliadoTem(atacante, "plus", "minus") ? UM_E_MEIO : 1.0;
  }

  // Showdown, onAnyModifyDamage: a Proteção Amiga de um aliado de quem apanha
  public static double danoFinal(Pokemon defensor) {
    return aliadoTem(defensor, "friendguard") ? PROTECAO_AMIGA : 1.0;
  }

  // Showdown, onAnyModifyAccuracy: a Estrela Vitória dele ou de um aliado
  public static double precisao(Pokemon atacante) {
    return eleOuAliadoTem(atacante, "victorystar") ? ESTRELA_VITORIA : 1.0;
  }

  // ---- O que não pega (Habilidades, ControleDeGolpes) ----

  // O Véu Doce (ninguém do lado dorme) e o Véu de Flores (o de Planta do lado não pega status)
  public static boolean imuneAoStatus(Pokemon pokemon, String sigla) {
    return "slp".equals(sigla) && eleOuAliadoTem(pokemon, "sweetveil")
        || ehDePlanta(pokemon) && eleOuAliadoTem(pokemon, "flowerveil");
  }

  // O Véu de Flores: o de Planta do lado não tem atributo baixado por outro
  public static boolean naoDeixaCair(Pokemon pokemon) {
    return ehDePlanta(pokemon) && eleOuAliadoTem(pokemon, "flowerveil");
  }

  private static boolean ehDePlanta(Pokemon pokemon) {
    return pokemon.getPrimaryType() == ElementalTypes.GRASS || pokemon.getSecondaryType() == ElementalTypes.GRASS;
  }

  // A Telepatia: o golpe de dano de um aliado não pega nele (a área do aliado, o Terremoto). true = não pegou
  public static boolean telepatia(Execucao execucao, Entity alvo) {
    Pokemon atacante = execucao.getPokemon();
    if (!(alvo instanceof PokemonEntity atingido) || atingido.getPokemon().getUuid().equals(atacante.getUuid())
        || execucao.getGolpe().getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()
        || !Habilidades.tem(atingido.getPokemon(), "telepathy") || !Lados.mesmoLado(atacante, atingido.getPokemon())) {
      return false;
    }
    Pokemon pokemon = atingido.getPokemon();
    CobblemonCombatZA.LOGGER.info("TELEPATIA: o golpe de {} não pegou em {}", atacante.getSpecies().getName(),
        pokemon.getSpecies().getName());
    HabilidadesQueReagem.avisar(pokemon, pokemon.getDisplayName(false).copy().append(" ")
        .append(Component.translatable("cobblemon_combat_za.status.nao_e_afetado_por", Habilidades.nome(pokemon))));
    return true;
  }

  // ---- Ao entrar (HabilidadesDeEntrada) ----

  public static void aoEntrar(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    List<Pokemon> aliados = aliados(pokemon);
    if (aliados.isEmpty()) {
      return;
    }
    switch (Habilidades.atual(pokemon)) {
      case "costar" -> duplaDinamica(pokemon, aliados.get(0));
      case "curiousmedicine" -> remedioEstranho(pokemon, aliados);
      case "hospitality" -> hospitalidade(pokemon, aliados);
      case "commander" -> comandar(entidade, aliados);
      default -> {
        // sem habilidade de dupla na entrada
      }
    }
    // O Dondozo que chega com um Tatsugiri de Comandante já na luta
    if (ehDondozo(pokemon)) {
      for (Pokemon aliado : aliados) {
        if (Habilidades.tem(aliado, "commander") && aliado.getEntity() != null) {
          comandar(aliado.getEntity(), List.of(pokemon));
          return;
        }
      }
    }
  }

  // Copia os estágios do aliado, como estão (os dele que não estão no aliado voltam a zero)
  private static void duplaDinamica(Pokemon pokemon, Pokemon aliado) {
    long ate = HabilidadesDeEstagio.ateQuando(pokemon);
    Map<String, Integer> doAliado = Estagios.atuais(aliado);
    for (String atributo : List.copyOf(Estagios.atuais(pokemon).keySet())) {
      if (!doAliado.containsKey(atributo)) {
        Estagios.mudar(pokemon, atributo, -Estagios.de(pokemon, atributo), ate);
      }
    }
    doAliado.forEach((atributo, valor) -> Estagios.mudar(pokemon, atributo, valor - Estagios.de(pokemon, atributo), ate));
    CobblemonCombatZA.LOGGER.info("DUPLA DINÂMICA: {} copiou os estágios de {} ({})", pokemon.getSpecies().getName(),
        aliado.getSpecies().getName(), doAliado);
    HabilidadesQueReagem.avisar(pokemon, Component.translatable(FRASE + "dupla_dinamica", pokemon.getDisplayName(false),
        aliado.getDisplayName(false)));
  }

  private static void remedioEstranho(Pokemon pokemon, List<Pokemon> aliados) {
    for (Pokemon aliado : aliados) {
      if (!Estagios.atuais(aliado).isEmpty()) {
        Estagios.zerar(aliado);
        HabilidadesQueReagem.avisar(aliado, Component.translatable(FRASE + "remedio_estranho",
            aliado.getDisplayName(false)));
      }
    }
  }

  private static void hospitalidade(Pokemon pokemon, List<Pokemon> aliados) {
    for (Pokemon aliado : aliados) {
      PokemonEntity entidade = aliado.getEntity();
      if (entidade != null && Vida.pvAtual(aliado) < aliado.getMaxHealth()
          && Vida.curar(entidade, aliado.getMaxHealth() / 4.0) > 0) {
        HabilidadesQueReagem.avisar(aliado, Component.translatable(FRASE + "hospitalidade",
            aliado.getDisplayName(false), pokemon.getDisplayName(false)));
      }
    }
  }

  // ---- O Comandante ----

  private static boolean ehDondozo(Pokemon pokemon) {
    return "dondozo".equals(pokemon.getSpecies().getName().toLowerCase(java.util.Locale.ROOT));
  }

  private static void comandar(PokemonEntity tatsugiri, List<Pokemon> aliados) {
    if (COMANDANDO.containsKey(tatsugiri.getUUID())) {
      return;
    }
    for (Pokemon aliado : aliados) {
      PokemonEntity dondozo = aliado.getEntity();
      if (!ehDondozo(aliado) || dondozo == null || estaComandado(dondozo)) {
        continue;
      }
      COMANDANDO.put(tatsugiri.getUUID(), new Comando(tatsugiri, dondozo));
      Pokemon doTatsugiri = tatsugiri.getPokemon();
      AgendaDeGolpes.largar(doTatsugiri);
      Andar.parar(tatsugiri);
      tatsugiri.setInvisible(true);
      tatsugiri.setInvulnerable(true);
      for (String atributo : TODOS_OS_ATRIBUTOS) {
        Estagios.mudar(aliado, atributo, COMANDANTE, PARA_SEMPRE);
      }
      // As suas teclas não ficam num Pokémon escondido: passam para o Dondozo
      ServerPlayer dono = doTatsugiri.isWild() ? null : doTatsugiri.getOwnerPlayer();
      Pokemon lutador = dono == null ? null : Lutadores.de(dono);
      if (lutador != null && lutador.getUuid().equals(doTatsugiri.getUuid())) {
        Lutadores.alternar(dono);
      }
      CobblemonCombatZA.LOGGER.info("COMANDANTE: {} entrou na boca de {} (+2 em tudo)", doTatsugiri.getSpecies().getName(),
          aliado.getSpecies().getName());
      HabilidadesQueReagem.avisar(aliado, Component.translatable(FRASE + "comandante", doTatsugiri.getDisplayName(false),
          aliado.getDisplayName(false)));
      return;
    }
  }

  private static boolean estaComandado(PokemonEntity dondozo) {
    for (Comando comando : COMANDANDO.values()) {
      if (comando.dondozo() == dondozo) {
        return true;
      }
    }
    return false;
  }

  // O Tatsugiri escondido: não é alvo, não leva golpe e não age (Lados, IaDoSelvagem)
  public static boolean escondido(Entity entidade) {
    return !COMANDANDO.isEmpty() && entidade != null && COMANDANDO.containsKey(entidade.getUUID());
  }

  // A cada tick: o Dondozo saiu da luta (caiu, voltou para a bola, a batalha acabou): o Tatsugiri aparece de novo
  public static void atualizar() {
    COMANDANDO.values().removeIf(comando -> {
      PokemonEntity tatsugiri = comando.tatsugiri();
      PokemonEntity dondozo = comando.dondozo();
      boolean acabou = tatsugiri.isRemoved() || !tatsugiri.isAlive() || dondozo.isRemoved() || !dondozo.isAlive()
          || HabilidadesQueReagem.caiu(dondozo) || !GerenciarCombates.estaEmCombate(dondozo.getPokemon());
      if (acabou) {
        aparecer(tatsugiri, dondozo);
      }
      return acabou;
    });
  }

  private static void aparecer(PokemonEntity tatsugiri, PokemonEntity dondozo) {
    tatsugiri.setInvisible(false);
    tatsugiri.setInvulnerable(false);
    if (!tatsugiri.isRemoved()) {
      CobblemonCombatZA.LOGGER.info("COMANDANTE: {} saiu de dentro de {}", tatsugiri.getPokemon().getSpecies().getName(),
          dondozo.getPokemon().getSpecies().getName());
      HabilidadesQueReagem.avisar(tatsugiri.getPokemon(), Component.translatable(FRASE + "comandante_saiu",
          tatsugiri.getPokemon().getDisplayName(false), dondozo.getPokemon().getDisplayName(false)));
    }
  }

  // ---- A cada turno (EfeitosDeStatus) ----

  // A Cura de um aliado: 30% de chance, a cada turno, de curar o status. true = curou
  public static boolean cura(PokemonEntity entidade, String sigla) {
    Pokemon pokemon = entidade.getPokemon();
    for (Pokemon aliado : aliados(pokemon)) {
      if (Habilidades.tem(aliado, "healer") && entidade.getRandom().nextFloat() < CHANCE_DA_CURA) {
        pokemon.setStatus(null);
        CobblemonCombatZA.LOGGER.info("CURA: {} curou {} de {}", aliado.getSpecies().getName(),
            pokemon.getSpecies().getName(), sigla);
        HabilidadesQueReagem.avisar(pokemon, Component.translatable("cobblemon_combat_za.status.se_curou",
            pokemon.getDisplayName(false), HabilidadesQueReagem.deOnde(aliado)));
        return true;
      }
    }
    return false;
  }

  // ---- Quando um aliado cai (HabilidadesDoTurno) ----

  // A Reação Química e o Receptor pegam a habilidade do aliado que caiu (as que não se copiam ficam: TrocaDeHabilidade)
  public static void caiu(PokemonEntity caido, List<PokemonEntity> dePe) {
    Pokemon doCaido = caido.getPokemon();
    String habilidade = Habilidades.atual(doCaido);
    if (!TrocaDeHabilidade.podeSerCopiada(habilidade)) {
      return;
    }
    for (PokemonEntity entidade : dePe) {
      Pokemon pokemon = entidade.getPokemon();
      if (entidade != caido && (Habilidades.tem(pokemon, "receiver") || Habilidades.tem(pokemon, "powerofalchemy"))
          && Lados.mesmoLado(pokemon, doCaido)) {
        TrocaDeHabilidade.definir(pokemon, habilidade);
        HabilidadesQueReagem.avisar(pokemon, Component.translatable(FRASE + "receptor", pokemon.getDisplayName(false),
            TrocaDeHabilidade.nomeDe(habilidade), doCaido.getDisplayName(false)));
        return;
      }
    }
  }

  // ---- Quando o aliado gasta o item (ItensSegurados) ----

  // A Simbiose: o aliado gastou o item e ficou de mão vazia; quem tem Simbiose passa o dele
  public static void simbiose(Pokemon quemGastou) {
    if (!quemGastou.heldItem().isEmpty()) {
      return;
    }
    for (Pokemon aliado : aliados(quemGastou)) {
      if (Habilidades.tem(aliado, "symbiosis") && !aliado.heldItem().isEmpty()) {
        ItemStack item = aliado.removeHeldItem();
        quemGastou.swapHeldItem(item, false, true);
        CobblemonCombatZA.LOGGER.info("SIMBIOSE: {} passou {} para {}", aliado.getSpecies().getName(),
            item.getHoverName().getString(), quemGastou.getSpecies().getName());
        HabilidadesQueReagem.avisar(quemGastou, Component.translatable(FRASE + "simbiose", aliado.getDisplayName(false),
            item.getHoverName(), quemGastou.getDisplayName(false)));
        return;
      }
    }
  }

  public static void limpar() {
    for (Comando comando : COMANDANDO.values()) {
      comando.tatsugiri().setInvisible(false);
      comando.tatsugiri().setInvulnerable(false);
    }
    COMANDANDO.clear();
  }
}
