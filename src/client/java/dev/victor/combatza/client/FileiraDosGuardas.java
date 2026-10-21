package dev.victor.combatza.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;

import dev.victor.combatza.ia.Guardas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// 7.8 (dele, 20/10: "meus pokemons guarda não deveriam aparecer dentro da batalha (...) em algum lugar deveria realmente
// aparecer quem tá em guarda", sempre à mostra; escolheu a fileirinha própria): o modo da roda e o rosto de cada guarda,
// com a barrinha de vida. 7.9 (dele: "essa parte queria que ficasse lá em cima, em cima da parte do combate"): no alto da
// tela, do lado do painel da batalha (HistoricoDaBatalha: à esquerda, se o minimapa está à direita), encostada na borda; o
// painel fica embaixo dela. Guarda = quem está fora da bola e não é o parceiro (LutadorNoCliente.vagaDoParceiro), nem a
// montaria em que você está. Sem guarda, nada aparece
public class FileiraDosGuardas {
  private static final float ROSTO = 18f;
  private static final float ESPACO = 3f;
  private static final float MARGEM = 3f;
  private static final float ESCUDO = 7f;
  private static final float ESPACO_DO_ESCUDO = 2.5f;
  private static final float ESPACO_ATE_O_PAINEL = 3f;
  private static final float BARRINHA = 2f;
  private static final float ESPACO_DA_BARRINHA = 1.5f;
  private static final float LETRA = 0.75f;
  private static final float ALTURA_DA_LETRA = 7f;
  private static final float[] CANTOS_NA_ESQUERDA = Formas.raios(0f, 4f, 4f, 0f);
  private static final float[] CANTOS_NA_DIREITA = Formas.raios(4f, 0f, 0f, 4f);
  // As cores dos modos são as da roda (RodaDosGuardas)
  private static final int AZUL_DO_NEUTRO = 0xFF5AA0FF;
  private static final int VERDE_DO_PASSIVO = 0xFF6FCF97;
  private static final int VERMELHO_DO_AGRESSIVO = 0xFFFF5A5A;
  private static final int FUNDO = 0x8C101418;
  private static final int FUNDO_DO_ROSTO = 0x404A90CC;
  private static final int FUNDO_DA_BARRINHA = 0x8C0F1E14;

  // O "estado" do retrato de cada guarda (a pose), como o Cobblemon guarda nas telas dele
  private static final Map<UUID, FloatingState> ESTADOS = new HashMap<>();

  private FileiraDosGuardas() {
  }

  // Chamado pelo HistoricoDaBatalha, que já conferiu se a HUD está à mostra. Devolve onde o painel da batalha pode
  // começar: embaixo da fileirinha, ou o próprio "topo" se não há guarda
  public static float desenhar(GuiGraphics tela, Minecraft cliente, boolean naEsquerda, float topo) {
    List<Pokemon> guardas = guardas(cliente);
    Set<UUID> vistos = new HashSet<>();
    guardas.forEach(pokemon -> vistos.add(pokemon.getUuid()));
    ESTADOS.keySet().retainAll(vistos);
    if (guardas.isEmpty()) {
      return topo;
    }
    Guardas.Modo modo = Guardas.Modo.de(cliente.player.getAttachedOrElse(Guardas.MODO, "neutro"));
    Component rotulo = modo.nome();
    float larguraDoRotulo = ESCUDO + ESPACO_DO_ESCUDO + cliente.font.width(rotulo) * LETRA;
    float altura = MARGEM + ROSTO + ESPACO_DA_BARRINHA + BARRINHA + MARGEM;
    float largura = MARGEM + larguraDoRotulo + ESPACO + guardas.size() * (ROSTO + ESPACO) - ESPACO + MARGEM;
    float x = naEsquerda ? 0 : tela.guiWidth() - largura;
    Formas.retangulo(tela, x, topo, largura, altura, naEsquerda ? CANTOS_NA_ESQUERDA : CANTOS_NA_DIREITA, FUNDO, FUNDO);
    float yDoRosto = topo + MARGEM;
    float xDoRosto = x + MARGEM + larguraDoRotulo + ESPACO;
    for (int i = 0; i < guardas.size(); i++) {
      float xDeste = xDoRosto + i * (ROSTO + ESPACO);
      Formas.retangulo(tela, xDeste, yDoRosto, ROSTO, ROSTO, Formas.raios(3f), FUNDO_DO_ROSTO, FUNDO_DO_ROSTO);
    }
    float meio = topo + altura / 2;
    Simbolos.escudo(tela, x + MARGEM + ESCUDO / 2, meio - 0.5f, ESCUDO, cor(modo), 1f);
    tela.flush();
    for (int i = 0; i < guardas.size(); i++) {
      Pokemon pokemon = guardas.get(i);
      float xDeste = xDoRosto + i * (ROSTO + ESPACO);
      HistoricoDaBatalha.rosto(tela, rosto(pokemon), xDeste, yDoRosto, ROSTO, 1f, 1f);
      barrinha(tela, xDeste, yDoRosto + ROSTO + ESPACO_DA_BARRINHA, ROSTO, BARRINHA, vida(pokemon));
    }
    escrever(tela, cliente, rotulo, x + MARGEM + ESCUDO + ESPACO_DO_ESCUDO, meio - ALTURA_DA_LETRA * LETRA / 2, LETRA,
        cor(modo));
    return topo + altura + ESPACO_ATE_O_PAINEL;
  }

  // Fora da bola, sem desmaiar, e nem o parceiro nem a montaria
  private static List<Pokemon> guardas(Minecraft cliente) {
    int parceiro = LutadorNoCliente.vagaDoParceiro();
    Entity montaria = cliente.player.getVehicle();
    List<Pokemon> guardas = new ArrayList<>();
    List<Pokemon> vagas = CobblemonClient.INSTANCE.getStorage().getParty().getSlots();
    for (int numero = 0; numero < vagas.size(); numero++) {
      Pokemon pokemon = vagas.get(numero);
      if (pokemon != null && numero != parceiro && pokemon.getState() instanceof SentOutState fora
          && !pokemon.isFainted() && (montaria == null || fora.getEntity() != montaria)) {
        guardas.add(pokemon);
      }
    }
    return guardas;
  }

  private static HistoricoDaBatalha.Rosto rosto(Pokemon pokemon) {
    FloatingState estado = ESTADOS.computeIfAbsent(pokemon.getUuid(), uuid -> {
      FloatingState novo = new FloatingState();
      novo.setCurrentAspects(pokemon.getAspects());
      return novo;
    });
    return new HistoricoDaBatalha.Rosto(pokemon.getSpecies().getResourceIdentifier(),
        pokemon.getForm().getBaseScale(), estado);
  }

  private static float vida(Pokemon pokemon) {
    return Math.clamp(pokemon.getCurrentHealth() / (float) Math.max(1, pokemon.getMaxHealth()), 0f, 1f);
  }

  private static void barrinha(GuiGraphics tela, float x, float y, float largura, float altura, float vida) {
    Formas.retangulo(tela, x, y, largura, altura, Formas.raios(altura / 2), FUNDO_DA_BARRINHA, FUNDO_DA_BARRINHA);
    if (vida > 0) {
      int cor = HistoricoDaBatalha.corDaVida(vida);
      Formas.retangulo(tela, x, y, largura * vida, altura, Formas.raios(altura / 2), cor, cor);
    }
  }

  private static int cor(Guardas.Modo modo) {
    return switch (modo) {
      case PASSIVO -> VERDE_DO_PASSIVO;
      case NEUTRO -> AZUL_DO_NEUTRO;
      case AGRESSIVO -> VERMELHO_DO_AGRESSIVO;
    };
  }

  private static void escrever(GuiGraphics tela, Minecraft cliente, Component texto, float x, float y, float escala,
      int cor) {
    tela.pose().pushPose();
    tela.pose().translate(x, y, 0);
    tela.pose().scale(escala, escala, 1f);
    tela.drawString(cliente.font, texto, 0, 0, cor, true);
    tela.pose().popPose();
  }
}
