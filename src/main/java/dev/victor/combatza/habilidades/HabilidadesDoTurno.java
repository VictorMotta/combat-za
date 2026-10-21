package dev.victor.combatza.habilidades;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.campo.Clima;
import dev.victor.combatza.campo.EfeitosDoCampo;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Vida;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

// 21/10 (habilidades, lote das reações): as habilidades que agem A CADA TURNO (o "onResidual" do Showdown), só nos Pokémon
// da batalha (os selvagens e o lutador de cada jogador; fora da batalha não existe turno, como nos itens). O turno de cada
// um é dele: o primeiro vem 3,5 s depois de ele entrar na batalha (nos jogos, o Acelerador não age no turno em que o Pokémon
// entra), e depois a cada 3,5 s:
// - Acelerador: Velocidade +1 (no máximo, para de avisar);
// - Mau-Humor: um atributo sorteado +2 e outro −1 (sem Precisão e Evasão, como na Geração 8 em diante);
// - Pesadelos: cada inimigo dormindo perde 1/8 da vida (o Escudo Mágico segura);
// - Colheita: 50% de replantar a última baga comida (no Sol, sempre), se a mão estiver vazia;
// - Pegar: com a mão vazia, apanha o item que outro Pokémon da batalha gastou no último turno (o que sumiu da mão dele).
// E também: o Veneno Curador (no turno do veneno, EfeitosDeStatus: cura 1/8 no lugar do dano) e a Coralma (alguém desmaiou
// na batalha: Ataque Especial +1), que este tick confere
public class HabilidadesDoTurno {
  private static final String ACELERADOR = "speedboost";
  private static final String MAU_HUMOR = "moody";
  private static final String PESADELOS = "baddreams";
  private static final String COLHEITA = "harvest";
  private static final String PEGAR = "pickup";
  private static final String VENENO_CURADOR = "poisonheal";
  private static final String CORALMA = "soulheart";
  // As dos outros lotes que estas consultam: o Comatoso conta como dormindo; o Escudo Mágico segura o dano que não é de
  // golpe
  private static final String COMATOSO = "comatose";
  private static final String ESCUDO_MAGICO = "magicguard";
  private static final int PESADELO_TIRA = 8;
  private static final int VENENO_CURA = 8;
  private static final List<String> DO_MAU_HUMOR = List.of("atk", "def", "spa", "spd", "spe");

  // O próximo turno de cada Pokémon da batalha (pela entidade: voltou da bola, o turno recomeça)
  private static final Map<PokemonEntity, Long> PROXIMO_TURNO = new HashMap<>();
  // Quem estava de pé em cada batalha no tick anterior (a Coralma conta quem caiu desde então)
  private static final Map<Combate, Set<PokemonEntity>> DE_PE = new IdentityHashMap<>();

  private HabilidadesDoTurno() {
  }

  // A cada tick (ItensDeVida.atualizar)
  public static void atualizar(long agora) {
    Set<PokemonEntity> vistos = new HashSet<>();
    List<Combate> combates = List.copyOf(GerenciarCombates.todos());
    for (Combate combate : combates) {
      List<PokemonEntity> naLuta = HabilidadesQueReagem.naLuta(combate);
      coralma(combate, naLuta);
      for (PokemonEntity entidade : naLuta) {
        vistos.add(entidade);
        Long proximo = PROXIMO_TURNO.get(entidade);
        if (proximo == null) {
          PROXIMO_TURNO.put(entidade, agora + EfeitosDeStatus.ticksPorTurno());
        } else if (agora >= proximo) {
          PROXIMO_TURNO.put(entidade, proximo + EfeitosDeStatus.ticksPorTurno());
          porTurno(entidade, naLuta, agora);
        }
      }
    }
    PROXIMO_TURNO.keySet().retainAll(vistos);
    DE_PE.keySet().retainAll(combates);
    HabilidadesQueReagem.atualizar(agora);
    ContagemDoPerecer.atualizar(agora);
  }

  private static void porTurno(PokemonEntity entidade, List<PokemonEntity> naLuta, long agora) {
    if (!HabilidadesQueReagem.dePe(entidade)) {
      return;
    }
    Pokemon pokemon = entidade.getPokemon();
    switch (Habilidades.atual(pokemon)) {
      case ACELERADOR -> {
        if (Estagios.de(pokemon, "spe") < Estagios.MAXIMO) {
          HabilidadesQueReagem.mudar(pokemon, Map.of("spe", 1), HabilidadesQueReagem.deOnde(pokemon));
        }
      }
      case MAU_HUMOR -> mauHumor(entidade);
      case PESADELOS -> pesadelos(entidade, naLuta);
      case COLHEITA -> colheita(entidade);
      case PEGAR -> pegar(entidade, naLuta, agora);
      default -> {
        // as outras habilidades não agem a cada turno
      }
    }
  }

  // Showdown, Mau-Humor: um atributo que ainda sobe ganha +2; outro (diferente) que ainda cai perde 1
  private static void mauHumor(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    RandomSource sorte = entidade.getRandom();
    List<String> podemSubir = DO_MAU_HUMOR.stream().filter(a -> Estagios.de(pokemon, a) < Estagios.MAXIMO).toList();
    String sobe = podemSubir.isEmpty() ? null : podemSubir.get(sorte.nextInt(podemSubir.size()));
    List<String> podemCair = DO_MAU_HUMOR.stream()
        .filter(a -> Estagios.de(pokemon, a) > Estagios.MINIMO && !a.equals(sobe)).toList();
    String cai = podemCair.isEmpty() ? null : podemCair.get(sorte.nextInt(podemCair.size()));
    Map<String, Integer> mudancas = new HashMap<>();
    if (sobe != null) {
      mudancas.put(sobe, 2);
    }
    if (cai != null) {
      mudancas.put(cai, -1);
    }
    if (!mudancas.isEmpty()) {
      HabilidadesQueReagem.mudar(pokemon, mudancas, HabilidadesQueReagem.deOnde(pokemon));
    }
  }

  // Pesadelos: os inimigos dormindo (ou Comatosos) perdem 1/8 da vida
  private static void pesadelos(PokemonEntity entidade, List<PokemonEntity> naLuta) {
    Pokemon dono = entidade.getPokemon();
    for (PokemonEntity inimigo : naLuta) {
      Pokemon pokemon = inimigo.getPokemon();
      boolean dormindo = "slp".equals(EfeitosDeStatus.siglaDoStatus(pokemon)) || Habilidades.tem(pokemon, COMATOSO);
      if (Lados.mesmoLado(pokemon, dono) || !dormindo || Habilidades.tem(pokemon, ESCUDO_MAGICO)
          || !HabilidadesQueReagem.dePe(inimigo)) {
        continue;
      }
      int perde = Math.max(1, pokemon.getMaxHealth() / PESADELO_TIRA);
      HabilidadesQueReagem.avisar(pokemon, Component.translatable("cobblemon_combat_za.habilidade.feriu",
          pokemon.getDisplayName(false), HabilidadesQueReagem.deOnde(dono), perde));
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} perdeu {} PV dormindo (Pesadelos de {})",
          pokemon.getSpecies().getName(), perde, dono.getSpecies().getName());
      HabilidadesQueReagem.ferir(inimigo, perde);
    }
  }

  // Colheita: a última baga comida volta para a mão vazia (50%; no Sol, sempre)
  private static void colheita(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    ItemStack baga = HabilidadesDosItens.ultimaBaga(pokemon);
    if (baga == null || !pokemon.heldItem().isEmpty()
        || EfeitosDoCampo.clima(pokemon) != Clima.SOL && entidade.getRandom().nextBoolean()) {
      return;
    }
    HabilidadesDosItens.esquecerOGasto(pokemon);
    pokemon.swapHeldItem(baga, false, true);
    HabilidadesQueReagem.avisar(pokemon, Component.translatable("cobblemon.battle.item.harvest",
        pokemon.getDisplayName(false), baga.getHoverName()));
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} colheu {} (Colheita)", pokemon.getSpecies().getName(),
        baga.getHoverName().getString());
  }

  // Pegar: com a mão vazia, apanha o item que outro Pokémon da batalha gastou no último turno (sorteado entre eles)
  private static void pegar(PokemonEntity entidade, List<PokemonEntity> naLuta, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    if (!pokemon.heldItem().isEmpty()) {
      return;
    }
    List<Pokemon> gastaram = new ArrayList<>();
    for (PokemonEntity outro : naLuta) {
      if (outro != entidade && HabilidadesDosItens.gastouHaPouco(outro.getPokemon(), agora) != null) {
        gastaram.add(outro.getPokemon());
      }
    }
    if (gastaram.isEmpty()) {
      return;
    }
    Pokemon deQuem = gastaram.get(entidade.getRandom().nextInt(gastaram.size()));
    ItemStack item = HabilidadesDosItens.gastouHaPouco(deQuem, agora);
    HabilidadesDosItens.esquecerOGasto(deQuem);
    pokemon.swapHeldItem(item, false, true);
    HabilidadesQueReagem.avisar(pokemon, Component.translatable("cobblemon.battle.item.recycle",
        pokemon.getDisplayName(false), item.getHoverName()));
    CobblemonCombatZA.LOGGER.info("HABILIDADE: {} apanhou {} que {} gastou (Pegar)", pokemon.getSpecies().getName(),
        item.getHoverName().getString(), deQuem.getSpecies().getName());
  }

  // Coralma: cada Pokémon que caiu na batalha desde o último tick (de qualquer jeito: golpe, veneno, Perecer) sobe o Ataque
  // Especial de quem tem Coralma e está de pé
  private static void coralma(Combate combate, List<PokemonEntity> naLuta) {
    Set<PokemonEntity> antes = DE_PE.put(combate, new HashSet<>(naLuta));
    if (antes == null) {
      return;
    }
    int cairam = 0;
    for (PokemonEntity entidade : antes) {
      if (!naLuta.contains(entidade) && HabilidadesQueReagem.caiu(entidade)) {
        cairam++;
      }
    }
    if (cairam == 0) {
      return;
    }
    for (PokemonEntity entidade : naLuta) {
      Pokemon pokemon = entidade.getPokemon();
      if (Habilidades.tem(pokemon, CORALMA)) {
        HabilidadesQueReagem.mudar(pokemon, Map.of("spa", cairam), HabilidadesQueReagem.deOnde(pokemon));
      }
    }
  }

  // EfeitosDeStatus, no turno do veneno: com Veneno Curador, o veneno (e o veneno forte) cura 1/8 da vida no lugar do dano.
  // true = curou (ou já estava com a vida cheia): o turno do veneno acabou aqui
  public static boolean venenoCurador(PokemonEntity entidade, String sigla) {
    Pokemon pokemon = entidade.getPokemon();
    if (!"psn".equals(sigla) && !"tox".equals(sigla) || !Habilidades.tem(pokemon, VENENO_CURADOR)) {
      return false;
    }
    if (Vida.pvAtual(pokemon) < pokemon.getMaxHealth()) {
      int curou = Vida.curar(entidade, Math.max(1, pokemon.getMaxHealth() / VENENO_CURA));
      HabilidadesQueReagem.avisar(pokemon, Component.translatable("cobblemon.battle.heal.poisonheal",
          pokemon.getDisplayName(false)).append(Component.translatable("cobblemon_combat_za.pv.ganhou", curou)));
      CobblemonCombatZA.LOGGER.info("HABILIDADE: {} +{} PV no veneno (Veneno Curador)", pokemon.getSpecies().getName(),
          curou);
    }
    return true;
  }
}
