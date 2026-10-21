package dev.victor.combatza.config;

import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

// 7.3 (decisão dele, 19/10: "arquivo + comando", ex.: /combatza dano pouco): o comando do mod, só para quem pode usar
// comandos (operador, nível 2, como o /pokespawn). Cada mudança vai para o arquivo (Configuracao).
// /combatza dano             mostra o modo do dano em você
// /combatza dano pouco       metade do dano de um monstro do nível dele
// /combatza dano monstro     como um monstro do Minecraft do nível dele
// /combatza recarregar       lê de novo o config/cobblemon_combat_za.json (depois de editar o arquivo)
public class ComandoCombatZA {
  private static final int NIVEL_DE_OPERADOR = 2;
  private static final String FRASE = "cobblemon_combat_za.comando.";

  private ComandoCombatZA() {
  }

  public static void registrar() {
    CommandRegistrationCallback.EVENT.register((despachante, registros, ambiente) -> despachante.register(
        Commands.literal("combatza").requires(fonte -> fonte.hasPermission(NIVEL_DE_OPERADOR))
            .then(Commands.literal("dano")
                .executes(ComandoCombatZA::mostrarODano)
                .then(Commands.literal(Configuracao.POUCO).executes(contexto -> mudarODano(contexto, Configuracao.POUCO)))
                .then(Commands.literal(Configuracao.MONSTRO)
                    .executes(contexto -> mudarODano(contexto, Configuracao.MONSTRO))))
            .then(Commands.literal("recarregar").executes(ComandoCombatZA::recarregar))));
  }

  private static int mostrarODano(CommandContext<CommandSourceStack> contexto) {
    contexto.getSource().sendSuccess(() -> Component.translatable(FRASE + "dano", descricao()), false);
    return 1;
  }

  private static int mudarODano(CommandContext<CommandSourceStack> contexto, String modo) {
    boolean gravou = Configuracao.mudarDanoNoJogador(modo);
    Component aviso = gravou ? Component.empty() : Component.translatable(FRASE + "nao_gravou");
    contexto.getSource().sendSuccess(() -> Component.translatable(FRASE + "dano_agora", descricao(), aviso), true);
    return 1;
  }

  // 7.3b: com erro no arquivo, avisa no chat e continua valendo o que já valia
  private static int recarregar(CommandContext<CommandSourceStack> contexto) {
    if (!Configuracao.carregar()) {
      contexto.getSource().sendFailure(Component.translatable(FRASE + "erro_no_arquivo"));
      return 0;
    }
    contexto.getSource().sendSuccess(() -> Component.translatable(FRASE + "recarregou", descricao()), true);
    return 1;
  }

  private static Component descricao() {
    return Component.translatable(FRASE + (Configuracao.MONSTRO.equals(Configuracao.danoNoJogador()) ? "dano_monstro"
        : "dano_pouco"));
  }
}
