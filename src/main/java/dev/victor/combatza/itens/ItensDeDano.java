package dev.victor.combatza.itens;

import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.golpes.DanoEspecial;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.combate.GerenciarCombates;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 9.1, lote 1: os itens que mexem nos NÚMEROS do dano (Dano), com os números do Showdown (o items.js do Cobblemon):
// - os 18 de tipo (Carvão, Água Mística, Ímã...): o golpe daquele tipo ×1,2 (4915/4096);
// - Faixa Musculosa e Óculos de Sabedoria: o físico / o especial ×1,1 (4505/4096);
// - Faixa Profissional: o super efetivo ×1,2; Orbe da Vida: todo golpe ×1,3 (5324/4096; o recuo fica em ItensDeVida);
// - Faixa e Lentes da Escolha: Ataque / Ataque Especial ×1,5 (a trava fica em TravaDaEscolha); o Lenço, a Velocidade ×1,5;
// - Colete de Ataque: Defesa Especial ×1,5; Eviolita: Defesa e Defesa Especial ×1,5 em quem ainda evolui;
// - Cinta de Foco: com a vida cheia, o golpe que derrubaria deixa 1 PV (uma vez por batalha).
// Lote 2 (os números que faltavam):
// - as 17 Placas (do Mega Showdown): como os de tipo, e o Julgamento do Arceus fica do tipo da placa; as Memórias mudam o tipo
//   do Multiataque (Silvally) e os Discos, o da Tecno-Explosão (Genesect);
// - as 18 Gemas: o primeiro golpe daquele tipo ×1,3 (5325/4096), e a gema some (gasta no acerto: ItensDoAcerto);
// - os de espécie: Orbe Adamante e Cristal (Dialga: Dragão e Aço), Orbe Lustroso e Globo (Palkia: Água e Dragão), Orbe
//   Platinado e Núcleo (Giratina: Fantasma e Dragão), Orvalho da Alma (Latios e Latias: Psíquico e Dragão), as Máscaras da
//   Ogerpon (todos os golpes): ×1,2; Bola de Luz (Pikachu: Ataque e Ataque Especial ×2), Dente do Mar Profundo (Clamperl: Ataque
//   Especial ×2), Escama do Mar Profundo (Clamperl: Defesa Especial ×2), Pó Metálico (Ditto: Defesa ×2), Pó Rápido (Ditto:
//   Velocidade ×2);
// - Luva de Soco: os socos ×1,1 (4506/4096), e sem contato (ItensSegurados.fazContato);
// - Metrônomo: o mesmo golpe seguido fica mais forte, +20% por vez, até ×2;
// - Bola de Ferro e os itens "Power" (Pulseira, Faixa, Cinto, Tornozeleira, Lente e Peso de Poder): Velocidade ×0,5;
// - Lentes de Mira e Garra Afiada: +1 no estágio de crítico (a Baga Lansat guardada: +2);
// - Pedra Flutuante: a metade do peso (Rasteira, Nó de Grama, Pancada Pesada).
// Os de quem ataca valem também contra monstros e contra você (o item é de quem bate); os de quem apanha, só entre Pokémon
public class ItensDeDano {
  private static final double DO_TIPO = 4915.0 / 4096.0;
  private static final double FAIXA_OU_OCULOS = 4505.0 / 4096.0;
  private static final double FAIXA_PROFISSIONAL = 4915.0 / 4096.0;
  private static final double ORBE_DA_VIDA = 5324.0 / 4096.0;
  private static final double ESCOLHA = 1.5;
  private static final double COLETE_OU_EVIOLITA = 1.5;
  private static final double GEMA = 5325.0 / 4096.0;
  private static final double LUVA_DE_SOCO = 4506.0 / 4096.0;
  private static final double DA_ESPECIE = 4915.0 / 4096.0;
  private static final double DOBRO = 2.0;
  private static final double METADE = 0.5;
  private static final double[] METRONOMO = { 1.0, 1.2, 1.4, 1.6, 1.8, 2.0 };

  private static final Map<String, ElementalType> DE_TIPO = Map.ofEntries(
      Map.entry("charcoal", ElementalTypes.FIRE), Map.entry("mysticwater", ElementalTypes.WATER),
      Map.entry("magnet", ElementalTypes.ELECTRIC), Map.entry("miracleseed", ElementalTypes.GRASS),
      Map.entry("nevermeltice", ElementalTypes.ICE), Map.entry("blackbelt", ElementalTypes.FIGHTING),
      Map.entry("poisonbarb", ElementalTypes.POISON), Map.entry("softsand", ElementalTypes.GROUND),
      Map.entry("sharpbeak", ElementalTypes.FLYING), Map.entry("twistedspoon", ElementalTypes.PSYCHIC),
      Map.entry("silverpowder", ElementalTypes.BUG), Map.entry("hardstone", ElementalTypes.ROCK),
      Map.entry("spelltag", ElementalTypes.GHOST), Map.entry("dragonfang", ElementalTypes.DRAGON),
      Map.entry("blackglasses", ElementalTypes.DARK), Map.entry("metalcoat", ElementalTypes.STEEL),
      Map.entry("silkscarf", ElementalTypes.NORMAL), Map.entry("fairyfeather", ElementalTypes.FAIRY));

  private static final Map<String, ElementalType> PLACAS = Map.ofEntries(
      Map.entry("flameplate", ElementalTypes.FIRE), Map.entry("splashplate", ElementalTypes.WATER),
      Map.entry("zapplate", ElementalTypes.ELECTRIC), Map.entry("meadowplate", ElementalTypes.GRASS),
      Map.entry("icicleplate", ElementalTypes.ICE), Map.entry("fistplate", ElementalTypes.FIGHTING),
      Map.entry("toxicplate", ElementalTypes.POISON), Map.entry("earthplate", ElementalTypes.GROUND),
      Map.entry("skyplate", ElementalTypes.FLYING), Map.entry("mindplate", ElementalTypes.PSYCHIC),
      Map.entry("insectplate", ElementalTypes.BUG), Map.entry("stoneplate", ElementalTypes.ROCK),
      Map.entry("spookyplate", ElementalTypes.GHOST), Map.entry("dracoplate", ElementalTypes.DRAGON),
      Map.entry("dreadplate", ElementalTypes.DARK), Map.entry("ironplate", ElementalTypes.STEEL),
      Map.entry("pixieplate", ElementalTypes.FAIRY));

  private static final Map<String, ElementalType> MEMORIAS = Map.ofEntries(
      Map.entry("firememory", ElementalTypes.FIRE), Map.entry("watermemory", ElementalTypes.WATER),
      Map.entry("electricmemory", ElementalTypes.ELECTRIC), Map.entry("grassmemory", ElementalTypes.GRASS),
      Map.entry("icememory", ElementalTypes.ICE), Map.entry("fightingmemory", ElementalTypes.FIGHTING),
      Map.entry("poisonmemory", ElementalTypes.POISON), Map.entry("groundmemory", ElementalTypes.GROUND),
      Map.entry("flyingmemory", ElementalTypes.FLYING), Map.entry("psychicmemory", ElementalTypes.PSYCHIC),
      Map.entry("bugmemory", ElementalTypes.BUG), Map.entry("rockmemory", ElementalTypes.ROCK),
      Map.entry("ghostmemory", ElementalTypes.GHOST), Map.entry("dragonmemory", ElementalTypes.DRAGON),
      Map.entry("darkmemory", ElementalTypes.DARK), Map.entry("steelmemory", ElementalTypes.STEEL),
      Map.entry("fairymemory", ElementalTypes.FAIRY));

  private static final Map<String, ElementalType> DISCOS = Map.of("burndrive", ElementalTypes.FIRE,
      "chilldrive", ElementalTypes.ICE, "dousedrive", ElementalTypes.WATER, "shockdrive", ElementalTypes.ELECTRIC);

  private static final Map<String, ElementalType> GEMAS = Map.ofEntries(
      Map.entry("firegem", ElementalTypes.FIRE), Map.entry("watergem", ElementalTypes.WATER),
      Map.entry("electricgem", ElementalTypes.ELECTRIC), Map.entry("grassgem", ElementalTypes.GRASS),
      Map.entry("icegem", ElementalTypes.ICE), Map.entry("fightinggem", ElementalTypes.FIGHTING),
      Map.entry("poisongem", ElementalTypes.POISON), Map.entry("groundgem", ElementalTypes.GROUND),
      Map.entry("flyinggem", ElementalTypes.FLYING), Map.entry("psychicgem", ElementalTypes.PSYCHIC),
      Map.entry("buggem", ElementalTypes.BUG), Map.entry("rockgem", ElementalTypes.ROCK),
      Map.entry("ghostgem", ElementalTypes.GHOST), Map.entry("dragongem", ElementalTypes.DRAGON),
      Map.entry("darkgem", ElementalTypes.DARK), Map.entry("steelgem", ElementalTypes.STEEL),
      Map.entry("normalgem", ElementalTypes.NORMAL), Map.entry("fairygem", ElementalTypes.FAIRY));

  // Os de espécie que fortalecem golpes: as espécies e os tipos (vazio = todos os golpes)
  private record DaEspecie(Set<String> especies, Set<ElementalType> tipos) {
  }

  private static final DaEspecie DIALGA = new DaEspecie(Set.of("dialga"),
      Set.of(ElementalTypes.DRAGON, ElementalTypes.STEEL));
  private static final DaEspecie PALKIA = new DaEspecie(Set.of("palkia"),
      Set.of(ElementalTypes.WATER, ElementalTypes.DRAGON));
  private static final DaEspecie GIRATINA = new DaEspecie(Set.of("giratina"),
      Set.of(ElementalTypes.GHOST, ElementalTypes.DRAGON));
  private static final DaEspecie OGERPON = new DaEspecie(Set.of("ogerpon"), Set.of());
  private static final Map<String, DaEspecie> FORTALECEM_A_ESPECIE = Map.of(
      "adamantorb", DIALGA, "adamantcrystal", DIALGA, "lustrousorb", PALKIA, "lustrousglobe", PALKIA,
      "griseousorb", GIRATINA, "griseouscore", GIRATINA,
      "souldew", new DaEspecie(Set.of("latios", "latias"), Set.of(ElementalTypes.PSYCHIC, ElementalTypes.DRAGON)),
      "cornerstonemask", OGERPON, "hearthflamemask", OGERPON, "wellspringmask", OGERPON);

  private static final Set<String> CORTAM_A_VELOCIDADE = Set.of(ItensSegurados.BOLA_DE_FERRO, "poweranklet", "powerband",
      "powerbelt", "powerbracer", "powerlens", "powerweight");
  private static final Set<String> DAO_CRITICO = Set.of("scopelens", "razorclaw");
  private static final int CRITICO_DA_LANSAT = 2;

  private ItensDeDano() {
  }

  private static boolean fisico(Move golpe) {
    return golpe.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
  }

  private static String especie(Pokemon pokemon) {
    return pokemon.getSpecies().getResourceIdentifier().getPath();
  }

  // O tipo que o item dá ao golpe: o Julgamento com uma placa, o Multiataque com uma memória, a Tecno-Explosão com um disco
  // (null = o item não muda). ClimaNosGolpes.tipo pergunta antes de tudo
  public static ElementalType tipoDoGolpe(Pokemon atacante, Move golpe) {
    String item = ItensSegurados.id(atacante);
    return switch (golpe.getName()) {
      case "judgment" -> PLACAS.get(item);
      case "multiattack" -> MEMORIAS.get(item);
      case "technoblast" -> DISCOS.get(item);
      default -> null;
    };
  }

  // O poder do golpe (ao lado do HabilidadesDeDano.poder)
  public static double poder(Pokemon atacante, Move golpe, double poder) {
    String item = ItensSegurados.id(atacante);
    ElementalType tipo = ClimaNosGolpes.tipo(atacante, golpe);
    double multiplicador = 1.0;
    if (DE_TIPO.get(item) == tipo || PLACAS.get(item) == tipo) {
      multiplicador *= DO_TIPO;
    }
    if (ItensSegurados.FAIXA_MUSCULOSA.equals(item) && fisico(golpe)
        || ItensSegurados.OCULOS_DE_SABEDORIA.equals(item) && !fisico(golpe)) {
      multiplicador *= FAIXA_OU_OCULOS;
    }
    if (usaGema(atacante, golpe)) {
      multiplicador *= GEMA;
    }
    if (ItensSegurados.LUVA_DE_SOCO.equals(item) && EfeitosDosGolpes.ehSoco(golpe)) {
      multiplicador *= LUVA_DE_SOCO;
    }
    DaEspecie daEspecie = FORTALECEM_A_ESPECIE.get(item);
    if (daEspecie != null && daEspecie.especies().contains(especie(atacante))
        && (daEspecie.tipos().isEmpty() || daEspecie.tipos().contains(tipo))) {
      multiplicador *= DA_ESPECIE;
    }
    if ("metronome".equals(item)) {
      int seguidas = Math.min(EstadoDosItens.vezesSeguidas(atacante, golpe), METRONOMO.length);
      multiplicador *= METRONOMO[seguidas - 1];
    }
    return poder * multiplicador;
  }

  // A gema vale neste golpe? (numa batalha, do tipo dele, de dano, sem dano fixo, e ainda não gasta). Quem gasta é o acerto.
  // Fora da batalha (num zumbi), como no Cobblemon, nenhum item é gasto
  public static boolean usaGema(Pokemon atacante, Move golpe) {
    String item = ItensSegurados.id(atacante);
    ElementalType tipo = GEMAS.get(item);
    return tipo != null && GerenciarCombates.estaEmCombate(atacante) && tipo == ClimaNosGolpes.tipo(atacante, golpe)
        && golpe.getDamageCategory() != DamageCategories.INSTANCE.getSTATUS() && !DanoEspecial.temDanoFixo(golpe)
        && ItensSegurados.disponivel(atacante, item);
  }

  // O golpe com a gema acertou: ela some ("A Gema de Fogo fortaleceu o poder de Charmander!")
  public static void gastarGema(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    String item = ItensSegurados.id(pokemon);
    if (!GEMAS.containsKey(item)) {
      return;
    }
    Component nome = ItensSegurados.gastarComNome(pokemon, item);
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.gem", pokemon.getDisplayName(false),
        nome));
    CobblemonCombatZA.LOGGER.info("ITEM: {} gastou {} em {}", pokemon.getSpecies().getName(), item,
        execucao.getGolpe().getName());
  }

  // O Ataque (físico) ou o Ataque Especial de quem bate
  public static double ataque(Pokemon atacante, boolean fisico) {
    String item = ItensSegurados.id(atacante);
    if (fisico && ItensSegurados.FAIXA_DA_ESCOLHA.equals(item) || !fisico && ItensSegurados.LENTES_DA_ESCOLHA.equals(item)) {
      return ESCOLHA;
    }
    if ("lightball".equals(item) && "pikachu".equals(especie(atacante))
        || !fisico && "deepseatooth".equals(item) && "clamperl".equals(especie(atacante))) {
      return DOBRO;
    }
    return 1.0;
  }

  // A Defesa (físico) ou a Defesa Especial de quem apanha
  public static double defesa(Pokemon defensor, boolean fisico) {
    String item = ItensSegurados.id(defensor);
    if (!fisico && ItensSegurados.COLETE_DE_ATAQUE.equals(item)) {
      return COLETE_OU_EVIOLITA;
    }
    if (!fisico && "deepseascale".equals(item) && "clamperl".equals(especie(defensor))
        || fisico && "metalpowder".equals(item) && "ditto".equals(especie(defensor))) {
      return DOBRO;
    }
    return ItensSegurados.EVIOLITA.equals(item) && aindaEvolui(defensor) ? COLETE_OU_EVIOLITA : 1.0;
  }

  private static boolean aindaEvolui(Pokemon pokemon) {
    return pokemon.getEvolutions().iterator().hasNext();
  }

  // O dano final (ao lado do HabilidadesDeDano.danoFinal)
  public static double danoFinal(Pokemon atacante, double efetividade) {
    String item = ItensSegurados.id(atacante);
    if (ItensSegurados.ORBE_DA_VIDA.equals(item)) {
      return ORBE_DA_VIDA;
    }
    return ItensSegurados.FAIXA_PROFISSIONAL.equals(item) && efetividade > 1.0 ? FAIXA_PROFISSIONAL : 1.0;
  }

  // Contra monstro e contra você: só os de quem bate (o poder, a Escolha, o Orbe)
  public static double doAtacante(Pokemon atacante, Move golpe) {
    return poder(atacante, golpe, 1.0) * ataque(atacante, fisico(golpe)) * danoFinal(atacante, 1.0);
  }

  // A Velocidade, na recarga e no ritmo da carga (como um estágio de Velocidade): o Lenço da Escolha ×1,5; a Bola de Ferro e
  // os itens "Power" ×0,5; o Pó Rápido no Ditto ×2
  public static double velocidade(Pokemon pokemon) {
    String item = ItensSegurados.id(pokemon);
    if (ItensSegurados.LENCO_DA_ESCOLHA.equals(item)) {
      return ESCOLHA;
    }
    if (CORTAM_A_VELOCIDADE.contains(item)) {
      return METADE;
    }
    return "quickpowder".equals(item) && "ditto".equals(especie(pokemon)) ? DOBRO : 1.0;
  }

  // Os estágios de crítico a mais: Lentes de Mira e Garra Afiada +1; a Baga Lansat guardada +2
  public static int estagioDeCritico(Pokemon atacante) {
    int estagios = DAO_CRITICO.contains(ItensSegurados.id(atacante)) ? 1 : 0;
    return EstadoDosItens.focado(atacante) ? estagios + CRITICO_DA_LANSAT : estagios;
  }

  // A Pedra Flutuante deixa o Pokémon com a metade do peso
  public static double peso(Pokemon pokemon) {
    return ItensSegurados.tem(pokemon, "floatstone") ? METADE : 1.0;
  }

  // A Cinta de Foco, depois da Robustez (no Showdown ela vem antes): vida cheia e um golpe que derrubaria = fica com 1 PV
  public static float cintaDeFoco(Entity alvo, float dano, boolean robustezAgiu) {
    if (robustezAgiu || !(alvo instanceof PokemonEntity entidade)) {
      return dano;
    }
    Pokemon pokemon = entidade.getPokemon();
    if (!ItensSegurados.tem(pokemon, ItensSegurados.CINTA_DE_FOCO)
        || ItensSegurados.usadoNestaBatalha(pokemon, ItensSegurados.CINTA_DE_FOCO)
        || Vida.pvAtual(pokemon) < pokemon.getMaxHealth() || dano < entidade.getHealth()) {
      return dano;
    }
    ItensSegurados.gastar(pokemon, ItensSegurados.CINTA_DE_FOCO);
    CobblemonCombatZA.LOGGER.info("ITEM: {} aguentou com 1 PV (Cinta de Foco)", pokemon.getSpecies().getName());
    return entidade.getHealth() - Vida.naVida(entidade, 1);
  }
}
