package dev.victor.combatza.party;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

// O comando da party, para qualquer jogador (Parties):
// /party                       quem está na sua party
// /party convidar <jogador>    convida (você vira o líder, se ainda não tem party)
// /party aceitar | recusar     responde o convite (ou clique nos botões da mensagem)
// /party sair                  sai da party
// /party expulsar <jogador>    só o líder
public class ComandoParty {
  private static final String JOGADOR = "jogador";

  private ComandoParty() {
  }

  public static void registrar() {
    CommandRegistrationCallback.EVENT.register((despachante, registros, ambiente) -> despachante.register(
        Commands.literal("party").requires(fonte -> fonte.isPlayer())
            .executes(contexto -> Parties.listar(contexto.getSource().getPlayerOrException()))
            .then(Commands.literal("convidar").then(Commands.argument(JOGADOR, EntityArgument.player())
                .executes(contexto -> Parties.convidar(contexto.getSource().getPlayerOrException(),
                    EntityArgument.getPlayer(contexto, JOGADOR)))))
            .then(Commands.literal("aceitar")
                .executes(contexto -> Parties.aceitar(contexto.getSource().getPlayerOrException())))
            .then(Commands.literal("recusar")
                .executes(contexto -> Parties.recusar(contexto.getSource().getPlayerOrException())))
            .then(Commands.literal("sair")
                .executes(contexto -> Parties.sair(contexto.getSource().getPlayerOrException())))
            .then(Commands.literal("expulsar").then(Commands.argument(JOGADOR, EntityArgument.player())
                .executes(contexto -> Parties.expulsar(contexto.getSource().getPlayerOrException(),
                    EntityArgument.getPlayer(contexto, JOGADOR)))))));
  }
}
