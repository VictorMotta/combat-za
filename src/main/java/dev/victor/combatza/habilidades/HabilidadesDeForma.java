package dev.victor.combatza.habilidades;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeature;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.SemDerrubar;
import dev.victor.combatza.campo.Arena;
import dev.victor.combatza.campo.Arenas;
import dev.victor.combatza.campo.Clima;
import dev.victor.combatza.campo.EfeitosDoCampo;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.AgendaDeGolpes;
import dev.victor.combatza.golpes.AplicarEfeitos;
import dev.victor.combatza.golpes.AplicarStatus;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Estagios;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.itens.ItensDeEstagio;
import dev.victor.combatza.pvp.Duelo;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 21/10 (habilidades, lote das formas; pedido dele: "que habilidades são essas? Como assim só 94 funcionam?"): as
// habilidades que MUDAM A FORMA do Pokémon no meio da nossa batalha, como nos jogos (Showdown, abilities.js):
// - Disfarce (Disguise, Mimikyu): o primeiro golpe de dano não tira nada; o disfarce cai e ele perde 1/8 da vida;
// - Cara de Gelo (Ice Face, Eiscue): o primeiro golpe FÍSICO não tira nada e a cara de gelo quebra (fica mais rápido e com
//   menos Defesa); ela volta quando a Neve começa (ou se ele entra com Neve);
// - Troca de Postura (Stance Change, Aegislash): golpe de dano = Forma de Lâmina (Ataque alto); Escudo Real = Forma de
//   Escudo;
// - Modo Zen (Zen Mode, Darmanitan): com metade da vida ou menos, no fim do turno, Modo Zen; acima da metade, volta;
// - Cardume (Schooling, Wishiwashi do nível 20 para cima): com mais de 1/4 da vida, forma de cardume (enorme e forte);
// - Baixar os Escudos (Shields Down, Minior): acima da metade da vida, Forma Meteoro (imune a status); abaixo, o núcleo;
// - Agrupamento (Power Construct, Zygarde 10% e 50%): com metade da vida ou menos, Forma Completa (a vida máxima sobe e a
//   diferença entra na vida); fica até o fim da batalha;
// - Mudapetite (Hunger Switch, Morpeko): troca entre Barriga Cheia e Faminto a cada turno; a Roda Áurea fica Sombria no
//   Faminto;
// - Míssil Glutão (Gulp Missile, Cramorant): ao usar Surfar ou Mergulho, pega uma presa (Arrokuda com mais da metade da
//   vida, Pikachu com menos); quem bater nele leva a presa: perde 1/4 da vida e a Defesa cai (Arrokuda) ou fica
//   paralisado (Pikachu);
// - Aclimatação (Forecast, Castform) e Presente Floral (Flower Gift, Cherrim): a forma do clima (Sol, Chuva, Neve); no
//   Sol, a Cherrim fica com o Ataque e a Defesa Especial ×1,5;
// - De Zero a Herói (Zero to Hero, Palafin): voltou para a bola na batalha, volta como Forma Heroica (muito mais forte);
// - Forte Vínculo (Battle Bond, Greninja do Vínculo): derrubou um inimigo e ainda há outro na luta: com a amizade no máximo
//   (250 ou mais), vira o Greninja de Ash; senão, Ataque, Ataque Especial e Velocidade +1 (uma vez por batalha). É a regra
//   do Showdown do próprio Mega Showdown (o abilities.js dele), a mesma da batalha por turnos do modpack;
// - Mudança Tera (Tera Shift, Terapagos): ao entrar, Forma Terastal (a vida máxima sobe, como no Agrupamento).
// Como a forma muda: pelo próprio Cobblemon, como o Mega Showdown faz na batalha por turnos (os arquivos battle_form dele,
// lidos no .jar): cada forma é um "traço" do Pokémon (stance_forme=blade, blazing_mode=zen...), e trocar o traço troca o
// modelo, os atributos e o tipo, porque a forma tem os números dela. O nosso Dano lê os atributos e o tipo da forma: a
// conta muda sozinha. As formas da batalha voltam ao normal quando ele volta para a bola (as que voltam nos jogos) e no fim
// da batalha (todas). O valor de antes fica anotado no próprio Pokémon (nos dados persistentes dele): se o servidor cair no
// meio da batalha, ele volta ao normal na próxima vez que sair da bola fora de uma batalha, ou quando o dono entrar no jogo.
// Esta classe também é a PORTA do lote das formas: os ganchos nos arquivos dos outros (Dano, ControleDeGolpes,
// AgendaDeGolpes, Recargas, Execucao, Habilidades, DanoEspecial, ClimaNosGolpes) chamam só ela (ou a TiposNaBatalha, para o
// tipo), e ela chama TiposNaBatalha, HabilidadesDeTurno e HabilidadesTrocadas. Tudo isso só na nossa batalha contra
// selvagens
public class HabilidadesDeForma {
  private static final String DISFARCE = "disguise";
  private static final String CARA_DE_GELO = "iceface";
  private static final String TROCA_DE_POSTURA = "stancechange";
  private static final String MODO_ZEN = "zenmode";
  private static final String CARDUME = "schooling";
  private static final String BAIXAR_OS_ESCUDOS = "shieldsdown";
  private static final String AGRUPAMENTO = "powerconstruct";
  private static final String MUDAPETITE = "hungerswitch";
  private static final String MISSIL_GLUTAO = "gulpmissile";
  private static final String ACLIMATACAO = "forecast";
  private static final String PRESENTE_FLORAL = "flowergift";
  private static final String DE_ZERO_A_HEROI = "zerotohero";
  private static final String FORTE_VINCULO = "battlebond";
  private static final String MUDANCA_TERA = "terashift";
  private static final String ESCUDO_REAL = "kingsshield";
  private static final String RODA_AUREA = "aurawheel";
  private static final List<String> PEGAM_PRESA = List.of("surf", "dive");
  private static final int NIVEL_DO_CARDUME = 20;
  // Mega Showdown, Forte Vínculo: com a amizade a partir daqui, o Greninja de Ash
  private static final int AMIZADE_DO_ASH = 250;
  private static final double PRESENTE_FLORAL_NO_SOL = 1.5;
  private static final List<String> ORDEM_DOS_ATRIBUTOS = List.of("atk", "def", "spa", "spd", "spe");
  // Na batalha: quem chegou ou saiu da luta, a cada 5 ticks; o clima e o campo, a cada 10 (meio segundo)
  private static final int CONFERIR_A_CADA = 5;
  private static final int CLIMA_A_CADA = 10;
  private static final int PARTICULAS = 20;
  // Nos dados persistentes do Pokémon: o valor de cada traço antes da batalha (para voltar)
  private static final String MARCA = CobblemonCombatZA.MOD_ID + ":formas_da_batalha";
  private static final String FRASE = "cobblemon_combat_za.habilidade.";

  // Os traços de forma do Cobblemon (data/cobblemon/species_features), o valor de fora da batalha (o padrão do Cobblemon)
  // e se a forma fica quando ele volta para a bola (no Showdown, a "permanente": Disfarce, Cara de Gelo, Agrupamento, De
  // Zero a Herói e Mudança Tera). Os valores são os dos arquivos battle_form do Mega Showdown
  private enum Traco {
    POSTURA("stance_forme", "shield", false),
    ZEN("blazing_mode", "standard", false),
    CARDUME("schooling_form", "solo", false),
    ESCUDO("meteor_shield", "meteor", false),
    FOME("hunger_mode", "full_belly", false),
    MISSIL("missile_form", "none", false),
    CLIMA("forecast_form", "normal", false),
    FLOR("blossom_form", "overcast", false),
    DISFARCE("disguise_form", "disguised", true),
    GELO("penguin_head", "ice_face", true),
    CELULAS("percent_cells", "50", true),
    GOLFINHO("dolphin_form", "zero", true),
    TERA("tera_form", "normal", true),
    VINCULO("battle_bond", "bond", true);

    private final String nome;
    private final String padrao;
    private final boolean ficaNaTroca;

    Traco(String nome, String padrao, boolean ficaNaTroca) {
      this.nome = nome;
      this.padrao = padrao;
      this.ficaNaTroca = ficaNaTroca;
    }

    private static Traco doNome(String nome) {
      for (Traco traco : values()) {
        if (traco.nome.equals(nome)) {
          return traco;
        }
      }
      return null;
    }
  }

  // Quem mudou de forma (e ainda não voltou)
  private static final Map<UUID, Pokemon> MUDARAM = new HashMap<>();
  // Quem está na luta de cada batalha (os selvagens e o lutador de cada jogador), para saber quem chegou e quem saiu
  private static final Map<Combate, Map<UUID, PokemonEntity>> NA_LUTA = new HashMap<>();
  // O golpe que o Disfarce (ou a Cara de Gelo) acabou de segurar: a forma muda logo depois do acerto
  private static final Map<UUID, Pokemon> QUEBRANDO = new HashMap<>();
  // De Zero a Herói: a frase sai uma vez por batalha. Forte Vínculo: uma vez por batalha
  private static final Map<UUID, Pokemon> HEROI_ANUNCIADO = new HashMap<>();
  private static final Map<UUID, Pokemon> VINCULO_USADO = new HashMap<>();
  // Cara de Gelo: o clima da última conferida (ela volta quando a Neve COMEÇA, ou ao entrar com Neve; não a cada turno)
  private static final Map<UUID, Clima> CLIMA_DA_CARA_DE_GELO = new HashMap<>();

  private HabilidadesDeForma() {
  }

  public static void registrar() {
    ServerTickEvents.END_SERVER_TICK.register(servidor -> SemDerrubar.rodar("HabilidadesDeForma", () -> aCadaTick(servidor)));
    // A rede de segurança: saiu da bola fora de uma batalha, ou o dono entrou no jogo, com uma forma de batalha anotada
    CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL, SemDerrubar.evento("HabilidadesDeForma.POKEMON_SENT_POST", HabilidadesDeForma::saiuDaBola));
    ServerPlayConnectionEvents.JOIN.register(SemDerrubar.aoEntrar("HabilidadesDeForma.JOIN", (conexao, remetente, servidor) -> {
      for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(conexao.getPlayer())) {
        voltarSeEstaForaDaBatalha(pokemon);
      }
    }));
    ServerLifecycleEvents.SERVER_STOPPED.register(servidor -> limpar());
  }

  // ---- Os ganchos (chamados pelos arquivos dos outros) ----

  // AgendaDeGolpes: o golpe começou. Proteico e Líbero (o tipo), Troca de Postura (a forma) e Míssil Glutão (a presa). Vale
  // na batalha e também no golpe que vai começar uma (o seu Pokémon mirando num selvagem: a batalha começa no acerto, e o
  // primeiro golpe do Aegislash já sai na Forma de Lâmina)
  public static void aoComecarGolpe(Execucao execucao) {
    Pokemon pokemon = execucao.getPokemon();
    boolean comecaUmaBatalha = !pokemon.isWild() && execucao.alvoMirado() instanceof PokemonEntity alvo
        && alvo.getPokemon().isWild();
    if (!comecaUmaBatalha && !GerenciarCombates.estaEmCombate(pokemon)) {
      return;
    }
    TiposNaBatalha.aoUsarGolpe(execucao);
    trocarDePostura(pokemon, execucao.getGolpe());
    pegarPresa(pokemon, execucao.getGolpe());
  }

  // ControleDeGolpes.acertar, antes do dano: o Disfarce e a Cara de Gelo seguram o golpe (dano 0)
  public static float antesDoDano(Execucao execucao, Entity alvo, float dano) {
    if (dano <= 0 || !(alvo instanceof PokemonEntity entidade)) {
      return dano;
    }
    Pokemon pokemon = entidade.getPokemon();
    if (QUEBRANDO.containsKey(pokemon.getUuid()) || !GerenciarCombates.estaEmCombate(pokemon)) {
      return dano;
    }
    if (Habilidades.tem(pokemon, DISFARCE) && especie(pokemon, "mimikyu")
        && "disguised".equals(valor(pokemon, Traco.DISFARCE))) {
      QUEBRANDO.put(pokemon.getUuid(), pokemon);
      CobblemonCombatZA.LOGGER.info("DISFARCE: {} segurou {}", pokemon.getSpecies().getName(),
          execucao.getGolpe().getName());
      avisar(pokemon, Component.translatable("cobblemon.battle.activate.disguise"));
      return 0f;
    }
    if (Habilidades.tem(pokemon, CARA_DE_GELO) && especie(pokemon, "eiscue")
        && "ice_face".equals(valor(pokemon, Traco.GELO))
        && execucao.getGolpe().getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL()) {
      QUEBRANDO.put(pokemon.getUuid(), pokemon);
      CobblemonCombatZA.LOGGER.info("CARA DE GELO: {} segurou {}", pokemon.getSpecies().getName(),
          execucao.getGolpe().getName());
      avisar(pokemon, Component.translatable("cobblemon.battle.ability.generic", pokemon.getDisplayName(false),
          Habilidades.nome(pokemon)));
      return 0f;
    }
    return dano;
  }

  // ControleDeGolpes.acertar, depois do dano: o Disfarce cai, a presa é cuspida, Múmia e companhia, Troca de Cor, Forte
  // Vínculo
  public static void depoisDoAcerto(Execucao execucao, PokemonEntity atacante, Entity alvo, double pvTirados) {
    if (!(alvo instanceof PokemonEntity atingido) || atingido == atacante) {
      return;
    }
    if (QUEBRANDO.remove(atingido.getPokemon().getUuid()) != null) {
      quebrar(atingido);
    }
    cuspirPresa(execucao, atacante, atingido);
    HabilidadesTrocadas.depoisDoContato(execucao, atacante, atingido, pvTirados);
    TiposNaBatalha.depoisDeApanhar(execucao, atingido);
    forteVinculo(atacante, atingido);
  }

  // Recargas: a recarga de um golpe começou na batalha (a Preguiça)
  public static void depoisDoGolpe(Pokemon pokemon, long agora) {
    HabilidadesDeTurno.depoisDoGolpe(pokemon, agora);
  }

  // Dano: o Ataque (Começo Lento ×0,5 no físico; Presente Floral ×1,5 no físico, no Sol)
  public static double ataque(Pokemon atacante, boolean fisico) {
    double floral = fisico && floresNoSol(atacante) ? PRESENTE_FLORAL_NO_SOL : 1.0;
    return HabilidadesDeTurno.ataque(atacante, fisico) * floral;
  }

  // Dano: a Defesa (Presente Floral: a Defesa Especial ×1,5 no Sol)
  public static double defesa(Pokemon defensor, boolean fisico) {
    return !fisico && floresNoSol(defensor) ? PRESENTE_FLORAL_NO_SOL : 1.0;
  }

  // Recargas e Execucao: a Velocidade (Começo Lento ×0,5)
  public static double velocidade(Pokemon pokemon) {
    return HabilidadesDeTurno.velocidade(pokemon);
  }

  // Execucao: o ritmo da carga (Adiar ×1,5, como a Cauda Pesada)
  public static double ritmoDaCarga(Pokemon pokemon) {
    return HabilidadesDeTurno.ritmoDaCarga(pokemon);
  }

  // Habilidades.imuneAoStatus: o Comatoso e o Minior na Forma Meteoro (Baixar os Escudos) não pegam status nenhum
  public static boolean imuneATodoStatus(Pokemon pokemon) {
    return HabilidadesDeTurno.comatoso(pokemon) || Habilidades.tem(pokemon, BAIXAR_OS_ESCUDOS) && especie(pokemon, "minior")
        && "meteor".equals(valor(pokemon, Traco.ESCUDO));
  }

  // DanoEspecial: o Comatoso conta como dormindo (o Comedor de Sonhos pega nele)
  public static boolean contaComoDormindo(Pokemon pokemon) {
    return HabilidadesDeTurno.contaComoDormindo(pokemon);
  }

  // ClimaNosGolpes.tipo: a Roda Áurea do Morpeko é Sombria no Faminto (Showdown, moves.js: onModifyType); null = não muda
  public static ElementalType tipoDoGolpe(Pokemon atacante, Move golpe) {
    if (!RODA_AUREA.equals(golpe.getName()) || !especie(atacante, "morpeko")) {
      return null;
    }
    return "hangry".equals(valor(atacante, Traco.FOME)) ? ElementalTypes.DARK : ElementalTypes.ELECTRIC;
  }

  // ---- A cada tick: quem chegou e quem saiu da luta, o clima, o fim do turno ----

  private static void aCadaTick(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    if (agora % CONFERIR_A_CADA != 0) {
      return;
    }
    boolean conferirOClima = agora % CLIMA_A_CADA == 0;
    boolean virouTurno = agora % EfeitosDeStatus.ticksPorTurno() == 0;
    for (Combate combate : List.copyOf(GerenciarCombates.todos())) {
      Arena arena = Arenas.daBatalha(combate);
      Map<UUID, PokemonEntity> antes = NA_LUTA.getOrDefault(combate, Map.of());
      Map<UUID, PokemonEntity> agoraNaLuta = new HashMap<>();
      for (PokemonEntity entidade : arena.pokemons()) {
        if (entidade.isAlive() && !entidade.getPokemon().isFainted()) {
          agoraNaLuta.put(entidade.getPokemon().getUuid(), entidade);
        }
      }
      NA_LUTA.put(combate, agoraNaLuta);
      antes.forEach((uuid, entidade) -> {
        if (!agoraNaLuta.containsKey(uuid)) {
          aoSair(entidade.getPokemon());
        }
      });
      HabilidadesTrocadas.conferirOGas(combate, arena.pokemons());
      for (PokemonEntity entidade : agoraNaLuta.values()) {
        if (!antes.containsKey(entidade.getPokemon().getUuid())) {
          aoEntrar(entidade, agora);
        }
        HabilidadesTrocadas.tentarTracar(entidade, arena, agora);
        HabilidadesDeTurno.atualizar(entidade.getPokemon(), agora);
        if (conferirOClima) {
          reagirAoCampo(entidade);
        }
        if (virouTurno) {
          fimDoTurno(entidade);
        }
      }
    }
    // As batalhas que acabaram: todos saíram da luta
    NA_LUTA.entrySet().removeIf(batalha -> {
      if (GerenciarCombates.todos().contains(batalha.getKey())) {
        return false;
      }
      batalha.getValue().values().forEach(entidade -> aoSair(entidade.getPokemon()));
      return true;
    });
    if (agora % SharedConstants.TICKS_PER_SECOND == 0) {
      varrer();
    }
  }

  // Chegou na luta: o começo da batalha, o selvagem que entrou, o lutador novo
  private static void aoEntrar(PokemonEntity entidade, long agora) {
    Pokemon pokemon = entidade.getPokemon();
    // Uma forma de batalha anotada de antes (o servidor caiu no meio de uma batalha): volta ao normal antes de tudo
    if (!MUDARAM.containsKey(pokemon.getUuid()) && pokemon.getPersistentData().contains(MARCA)) {
      voltar(pokemon, traco -> true);
    }
    HabilidadesTrocadas.aoEntrar(pokemon);
    HabilidadesDeTurno.aoEntrar(entidade, agora);
    mudancaTera(entidade);
    cardume(pokemon);
    escudos(pokemon);
    reagirAoCampo(entidade);
    if (Habilidades.tem(pokemon, DE_ZERO_A_HEROI) && "hero".equals(valor(pokemon, Traco.GOLFINHO))
        && HEROI_ANUNCIADO.put(pokemon.getUuid(), pokemon) == null) {
      avisar(pokemon, Component.translatable(FRASE + "forma.heroi", pokemon.getDisplayName(false)));
    }
  }

  // Saiu da luta (voltou para a bola, desmaiou, fugiu, a batalha acabou): o tipo, a habilidade e as formas que não ficam
  // voltam. De Zero a Herói: voltou para a bola no meio da batalha, vira Herói
  private static void aoSair(Pokemon pokemon) {
    TiposNaBatalha.esquecer(pokemon);
    HabilidadesTrocadas.esquecer(pokemon);
    HabilidadesDeTurno.esquecer(pokemon);
    QUEBRANDO.remove(pokemon.getUuid());
    CLIMA_DA_CARA_DE_GELO.remove(pokemon.getUuid());
    voltar(pokemon, traco -> !traco.ficaNaTroca);
    if (Habilidades.tem(pokemon, DE_ZERO_A_HEROI) && especie(pokemon, "palafin") && !pokemon.isFainted()
        && GerenciarCombates.estaEmCombate(pokemon) && mudar(pokemon, Traco.GOLFINHO, "hero")) {
      CobblemonCombatZA.LOGGER.info("DE ZERO A HERÓI: {} voltou para a bola e virou Herói", pokemon.getSpecies().getName());
    }
  }

  // Showdown, onResidual (no fim de cada turno de 3,5 s): Modo Zen, Cardume, Baixar os Escudos, Mudapetite e Agrupamento
  private static void fimDoTurno(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    modoZen(pokemon);
    cardume(pokemon);
    escudos(pokemon);
    if (Habilidades.tem(pokemon, MUDAPETITE) && especie(pokemon, "morpeko")) {
      mudar(pokemon, Traco.FOME, "hangry".equals(valor(pokemon, Traco.FOME)) ? "full_belly" : "hangry");
    }
    agrupamento(entidade);
  }

  private static void modoZen(Pokemon pokemon) {
    if (!Habilidades.tem(pokemon, MODO_ZEN) || !especie(pokemon, "darmanitan")) {
      return;
    }
    boolean zen = "zen".equals(valor(pokemon, Traco.ZEN));
    boolean metadeOuMenos = Vida.pvAtual(pokemon) * 2 <= pokemon.getMaxHealth();
    if (metadeOuMenos && !zen && mudar(pokemon, Traco.ZEN, "zen")) {
      avisar(pokemon, Component.translatable(FRASE + "forma.zen", pokemon.getDisplayName(false)));
    } else if (!metadeOuMenos && zen && mudar(pokemon, Traco.ZEN, "standard")) {
      avisar(pokemon, Component.translatable(FRASE + "forma.zen_acabou", pokemon.getDisplayName(false)));
    }
  }

  // Showdown, Cardume: do nível 20 para cima, com mais de 1/4 da vida, o cardume (ao entrar e no fim de cada turno)
  private static void cardume(Pokemon pokemon) {
    if (!Habilidades.tem(pokemon, CARDUME) || !especie(pokemon, "wishiwashi") || pokemon.getLevel() < NIVEL_DO_CARDUME) {
      return;
    }
    boolean forte = Vida.pvAtual(pokemon) * 4 > pokemon.getMaxHealth();
    if (mudar(pokemon, Traco.CARDUME, forte ? "school" : "solo")) {
      avisar(pokemon, Component.translatable(forte ? "cobblemon.battle.formechange.school"
          : "cobblemon.battle.formechange.wishiwashi", pokemon.getDisplayName(false)));
    }
  }

  // Showdown, Baixar os Escudos: acima da metade da vida, a Forma Meteoro; senão, o núcleo (ao entrar e no fim do turno)
  private static void escudos(Pokemon pokemon) {
    if (!Habilidades.tem(pokemon, BAIXAR_OS_ESCUDOS) || !especie(pokemon, "minior")) {
      return;
    }
    boolean meteoro = Vida.pvAtual(pokemon) * 2 > pokemon.getMaxHealth();
    if (mudar(pokemon, Traco.ESCUDO, meteoro ? "meteor" : "core")) {
      avisar(pokemon, Component.translatable(meteoro ? "cobblemon.battle.formechange.meteor"
          : "cobblemon.battle.formechange.minior", pokemon.getDisplayName(false)));
    }
  }

  // Showdown, Agrupamento: com metade da vida ou menos, a Forma Completa, até o fim da batalha
  private static void agrupamento(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if (!Habilidades.tem(pokemon, AGRUPAMENTO) || !especie(pokemon, "zygarde")
        || "complete".equals(valor(pokemon, Traco.CELULAS)) || Vida.pvAtual(pokemon) * 2 > pokemon.getMaxHealth()) {
      return;
    }
    if (mudarComAVida(entidade, Traco.CELULAS, "complete")) {
      avisar(pokemon, Component.translatable(FRASE + "forma.completa", pokemon.getDisplayName(false)));
    }
  }

  // Showdown, Mudança Tera (onPreStart): ao entrar, a Forma Terastal
  private static void mudancaTera(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    if (Habilidades.tem(pokemon, MUDANCA_TERA) && especie(pokemon, "terapagos")
        && mudarComAVida(entidade, Traco.TERA, "terastal")) {
      avisar(pokemon, Component.translatable(FRASE + "forma.terastal", pokemon.getDisplayName(false)));
    }
  }

  // Meio em meio segundo: Aclimatação e Presente Floral (a forma do clima), a Cara de Gelo que volta na Neve, o Mimetismo
  private static void reagirAoCampo(PokemonEntity entidade) {
    Pokemon pokemon = entidade.getPokemon();
    Clima clima = EfeitosDoCampo.clima(pokemon);
    if (Habilidades.tem(pokemon, ACLIMATACAO) && especie(pokemon, "castform")) {
      String forma = switch (clima) {
        case SOL -> "sunny";
        case CHUVA -> "rainy";
        case NEVE -> "snowy";
        default -> "normal";
      };
      if (mudar(pokemon, Traco.CLIMA, forma)) {
        avisar(pokemon, Component.translatable(FRASE + "forma.clima", pokemon.getDisplayName(false),
            Habilidades.nome(pokemon)));
      }
    }
    if (Habilidades.tem(pokemon, PRESENTE_FLORAL) && especie(pokemon, "cherrim")
        && mudar(pokemon, Traco.FLOR, clima == Clima.SOL ? "sunshine" : "overcast")) {
      avisar(pokemon, Component.translatable(FRASE + "forma.clima", pokemon.getDisplayName(false),
          Habilidades.nome(pokemon)));
    }
    if (Habilidades.tem(pokemon, CARA_DE_GELO) && especie(pokemon, "eiscue")) {
      Clima antes = CLIMA_DA_CARA_DE_GELO.put(pokemon.getUuid(), clima);
      if (clima == Clima.NEVE && antes != Clima.NEVE && mudar(pokemon, Traco.GELO, "ice_face")) {
        avisar(pokemon, Component.translatable(FRASE + "cara_de_gelo_voltou", pokemon.getDisplayName(false)));
      }
    }
    TiposNaBatalha.conferirMimetismo(pokemon);
  }

  private static boolean floresNoSol(Pokemon pokemon) {
    return Habilidades.tem(pokemon, PRESENTE_FLORAL) && especie(pokemon, "cherrim")
        && EfeitosDoCampo.clima(pokemon) == Clima.SOL;
  }

  // Showdown, Troca de Postura (onModifyMove): golpe de dano = Lâmina; Escudo Real = Escudo; os outros de status, nada
  private static void trocarDePostura(Pokemon pokemon, Move golpe) {
    if (!Habilidades.tem(pokemon, TROCA_DE_POSTURA) || !especie(pokemon, "aegislash")) {
      return;
    }
    boolean escudoReal = ESCUDO_REAL.equals(golpe.getName());
    if (golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS() && !escudoReal) {
      return;
    }
    if (mudar(pokemon, Traco.POSTURA, escudoReal ? "shield" : "blade")) {
      avisar(pokemon, Component.translatable(FRASE + (escudoReal ? "forma.escudo" : "forma.lamina"),
          pokemon.getDisplayName(false)));
    }
  }

  // Showdown, Míssil Glutão: Surfar (e Mergulho) pega a presa. Mais da metade da vida: Arrokuda; senão, Pikachu
  private static void pegarPresa(Pokemon pokemon, Move golpe) {
    if (!PEGAM_PRESA.contains(golpe.getName()) || !Habilidades.tem(pokemon, MISSIL_GLUTAO) || !especie(pokemon, "cramorant")
        || !"none".equals(valor(pokemon, Traco.MISSIL))) {
      return;
    }
    boolean arrokuda = Vida.pvAtual(pokemon) * 2 > pokemon.getMaxHealth();
    mudar(pokemon, Traco.MISSIL, arrokuda ? "gulping" : "gorging");
    avisar(pokemon, Component.translatable(FRASE + "missil.pegou", pokemon.getDisplayName(false),
        Component.translatable("cobblemon.species." + (arrokuda ? "arrokuda" : "pikachu") + ".name")));
  }

  // Showdown, Míssil Glutão (onDamagingHit): apanhou com a presa na boca e cospe em quem bateu
  private static void cuspirPresa(Execucao execucao, PokemonEntity atacante, PokemonEntity atingido) {
    Pokemon cramorant = atingido.getPokemon();
    String presa = valor(cramorant, Traco.MISSIL);
    if ("none".equals(presa) || !Habilidades.tem(cramorant, MISSIL_GLUTAO) || !atacante.isAlive()
        || atacante.getPokemon().isFainted() || !GerenciarCombates.estaEmCombate(cramorant)) {
      return;
    }
    mudar(cramorant, Traco.MISSIL, "none");
    Pokemon quemBateu = atacante.getPokemon();
    int pv = Math.max(1, quemBateu.getMaxHealth() / 4);
    CobblemonCombatZA.LOGGER.info("MÍSSIL GLUTÃO: {} cuspiu {} em {} (−{} PV)", cramorant.getSpecies().getName(), presa,
        quemBateu.getSpecies().getName(), pv);
    avisar(quemBateu, Component.translatable(FRASE + "missil.cuspiu", cramorant.getDisplayName(false),
        quemBateu.getDisplayName(false), pv));
    Vida.ferir(atacante, pv);
    if (!atacante.isAlive() || quemBateu.isFainted()) {
      return;
    }
    Component deOnde = Component.translatable(FRASE + "de", Habilidades.nome(cramorant), cramorant.getDisplayName(false));
    if ("gulping".equals(presa)) {
      cairPorOutro(quemBateu, "def", cramorant);
    } else {
      AplicarStatus.pelaHabilidade(execucao, atacante, "par", deOnde);
    }
  }

  // O Disfarce cai (e ele perde 1/8 da vida) ou a Cara de Gelo quebra: logo depois do golpe que eles seguraram
  private static void quebrar(PokemonEntity atingido) {
    Pokemon pokemon = atingido.getPokemon();
    if ("disguised".equals(valor(pokemon, Traco.DISFARCE)) && especie(pokemon, "mimikyu")) {
      mudar(pokemon, Traco.DISFARCE, "busted");
      int pv = Math.max(1, pokemon.getMaxHealth() / 8);
      avisar(pokemon, Component.translatable(FRASE + "disfarce", pokemon.getDisplayName(false), pv));
      if (atingido.isAlive()) {
        Vida.ferir(atingido, pv);
      }
    } else if ("ice_face".equals(valor(pokemon, Traco.GELO)) && especie(pokemon, "eiscue")) {
      mudar(pokemon, Traco.GELO, "noice_face");
      avisar(pokemon, Component.translatable(FRASE + "cara_de_gelo", pokemon.getDisplayName(false)));
    }
  }

  // Showdown do Mega Showdown, Forte Vínculo: o Greninja do Vínculo derrubou um inimigo com um golpe e ainda há outro na
  // luta. Amizade 250 ou mais: o Greninja de Ash (até o fim da batalha); senão, +1 em três atributos
  private static void forteVinculo(PokemonEntity atacante, PokemonEntity atingido) {
    Pokemon greninja = atacante.getPokemon();
    if (!caiu(atingido) || !Habilidades.tem(greninja, FORTE_VINCULO) || !especie(greninja, "greninja")
        || !"bond".equals(valor(greninja, Traco.VINCULO)) || !atacante.isAlive() || greninja.isFainted()
        || VINCULO_USADO.containsKey(greninja.getUuid()) || !sobraInimigo(greninja, atingido)) {
      return;
    }
    VINCULO_USADO.put(greninja.getUuid(), greninja);
    CobblemonCombatZA.LOGGER.info("FORTE VÍNCULO: {} derrubou {} (amizade {})", greninja.getSpecies().getName(),
        atingido.getPokemon().getSpecies().getName(), greninja.getFriendship());
    if (greninja.getFriendship() >= AMIZADE_DO_ASH && mudar(greninja, Traco.VINCULO, "ash")) {
      avisar(greninja, Component.translatable("cobblemon.battle.formechange.ash", greninja.getDisplayName(false)));
      return;
    }
    avisar(greninja, Component.translatable(FRASE + "vinculo", greninja.getDisplayName(false)));
    long agora = atacante.getServer() == null ? 0 : atacante.getServer().getTickCount();
    subir(greninja, Map.of("atk", 1, "spa", 1, "spe", 1), agora);
  }

  // Fora de qualquer batalha e sem golpe no meio
  static boolean foraDaBatalha(Pokemon pokemon) {
    return !GerenciarCombates.estaEmCombate(pokemon) && !AgendaDeGolpes.estaOcupado(pokemon);
  }

  private static boolean caiu(PokemonEntity entidade) {
    return !entidade.isAlive() || entidade.getPokemon().isFainted() || Desmaio.esta(entidade)
        || BatalhasDeTreinador.desmaiou(entidade);
  }

  // Ainda há inimigo na luta? Do seu lado: outro selvagem de pé. Do selvagem: um jogador com alguém do time de pé
  private static boolean sobraInimigo(Pokemon quem, PokemonEntity derrubado) {
    Combate combate = GerenciarCombates.combateDe(quem);
    if (combate == null) {
      return false;
    }
    if (quem.isWild()) {
      return combate.getJogadores().stream().anyMatch(jogador -> !Combate.timeTodoDesmaiado(jogador));
    }
    // 21/10: no duelo, outro do time do adversário com vida; contra um treinador, o próximo Pokémon dele
    if (combate instanceof Duelo duelo && quem.getOwnerPlayer() != null) {
      return duelo.temOutroComVida(duelo.outro(quem.getOwnerPlayer()), derrubado.getPokemon());
    }
    if (BatalhasDeTreinador.aindaVemPokemon(combate)) {
      return true;
    }
    return combate.getSelvagens().stream().anyMatch(selvagem -> selvagem != derrubado && !caiu(selvagem));
  }

  // ---- As formas: mudar, voltar e anotar ----

  // O valor do traço agora (sem o traço anotado, o padrão do Cobblemon)
  private static String valor(Pokemon pokemon, Traco traco) {
    SpeciesFeature feature = pokemon.getFeature(traco.nome);
    return feature instanceof StringSpeciesFeature escolha ? escolha.getValue() : traco.padrao;
  }

  private static boolean especie(Pokemon pokemon, String especie) {
    return especie.equals(pokemon.getSpecies().getResourceIdentifier().getPath());
  }

  // Muda a forma (como o Mega Showdown: StringSpeciesFeature.apply) e anota o valor de antes. Devolve se mudou
  private static boolean mudar(Pokemon pokemon, Traco traco, String valor) {
    String antes = valor(pokemon, traco);
    if (antes.equals(valor)) {
      return false;
    }
    CompoundTag dados = pokemon.getPersistentData();
    CompoundTag marcas = dados.getCompound(MARCA);
    if (!marcas.contains(traco.nome)) {
      marcas.putString(traco.nome, antes);
    } else if (marcas.getString(traco.nome).equals(valor)) {
      marcas.remove(traco.nome);
    }
    if (marcas.isEmpty()) {
      dados.remove(MARCA);
      MUDARAM.remove(pokemon.getUuid());
    } else {
      dados.put(MARCA, marcas);
      MUDARAM.put(pokemon.getUuid(), pokemon);
    }
    new StringSpeciesFeature(traco.nome, valor).apply(pokemon);
    CobblemonCombatZA.LOGGER.info("FORMA: {} {} {} → {}", pokemon.getSpecies().getName(), traco.nome, antes, valor);
    Feedback.particulas(pokemon.getEntity(), ParticleTypes.END_ROD, PARTICULAS);
    return true;
  }

  // Showdown, Agrupamento e Mudança Tera: a vida máxima muda com a forma, e a diferença entra na vida de agora (o Cobblemon
  // sozinho guardaria a mesma fração da vida)
  private static boolean mudarComAVida(PokemonEntity entidade, Traco traco, String valor) {
    Pokemon pokemon = entidade.getPokemon();
    int maximoAntes = pokemon.getMaxHealth();
    int pvAntes = Vida.pvAtual(pokemon);
    if (!mudar(pokemon, traco, valor)) {
      return false;
    }
    int pv = Math.clamp(pokemon.getMaxHealth() - (maximoAntes - pvAntes), 1, pokemon.getMaxHealth());
    pokemon.setCurrentHealth(pv);
    entidade.setHealth(Vida.naVida(entidade, pv));
    return true;
  }

  // Volta os traços anotados que "quais" aceita ao valor de antes da batalha
  private static void voltar(Pokemon pokemon, Predicate<Traco> quais) {
    CompoundTag dados = pokemon.getPersistentData();
    if (!dados.contains(MARCA)) {
      MUDARAM.remove(pokemon.getUuid());
      return;
    }
    CompoundTag marcas = dados.getCompound(MARCA);
    for (String nome : List.copyOf(marcas.getAllKeys())) {
      Traco traco = Traco.doNome(nome);
      if (traco != null && !quais.test(traco)) {
        continue;
      }
      String antes = marcas.getString(nome);
      marcas.remove(nome);
      new StringSpeciesFeature(nome, antes).apply(pokemon);
      CobblemonCombatZA.LOGGER.info("FORMA: {} voltou ({} = {})", pokemon.getSpecies().getName(), nome, antes);
    }
    if (marcas.isEmpty()) {
      dados.remove(MARCA);
      MUDARAM.remove(pokemon.getUuid());
    } else {
      dados.put(MARCA, marcas);
    }
  }

  private static void saiuDaBola(PokemonSentEvent.Post evento) {
    voltarSeEstaForaDaBatalha(evento.getPokemon());
  }

  private static void voltarSeEstaForaDaBatalha(Pokemon pokemon) {
    if (pokemon.getPersistentData().contains(MARCA) && !GerenciarCombates.estaEmCombate(pokemon)) {
      voltar(pokemon, traco -> true);
    }
  }

  // A cada segundo: quem não está mais em nenhuma batalha volta à forma de antes (todas as formas); o resto do lote esquece.
  // No meio de um golpe, espera ele acabar (o golpe que começa uma batalha muda a forma antes de a batalha existir)
  private static void varrer() {
    for (Pokemon pokemon : List.copyOf(MUDARAM.values())) {
      if (foraDaBatalha(pokemon)) {
        voltar(pokemon, traco -> true);
      }
    }
    HEROI_ANUNCIADO.values().removeIf(pokemon -> !GerenciarCombates.estaEmCombate(pokemon));
    VINCULO_USADO.values().removeIf(pokemon -> !GerenciarCombates.estaEmCombate(pokemon));
    QUEBRANDO.values().removeIf(pokemon -> !GerenciarCombates.estaEmCombate(pokemon));
    TiposNaBatalha.varrer();
    HabilidadesTrocadas.varrer();
  }

  // ---- Frases e atributos ----

  // A frase vai para o histórico de quem está na batalha dele
  static void avisar(Pokemon pokemon, Component frase) {
    Combate combate = GerenciarCombates.combateDe(pokemon);
    if (combate == null) {
      return;
    }
    for (ServerPlayer jogador : combate.getJogadores()) {
      Feedback.historico(jogador, pokemon, frase);
    }
  }

  // Sobe os atributos dele (Forte Vínculo, Incorporar Aspecto), com a frase de cada um
  static void subir(Pokemon pokemon, Map<String, Integer> quanto, long agora) {
    Map<String, Integer> mudaram = new LinkedHashMap<>();
    for (String atributo : ORDEM_DOS_ATRIBUTOS) {
      Integer pedido = quanto.get(atributo);
      if (pedido == null) {
        continue;
      }
      int mudou = Estagios.mudar(pokemon, atributo, pedido, agora);
      avisar(pokemon, Component.translatable(Estagios.FRASE + AplicarEfeitos.comoMudou(pedido, mudou),
          Estagios.nomeDoAtributo(atributo, pokemon)));
      if (mudou != 0) {
        mudaram.put(atributo, mudou);
      }
    }
    if (!mudaram.isEmpty()) {
      ItensDeEstagio.mudaram(pokemon, mudaram);
    }
  }

  // Um atributo dele cai 1 pela habilidade de outro (o Arrokuda do Míssil Glutão): a queda de todas as habilidades
  // (HabilidadesDeEstagio.baixar: o Corpo Claro, o Amuleto, a Névoa, a Armadura Espelhada, o Competitivo e o Desafiador)
  private static void cairPorOutro(Pokemon vitima, String atributo, Pokemon deQuem) {
    HabilidadesDeEstagio.baixar(deQuem, vitima, Map.of(atributo, -1), false);
  }

  public static void limpar() {
    MUDARAM.clear();
    NA_LUTA.clear();
    QUEBRANDO.clear();
    HEROI_ANUNCIADO.clear();
    VINCULO_USADO.clear();
    CLIMA_DA_CARA_DE_GELO.clear();
    TiposNaBatalha.limpar();
    HabilidadesDeTurno.limpar();
    HabilidadesTrocadas.limpar();
  }
}
