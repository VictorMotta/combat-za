package dev.victor.combatza.habilidades;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.Clima;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.campo.EfeitosDoCampo;
import dev.victor.combatza.campo.Terreno;
import dev.victor.combatza.golpes.Confusao;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.itens.ItensSegurados;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 21/10 (habilidades, lote da conta do dano: o grupo das 66 "na conta do dano, da precisão ou da velocidade"): as
// habilidades que são um NÚMERO na conta, com os números do Showdown (o abilities.js que vem no Cobblemon). Dano chama aqui
// ao lado de HabilidadesDeDano; Precisao, Recargas/Execucao (a Velocidade), DanoEspecial (o peso), ClimaNosGolpes (o tipo do
// golpe), AplicarEfeitos (Força Absoluta e Fedor), as Bagas (Amadurecer) e a TravaDaEscolha (Táticas de Gorila) também. Como
// as outras habilidades da conta, valem entre Pokémon (contra monstros e contra você, só o tipo do golpe e a Supersorte).
// - O poder: Punho de Ferro (socos ×1,2), Megalançamento (pulsos ×1,5), Mandíbula Forte (mordidas ×1,5), Afiação (cortes
//   ×1,5), Garras Duras (contato ×1,3), Imprudência (golpes com recuo ou tombo ×1,2), Punk Rock (som ×1,3), Força Absoluta
//   (golpe com efeito extra ×1,3, e o efeito some), Rivalidade (mesmo gênero ×1,25, outro ×0,75), Analisar (×1,3 em quem já
//   agiu), Senhor Supremo (+10% por companheiro caído, até 5), Impulso Ardente (queimado: especial ×1,5), Impulso Tóxico
//   (envenenado: físico ×1,5), as Auras (HabilidadesDaBatalha) e as que mudam o tipo do golpe (Aeração, Mãos de Fada,
//   Refrigeração e Crosta Elétrica: Normal vira Voador, Fada, Gelo e Elétrico ×1,2; Normalidade: tudo vira Normal ×1,2; Voz
//   Líquida: som vira Água);
// - o Ataque de quem bate: Metalista, Mandíbula de Dragão, Carga Rochosa (o tipo ×1,5), Transistor (Elétrico ×1,3), Motor
//   Hádron (Ataque Especial ×1,33 no Campo Elétrico), Pulso de Oricalco (Ataque ×1,33 no Sol), Derrotista (metade da vida ou
//   menos: ×0,5), Táticas de Gorila e Entusiasmo (Ataque ×1,5), Vigilância (×2 em quem acabou de trocar), as Ruínas;
// - a Defesa de quem apanha: Camada de Pelos (×2), Escama Maravilhosa (com status ×1,5), Manto Frondoso (no Campo de Plantas
//   ×1,5), as Ruínas; Ignorância (Unaware) ignora os estágios do outro;
// - o dano final: Multiescamas e Escudo Sombrio (vida cheia: metade), Macio (contato metade, Fogo ×2), Escama de Gelo
//   (especial: metade), Punk Rock (som: metade), Força Neural (super efetivo ×1,25), Lentes Tingidas (pouco efetivo ×2),
//   Franco-Atirador (crítico ×1,5); Casco Tera (vida cheia: todo golpe fica "pouco efetivo");
// - o crítico: Supersorte (+1 estágio), Crueldade (no envenenado, sempre crítico);
// - a precisão: Entusiasmo (físico ×0,8), Pele Milagrosa (golpe de status nele: 50%), Pés Laçados (confuso: metade);
// - a Velocidade (na recarga e no ritmo da carga, como o Nado Rápido): Pés Rápidos (com status ×1,5, sem a lentidão da
//   paralisia), Cauda Surfista (Campo Elétrico ×2);
// - o peso: Metal Pesado (×2), Metal Leve (metade);
// - Longo Alcance: os golpes de contato não encostam (nada de Estática, Elmo Rochoso...);
// - Amadurecer: as bagas valem o dobro; Táticas de Gorila: presa no primeiro golpe, como a Escolha
public class HabilidadesDaConta {
  private static final double UM_VIRGULA_DOIS = 4915.0 / 4096.0;
  private static final double UM_VIRGULA_TRES = 5325.0 / 4096.0;
  private static final double UM_E_MEIO = 1.5;
  private static final double UM_TERCO_A_MAIS = 5461.0 / 4096.0;
  private static final double METADE = 0.5;
  private static final double DOBRO = 2.0;
  private static final double FORCA_NEURAL = 5120.0 / 4096.0;
  private static final double ENTUSIASMO_NA_PRECISAO = 3277.0 / 4096.0;
  private static final double PELE_MILAGROSA = 0.5;
  private static final double RIVAL = 1.25;
  private static final double NAO_RIVAL = 0.75;
  private static final double RUINA = 0.75;
  private static final double CASCO_TERA = 0.5;
  private static final double[] SENHOR_SUPREMO = { 4096, 4506, 4915, 5325, 5734, 6144 };

  // As que mudam o golpe Normal para o tipo delas (e ganham ×1,2)
  private static final Map<String, ElementalType> NORMAL_VIRA = Map.of("aerilate", ElementalTypes.FLYING,
      "pixilate", ElementalTypes.FAIRY, "refrigerate", ElementalTypes.ICE, "galvanize", ElementalTypes.ELECTRIC);
  private static final String NORMALIDADE = "normalize";
  private static final String VOZ_LIQUIDA = "liquidvoice";
  // Os golpes que mudam de tipo sozinhos: as habilidades não mexem neles (a Normalidade também deixa o Poder Oculto e o
  // Debater)
  private static final Set<String> TIPO_PROPRIO = Set.of("judgment", "multiattack", "naturalgift", "revelationdance",
      "technoblast", "terrainpulse", "weatherball");
  private static final Set<String> TIPO_PROPRIO_NA_NORMALIDADE = Set.of("hiddenpower", "struggle");
  // O Ataque e o Ataque Especial ×1,5 nos golpes de um tipo
  private static final Map<String, ElementalType> DO_TIPO = Map.of("steelworker", ElementalTypes.STEEL,
      "dragonsmaw", ElementalTypes.DRAGON, "rockypayload", ElementalTypes.ROCK);
  // O Imprudência pega o recuo e o tombo, não o "recuo" do Explosão Mental e do Raio de Aço (a marca "recoil" tem os dois)
  private static final Set<String> SEM_IMPRUDENCIA = Set.of("mindblown", "steelbeam");
  // A Força Absoluta também pega os golpes que o Showdown marca à parte (o efeito deles está no código do golpe)
  private static final Set<String> COM_FORCA_BRUTA = Set.of("electroshot", "orderup");
  private static final String FEDOR = "stench";
  private static final int CHANCE_DO_FEDOR = 10;
  private static final String CASCO = "terashell";
  // Casco Tera: num golpe que ele já distorceu, os outros acertos (o jato, os vários acertos) também ficam "pouco efetivos"
  private static final Map<Execucao, Set<UUID>> CASCO_ATIVO = new WeakHashMap<>();

  private HabilidadesDaConta() {
  }

  private static boolean fisico(Move golpe) {
    return golpe.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL();
  }

  private static boolean especial(Move golpe) {
    return golpe.getDamageCategory() == DamageCategories.INSTANCE.getSPECIAL();
  }

  private static boolean deStatus(Move golpe) {
    return golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
  }

  private static boolean vidaCheia(Pokemon pokemon) {
    return Vida.pvAtual(pokemon) >= pokemon.getMaxHealth();
  }

  private static long agora(Pokemon pokemon) {
    PokemonEntity entidade = pokemon.getEntity();
    return entidade == null || entidade.getServer() == null ? 0 : entidade.getServer().getTickCount();
  }

  // O tipo que a habilidade dá ao golpe (null = não muda). ClimaNosGolpes.tipo pergunta depois do item (Julgamento, Placas)
  public static ElementalType tipoDoGolpe(Pokemon atacante, Move golpe) {
    String habilidade = Habilidades.atual(atacante);
    String nome = golpe.getName();
    if (NORMALIDADE.equals(habilidade)) {
      return TIPO_PROPRIO.contains(nome) || TIPO_PROPRIO_NA_NORMALIDADE.contains(nome) ? null : ElementalTypes.NORMAL;
    }
    if (VOZ_LIQUIDA.equals(habilidade) && EfeitosDosGolpes.ehDeSom(golpe)) {
      return ElementalTypes.WATER;
    }
    ElementalType vira = NORMAL_VIRA.get(habilidade);
    return vira != null && golpe.getType() == ElementalTypes.NORMAL && !TIPO_PROPRIO.contains(nome) ? vira : null;
  }

  // A habilidade mudou o tipo do golpe e ganha ×1,2 (as -ções e a Normalidade; a Voz Líquida só muda)
  private static boolean mudouOTipo(Pokemon atacante, Move golpe) {
    return !VOZ_LIQUIDA.equals(Habilidades.atual(atacante)) && tipoDoGolpe(atacante, golpe) != null;
  }

  // O contato de verdade (as marcas do golpe): o Longo Alcance e a Luva de Soco (nos socos) tiram
  private static boolean encosta(Pokemon atacante, Move golpe) {
    return EfeitosDosGolpes.fazContato(golpe) && !semContato(atacante)
        && !(ItensSegurados.tem(atacante, ItensSegurados.LUVA_DE_SOCO) && EfeitosDosGolpes.ehSoco(golpe));
  }

  // Showdown, onBasePower (de quem bate e, nas Auras, de qualquer um da batalha)
  public static double poder(Pokemon atacante, Pokemon defensor, Move golpe) {
    String habilidade = Habilidades.atual(atacante);
    ElementalType tipo = ClimaNosGolpes.tipo(atacante, golpe);
    double multiplicador = HabilidadesDaBatalha.aura(atacante, defensor, tipo);
    if (mudouOTipo(atacante, golpe)) {
      multiplicador *= UM_VIRGULA_DOIS;
    }
    switch (habilidade) {
      case "ironfist" -> multiplicador *= EfeitosDosGolpes.ehSoco(golpe) ? UM_VIRGULA_DOIS : 1.0;
      case "megalauncher" -> multiplicador *= EfeitosDosGolpes.temMarca(golpe, "pulse") ? UM_E_MEIO : 1.0;
      case "strongjaw" -> multiplicador *= EfeitosDosGolpes.temMarca(golpe, "bite") ? UM_E_MEIO : 1.0;
      case "sharpness" -> multiplicador *= EfeitosDosGolpes.temMarca(golpe, "slicing") ? UM_E_MEIO : 1.0;
      case "toughclaws" -> multiplicador *= encosta(atacante, golpe) ? UM_VIRGULA_TRES : 1.0;
      case "reckless" -> multiplicador *= EfeitosDosGolpes.temMarca(golpe, "recoil")
          && !SEM_IMPRUDENCIA.contains(golpe.getName()) ? UM_VIRGULA_DOIS : 1.0;
      case "punkrock" -> multiplicador *= EfeitosDosGolpes.ehDeSom(golpe) ? UM_VIRGULA_TRES : 1.0;
      case "sheerforce" -> multiplicador *= forcaAbsoluta(atacante, golpe) ? UM_VIRGULA_TRES : 1.0;
      case "rivalry" -> multiplicador *= rivalidade(atacante, defensor);
      case "analytic" -> multiplicador *= HabilidadesDaBatalha.agiuHaPouco(defensor, agora(defensor))
          ? UM_VIRGULA_TRES : 1.0;
      case "supremeoverlord" -> multiplicador *= SENHOR_SUPREMO[HabilidadesDaBatalha.caidos(atacante)] / 4096.0;
      case "flareboost" -> multiplicador *= especial(golpe)
          && "brn".equals(EfeitosDeStatus.siglaDoStatus(atacante)) ? UM_E_MEIO : 1.0;
      case "toxicboost" -> multiplicador *= fisico(golpe) && envenenado(atacante) ? UM_E_MEIO : 1.0;
      default -> {
        // sem habilidade de poder
      }
    }
    return anotar("poder", atacante, defensor, multiplicador);
  }

  private static boolean envenenado(Pokemon pokemon) {
    String sigla = EfeitosDeStatus.siglaDoStatus(pokemon);
    return "psn".equals(sigla) || "tox".equals(sigla);
  }

  // Rivalidade: mesmo gênero ×1,25; gêneros diferentes ×0,75; sem gênero (um dos dois), nada
  private static double rivalidade(Pokemon atacante, Pokemon defensor) {
    Gender dele = atacante.getGender();
    Gender doAlvo = defensor.getGender();
    if (dele == Gender.GENDERLESS || doAlvo == Gender.GENDERLESS) {
      return 1.0;
    }
    return dele == doAlvo ? RIVAL : NAO_RIVAL;
  }

  // Showdown, onModifyAtk/onModifySpA (de quem bate) e as Ruínas de quem está na batalha
  public static double ataque(Pokemon atacante, Pokemon defensor, Move golpe, boolean fisico) {
    String habilidade = Habilidades.atual(atacante);
    ElementalType tipo = ClimaNosGolpes.tipo(atacante, golpe);
    double multiplicador = 1.0;
    if (tipo == DO_TIPO.get(habilidade)) {
      multiplicador *= UM_E_MEIO;
    }
    switch (habilidade) {
      case "transistor" -> multiplicador *= tipo == ElementalTypes.ELECTRIC ? UM_VIRGULA_TRES : 1.0;
      case "hadronengine" -> multiplicador *= !fisico && CampoEBarreiras.terreno(atacante) == Terreno.ELETRICO
          ? UM_TERCO_A_MAIS : 1.0;
      case "orichalcumpulse" -> multiplicador *= fisico && EfeitosDoCampo.clima(atacante) == Clima.SOL
          ? UM_TERCO_A_MAIS : 1.0;
      case "defeatist" -> multiplicador *= Vida.pvAtual(atacante) <= atacante.getMaxHealth() / 2.0 ? METADE : 1.0;
      case "gorillatactics", "hustle" -> multiplicador *= fisico ? UM_E_MEIO : 1.0;
      case "stakeout" -> multiplicador *= HabilidadesDaBatalha.acabouDeTrocar(defensor, agora(defensor)) ? DOBRO : 1.0;
      default -> {
        // sem habilidade de ataque
      }
    }
    if (HabilidadesDaBatalha.sobRuina(atacante, defensor, fisico ? "tabletsofruin" : "vesselofruin")) {
      multiplicador *= RUINA;
    }
    return anotar("ataque", atacante, defensor, multiplicador);
  }

  // Showdown, onModifyDef/onModifySpD (de quem apanha) e as Ruínas
  public static double defesa(Pokemon atacante, Pokemon defensor, boolean fisico) {
    double multiplicador = 1.0;
    if (fisico && Habilidades.tem(defensor, "furcoat")) {
      multiplicador *= DOBRO;
    }
    if (fisico && Habilidades.tem(defensor, "marvelscale") && defensor.getStatus() != null) {
      multiplicador *= UM_E_MEIO;
    }
    if (fisico && Habilidades.tem(defensor, "grasspelt") && CampoEBarreiras.terreno(defensor) == Terreno.PLANTAS) {
      multiplicador *= UM_E_MEIO;
    }
    if (HabilidadesDaBatalha.sobRuina(defensor, atacante, fisico ? "swordofruin" : "beadsofruin")) {
      multiplicador *= RUINA;
    }
    return anotar("defesa", atacante, defensor, multiplicador);
  }

  // Ignorância (Unaware): quem tem ignora os estágios do outro (ataca sem ver a Defesa subida; apanha sem ver o Ataque
  // subido). O multiplicador do estágio de "dono", ou 1 se "outro" tem Ignorância
  public static double estagio(Pokemon dono, Pokemon outro, String atributo) {
    return ignoraOsEstagios(dono, outro) ? 1.0 : Estagios.multiplicador(dono, atributo);
  }

  // O mesmo, em estágios (a Precisão e a Evasão)
  public static int estagioQueVale(Pokemon dono, Pokemon outro, String atributo) {
    return ignoraOsEstagios(dono, outro) ? 0 : Estagios.de(dono, atributo);
  }

  private static boolean ignoraOsEstagios(Pokemon dono, Pokemon outro) {
    return outro != null && !outro.getUuid().equals(dono.getUuid()) && Habilidades.tem(outro, "unaware");
  }

  // Showdown, onSourceModifyDamage (de quem apanha) e onModifyDamage (de quem bate)
  public static double danoFinal(Pokemon atacante, Pokemon defensor, Move golpe, double efetividade, boolean critico) {
    double multiplicador = 1.0;
    if ((Habilidades.tem(defensor, "multiscale") || Habilidades.tem(defensor, "shadowshield")) && vidaCheia(defensor)) {
      multiplicador *= METADE;
    }
    if (Habilidades.tem(defensor, "fluffy")) {
      multiplicador *= ClimaNosGolpes.tipo(atacante, golpe) == ElementalTypes.FIRE ? DOBRO : 1.0;
      multiplicador *= encosta(atacante, golpe) ? METADE : 1.0;
    }
    if (Habilidades.tem(defensor, "icescales") && especial(golpe)
        || Habilidades.tem(defensor, "punkrock") && EfeitosDosGolpes.ehDeSom(golpe)) {
      multiplicador *= METADE;
    }
    switch (Habilidades.atual(atacante)) {
      case "neuroforce" -> multiplicador *= efetividade > 1.0 ? FORCA_NEURAL : 1.0;
      case "tintedlens" -> multiplicador *= efetividade > 0 && efetividade < 1.0 ? DOBRO : 1.0;
      case "sniper" -> multiplicador *= critico ? UM_E_MEIO : 1.0;
      default -> {
        // sem habilidade no dano final
      }
    }
    return anotar("dano final", atacante, defensor, multiplicador);
  }

  // Para conferir no latest.log: cada parte da conta que uma habilidade mudou (quem bate [habilidade] em quem apanha
  // [habilidade])
  private static double anotar(String parte, Pokemon atacante, Pokemon defensor, double multiplicador) {
    if (multiplicador != 1.0) {
      CobblemonCombatZA.LOGGER.info("HABILIDADE NA CONTA: {} x{} ({} [{}] em {} [{}])", parte,
          String.format("%.2f", multiplicador), atacante.getSpecies().getName(), Habilidades.atual(atacante),
          defensor.getSpecies().getName(), Habilidades.atual(defensor));
    }
    return multiplicador;
  }

  // Supersorte: +1 estágio de crítico
  public static int estagioDeCritico(Pokemon atacante) {
    return Habilidades.tem(atacante, "superluck") ? 1 : 0;
  }

  // Crueldade: no alvo envenenado, todo golpe é crítico (a Armadura Concha e o Cântico da Sorte ainda seguram)
  public static boolean criticoCerto(Pokemon atacante, Pokemon defensor) {
    return Habilidades.tem(atacante, "merciless") && envenenado(defensor);
  }

  // Casco Tera (a Terapagos Terastal): com a vida cheia, o golpe de dano que pegaria nela fica "pouco efetivo" (×0,5), mesmo
  // o super efetivo; a imunidade continua imunidade. Durante um golpe, avisa uma vez
  public static double cascoTera(Pokemon defensor, double efetividade) {
    if (efetividade <= 0 || !Habilidades.tem(defensor, CASCO)) {
      return efetividade;
    }
    Execucao golpe = QuebraMolde.emAndamento();
    Set<UUID> distorcidos = golpe == null ? null : CASCO_ATIVO.get(golpe);
    boolean jaDistorceu = distorcidos != null && distorcidos.contains(defensor.getUuid());
    if (!jaDistorceu && !vidaCheia(defensor)) {
      return efetividade;
    }
    if (golpe != null && !jaDistorceu) {
      CASCO_ATIVO.computeIfAbsent(golpe, execucao -> new HashSet<>()).add(defensor.getUuid());
      CobblemonCombatZA.LOGGER.info("CASCO TERA: {} distorceu {}", defensor.getSpecies().getName(),
          golpe.getGolpe().getName());
      Feedback.historico(golpe.getJogador(), defensor,
          Component.translatable("cobblemon_combat_za.habilidade.casco_tera", defensor.getDisplayName(false)));
    }
    return CASCO_TERA;
  }

  // Showdown, onModifyAccuracy: Pele Milagrosa (no alvo: golpe de status com 50%), Pés Laçados (alvo confuso: metade) e
  // Entusiasmo (quem bate: físico ×0,8). "chance" já tem os estágios; o que vem depois (Olhos Compostos, itens) multiplica
  public static double precisao(Pokemon atacante, Move golpe, Pokemon alvo, double chance) {
    double resultado = chance;
    if (deStatus(golpe) && Habilidades.tem(alvo, "wonderskin")) {
      resultado = PELE_MILAGROSA * ClimaNosGolpes.evasao(alvo);
    }
    if (Habilidades.tem(alvo, "tangledfeet") && Confusao.estaConfuso(alvo)) {
      resultado *= METADE;
    }
    if (Habilidades.tem(atacante, "hustle") && fisico(golpe)) {
      resultado *= ENTUSIASMO_NA_PRECISAO;
    }
    anotar("precisão", atacante, alvo, chance > 0 ? resultado / chance : 1.0);
    return resultado;
  }

  // A Velocidade (na recarga e no ritmo da carga): Pés Rápidos com status ×1,5; Cauda Surfista no Campo Elétrico ×2
  public static double velocidade(Pokemon pokemon) {
    if (Habilidades.tem(pokemon, "quickfeet") && pokemon.getStatus() != null) {
      return UM_E_MEIO;
    }
    return Habilidades.tem(pokemon, "surgesurfer") && CampoEBarreiras.terreno(pokemon) == Terreno.ELETRICO ? DOBRO : 1.0;
  }

  // Pés Rápidos: a paralisia não deixa ele mais lento
  public static boolean semLentidaoDaParalisia(Pokemon pokemon) {
    return Habilidades.tem(pokemon, "quickfeet");
  }

  // O peso (Rasteira, Nó de Grama, Pancada Pesada): Metal Pesado ×2, Metal Leve metade
  public static double peso(Pokemon pokemon) {
    if (Habilidades.tem(pokemon, "heavymetal")) {
      return DOBRO;
    }
    return Habilidades.tem(pokemon, "lightmetal") ? METADE : 1.0;
  }

  // Longo Alcance: os golpes dele não encostam (as reações de contato não acontecem)
  public static boolean semContato(Pokemon atacante) {
    return Habilidades.tem(atacante, "longreach");
  }

  // Força Absoluta: o golpe tem efeito extra (que a habilidade corta e troca por ×1,3)?
  public static boolean forcaAbsoluta(Pokemon atacante, Move golpe) {
    return Habilidades.tem(atacante, "sheerforce")
        && (EfeitosDosGolpes.temMarca(golpe, "secondary") || COM_FORCA_BRUTA.contains(golpe.getName()));
  }

  // Fedor: o golpe de dano que não faz hesitar ganha 10% de fazer (como a Pedra do Rei). null = sem
  public static EfeitosDosGolpes.Secundario fedor(PokemonEntity atacante, Move golpe, EfeitosDosGolpes.Efeito efeito,
      Entity alvo) {
    if (!Habilidades.tem(atacante.getPokemon(), FEDOR) || deStatus(golpe) || alvo == atacante || jaFazHesitar(efeito)) {
      return null;
    }
    return new EfeitosDosGolpes.Secundario(CHANCE_DO_FEDOR, null, null, "flinch", null);
  }

  private static boolean jaFazHesitar(EfeitosDosGolpes.Efeito efeito) {
    return efeito != null && efeito.secondaries() != null
        && efeito.secondaries().stream().anyMatch(secundario -> "flinch".equals(secundario.volatileStatus()));
  }

  // Amadurecer: o que a baga dá (a cura, os estágios, o dano da Jaboca e da Rowap) vale o dobro
  public static int amadurecida(Pokemon pokemon, int quanto) {
    return Habilidades.tem(pokemon, "ripen") ? quanto * 2 : quanto;
  }

  // Amadurecer na baga de resistência: o golpe tira 1/4, e não a metade
  public static double bagaDeResistencia(Pokemon defensor, double metade) {
    return Habilidades.tem(defensor, "ripen") ? metade / 2 : metade;
  }

  // Táticas de Gorila: presa no primeiro golpe da batalha, como os itens da Escolha
  public static boolean travaComoEscolha(Pokemon pokemon) {
    return Habilidades.tem(pokemon, "gorillatactics");
  }
}
