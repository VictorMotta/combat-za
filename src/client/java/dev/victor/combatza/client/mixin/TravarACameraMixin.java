package dev.victor.combatza.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import dev.victor.combatza.client.Foco;
import dev.victor.combatza.client.PreviaDoGolpe;
import net.minecraft.client.MouseHandler;

// A cada quadro, o Minecraft gira o jogador pelo mouse (MouseHandler.turnPlayer chama o turn do jogador), e o Fabric não
// tem evento para isso. Com o foco travado, este Mixin troca os dois números que o mouse mandaria pelo giro na direção do
// alvo (Foco). Não cancela nada: quem escuta o turn (como o Better Third Person, na 3ª pessoa) recebe o giro normalmente.
// Antes de trocar, o giro do mouse vai para o Foco: um movimento rápido para o lado troca o alvo (6.3f)
@Mixin(MouseHandler.class)
public class TravarACameraMixin {
  @ModifyArgs(method = "turnPlayer", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
  private void girarParaOAlvo(Args giro) {
    // 6.11c: segurando um golpe com o marcador, o mouse move o marcador (e não troca de alvo)
    if (PreviaDoGolpe.moveOMarcador()) {
      PreviaDoGolpe.moverMarcador(giro.<Double>get(0), giro.<Double>get(1));
    } else {
      Foco.mouseDeLado(giro.<Double>get(0));
    }
    double[] paraOAlvo = Foco.giroParaOAlvo();
    if (paraOAlvo != null) {
      giro.set(0, paraOAlvo[0]);
      giro.set(1, paraOAlvo[1]);
    }
  }
}
