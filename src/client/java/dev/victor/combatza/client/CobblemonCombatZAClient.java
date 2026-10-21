package dev.victor.combatza.client;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.VigiaDeTravamento;
import dev.victor.combatza.golpes.Projetil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.fabricmc.loader.api.FabricLoader;

public class CobblemonCombatZAClient implements ClientModInitializer {

  @Override
  public void onInitializeClient() {
    CobblemonCombatZA.LOGGER.info("Cobblemon Combat ZA: lado do CLIENTE carregou!");
    ClientReceiveMessageEvents.ALLOW_GAME.register((message, naBarraDeAcao) -> {
      return !(message.getContents() instanceof TranslatableContents conteudo
          && conteudo.getKey().equals(CobblemonCombatZA.CHAVE_SILENCIO));
    });
    // 5.2: quem desenha o projétil no mundo; 5.2b: as partículas nossas do Snowstorm, presas a ele
    EntityRendererRegistry.register(Projetil.TIPO, RenderDoProjetil::new);
    Projetil.AO_APARECER_NO_CLIENTE = ProjeteisNossos::aoAparecer;
    TeclasDeGolpe.registrar();
    TeclasDoTime.registrar();
    Foco.registrar();
    ZoomDaCamera.registrar();
    RolamentoNoCliente.registrar();
    RecargasNoCliente.registrar();
    LutadorNoCliente.registrar();
    PrimeiroTurnoNoCliente.registrar();
    CargasNoCliente.registrar();
    CorpoAlinhado.registrar();
    SinaisNaTela.registrar();
    FaixaDoGolpe.registrar();
    RodaDosGuardas.registrar();
    PlacasNaTela.registrar();
    HistoricoDaBatalha.registrar();
    HudDeCombate.registrar();
    PainelDoTime.registrar();
    MegaNoCliente.registrar();
    CorridaMontadaNoCliente.registrar();
    TravaDaEscolhaNoCliente.registrar();
    // 21/10: os golpes desligados pela Provocação e pelo Bis
    GolpesDesligadosNoCliente.registrar();
    PassivasNaTela.registrar();
    PartyNoCliente.registrar();
    PartyNaTela.registrar();
    TelaDaParty.registrar();
    // 21/10: a telinha "Tempo real / Por turnos" quando um treinador do RCT quer batalhar
    TelaDoTreinador.registrar();
    // 21/10 (PvP): a tela do duelo, "quantos Pokémon cada um usa?", para quem desafia pela roda do Cobblemon
    TelaDoDuelo.registrar();
    // A party e os waypoints da party no mapa do Xaero: só com ele instalado (sem ele, as classes nem são carregadas)
    if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
      try {
        PartyNoXaero.registrar();
        PontosNoXaero.registrar();
      } catch (LinkageError erro) {
        CobblemonCombatZA.LOGGER.error("PARTY: o Xaero's Minimap mudou; a party não aparece no mapa dele", erro);
      }
    }
    PreviaDoGolpe.registrar();
    DesenhoDaPrevia.registrar();
    Tutorial.registrar();
    // O layout da HUD: o lugar e o tamanho de cada bloco (salvos no seu jogo) e o editor, pelo menu de pausa
    LayoutDaHud.registrar();
    // As cores da prévia do golpe (salvas no seu jogo)
    CoresDaPrevia.registrar();
    TelaDoLayout.registrar();
    // O vigia de travamento da tela (VigiaDeTravamento): sem mundo aberto (menus, carregando), parar é normal
    VigiaDeTravamento vigiaDaTela = VigiaDeTravamento.comecar("jogo (tela)", () -> Minecraft.getInstance().level == null);
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> vigiaDaTela.sinal());
  }
}