package dev.victor.combatza.ia;

import java.util.ArrayList;
import java.util.List;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.util.BrainExtensionsKt;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.Captura;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.golpes.StatusNossos;
import net.minecraft.server.level.ServerPlayer;

// 7.5 (decisão dele, 19/10): os BANDOS do Cobblemon. Um do bando entrou na batalha, o bando todo te nota: os AGRESSIVOS e
// os PACÍFICOS entram para defender; os ARISCOS fogem.
// No Cobblemon (o "herd" do comportamento da espécie), um selvagem segue um LÍDER de uma espécie que ele aceita: o Mankey
// segue um Mankey ou um Primeape do mesmo nível ou maior. Cada seguidor guarda no "cérebro" o UUID do líder (a memória
// HERD_LEADER); o líder não guarda nada. Então o bando de um selvagem = o líder (ou ele mesmo, se é o líder) + todos por
// perto que seguem esse líder
public class Bandos {
  // Em volta de quem entrou; e perto de você, senão ele entraria e já sairia (a batalha solta quem passa de 24 blocos)
  private static final double EM_VOLTA = 16;
  private static final double PERTO_DE_VOCE = 20;

  // Um chamado de cada vez: quem entra pelo bando não chama o bando de novo
  private static boolean chamando;

  private Bandos() {
  }

  // GerenciarCombates: este selvagem acabou de entrar na batalha (você ou o seu Pokémon bateu nele, ou ele te atacou)
  public static void chamar(ServerPlayer jogador, PokemonEntity selvagem) {
    if (chamando) {
      return;
    }
    List<PokemonEntity> bando = bandoDe(jogador, selvagem);
    if (bando.isEmpty()) {
      return;
    }
    chamando = true;
    try {
      long agora = jogador.server.getTickCount();
      List<String> entraram = new ArrayList<>();
      List<String> fugiram = new ArrayList<>();
      for (PokemonEntity membro : bando) {
        String nome = membro.getPokemon().getSpecies().getName();
        if (Temperamento.de(membro) == Temperamento.Tipo.ARISCO) {
          Deteccao.fugirComOBando(membro, jogador, agora);
          fugiram.add(nome);
        } else {
          GerenciarCombates.entrarEmCombate(jogador, membro, null);
          entraram.add(nome);
        }
      }
      CobblemonCombatZA.LOGGER.info("BANDO: {} entrou na batalha e chamou o bando; entraram {}, fugiram {}",
          selvagem.getPokemon().getSpecies().getName(), entraram, fugiram);
    } finally {
      chamando = false;
    }
  }

  // Os outros do mesmo bando, acordados e livres (fora da bola e de outra batalha)
  private static List<PokemonEntity> bandoDe(ServerPlayer jogador, PokemonEntity selvagem) {
    String lider = liderDe(selvagem);
    return selvagem.level().getEntitiesOfClass(PokemonEntity.class, selvagem.getBoundingBox().inflate(EM_VOLTA),
        e -> e != selvagem && e.isAlive() && e.getPokemon().isWild() && !e.isBattling() && !Captura.dentroDaBola(e)
            && !GerenciarCombates.estaEmCombate(e.getPokemon()) && !StatusNossos.estaDormindo(e.getPokemon())
            && e.distanceTo(jogador) <= PERTO_DE_VOCE && lider.equals(liderDe(e)));
  }

  // Na batalha quem manda é a nossa IA (IaDoSelvagem): o selvagem larga o líder, porque o "seguir o líder" do Cobblemon
  // puxaria ele para perto do líder no meio da luta. Depois da batalha, o Cobblemon refaz o bando sozinho (quem não tem
  // líder procura um a cada segundo)
  public static void largarOLider(PokemonEntity selvagem) {
    if (selvagem.getBrain().hasMemoryValue(CobblemonMemories.HERD_LEADER)) {
      selvagem.getBrain().eraseMemory(CobblemonMemories.HERD_LEADER);
    }
  }

  // O UUID do líder, como o cérebro guarda; quem não segue ninguém é o próprio líder (e quem não tem bando fica sozinho)
  private static String liderDe(PokemonEntity selvagem) {
    return BrainExtensionsKt.getMemorySafely(selvagem.getBrain(), CobblemonMemories.HERD_LEADER)
        .orElse(selvagem.getStringUUID());
  }
}
