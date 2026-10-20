package dev.victor.combatza.campo;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 5b.1: o "campo" de uma batalha (cada Combate tem o seu): o clima do mundo (a chuva e a neve do Minecraft onde vocês
// estão, lidas a cada segundo) e o clima que um golpe ou uma habilidade chamou, com hora para acabar. O do golpe vence o
// do mundo enquanto dura; quando acaba, volta o do mundo. 5b.3: o campo (terreno) e as barreiras de cada lado.
// P5-10 (veto dele, 20/10: "fora de batalha pode adicionar também a mesma coisa de dentro"): cada jogador também tem um campo
// pessoal fora da batalha (Arenas). Cada efeito guarda quando acaba E quanto dura (o ícone da passiva mostra a barrinha)
public class CampoDaBatalha {
  // Quando acaba e quanto dura, em ticks
  public record Prazo(long fim, long duracao) {
  }

  private Clima doMundo = Clima.NENHUM;
  private Clima doGolpe = Clima.NENHUM;
  private Prazo prazoDoClima = null;
  // 5b.2: quem já está na luta (quem chega agora "entra": o Chuvisco chama a chuva)
  private final Set<UUID> naLuta = new HashSet<>();
  private Terreno terreno = Terreno.NENHUM;
  private Prazo prazoDoTerreno = null;
  private final Map<Barreira, Prazo> doSeuLado = new EnumMap<>(Barreira.class);
  private final Map<Barreira, Prazo> dosSelvagens = new EnumMap<>(Barreira.class);

  public Clima clima() {
    return doGolpe != Clima.NENHUM ? doGolpe : doMundo;
  }

  public Clima getDoMundo() {
    return doMundo;
  }

  public void lerOMundo(Clima agora) {
    doMundo = agora;
  }

  public Clima getDoGolpe() {
    return doGolpe;
  }

  public Prazo getPrazoDoClima() {
    return prazoDoClima;
  }

  public void chamarClima(Clima clima, Prazo prazo) {
    doGolpe = clima;
    prazoDoClima = prazo;
  }

  public void acabarOClimaDoGolpe() {
    doGolpe = Clima.NENHUM;
    prazoDoClima = null;
  }

  // Os que estão na luta agora; devolve os que acabaram de chegar
  public Set<UUID> chegaram(Set<UUID> agora) {
    Set<UUID> novos = new HashSet<>(agora);
    novos.removeAll(naLuta);
    naLuta.clear();
    naLuta.addAll(agora);
    return novos;
  }

  public Terreno getTerreno() {
    return terreno;
  }

  public Prazo getPrazoDoTerreno() {
    return prazoDoTerreno;
  }

  public void mudarOTerreno(Terreno novo, Prazo prazo) {
    terreno = novo;
    prazoDoTerreno = prazo;
  }

  // As barreiras de um lado (true = o seu lado, o dos jogadores)
  public Map<Barreira, Prazo> barreiras(boolean doSeuLado) {
    return doSeuLado ? this.doSeuLado : dosSelvagens;
  }

  // P5-10: o que vem de outro campo (o pessoal entrando na batalha, ou a batalha voltando para o pessoal): o clima e o campo
  // dos golpes ficam com quem acaba mais tarde; as barreiras do SEU lado vêm junto (as dos selvagens ficam na batalha)
  public void receber(CampoDaBatalha outro) {
    if (outro.doGolpe != Clima.NENHUM && (doGolpe == Clima.NENHUM || outro.prazoDoClima.fim() > prazoDoClima.fim())) {
      chamarClima(outro.doGolpe, outro.prazoDoClima);
    }
    if (outro.terreno != Terreno.NENHUM
        && (terreno == Terreno.NENHUM || outro.prazoDoTerreno.fim() > prazoDoTerreno.fim())) {
      mudarOTerreno(outro.terreno, outro.prazoDoTerreno);
    }
    outro.doSeuLado.forEach((barreira, prazo) -> doSeuLado.merge(barreira, prazo,
        (meu, dele) -> meu.fim() >= dele.fim() ? meu : dele));
  }

  public boolean temEfeitoDeGolpe() {
    return doGolpe != Clima.NENHUM || terreno != Terreno.NENHUM || !doSeuLado.isEmpty();
  }

  // Os efeitos dos golpes foram para a batalha: o campo pessoal fica só com o clima do mundo
  public void esvaziarOsGolpes() {
    acabarOClimaDoGolpe();
    mudarOTerreno(Terreno.NENHUM, null);
    doSeuLado.clear();
    dosSelvagens.clear();
  }
}
