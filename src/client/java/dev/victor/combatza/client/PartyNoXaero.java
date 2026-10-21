package dev.victor.combatza.client;

import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.rede.PartyPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import xaero.common.HudMod;
import xaero.hud.minimap.player.tracker.system.IRenderedPlayerTracker;
import xaero.hud.minimap.player.tracker.system.ITrackedPlayerReader;

// Party (dele, 20/10: "ver no mapa do Xaero se tiver instalado"): a party aparece no minimapa e no mapa-múndi do Xaero, até
// longe, como a do Open Parties and Claims. O Xaero tem um "sistema de jogadores acompanhados" (javap: IRenderedPlayerTracker,
// registrado no HudMod.getPlayerTracker(); o getPlayerTrackerSystemManager(), que o suporte dele ao OPAC usa, está marcado como
// antigo): a gente diz quem acompanhar (os da party) e onde cada um está (a posição que o servidor manda; perto, a do próprio
// jogador, que anda mais liso). Esta classe só é carregada com o Xaero instalado (CobblemonCombatZAClient confere antes)
public class PartyNoXaero implements IRenderedPlayerTracker<PartyPayload.Membro> {
  private static final String NOME = CobblemonCombatZA.MOD_ID + "_party";
  private static boolean registrado = false;

  private final ITrackedPlayerReader<PartyPayload.Membro> leitor = new Leitor();

  // O Xaero se prepara depois de nós: tenta a cada tick até ele estar pronto, e registra uma vez
  public static void registrar() {
    ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
      try {
        if (registrado || HudMod.INSTANCE == null || HudMod.INSTANCE.getPlayerTracker() == null) {
          return;
        }
      } catch (LinkageError erro) {
        registrado = true;
        CobblemonCombatZA.LOGGER.error("PARTY: o Xaero's Minimap mudou; a party não aparece no mapa dele", erro);
        return;
      }
      registrado = true;
      // Se o Xaero mudar por dentro numa versão nova, a party só não aparece no mapa (um aviso no log), em vez de fechar o jogo
      try {
        HudMod.INSTANCE.getPlayerTracker().register(NOME, new PartyNoXaero());
        CobblemonCombatZA.LOGGER.info("PARTY: a party aparece no mapa do Xaero");
      } catch (LinkageError erro) {
        CobblemonCombatZA.LOGGER.error("PARTY: o Xaero's Minimap mudou; a party não aparece no mapa dele", erro);
      }
    });
  }

  @Override
  public ITrackedPlayerReader<PartyPayload.Membro> getReader() {
    return leitor;
  }

  @Override
  public Iterator<PartyPayload.Membro> getTrackedPlayerIterator() {
    // Com a party do OPAC, ele mesmo já mostra no Xaero: aqui ninguém, para não aparecer duas vezes
    if (PartyNoCliente.doOpac()) {
      return Collections.emptyIterator();
    }
    return PartyNoCliente.membros().stream().filter(membro -> !membro.onde().mundo().isEmpty()).iterator();
  }

  private static final class Leitor implements ITrackedPlayerReader<PartyPayload.Membro> {
    @Override
    public UUID getId(PartyPayload.Membro membro) {
      return membro.jogador();
    }

    @Override
    public double getX(PartyPayload.Membro membro) {
      Player perto = perto(membro);
      return perto != null ? perto.getX() : membro.onde().x();
    }

    @Override
    public double getY(PartyPayload.Membro membro) {
      Player perto = perto(membro);
      return perto != null ? perto.getY() : membro.onde().y();
    }

    @Override
    public double getZ(PartyPayload.Membro membro) {
      Player perto = perto(membro);
      return perto != null ? perto.getZ() : membro.onde().z();
    }

    @Override
    public ResourceKey<Level> getDimension(PartyPayload.Membro membro) {
      return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(membro.onde().mundo()));
    }

    private static Player perto(PartyPayload.Membro membro) {
      Minecraft cliente = Minecraft.getInstance();
      return cliente.level == null ? null : cliente.level.getPlayerByUUID(membro.jogador());
    }
  }
}
