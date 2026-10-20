package dev.victor.combatza.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSlotWidget;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.ReorderMoveButton;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.SwapMoveButton;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.victor.combatza.client.HistoricoDaBatalha;
import net.minecraft.client.gui.GuiGraphics;

// 5.5b (dele, com print do resumo do Pokémon: "tem como bloquear esse botão em combate?"; escolheu desabilitado, cinza): cada
// golpe do resumo (o MoveSlotWidget do Cobblemon) tem 3 botões: ▲ e ▼ (trocar a ordem) e ⇄ (pôr um guardado no lugar). Em
// batalha, os três ficam com active = false (um botão do Minecraft desligado ignora o clique) e são desenhados em cinza. O
// servidor também recusa (GolpesTravados): o botão é a cara, o servidor é a regra.
// Os nomes "renderWidget" e "render" são do Minecraft (no jogo, viram method_48579 e method_25394): este Mixin é traduzido
// (remap); os campos são do Cobblemon, e não (remap = false)
@Mixin(MoveSlotWidget.class)
public abstract class BotoesDosGolpesMixin {
  private static final float CINZA = 0.4f;

  @Shadow(remap = false)
  @Final
  private ReorderMoveButton moveUpButton;

  @Shadow(remap = false)
  @Final
  private ReorderMoveButton moveDownButton;

  @Shadow(remap = false)
  @Final
  private SwapMoveButton switchMoveButton;

  @Inject(method = "renderWidget", at = @At("HEAD"))
  private void travarEmBatalha(GuiGraphics tela, int mouseX, int mouseY, float parcial, CallbackInfo info) {
    boolean livre = !HistoricoDaBatalha.emBatalha();
    moveUpButton.active = livre;
    moveDownButton.active = livre;
    switchMoveButton.active = livre;
  }

  @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
      target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/moves/ReorderMoveButton;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
  private void setaEmCinza(ReorderMoveButton botao, GuiGraphics tela, int mouseX, int mouseY, float parcial,
      Operation<Void> original) {
    emCinzaSeDesligado(botao.active, () -> original.call(botao, tela, mouseX, mouseY, parcial));
  }

  @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
      target = "Lcom/cobblemon/mod/common/client/gui/summary/widgets/screens/moves/SwapMoveButton;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
  private void trocaEmCinza(SwapMoveButton botao, GuiGraphics tela, int mouseX, int mouseY, float parcial,
      Operation<Void> original) {
    emCinzaSeDesligado(botao.active, () -> original.call(botao, tela, mouseX, mouseY, parcial));
  }

  // A cor do desenho multiplica tudo o que é desenhado: 0,4 em vermelho, verde e azul deixa o botão escuro e acinzentado
  private static void emCinzaSeDesligado(boolean ativo, Runnable desenhar) {
    if (ativo) {
      desenhar.run();
      return;
    }
    RenderSystem.setShaderColor(CINZA, CINZA, CINZA, 1f);
    desenhar.run();
    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
  }
}
