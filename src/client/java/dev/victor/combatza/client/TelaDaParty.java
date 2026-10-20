package dev.victor.combatza.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import dev.victor.combatza.rede.PartyPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

// A tela da party (dele, 20/10: "quero os dois, faça o comando primeiro e aí a tela em seguida"; e, vendo a tela: "aqui no P
// ter como adicionar a pessoa na party"): uma tecla abre (o ";", que no teclado brasileiro é o Ç: o P do modpack é o PC do
// Lumymon, e ele mesmo trocou para essa). Mostra o convite que chegou (Aceitar / Recusar), a sua party
// (com Tirar, se você é o líder, e Sair), uma caixa para convidar pelo NOME (Enter ou o botão) e os jogadores online que não
// são da sua party (Convidar; só os que cabem na tela, os outros pelo nome). Com a party cheia, a parte de convidar some. Cada
// botão manda o mesmo comando /party (party.ComandoParty): as regras ficam só no servidor. A tela se remonta quando a party
// ou a lista de online muda, e o que você digitou fica
public class TelaDaParty extends Screen {
  private static final String FRASE = "cobblemon_combat_za.tela_party.";
  private static final int LARGURA = 230;
  private static final int LINHA = 21;
  private static final int BOTAO = 70;
  private static final int ALTURA_DO_BOTAO = 18;
  private static final int MAXIMO_ONLINE = 6;
  private static final int MAXIMO_NA_PARTY = 4;
  private static final int CINZA = 0xFFB4B8C4;
  private static final int AMARELO = 0xFFFFD24A;
  private static final int BRANCO = 0xFFFFFFFF;

  private static KeyMapping tecla;

  private record Rotulo(Component texto, int x, int y, int cor) {
  }

  private final List<Rotulo> rotulos = new ArrayList<>();
  private EditBox nome;
  private String digitado = "";
  private boolean digitando = true;
  private int versaoMontada = -1;
  private List<String> onlineMontado = List.of();

  public TelaDaParty() {
    super(Component.translatable(FRASE + "titulo"));
  }

  public static void registrar() {
    tecla = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblemon_combat_za.party",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, TeclasDeGolpe.CATEGORIA));
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      while (tecla.consumeClick()) {
        if (cliente.screen == null && cliente.player != null) {
          cliente.setScreen(new TelaDaParty());
        }
      }
    });
  }

  // O tutorial mostra a tecla como está nos Controles
  static KeyMapping tecla() {
    return tecla;
  }

  @Override
  protected void init() {
    rotulos.clear();
    versaoMontada = PartyNoCliente.versao();
    onlineMontado = online();
    int x = (width - LARGURA) / 2;
    int y = Math.max(8, height / 2 - 130);
    rotulos.add(new Rotulo(title.copy().withStyle(ChatFormatting.BOLD), x, y, BRANCO));
    y += LINHA;
    y = convite(x, y);
    y = suaParty(x, y);
    if (PartyNoCliente.membros().size() + 1 < MAXIMO_NA_PARTY) {
      y = convidarPeloNome(x, y + 6);
      y = listaOnline(x, y + 4);
    } else {
      nome = null;
    }
    addRenderableWidget(Button.builder(Component.translatable(FRASE + "fechar"), botao -> onClose())
        .bounds(x + LARGURA - BOTAO, y + 8, BOTAO, ALTURA_DO_BOTAO).build());
  }

  private int convite(int x, int y) {
    String quem = PartyNoCliente.convite();
    if (quem.isEmpty()) {
      return y;
    }
    rotulos.add(new Rotulo(Component.translatable(FRASE + "convite", quem), x, y + 5, AMARELO));
    botao(x + LARGURA - 2 * BOTAO - 4, y, "aceitar", "party aceitar");
    botao(x + LARGURA - BOTAO, y, "recusar", "party recusar");
    return y + LINHA + 6;
  }

  private int suaParty(int x, int y) {
    List<PartyPayload.Membro> membros = PartyNoCliente.membros();
    if (membros.isEmpty()) {
      rotulos.add(new Rotulo(Component.translatable(FRASE + "sem_party"), x, y, CINZA));
      return y + LINHA - 6;
    }
    rotulos.add(new Rotulo(Component.translatable(FRASE + "membros", membros.size() + 1, MAXIMO_NA_PARTY), x, y, CINZA));
    y += LINHA - 6;
    String eu = Minecraft.getInstance().player.getGameProfile().getName();
    rotulos.add(new Rotulo(Component.literal((PartyNoCliente.souLider() ? "♛ " : "• ") + eu)
        .append(Component.translatable(FRASE + "voce")), x + 6, y + 5, BRANCO));
    y += LINHA;
    for (PartyPayload.Membro membro : membros) {
      rotulos.add(new Rotulo(Component.literal((membro.lider() ? "♛ " : "• ") + membro.nome()), x + 6, y + 5, BRANCO));
      if (PartyNoCliente.souLider()) {
        botao(x + LARGURA - BOTAO, y, "tirar", "party expulsar " + membro.nome());
      }
      y += LINHA;
    }
    botao(x + LARGURA - BOTAO, y, "sair", "party sair");
    return y + LINHA;
  }

  // A caixa do nome: digite e aperte Enter (ou o botão). O que você digitou fica quando a tela se remonta
  private int convidarPeloNome(int x, int y) {
    rotulos.add(new Rotulo(Component.translatable(FRASE + "pelo_nome"), x, y, CINZA));
    y += LINHA - 6;
    nome = new EditBox(font, x + 1, y + 1, LARGURA - BOTAO - 6, ALTURA_DO_BOTAO - 2,
        Component.translatable(FRASE + "pelo_nome"));
    nome.setMaxLength(16);
    nome.setValue(digitado);
    nome.setResponder(texto -> digitado = texto);
    addRenderableWidget(nome);
    if (digitando) {
      setInitialFocus(nome);
    }
    addRenderableWidget(Button.builder(Component.translatable(FRASE + "convidar"), botao -> convidarODigitado())
        .bounds(x + LARGURA - BOTAO, y, BOTAO, ALTURA_DO_BOTAO).build());
    return y + LINHA;
  }

  private void convidarODigitado() {
    String alvo = digitado.trim();
    if (!alvo.isEmpty()) {
      mandar("party convidar " + alvo);
      digitado = "";
      nome.setValue("");
    }
  }

  // Os jogadores online que não são da sua party: até 6, e só os que cabem acima do Fechar; os outros, pelo nome
  private int listaOnline(int x, int y) {
    rotulos.add(new Rotulo(Component.translatable(FRASE + "online"), x, y, CINZA));
    y += LINHA - 6;
    if (onlineMontado.isEmpty()) {
      rotulos.add(new Rotulo(Component.translatable(FRASE + "ninguem_online"), x + 6, y + 5, CINZA));
      return y + LINHA;
    }
    int cabem = Math.max(1, (height - y - ALTURA_DO_BOTAO - 12) / LINHA);
    int mostrar = Math.min(onlineMontado.size(), Math.min(MAXIMO_ONLINE, cabem));
    if (mostrar < onlineMontado.size() && mostrar > 1) {
      mostrar--;
    }
    for (String outro : onlineMontado.subList(0, mostrar)) {
      rotulos.add(new Rotulo(Component.literal(outro), x + 6, y + 5, BRANCO));
      botao(x + LARGURA - BOTAO, y, "convidar", "party convidar " + outro);
      y += LINHA;
    }
    if (mostrar < onlineMontado.size()) {
      rotulos.add(new Rotulo(Component.translatable(FRASE + "mais", onlineMontado.size() - mostrar), x + 6, y + 5, CINZA));
      y += LINHA;
    }
    return y;
  }

  // A lista de jogadores do servidor (a mesma do Tab), sem você e sem quem já é da sua party, em ordem de nome
  private static List<String> online() {
    Minecraft cliente = Minecraft.getInstance();
    List<String> nomes = new ArrayList<>();
    if (cliente.getConnection() == null || cliente.player == null) {
      return nomes;
    }
    for (PlayerInfo info : cliente.getConnection().getListedOnlinePlayers()) {
      if (!info.getProfile().getId().equals(cliente.player.getUUID())
          && !PartyNoCliente.ehDaParty(info.getProfile().getId())) {
        nomes.add(info.getProfile().getName());
      }
    }
    nomes.sort(String.CASE_INSENSITIVE_ORDER);
    return nomes;
  }

  private Button botao(int x, int y, String nomeDoBotao, String comando) {
    return addRenderableWidget(Button.builder(Component.translatable(FRASE + nomeDoBotao), botao -> mandar(comando))
        .bounds(x, y, BOTAO, ALTURA_DO_BOTAO).build());
  }

  private static void mandar(String comando) {
    Minecraft cliente = Minecraft.getInstance();
    if (cliente.player != null) {
      cliente.player.connection.sendCommand(comando);
    }
  }

  @Override
  public boolean keyPressed(int tecla, int codigo, int modificadores) {
    if (nome != null && nome.isFocused()
        && (tecla == GLFW.GLFW_KEY_ENTER || tecla == GLFW.GLFW_KEY_KP_ENTER)) {
      convidarODigitado();
      return true;
    }
    return super.keyPressed(tecla, codigo, modificadores);
  }

  @Override
  public void tick() {
    super.tick();
    if (PartyNoCliente.versao() != versaoMontada || !online().equals(onlineMontado)) {
      digitando = nome != null && nome.isFocused();
      rebuildWidgets();
    }
  }

  @Override
  public void render(GuiGraphics tela, int mouseX, int mouseY, float parcial) {
    super.render(tela, mouseX, mouseY, parcial);
    for (Rotulo rotulo : rotulos) {
      tela.drawString(font, rotulo.texto(), rotulo.x(), rotulo.y(), rotulo.cor(), true);
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
