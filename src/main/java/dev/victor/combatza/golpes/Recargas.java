package dev.victor.combatza.golpes;

import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.campo.SalaTrick;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.habilidades.HabilidadesDeForma;
import dev.victor.combatza.habilidades.Paradoxo;
import dev.victor.combatza.itens.Bagas;
import dev.victor.combatza.itens.ItensDeDano;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.rede.RecargaPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class Recargas {

  private static final float PORDER_POR_SEGUNDO = 10.0f;
  private static final float RECARGA_BASE_STATUS = 6.0f;
  private static final float FATOR_VELOCIDADE = 1.8f;
  private static final float DESCONTO_VELOCIDADE = 44.0f;
  private static final float FRAMES_POR_SEGUNDO_ZA = 60.0f;
  private static final float RECARGA_MINIMA = 3.0f;
  private static final float JANELA_DE_RECARGA = 0.25f;
  private static final int TICKS_POR_SEGUNDO = 20;
  // Dele, 20/10: os golpes que curam (Recuperação, Pouso, Ovo Macio, Descansar...) descansam 10 s fixos, sem contar a velocidade
  // (antes, uns 6 s como qualquer golpe de status: dava para curar metade da vida sem parar)
  private static final float RECARGA_DA_CURA = 10.0f;
  private static final Map<UUID, long[]> PRONTO_NO_TICK = new HashMap<>();
  // P1-7 (dele, 20/10: "transforma em outro item... de dar mais velocidade na recarga"): até que tick cada golpe recarrega 2×
  // mais rápido (o Éter e o Elixir, combate.EterEElixir)
  private static final Map<UUID, long[]> ACELERADO_ATE = new HashMap<>();
  private static final int ACELERACAO = 2;

  private Recargas() {
  }

  public static long ticksRestantes(Pokemon pokemon, int slot, long agora) {
    long[] prontos = PRONTO_NO_TICK.get(pokemon.getUuid());
    if (prontos == null) {
      return 0;
    }

    return Math.max(0, prontos[slot] - agora);
  }

  public static float iniciarRecarga(Pokemon pokemon, int slot, Move golpe, long agora) {
    // 5.4b e 5.4d: a recarga começou, então as cargas que sobravam deste golpe e a fúria acabaram
    Cargas.esquecer(pokemon, slot);
    Furia.esquecer(pokemon);
    long[] prontos = PRONTO_NO_TICK.get(pokemon.getUuid());
    if (prontos == null) {
      prontos = new long[ControleDeGolpes.MAXIMO_DE_GOLPES];
      PRONTO_NO_TICK.put(pokemon.getUuid(), prontos);
    }
    // A recarga é da batalha contra Pokémon selvagens. Fora dela (lutando com um zumbi, ou sem lutar), o golpe fica
    // pronto assim que termina: os monstros atacam sem parar e, com recarga, ficava difícil (pedido dele, 18/10)
    if (!GerenciarCombates.estaEmCombate(pokemon)) {
      prontos[slot] = agora;
      avisarODono(pokemon, slot, 0);
      return 0f;
    }

    // 21/10: no Truque de Agilidade, a velocidade vira do avesso (SalaTrick)
    int velocidade = (int) Math.round(SalaTrick.velocidade(pokemon, velocidadeDaRecarga(pokemon),
        Recargas::velocidadeDaRecarga));
    float segundos = EfeitosDeVida.ehDeCura(golpe) ? RECARGA_DA_CURA : calcularRecarga(golpe, velocidade);
    // Lote 2 dos itens: a Baga Leppa acelera esta recarga (como o Éter)
    Bagas.leppa(pokemon, slot, golpe, agora);

    int ticks = Math.round(segundos * TICKS_POR_SEGUNDO);
    if (aceleradoAte(pokemon, slot) > agora) {
      ticks /= ACELERACAO;
    }
    prontos[slot] = agora + ticks;
    avisarODono(pokemon, slot, ticks);
    // 21/10 (lote das formas): a Preguiça vadia um turno depois de cada golpe
    HabilidadesDeForma.depoisDoGolpe(pokemon, agora);
    return segundos;
  }

  // Quem entra trocando no meio da batalha fica um tempo sem atacar (Balanceamento.SEGUNDOS_DA_TROCA), como a vez
  // perdida na troca dos jogos. É a mesma recarga, nos 4 golpes, e a HUD mostra o relógio de sempre. Golpe que já tinha
  // recarga maior (ela continua contando enquanto o Pokémon está na bola) fica como está
  public static void esperarAoEntrar(Pokemon pokemon, long agora) {
    esperar(pokemon, agora, Math.round(Balanceamento.SEGUNDOS_DA_TROCA * TICKS_POR_SEGUNDO));
  }

  // 21/10 (lote das formas): os 4 golpes esperam pelo menos "ticks" a partir de agora (a troca acima; a Preguiça, 1 turno)
  public static void esperar(Pokemon pokemon, long agora, long ticks) {
    long[] prontos = PRONTO_NO_TICK.computeIfAbsent(pokemon.getUuid(),
        uuid -> new long[ControleDeGolpes.MAXIMO_DE_GOLPES]);
    long fimDaEspera = agora + ticks;
    for (int slot = 0; slot < prontos.length; slot++) {
      if (prontos[slot] < fimDaEspera) {
        prontos[slot] = fimDaEspera;
        avisarODono(pokemon, slot, (int) (fimDaEspera - agora));
      }
    }
  }

  // O Éter Máximo e o Elixir Máximo: o golpe fica pronto agora
  public static void zerar(Pokemon pokemon, int slot, long agora) {
    long[] prontos = PRONTO_NO_TICK.get(pokemon.getUuid());
    if (prontos != null && prontos[slot] > agora) {
      prontos[slot] = agora;
      avisarODono(pokemon, slot, 0);
    }
  }

  // O Éter e o Elixir: até "ate", este golpe recarrega 2× mais rápido. A recarga que já está contando cai pela metade
  public static void acelerar(Pokemon pokemon, int slot, long ate, long agora) {
    long[] acelerados = ACELERADO_ATE.computeIfAbsent(pokemon.getUuid(),
        uuid -> new long[ControleDeGolpes.MAXIMO_DE_GOLPES]);
    acelerados[slot] = Math.max(acelerados[slot], ate);
    long[] prontos = PRONTO_NO_TICK.get(pokemon.getUuid());
    if (prontos != null && prontos[slot] > agora) {
      long falta = (prontos[slot] - agora) / ACELERACAO;
      prontos[slot] = agora + falta;
      avisarODono(pokemon, slot, (int) falta);
    }
  }

  private static long aceleradoAte(Pokemon pokemon, int slot) {
    long[] acelerados = ACELERADO_ATE.get(pokemon.getUuid());
    return acelerados == null ? 0 : acelerados[slot];
  }

  // A HUD do dono (Etapa 6) mostra a recarga contando: só o servidor sabe quanto tempo ela dura
  private static void avisarODono(Pokemon pokemon, int slot, int ticks) {
    ServerPlayer dono = pokemon.getOwnerPlayer();
    if (dono != null && ServerPlayNetworking.canSend(dono, RecargaPayload.TIPO)) {
      ServerPlayNetworking.send(dono, new RecargaPayload(pokemon.getUuid(), slot, ticks));
    }
  }

  // A Velocidade de verdade (o atributo), com os estágios, a paralisia, os itens, o clima e o Vento de Cauda
  // A Velocidade de verdade na batalha (todos os fatores, sem o Truque de Agilidade); também a do Giro Bola (DanoEspecial)
  static double velocidadeDaRecarga(Pokemon pokemon) {
    return pokemon.getSpeed() * Estagios.multiplicador(pokemon, "spe") * Paralisia.multiplicador(pokemon)
        * ItensDeDano.velocidade(pokemon) * ClimaNosGolpes.velocidade(pokemon) * CampoEBarreiras.velocidade(pokemon)
        * HabilidadesDaConta.velocidade(pokemon) * Paradoxo.atributo(pokemon, "spe")
        * HabilidadesDeForma.velocidade(pokemon);
  }

  private static float calcularRecarga(Move golpe, int velocidade) {
    float base = golpe.getPower() > 0 ? (float) golpe.getPower() / PORDER_POR_SEGUNDO : RECARGA_BASE_STATUS;
    float reducao = Math.max(0f, (FATOR_VELOCIDADE * velocidade - DESCONTO_VELOCIDADE) / FRAMES_POR_SEGUNDO_ZA);
    float segundos = Math.max(RECARGA_MINIMA, base - reducao);
    return Math.round(segundos / JANELA_DE_RECARGA) * JANELA_DE_RECARGA;
  }

  public static void limpar() {
    PRONTO_NO_TICK.clear();
    ACELERADO_ATE.clear();
  }

  public static float emSegundos(long ticks) {
    return ticks / (float) TICKS_POR_SEGUNDO;
  }
}
