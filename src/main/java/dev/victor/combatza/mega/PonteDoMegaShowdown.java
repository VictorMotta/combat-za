package dev.victor.combatza.mega;

import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.github.yajatkaul.mega_showdown.advancement.AdvancementHelper;
import com.github.yajatkaul.mega_showdown.gimmick.GimmickTurnCheck;
import com.github.yajatkaul.mega_showdown.gimmick.MegaGimmick;
import com.github.yajatkaul.mega_showdown.utils.RegistryLocator;

import net.minecraft.server.level.ServerPlayer;

// 8.4: tudo o que a nossa Mega pede ao Mega Showdown (o mod da Mega Evolução do modpack) fica AQUI, e só aqui. O Java só
// carrega uma classe quando ela é usada pela primeira vez: a Mega confere se o mod está instalado antes de chamar esta,
// então, sem o Mega Showdown, nenhuma classe dele é procurada e o jogo não quebra (a Mega só fica desligada).
// O que cada chamada faz foi lido com javap no .jar dele (MegaGimmick, GimmickTurnCheck)
class PonteDoMegaShowdown {
  // A conquista que o próprio Mega Showdown dá na primeira Mega (as missões do modpack podem depender dela)
  private static final String CONQUISTA_DA_MEGA = "mega/mega_evolve";
  private static final String RAYQUAZA = "Rayquaza";

  private PonteDoMegaShowdown() {
  }

  // Por que ele pode (ou não) megar agora. Na ordem do Mega Showdown (MegaGimmick.canMega): o bracelete equipado nos
  // acessórios (GimmickTurnCheck.hasGimmick, que também recusa se já há um Mega no time e o modpack só deixa um), a Mega
  // Pedra segurada e se a pedra é da espécie dele. A Rayquaza mega sem pedra, com o golpe Dragon Ascent (canMega cuida)
  static Mega.Motivo podeMegar(ServerPlayer jogador, Pokemon pokemon) {
    if (MegaGimmick.isMega(pokemon)) {
      return Mega.Motivo.JA_ESTA_MEGA;
    }
    if (!GimmickTurnCheck.hasGimmick(ShowdownMoveset.Gimmick.MEGA_EVOLUTION, jogador)) {
      return MegaGimmick.hasMega(jogador) ? Mega.Motivo.JA_TEM_UM_MEGA : Mega.Motivo.SEM_BRACELETE;
    }
    boolean temPedra = RegistryLocator.getComponent(MegaGimmick.class, pokemon.heldItem()) != null;
    if (!temPedra && !RAYQUAZA.equals(pokemon.getSpecies().getName())) {
      return Mega.Motivo.SEM_PEDRA;
    }
    return MegaGimmick.canMega(pokemon) ? Mega.Motivo.PODE : Mega.Motivo.PEDRA_DE_OUTRO;
  }

  // A Mega do Mega Showdown: a animação (uns 6 segundos parado, sem IA, com a marca "form_changing"), depois a forma Mega
  static void megar(ServerPlayer jogador, Pokemon pokemon) {
    AdvancementHelper.grantAdvancement(jogador, CONQUISTA_DA_MEGA);
    MegaGimmick.megaEvolve(pokemon);
  }

  static void desmegar(Pokemon pokemon) {
    if (MegaGimmick.isMega(pokemon)) {
      MegaGimmick.unmegaEvolve(pokemon);
    }
  }

  static boolean ehMega(Pokemon pokemon) {
    return MegaGimmick.isMega(pokemon);
  }
}
