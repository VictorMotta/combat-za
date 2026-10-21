package dev.victor.combatza.itens;

import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.golpes.Dano;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Flinch;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.habilidades.Habilidades;
import dev.victor.combatza.ia.IaDoSelvagem;
import dev.victor.combatza.combate.GerenciarCombates;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

// Lote 3 dos itens segurados: os que reagem quando um golpe ACERTA (ControleDeGolpes.acertar chama aqui, depois do dano):
// - de quem bate: a Gema é gasta; Sino de Concha (cura 1/8 do dano que tirou); Pedra do Rei e Presa Afiada (10% de fazer o
//   alvo hesitar, nos golpes de dano que não fazem hesitar sozinhos); Spray de Garganta (golpe de som: Ataque Especial +1);
// - de quem apanha (só quando o golpe tirou vida): a baga de resistência é comida; o Balão de Ar estoura; Política de
//   Fraqueza (super efetivo: Ataque e Ataque Especial +2); Bulbo Absorvente (Água: Ataque Especial +1), Pilha (Elétrico:
//   Ataque +1), Musgo Luminoso (Água: Defesa Especial +1); as bagas que reagem (Bagas); Farpa Grudenta (quem encostou sem
//   item fica com ela); Cartão Vermelho (quem bateu sai da batalha: o selvagem foge, o seu volta para a bola) e Botão Ejetor
//   (o seu Pokémon volta para a bola);
// - antes do dano, a Faixa de Foco (10% de aguentar com 1 PV); e quando o golpe erra, a Política de Erro (Velocidade +2).
// Os itens que somem: ItensSegurados.gastar (como no Cobblemon, contra selvagem)
public class ItensDoAcerto {
  private static final double CHANCE_DA_FAIXA = 0.1;
  private static final int CHANCE_DE_HESITAR = 10;
  private static final int SINO = 8;
  private static final Map<String, ElementalType> REAGEM_AO_TIPO = Map.of("absorbbulb", ElementalTypes.WATER,
      "cellbattery", ElementalTypes.ELECTRIC, "luminousmoss", ElementalTypes.WATER);
  private static final Map<String, String> ATRIBUTO_QUE_SOBE = Map.of("absorbbulb", "spa", "cellbattery", "atk",
      "luminousmoss", "spd");
  private static final String FRASE = "cobblemon_combat_za.item.";

  private ItensDoAcerto() {
  }

  // Antes do dano: a Faixa de Foco segura com 1 PV em 10% das vezes (se nada segurou antes: Robustez, Cinta de Foco)
  public static float faixaDeFoco(Entity alvo, float dano, boolean jaSegurou) {
    if (jaSegurou || !(alvo instanceof PokemonEntity entidade) || dano < entidade.getHealth()
        || !ItensSegurados.tem(entidade.getPokemon(), "focusband") || entidade.getRandom().nextDouble() >= CHANCE_DA_FAIXA) {
      return dano;
    }
    CobblemonCombatZA.LOGGER.info("ITEM: {} aguentou com 1 PV (Faixa de Foco)", entidade.getPokemon().getSpecies().getName());
    return entidade.getHealth() - Vida.naVida(entidade, 1);
  }

  // Depois do dano. "primeiro": o primeiro acerto do golpe (o jato acerta várias vezes)
  public static void depoisDoAcerto(Execucao execucao, PokemonEntity atacante, Entity alvo, Dano.Resultado resultado,
      double pvTirados, boolean primeiro) {
    Pokemon doAtacante = execucao.getPokemon();
    Move golpe = execucao.getGolpe();
    if (primeiro && resultado.gema()) {
      ItensDeDano.gastarGema(execucao);
    }
    if (pvTirados > 0) {
      sinoDeConcha(atacante, doAtacante, pvTirados, primeiro);
    }
    if (primeiro && pvTirados > 0) {
      pedraDoRei(execucao, atacante, alvo);
    }
    if (primeiro && atacante.isAlive()) {
      sprayDeGarganta(doAtacante, golpe);
    }
    if (!(alvo instanceof PokemonEntity atingido) || pvTirados <= 0) {
      return;
    }
    Pokemon defensor = atingido.getPokemon();
    if (resultado.bagaDeResistencia()) {
      Bagas.comeuDeResistencia(defensor);
    }
    balao(defensor);
    if (!atingido.isAlive() || defensor.isFainted()) {
      return;
    }
    politicaDeFraqueza(defensor, resultado.efetividade());
    reagemAoTipo(defensor, ClimaNosGolpes.tipo(doAtacante, golpe), golpe);
    Bagas.depoisDeApanhar(atingido, atacante, golpe, resultado.efetividade());
    farpaGrudenta(doAtacante, atacante, defensor, golpe);
    if (!cartaoVermelho(defensor, atacante)) {
      botaoEjetor(defensor);
    }
  }

  private static void sinoDeConcha(PokemonEntity atacante, Pokemon pokemon, double pvTirados, boolean primeiro) {
    int cura = (int) Math.floor(pvTirados / SINO);
    if (cura < 1 || !atacante.isAlive() || !ItensSegurados.tem(pokemon, "shellbell")
        || Vida.pvAtual(pokemon) >= pokemon.getMaxHealth()) {
      return;
    }
    int curou = Vida.curar(atacante, cura);
    if (primeiro) {
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.heal.item", pokemon.getDisplayName(false),
          ItensSegurados.nome(pokemon)).append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
    }
  }

  // Showdown, onModifyMove: o golpe de dano que não faz hesitar ganha 10% de fazer (a Graça Serena dobra; o Escudo de Poeira
  // e a Capa Furtiva seguram, como qualquer efeito extra)
  private static void pedraDoRei(Execucao execucao, PokemonEntity atacante, Entity alvo) {
    Pokemon pokemon = execucao.getPokemon();
    String item = ItensSegurados.id(pokemon);
    Move golpe = execucao.getGolpe();
    if (!"kingsrock".equals(item) && !"razorfang".equals(item)
        || golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS() || jaFazHesitar(golpe)
        || alvo instanceof PokemonEntity protegido && (Habilidades.tem(protegido.getPokemon(), Habilidades.ESCUDO_DE_POEIRA)
            || ItensSegurados.tem(protegido.getPokemon(), ItensSegurados.CAPA_FURTIVA))) {
      return;
    }
    int chance = Habilidades.tem(pokemon, Habilidades.GRACA_SERENA) ? CHANCE_DE_HESITAR * 2 : CHANCE_DE_HESITAR;
    if (atacante.getRandom().nextInt(100) < chance) {
      Flinch.aplicar(execucao, atacante, alvo);
    }
  }

  private static boolean jaFazHesitar(Move golpe) {
    EfeitosDosGolpes.Efeito efeito = EfeitosDosGolpes.de(golpe);
    return efeito != null && efeito.secondaries() != null
        && efeito.secondaries().stream().anyMatch(secundario -> "flinch".equals(secundario.volatileStatus()));
  }

  private static void sprayDeGarganta(Pokemon pokemon, Move golpe) {
    if (!EfeitosDosGolpes.ehDeSom(golpe) || !ItensSegurados.disponivel(pokemon, "throatspray")
        || !GerenciarCombates.estaEmCombate(pokemon)) {
      return;
    }
    usarEMudar(pokemon, "throatspray", Map.of("spa", 1));
  }

  private static void balao(Pokemon pokemon) {
    if (!ItensSegurados.disponivel(pokemon, ItensSegurados.BALAO_DE_AR)) {
      return;
    }
    ItensSegurados.gastar(pokemon, ItensSegurados.BALAO_DE_AR);
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.airballoon",
        pokemon.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("ITEM: o Balão de Ar de {} estourou", pokemon.getSpecies().getName());
  }

  private static void politicaDeFraqueza(Pokemon pokemon, double efetividade) {
    if (efetividade > 1.0 && ItensSegurados.disponivel(pokemon, "weaknesspolicy")) {
      usarEMudar(pokemon, "weaknesspolicy", Map.of("atk", 2, "spa", 2));
    }
  }

  private static void reagemAoTipo(Pokemon pokemon, ElementalType tipo, Move golpe) {
    String item = ItensSegurados.id(pokemon);
    if (REAGEM_AO_TIPO.get(item) != tipo || golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()
        || !ItensSegurados.disponivel(pokemon, item)) {
      return;
    }
    if ("cellbattery".equals(item)) {
      ItensSegurados.gastar(pokemon, item);
      ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.cellbattery",
          pokemon.getDisplayName(false)));
      ItensDeEstagio.mudar(pokemon, ATRIBUTO_QUE_SOBE.get(item), 1);
      return;
    }
    usarEMudar(pokemon, item, Map.of(ATRIBUTO_QUE_SOBE.get(item), 1));
  }

  // "X usou o item Y!" e as mudanças de atributo
  private static void usarEMudar(Pokemon pokemon, String item, Map<String, Integer> mudancas) {
    Component nome = ItensSegurados.gastarComNome(pokemon, item);
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.generic", pokemon.getDisplayName(false),
        nome));
    ItensDeEstagio.mudarVarios(pokemon, mudancas);
  }

  // A Farpa Grudenta gruda em quem encostou, se ele não segura nada
  private static void farpaGrudenta(Pokemon doAtacante, PokemonEntity atacante, Pokemon defensor, Move golpe) {
    if (!ItensSegurados.tem(defensor, "stickybarb") || !atacante.isAlive() || !doAtacante.heldItem().isEmpty()
        || !ItensSegurados.fazContato(doAtacante, golpe)) {
      return;
    }
    Component nome = ItensSegurados.nome(defensor);
    ItemStack farpa = defensor.removeHeldItem();
    doAtacante.swapHeldItem(farpa, false, true);
    ItensSegurados.avisar(doAtacante, Component.translatable(FRASE + "farpa", nome, defensor.getDisplayName(false),
        doAtacante.getDisplayName(false)));
    CobblemonCombatZA.LOGGER.info("ITEM: a Farpa Grudenta de {} grudou em {}", defensor.getSpecies().getName(),
        doAtacante.getSpecies().getName());
  }

  // Cartão Vermelho: quem bateu sai da batalha (o selvagem foge; o seu volta para a bola). Devolve se agiu
  private static boolean cartaoVermelho(Pokemon dono, PokemonEntity atacante) {
    Pokemon doAtacante = atacante.getPokemon();
    if (!ItensSegurados.disponivel(dono, "redcard") || !atacante.isAlive() || doAtacante == dono
        || !doAtacante.isWild() && !ItensDeEstagio.podeVoltar(doAtacante)) {
      return false;
    }
    Component nome = ItensSegurados.gastarComNome(dono, "redcard");
    ItensSegurados.avisar(dono, Component.translatable("cobblemon.battle.enditem.redcard", dono.getDisplayName(false), nome,
        doAtacante.getDisplayName(false)));
    if (doAtacante.isWild()) {
      IaDoSelvagem.fazerFugir(atacante);
    } else {
      ItensDeEstagio.recolher(doAtacante, "Cartão Vermelho");
    }
    return true;
  }

  // Botão Ejetor: o seu Pokémon que apanhou volta para a bola (o selvagem não tem para quem trocar)
  private static void botaoEjetor(Pokemon pokemon) {
    if (!ItensSegurados.disponivel(pokemon, "ejectbutton") || !ItensDeEstagio.podeVoltar(pokemon)) {
      return;
    }
    ItensSegurados.gastar(pokemon, "ejectbutton");
    ItensSegurados.avisar(pokemon, Component.translatable("cobblemon.battle.enditem.ejectbutton",
        pokemon.getDisplayName(false)));
    ItensDeEstagio.recolher(pokemon, "Botão Ejetor");
  }

  // O golpe errou (o alvo desviou): a Política de Erro sobe a Velocidade 2
  public static void politicaDeErro(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    if (ItensSegurados.disponivel(pokemon, "blunderpolicy")) {
      usarEMudar(pokemon, "blunderpolicy", Map.of("spe", 2));
    }
  }
}
