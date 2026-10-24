package dev.victor.combatza.captura;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.api.pokeball.catching.CatchRateModifier;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.SemDerrubar;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.golpes.EfeitosDeStatus;
import dev.victor.combatza.golpes.Mira;
import dev.victor.combatza.golpes.Vida;
import dev.victor.combatza.ia.Deteccao;
import dev.victor.combatza.treinadores.BatalhasDeTreinador;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

// 8.1 (pedido dele, 19/10, com prints do Z-A: "ele mede o quanto você tem de taxa de captura com esses ícones fora do
// parêntese; vai até 4"): a chance de capturar este selvagem AGORA, com a bola da sua mão (sem bola na mão, a Poké Ball
// comum), pela MESMA conta do Cobblemon (CobblemonCaptureCalculator, lida com javap), para as setas não mentirem:
// - taxa = a da espécie, passando pelo evento POKEMON_CATCH_RATE (onde entram os extras abaixo), × 0,5 fora da batalha
//   por turnos do Cobblemon;
// - a = bola((3 × PV máx − 2 × PV) × taxa, bônus da bola) ÷ (3 × PV máx) × status (sono e gelo 2,5; paralisia, queimadura
//   e veneno 1,5) × bônus de nível baixo (abaixo do 13);
// - cada um dos 4 balanços passa com round(65536 ÷ (255 ÷ a)^0,1875) ÷ 65537. A chance é isso elevado a 4 (a captura
//   crítica, que é rara, fica de fora: a seta pode errar só para menos).
// E os extras do Z-A que ele escolheu, valendo DE VERDADE na captura (o evento): pelas costas ×1,5 (ele não te notou e não
// está em batalha), alfa ×0,5 e brilhante ×0,5 ("na verdade shiny fica mais difícil", dele). Mais o ×2 que desfaz a metade
// do Cobblemon fora da batalha por turnos: no nosso mod, toda luta é fora dela.
// 8.1b (dele: "tem que mudar real a taxa de captura para as nossas regras"; "a gente que controla a vida"): a captura de
// verdade já segue as nossas regras (a vida que a gente controla vai para o PV que o Cobblemon lê, Vida.guardarNoPv, e os
// extras entram no sorteio pelo evento). Para conferir por números, o log mostra a conta das setas (com a chance do
// Cobblemon puro, a que o Catch Rate Display calcula) e, na bola lançada, a taxa que o sorteio recebeu e o resultado.
// 8.1c (dele: "não está passando de faixa; deixei o Squirtle com pouca vida e ficou 2 setas"; escolheu o bônus ×2): com menos
// de 1/4 da vida, a taxa dobra. Sem isso, uma espécie de taxa baixa (o Squirtle, 45) ficava em 2 setas mesmo com 1 de vida.
// 8.1d (dele: "esse tem 5 traços de chance de pegar e não pegou": eram 4 setas, 80,6%, e a Great Ball falhou; escolheu "de 99
// a 100 brilha"): de 99% a 100% as 4 setas BRILHAM, para separar o "certo" das 4 setas comuns (80% ou mais)
// 8.7 (decisões dele, 19/10): as 4 bolas que no Cobblemon só valem na batalha por turnos (pelo "turno" e pelo Pokémon ativo; o
// modificador dele dá ×1 fora dela, javap no BattleModifier) passam a valer na nossa:
// - RÁPIDA ×5 fora de batalha (pelas costas ou antes de qualquer ataque) e nos primeiros 3,5 s dela (o "turno 1");
// - TEMPO: o tempo da batalha vira turno (1 turno = 3,5 s): 1 + 0,3 por turno, até ×4 (em 35 s). Fora de batalha, ×1;
// - NÍVEL: o seu lutador (sem lutador, o seu Pokémon fora mais perto) contra o selvagem: mais que 4 vezes o nível dele, ×4;
//   mais que 2 vezes, ×3; maior, ×2 (os números do Cobblemon, CatchRateModifiers.LEVEL);
// - AMOR: o mesmo Pokémon, da mesma espécie e do gênero oposto, ×8 (CatchRateModifiers.LOVE).
// Elas multiplicam a taxa, igual ao bônus da bola (o modo "multiplicar" do Cobblemon): o sorteio e as setas usam a mesma conta
public class ChanceDeCaptura {
  // O que entrou na conta, para as setas e para o log
  public record Conta(double chance, double chanceDoCobblemonPuro, int setas, boolean brilha, String bola, int pv,
      int pvMaximo, double status, double extras, boolean pelasCostas) {
  }

  // 4 setas: 80% ou mais; 3: 50%; 2: 20%; 1: mais que zero; 0: impossível (as faixas do Z-A). De 99% a 100%, as 4 brilham
  private static final double BRILHA = 0.99;
  private static final double QUATRO_SETAS = 0.8;
  private static final double TRES_SETAS = 0.5;
  private static final double DUAS_SETAS = 0.2;
  private static final double PELAS_COSTAS = 1.5;
  private static final double ALFA = 0.5;
  private static final double BRILHANTE = 0.5;
  private static final double VIDA_BAIXA = 0.25;
  private static final double BONUS_DA_VIDA_BAIXA = 2.0;
  private static final String ASPECTO_ALFA = "alpha";
  private static final double FORA_DA_BATALHA_DO_COBBLEMON = 0.5;
  private static final double SEM_EXTRAS = 1.0;
  // As constantes da conta do Cobblemon
  private static final float TRES = 3f;
  private static final float DOIS = 2f;
  private static final double MAXIMO_DO_BALANCO = 65536.0;
  private static final double SORTEIO_DO_BALANCO = 65537.0;
  private static final double TETO = 255.0;
  private static final double EXPOENTE = 0.1875;
  private static final int BALANCOS = 4;
  private static final double SONO_E_GELO = 2.5;
  private static final double OUTROS_STATUS = 1.5;
  private static final int NIVEL_BAIXO = 13;
  // As 4 bolas da 8.7
  private static final double RAPIDA = 5.0;
  private static final double POR_TURNO_DA_TEMPO = 1229.0 / 4096.0;
  private static final double MAXIMO_DA_TEMPO = 4.0;
  private static final double AMOR = 8.0;

  private ChanceDeCaptura() {
  }

  public static void registrar() {
    CobblemonEvents.POKEMON_CATCH_RATE.subscribe(Priority.NORMAL, SemDerrubar.evento("ChanceDeCaptura.POKEMON_CATCH_RATE", ChanceDeCaptura::aoCalcularATaxa));
    CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.NORMAL, SemDerrubar.evento("ChanceDeCaptura.POKE_BALL_CAPTURE_CALCULATED", ChanceDeCaptura::aoSortear));
  }

  // A bola lançada: os nossos extras entram na taxa que o sorteio do Cobblemon vai usar
  private static void aoCalcularATaxa(PokemonCatchRateEvent evento) {
    double extras = extras(evento.getThrower(), evento.getPokemonEntity(), evento.getPokeBallEntity().getPokeBall());
    float antes = evento.getCatchRate();
    evento.setCatchRate((float) (antes * extras));
    Pokemon pokemon = evento.getPokemonEntity().getPokemon();
    CobblemonCombatZA.LOGGER.info("CAPTURA (bola lançada): {} taxa {} → {} (extras ×{}), PV {}/{}",
        pokemon.getSpecies().getName(), String.format("%.0f", antes), String.format("%.0f", evento.getCatchRate()),
        String.format("%.2f", extras), pokemon.getCurrentHealth(), pokemon.getMaxHealth());
  }

  private static void aoSortear(PokeBallCaptureCalculatedEvent evento) {
    PokemonEntity alvo = evento.getPokemonEntity();
    ServerPlayer quemJogou = evento.getThrower() instanceof ServerPlayer jogador ? jogador : null;
    // Party: o selvagem da batalha de outra pessoa não é seu para capturar: a bola falha e volta
    if (Lados.daBatalhaDeOutro(quemJogou, alvo)) {
      evento.setCaptureResult(new CaptureContext(0, false, false));
      devolverABola(quemJogou, evento.getPokeBallEntity().getPokeBall());
      Irritados.avisar(quemJogou, alvo.getPokemon(), Component.translatable("cobblemon_combat_za.party.batalha_de_outro"));
    } else if (BatalhasDeTreinador.ehDoTreinador(alvo)) {
      // 21/10 (treinadores): o Pokémon de um treinador não se captura: a bola falha e volta, como a do irritado
      evento.setCaptureResult(new CaptureContext(0, false, false));
      devolverABola(quemJogou, evento.getPokeBallEntity().getPokeBall());
      Irritados.avisar(quemJogou, alvo.getPokemon(), Component.translatable("cobblemon_combat_za.treinador.nao_captura",
          alvo.getPokemon().getDisplayName(false)));
    } else if (Desmaio.esta(alvo)) {
      // Etapa 8: o caído no chão tem UMA tentativa; o sorteio do Cobblemon vale, e se falhar ele some (Desmaio)
      Desmaio.tentou(alvo);
    } else if (Irritados.esta(alvo.getPokemon())) {
      // Irritado: a bola falha na hora e volta para você
      evento.setCaptureResult(new CaptureContext(0, false, false));
      devolverABola(quemJogou, evento.getPokeBallEntity().getPokeBall());
      Irritados.avisar(quemJogou, alvo.getPokemon(), Component.translatable("cobblemon_combat_za.captura.ainda_irritado",
          alvo.getPokemon().getDisplayName(false)));
    } else if (!evento.getCaptureResult().isSuccessfulCapture() && alvo.getServer() != null) {
      // A bola falhou: ele fica irritado e, quando sair da bola, acorda e luta com quem jogou
      Irritados.irritar(alvo, quemJogou, alvo.getServer().getTickCount());
      Irritados.lutarAoSair(alvo, quemJogou, alvo.getServer().getTickCount());
    }
    CaptureContext resultado = evento.getCaptureResult();
    CobblemonCombatZA.LOGGER.info("CAPTURA (resultado): {} com {}: {} balanço(s), capturou? {}, crítica? {}",
        evento.getPokemonEntity().getPokemon().getSpecies().getName(),
        evento.getPokeBallEntity().getPokeBall().getName().getPath(), resultado.getNumberOfShakes(),
        resultado.isSuccessfulCapture(), resultado.isCriticalCapture());
  }

  // A bola não é gasta com o irritado (no criativo, nada some mesmo)
  private static void devolverABola(ServerPlayer jogador, PokeBall bola) {
    if (jogador == null || jogador.isCreative()) {
      return;
    }
    ItemStack item = new ItemStack(bola.item());
    if (!jogador.getInventory().add(item)) {
      jogador.drop(item, false);
    }
  }

  public static Conta calcular(ServerPlayer jogador, PokemonEntity selvagem) {
    Pokemon pokemon = selvagem.getPokemon();
    PokeBall bola = bolaDaMao(jogador);
    double extras = extras(jogador, selvagem, bola);
    // Etapa 8: irritado, não dá para capturar (nenhuma seta). 21/10: nem o Pokémon de um treinador, nem o do adversário no
    // duelo (tem dono)
    double chance = Irritados.esta(pokemon) || BatalhasDeTreinador.ehDoTreinador(selvagem) || !pokemon.isWild() ? 0
        : chanceCom(jogador, selvagem, bola, extras);
    double puro = chanceCom(jogador, selvagem, bola, SEM_EXTRAS);
    return new Conta(chance, puro, setasDe(chance), chance >= BRILHA, bola.getName().getPath(), Vida.pvAtual(pokemon),
        pokemon.getMaxHealth(), status(pokemon), extras, pelasCostas(jogador, selvagem));
  }

  private static int setasDe(double chance) {
    if (chance <= 0) {
      return 0;
    }
    if (chance >= QUATRO_SETAS) {
      return 4;
    }
    if (chance >= TRES_SETAS) {
      return 3;
    }
    return chance >= DUAS_SETAS ? 2 : 1;
  }

  // "extras" = o que o nosso evento multiplica na taxa (SEM_EXTRAS = o Cobblemon puro)
  private static double chanceCom(ServerPlayer jogador, PokemonEntity selvagem, PokeBall bola, double extras) {
    Pokemon pokemon = selvagem.getPokemon();
    if (!pokemon.isWild() || pokemon.isUncatchable()) {
      return 0;
    }
    CatchRateModifier daBola = bola.getCatchRateModifier();
    if (daBola.isGuaranteed()) {
      return 1;
    }
    double foraDaBatalha = selvagem.getBattleId() == null ? FORA_DA_BATALHA_DO_COBBLEMON : 1.0;
    float taxa = (float) (pokemon.getForm().getCatchRate() * extras * foraDaBatalha);
    float bonusDaBola = daBola.isValid(jogador, pokemon) ? daBola.value(jogador, pokemon) : 1f;
    float vidaMaxima = pokemon.getMaxHealth();
    float vida = Vida.pvAtual(pokemon);
    float comABola = daBola.behavior(jogador, pokemon).getMutator()
        .invoke((TRES * vidaMaxima - DOIS * vida) * taxa, bonusDaBola);
    double a = comABola / (TRES * vidaMaxima) * status(pokemon) * nivelBaixo(pokemon);
    if (a <= 0) {
      return 0;
    }
    long porBalanco = Math.round(MAXIMO_DO_BALANCO / Math.pow(TETO / a, EXPOENTE));
    return Math.pow(Math.min(1.0, porBalanco / SORTEIO_DO_BALANCO), BALANCOS);
  }

  // Os extras do Z-A, o bônus da vida baixa, o ×2 que desfaz a metade do Cobblemon fora da batalha dele e as 4 bolas da 8.7
  private static double extras(LivingEntity quemJogou, PokemonEntity selvagem, PokeBall bola) {
    Pokemon pokemon = selvagem.getPokemon();
    double multiplicador = selvagem.getBattleId() == null ? 1 / FORA_DA_BATALHA_DO_COBBLEMON : 1.0;
    multiplicador *= daBola(bola, quemJogou, selvagem);
    if (pokemon.getAspects().contains(ASPECTO_ALFA)) {
      multiplicador *= ALFA;
    }
    if (pokemon.getShiny()) {
      multiplicador *= BRILHANTE;
    }
    if (Vida.pvAtual(pokemon) < pokemon.getMaxHealth() * VIDA_BAIXA) {
      multiplicador *= BONUS_DA_VIDA_BAIXA;
    }
    if (quemJogou instanceof ServerPlayer jogador && pelasCostas(jogador, selvagem)) {
      multiplicador *= PELAS_COSTAS;
    }
    return multiplicador;
  }

  // 8.7: o bônus das 4 bolas que dependem da batalha (as outras: ×1, quem cuida delas é o próprio Cobblemon)
  private static double daBola(PokeBall bola, LivingEntity quemJogou, PokemonEntity selvagem) {
    return switch (bola.getName().getPath()) {
      case "quick_ball" -> rapida(selvagem);
      case "timer_ball" -> tempo(selvagem);
      case "level_ball" -> quemJogou instanceof ServerPlayer jogador ? nivel(jogador, selvagem) : 1.0;
      case "love_ball" -> quemJogou instanceof ServerPlayer jogador ? amor(jogador, selvagem) : 1.0;
      default -> 1.0;
    };
  }

  // Quantos ticks a batalha dele já tem (-1 = ele não está em batalha)
  private static long idadeDaBatalha(PokemonEntity selvagem) {
    Combate combate = GerenciarCombates.combateDoSelvagem(selvagem.getPokemon().getUuid());
    return combate == null ? -1 : selvagem.getServer().getTickCount() - combate.getComecouNoTick();
  }

  private static double rapida(PokemonEntity selvagem) {
    long idade = idadeDaBatalha(selvagem);
    return idade < 0 || idade <= EfeitosDeStatus.ticksPorTurno() ? RAPIDA : 1.0;
  }

  private static double tempo(PokemonEntity selvagem) {
    long idade = idadeDaBatalha(selvagem);
    if (idade < 0) {
      return 1.0;
    }
    long turnos = idade / EfeitosDeStatus.ticksPorTurno();
    return Math.min(MAXIMO_DA_TEMPO, 1.0 + turnos * POR_TURNO_DA_TEMPO);
  }

  private static double nivel(ServerPlayer jogador, PokemonEntity selvagem) {
    Pokemon meu = oSeu(jogador, selvagem);
    if (meu == null) {
      return 1.0;
    }
    int dele = selvagem.getPokemon().getLevel();
    if (meu.getLevel() > 4 * dele) {
      return 4.0;
    }
    if (meu.getLevel() > 2 * dele) {
      return 3.0;
    }
    return meu.getLevel() > dele ? 2.0 : 1.0;
  }

  private static double amor(ServerPlayer jogador, PokemonEntity selvagem) {
    Pokemon meu = oSeu(jogador, selvagem);
    Pokemon dele = selvagem.getPokemon();
    boolean opostos = meu != null && meu.getGender() != Gender.GENDERLESS && dele.getGender() != Gender.GENDERLESS
        && meu.getGender() != dele.getGender();
    return opostos && meu.getSpecies().getResourceIdentifier().equals(dele.getSpecies().getResourceIdentifier()) ? AMOR
        : 1.0;
  }

  // O seu lutador; sem lutador, o seu Pokémon fora mais perto do selvagem
  private static Pokemon oSeu(ServerPlayer jogador, PokemonEntity selvagem) {
    Pokemon lutador = Lutadores.de(jogador);
    return lutador != null ? lutador : Lutadores.oMaisPertoDe(jogador, selvagem, Mira.ALCANCE);
  }

  // Pelas costas = ele ainda não te notou (o "?" não encheu) e não está em batalha
  public static boolean pelasCostas(ServerPlayer jogador, PokemonEntity selvagem) {
    return !GerenciarCombates.estaEmCombate(selvagem.getPokemon()) && !Deteccao.teNotou(selvagem, jogador);
  }

  private static PokeBall bolaDaMao(ServerPlayer jogador) {
    for (InteractionHand mao : InteractionHand.values()) {
      if (jogador.getItemInHand(mao).getItem() instanceof PokeBallItem bola) {
        return bola.getPokeBall();
      }
    }
    return PokeBalls.getPokeBall();
  }

  private static double status(Pokemon pokemon) {
    return switch (EfeitosDeStatus.siglaDoStatus(pokemon)) {
      case "slp", "frz" -> SONO_E_GELO;
      case "par", "brn", "psn", "tox" -> OUTROS_STATUS;
      default -> 1.0;
    };
  }

  // A conta do Cobblemon em números inteiros: (36 − 2 × nível) ÷ 10, no mínimo 1, abaixo do nível 13
  private static int nivelBaixo(Pokemon pokemon) {
    int nivel = pokemon.getLevel();
    return nivel < NIVEL_BAIXO ? Math.max((36 - 2 * nivel) / 10, 1) : 1;
  }
}
