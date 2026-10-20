package dev.victor.combatza.golpes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

// A animação de BATALHA do Cobblemon para cada golpe (data/cobblemon/action_effects/moves/<golpe>.json), lida do jar dele
// e guardada em animacoes_de_batalha.json (106 golpes). O que usamos dela:
// - a ANIMAÇÃO do Pokémon, com as alternativas da batalha (a Brasa: "ember", senão "fire", "spray", "special")
// - a PARTE QUE VOA (44 golpes), da boca de quem ataca até o ponto "target" de um Pokémon, e quanto tempo ela leva na
//   batalha (a Brasa, 0,5 s). 5.2d: com a mira num Pokémon, ela mira NO SELVAGEM, como na batalha, e a nossa bola
//   (invisível) voa no mesmo tempo, para o dano entrar quando o visual chega (a 5.2b mirava na bola e não funcionou)
// - o efeito NO ALVO, em quem foi acertado, no ponto "target" dele (80 golpes)
// - os SONS de quem ataca e de quem leva (26 golpes)
public class AnimacoesDeBatalha {
  private static final String ARQUIVO = "/cobblemon_combat_za/animacoes_de_batalha.json";
  private static final double DISTANCIA = 64.0;
  // Golpe com a parte que voa mas sem o tempo na animação (a Bolha Dupla, por exemplo)
  private static final double TEMPO_PADRAO = 0.4;
  // Até onde vai uma batalha por turnos: a parte que voa "pesada de longe" só sai até aqui
  private static final double DISTANCIA_DA_BATALHA = 6.0;

  // "pesado_de_longe" (dele, 18/10: "quando tô tacando o lança-chamas algumas vezes tá travando o jogo"): 3 efeitos (o da
  // Bolha, o do Lança-chamas e o da Respiração de Gelo) têm partículas que colidem com os blocos e uma velocidade que cresce
  // com a distância AO QUADRADO (v.target_deltaz × ... × v.target_deltaz / 2). Na batalha, a 5 blocos, andam 1 ou 2
  // blocos por tick. A 20 blocos, até 30 por tick: cada uma faz o Minecraft varrer milhares de blocos por tick procurando
  // colisão, o tick do seu jogo trava, e como a vida delas é contada em ticks, elas não morrem (travou 45 s)
  private record Voando(String efeito, List<String> de, List<String> para,
      @SerializedName("pesado_de_longe") boolean pesadoDeLonge) {
  }

  private record NoAlvo(String efeito, List<String> em) {
  }

  private record Animacao(List<String> animacao, List<Voando> voando,
      @SerializedName("tempo_ate_o_alvo") Double tempoAteOAlvo, @SerializedName("no_alvo") List<NoAlvo> noAlvo,
      @SerializedName("som_de_quem_ataca") String somDeQuemAtaca, @SerializedName("som_no_alvo") String somNoAlvo) {
  }

  private static Map<String, Animacao> golpes = Map.of();

  private AnimacoesDeBatalha() {
  }

  public static void carregar() {
    Type tipoDoArquivo = TypeToken.getParameterized(Map.class, String.class, Animacao.class).getType();
    try (InputStream entrada = AnimacoesDeBatalha.class.getResourceAsStream(ARQUIVO)) {
      if (entrada == null) {
        CobblemonCombatZA.LOGGER.error("Não achei o {} dentro do jar!", ARQUIVO);
        return;
      }
      golpes = new Gson().fromJson(new InputStreamReader(entrada, StandardCharsets.UTF_8), tipoDoArquivo);
      CobblemonCombatZA.LOGGER.info("Animações de batalha dos golpes: {}", golpes.size());
    } catch (IOException erro) {
      CobblemonCombatZA.LOGGER.error("Falha ao ler o {}", ARQUIVO, erro);
    }
  }

  // Os nomes da animação de ataque na batalha, em ordem de preferência (o modelo usa a primeira que tiver). Vazio = o
  // golpe não tem animação de batalha
  public static List<String> animacao(Move golpe) {
    Animacao animacao = golpes.get(golpe.getName());
    return animacao == null || animacao.animacao() == null ? List.of() : animacao.animacao();
  }

  // O golpe tem a parte que voa na batalha, e ela pode sair desta distância? As "pesadas de longe" só saem até a distância
  // de uma batalha; mais longe, fica a nossa bola com o rastro do tipo
  public static boolean voa(Move golpe, double distancia) {
    Animacao animacao = golpes.get(golpe.getName());
    if (animacao == null || animacao.voando() == null || animacao.voando().isEmpty()) {
      return false;
    }
    return distancia <= DISTANCIA_DA_BATALHA || animacao.voando().stream().noneMatch(Voando::pesadoDeLonge);
  }

  // Quanto tempo, em segundos, a parte que voa leva da boca até o alvo na batalha
  public static double tempoAteOAlvo(Move golpe) {
    Animacao animacao = golpes.get(golpe.getName());
    return animacao == null || animacao.tempoAteOAlvo() == null ? TEMPO_PADRAO : animacao.tempoAteOAlvo();
  }

  // A parte que voa, exatamente como na batalha: da boca de quem ataca até os pontos do Pokémon mirado. E o som
  public static void voar(Move golpe, PokemonEntity atacante, Entity alvo) {
    Animacao animacao = golpes.get(golpe.getName());
    if (animacao == null || animacao.voando() == null) {
      return;
    }
    for (Voando voando : animacao.voando()) {
      enviar(atacante, voando.efeito(), voando.de(), alvo.getId(), voando.para());
    }
    som(atacante, animacao.somDeQuemAtaca());
  }

  // O som do golpe saindo (o da batalha), em quem ataca
  public static void somDeQuemAtaca(Move golpe, Entity atacante) {
    Animacao animacao = golpes.get(golpe.getName());
    if (animacao != null) {
      som(atacante, animacao.somDeQuemAtaca());
    }
  }

  // O efeito da batalha em quem foi acertado (só Pokémon têm os pontos do modelo). true = tinha efeito
  public static boolean noAlvo(Move golpe, Entity alvo) {
    Animacao animacao = golpes.get(golpe.getName());
    if (!(alvo instanceof PokemonEntity) || animacao == null || animacao.noAlvo() == null
        || animacao.noAlvo().isEmpty()) {
      return false;
    }
    for (NoAlvo noAlvo : animacao.noAlvo()) {
      enviar(alvo, noAlvo.efeito(), noAlvo.em(), null, List.of());
    }
    som(alvo, animacao.somNoAlvo());
    return true;
  }

  // O mesmo pacote do Cobblemon que já usamos para os efeitos em Pokémon (Feedback.efeito), agora com o destino
  private static void enviar(Entity fonte, String efeito, List<String> pontos, Integer destino, List<String> pontosDoDestino) {
    ResourceLocation id = ResourceLocation.tryParse(efeito);
    if (id == null || !(fonte.level() instanceof ServerLevel nivel)) {
      return;
    }
    new SpawnSnowstormEntityParticlePacket(id, fonte.getId(), pontos, destino, pontosDoDestino).sendToPlayersAround(
        fonte.getX(), fonte.getY(), fonte.getZ(), DISTANCIA, nivel.dimension(), jogador -> false);
  }

  private static void som(Entity onde, String som) {
    ResourceLocation id = som == null ? null : ResourceLocation.tryParse(som);
    if (id != null) {
      Feedback.som(onde, id);
    }
  }
}
