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
    contexto.getSource().sendSuccess(() -> Component.literal("Dano dos selvagens em você: " + descricao()), false);
    return 1;
  }

  private static int mudarODano(CommandContext<CommandSourceStack> contexto, String modo) {
    boolean gravou = Configuracao.mudarDanoNoJogador(modo);
    String aviso = gravou ? "" : " (só até fechar o jogo: o arquivo de configuração tem um erro; veja o log)";
    contexto.getSource().sendSuccess(() -> Component.literal("Dano dos selvagens em você agora: " + descricao() + aviso),
        true);
    return 1;
  }

  // 7.3b: com erro no arquivo, avisa no chat e continua valendo o que já valia
  private static int recarregar(CommandContext<CommandSourceStack> contexto) {
    if (!Configuracao.carregar()) {
      contexto.getSource().sendFailure(Component.literal("O config/cobblemon_combat_za.json tem um erro (veja o log). "
          + "Continua valendo o que já valia."));
      return 0;
    }
    contexto.getSource().sendSuccess(() -> Component.literal("Configuração do Combat ZA recarregada. Dano em você: "
        + descricao()), true);
    return 1;
  }

  private static String descricao() {
    return Configuracao.MONSTRO.equals(Configuracao.danoNoJogador()) ? "monstro (como um monstro do nível dele)"
        : "pouco (metade de um monstro do nível dele)";
  }
}
