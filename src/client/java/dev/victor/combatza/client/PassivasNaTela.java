package dev.victor.combatza.client;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.victor.combatza.rede.PassivasPayload;
import dev.victor.combatza.rede.PassivasPayload.Passiva;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// P5-10 (veto dele, 20/10: "se for um status deixa tanto dentro quanto fora da batalha um iconezinho com tempo contando, como
// se fosse uma passiva de RPG"): em cima do painel do time (PainelDoTime chama, já na escala dele), um quadradinho por efeito
// que vale para você: o símbolo na cor dele, a barrinha do tempo que falta dentro, os segundos embaixo (piscando nos 3
// últimos). A borda: na cor do clima/campo; azul nas barreiras do seu lado; rosa nas dos selvagens. A chuva do mundo não tem
// tempo: sem barrinha nem segundos. Quando um efeito aparece, o nome dele fica 3 s ao lado dos ícones
public class PassivasNaTela {
  private static final float ICONE = 14f;
  private static final float ESPACO = 3f;
  private static final float ACIMA_DO_PAINEL = 3f;
  private static final float LETRA_DO_TEMPO = 0.6f;
  private static final float LETRA_DO_NOME = 0.75f;
  private static final float ALTURA_DA_LETRA = 7f;
  private static final float BARRINHA = 1.5f;
  private static final float BORDA = 0.75f;
  private static final float[] CANTOS = Formas.raios(3f);
  private static final long NOME_FICA = 3000;
  private static final long NOME_SOME = 600;
  private static final int PISCA_NOS_ULTIMOS = 3;
  private static final int FUNDO_DE_CIMA = 0xC0222838;
  private static final int FUNDO_DE_BAIXO = 0xC0101420;
  private static final int BRANCO = 0xFFFFFFFF;
  private static final int SEU_LADO = 0xFF8EC5FF;
  private static final int DOS_SELVAGENS = 0xFFFF8FB0;
  private static final Map<String, String> SIMBOLOS = Map.ofEntries(
      Map.entry("chuva", "☂"), Map.entry("sol", "☀"), Map.entry("areia", "≈"), Map.entry("neve", "❄"),
      Map.entry("eletrico", "⚡"), Map.entry("plantas", "✿"), Map.entry("enevoado", "☁"), Map.entry("psiquico", "✦"),
      Map.entry("refletir", "◈"), Map.entry("tela_de_luz", "◇"), Map.entry("veu_de_aurora", "✧"),
      Map.entry("vento_de_cauda", "➶"), Map.entry("salvaguarda", "✚"), Map.entry("nevoa", "≋"),
      Map.entry("cantico", "✪"), Map.entry("eter", "⟳"), Map.entry("elixir", "⟳"));
  private static final Map<String, Integer> CORES = Map.ofEntries(
      Map.entry("chuva", 0xFF6AAEFF), Map.entry("sol", 0xFFFFB347), Map.entry("areia", 0xFFD9B26F),
      Map.entry("neve", 0xFFBDE8FF), Map.entry("eletrico", 0xFFF7D02C), Map.entry("plantas", 0xFF7AC74C),
      Map.entry("enevoado", 0xFFF4A6D7), Map.entry("psiquico", 0xFFF95587), Map.entry("refletir", 0xFFFFD27A),
      Map.entry("tela_de_luz", 0xFFFFF59A), Map.entry("veu_de_aurora", 0xFFA8E6FF), Map.entry("vento_de_cauda", 0xFFB8F0C8),
      Map.entry("salvaguarda", 0xFFFFB8D0), Map.entry("nevoa", 0xFFD0D8E8), Map.entry("cantico", 0xFFFFE08A),
      Map.entry("eter", 0xFF7FE0D0), Map.entry("elixir", 0xFFA0E8B8));

  private static List<Passiva> passivas = List.of();
  // O último efeito que apareceu (o nome fica ao lado dos ícones por 3 s)
  private static String novo = null;
  private static long novoChegou = 0;

  private PassivasNaTela() {
  }

  public static void registrar() {
    ClientPlayNetworking.registerGlobalReceiver(PassivasPayload.TIPO, (pacote, contexto) -> chegou(pacote));
    ClientPlayConnectionEvents.DISCONNECT.register((conexao, cliente) -> {
      passivas = List.of();
      novo = null;
    });
  }

  private static void chegou(PassivasPayload pacote) {
    Set<String> antes = new HashSet<>();
    passivas.forEach(passiva -> antes.add(passiva.nome() + passiva.lado()));
    for (Passiva passiva : pacote.passivas()) {
      if (!antes.contains(passiva.nome() + passiva.lado())) {
        novo = passiva.nome();
        novoChegou = Util.getMillis();
      }
    }
    passivas = pacote.passivas();
  }

  // Na escala e no lugar do painel do time: (0, 0) é o canto de cima dele, os ícones ficam logo acima
  public static void desenhar(GuiGraphics tela, Font fonte) {
    if (passivas.isEmpty()) {
      return;
    }
    float alturaDoTempo = ALTURA_DA_LETRA * LETRA_DO_TEMPO + 1;
    float y = -(ICONE + alturaDoTempo + ACIMA_DO_PAINEL);
    long agora = Util.getMillis();
    float x = 0;
    for (Passiva passiva : passivas) {
      icone(tela, fonte, passiva, x, y, agora);
      x += ICONE + ESPACO;
    }
    nomeDoNovo(tela, fonte, x, y + ICONE / 2, agora);
  }

  private static void icone(GuiGraphics tela, Font fonte, Passiva passiva, float x, float y, long agora) {
    int cor = CORES.getOrDefault(passiva.nome(), BRANCO);
    int borda = switch (passiva.lado()) {
      case Passiva.SEU_LADO -> SEU_LADO;
      case Passiva.SELVAGENS -> DOS_SELVAGENS;
      default -> cor;
    };
    boolean acabando = passiva.segundos() >= 0 && passiva.segundos() <= PISCA_NOS_ULTIMOS;
    float alfa = acabando ? 0.55f + 0.45f * (float) Math.abs(Math.sin(agora / 180.0)) : 1f;
    Formas.retangulo(tela, x, y, ICONE, ICONE, CANTOS, Formas.comAlfa(FUNDO_DE_CIMA, alfa),
        Formas.comAlfa(FUNDO_DE_BAIXO, alfa));
    if (passiva.total() > 0) {
      float fracao = Math.clamp(passiva.segundos() / (float) passiva.total(), 0f, 1f);
      Formas.retangulo(tela, x + 2, y + ICONE - 2 - BARRINHA, (ICONE - 4) * fracao, BARRINHA, Formas.raios(0.5f),
          Formas.comAlfa(cor, alfa), Formas.comAlfa(cor, alfa));
    }
    Formas.borda(tela, x, y, ICONE, ICONE, CANTOS, BORDA, Formas.comAlfa(borda, alfa));
    String simbolo = SIMBOLOS.getOrDefault(passiva.nome(), "?");
    tela.drawString(fonte, simbolo, Math.round(x + (ICONE - fonte.width(simbolo)) / 2),
        Math.round(y + (ICONE - BARRINHA - ALTURA_DA_LETRA) / 2), Formas.comAlfa(cor, alfa), true);
    if (passiva.segundos() >= 0) {
      String tempo = passiva.segundos() + "s";
      escrever(tela, fonte, Component.literal(tempo), x + (ICONE - fonte.width(tempo) * LETRA_DO_TEMPO) / 2, y + ICONE + 1,
          LETRA_DO_TEMPO, Formas.comAlfa(BRANCO, alfa));
    }
  }

  // O nome do efeito que acabou de aparecer, ao lado dos ícones, sumindo no fim
  private static void nomeDoNovo(GuiGraphics tela, Font fonte, float x, float meio, long agora) {
    long passou = agora - novoChegou;
    if (novo == null || passou > NOME_FICA || passivas.stream().noneMatch(passiva -> passiva.nome().equals(novo))) {
      return;
    }
    float alfa = passou < NOME_FICA - NOME_SOME ? 1f : (NOME_FICA - passou) / (float) NOME_SOME;
    escrever(tela, fonte, Component.translatable("cobblemon_combat_za.campo." + novo), x + 1,
        meio - ALTURA_DA_LETRA * LETRA_DO_NOME / 2, LETRA_DO_NOME,
        Formas.comAlfa(CORES.getOrDefault(novo, BRANCO), Math.max(0.05f, alfa)));
  }

  private static void escrever(GuiGraphics tela, Font fonte, Component texto, float x, float y, float escala, int cor) {
    tela.pose().pushPose();
    tela.pose().translate(x, y, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.drawString(fonte, texto, 0, 0, cor, true);
    tela.pose().popPose();
  }
}
