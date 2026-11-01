package dev.victor.combatza.golpes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.mega.GolpesZ;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.campo.CampoEBarreiras;
import dev.victor.combatza.campo.ClimaNosGolpes;
import dev.victor.combatza.campo.SalaTrick;
import dev.victor.combatza.habilidades.HabilidadesDaConta;
import dev.victor.combatza.habilidades.Paradoxo;
import dev.victor.combatza.habilidades.HabilidadesDePrioridade;
import dev.victor.combatza.habilidades.HabilidadesQueReagem;
import dev.victor.combatza.habilidades.HabilidadesDeForma;
import dev.victor.combatza.itens.ItensDeDano;
import dev.victor.combatza.itens.EstadoDosItens;
import dev.victor.combatza.itens.ItensDeCarga;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Execucao {

  private final ServerPlayer jogador;
  private final Pokemon pokemon;
  private final Move golpe;
  private final int slot;
  private final Arquetipo arquetipo;
  private long tickImpacto;
  private long tickFim;
  private final long tickImpactoMarcado;
  private boolean impactou = false;
  private boolean chegou = false;
  private final PokemonEntity entidade;
  private final HitResult mirada;
  private final long tickInicio;
  private final ServerBossEvent barra;
  // 5.6b: o alvo está além do alcance do golpe: o Pokémon corre até ele antes de soltar (Corrida). Com o marcador (6.11c), é
  // decidido de novo quando ele chega lá
  private boolean aproximar;
  // 6.11c: para onde ele vai antes de soltar (null = solta de onde está), e se já chegou lá
  private Vec3 marcador = null;
  private boolean chegouAoMarcador = false;
  // 9.1: o recuo do Orbe da Vida vem uma vez por golpe (o jato e a área acertam várias vezes)
  private boolean recuoDoOrbe = false;
  // 5.3: contra quem o desvio já foi sorteado neste golpe, e quem desviou (Precisao)
  private final Set<UUID> sorteados = new HashSet<>();
  private final Set<UUID> desviaram = new HashSet<>();
  // 7.2 e 7.4: o golpe veio da IA (o selvagem, o guarda), e não de uma tecla sua
  private final boolean daIa;
  // 01/11: de onde veio o golpe (UsarOutroGolpe): a sua tecla ou a IA; chamado por outro (Metrônomo, Imitador...: a
  // recarga é a do lugar do golpe que chamou); de graça (a Instrução e o Dançarino: sem carga e sem recarga)
  public enum Origem {
    NORMAL, CHAMADO, DE_GRACA, DANCARINO
  }

  private final Origem origem;
  // O golpe que este chamou (UsarOutroGolpe), e a mira dele: começa quando este acerta (AgendaDeGolpes)
  private Move chamado;
  private HitResult miraDoChamado;
  // 01/11: a vaga da setinha quando você apertou o golpe (-1 = golpe da IA): a Bênção Ressuscitadora revive esse (Saidas)
  private int setinha = -1;
  private static final int FEIXE_RECOLHENDO = 3;

  // semCarga (5.4b): as cargas de um golpe de vários acertos, depois da primeira, pulam a fase de carregar
  public Execucao(ServerPlayer jogador, Pokemon pokemon, Move golpe, int slot, long agora, int alvoTravado,
      boolean semCarga) {
    this(jogador, pokemon, golpe, slot, agora, Mira.mirar(jogador, alvoTravado, GerenciarCombates.estaEmCombate(pokemon)),
        semCarga, false);
  }

  // 7.2: o golpe escolhido pela IA (o selvagem, IaDoSelvagem; e, desde a 7.4, o guarda, Guardas). "quemVe" é o jogador da
  // luta (ou o dono do guarda), que vê o histórico; a mira é o alvo que a IA escolheu, sem o olho de ninguém
  public static Execucao daIa(ServerPlayer quemVe, Pokemon pokemon, Move golpe, int slot, long agora, Entity alvo,
      boolean semCarga) {
    return new Execucao(quemVe, pokemon, golpe, slot, agora, new EntityHitResult(alvo, alvo.getBoundingBox().getCenter()),
        semCarga, true);
  }

  private Execucao(ServerPlayer jogador, Pokemon pokemon, Move golpe, int slot, long agora, HitResult mirada,
      boolean semCarga, boolean daIa) {
    this(jogador, pokemon, golpe, slot, agora, mirada, semCarga, daIa, Origem.NORMAL);
  }

  // 01/11: o golpe que outro chamou (UsarOutroGolpe): sai sem carga, na mira que o outro deixou, no lugar dele
  public static Execucao chamadoPor(Execucao quemChamou, long agora) {
    Execucao chamado = new Execucao(quemChamou.jogador, quemChamou.pokemon, quemChamou.chamado, quemChamou.slot, agora,
        quemChamou.miraDoChamado, true, quemChamou.daIa, Origem.CHAMADO);
    chamado.setinha = quemChamou.setinha;
    return chamado;
  }

  // 01/11: o golpe de graça (a Instrução e o Dançarino): sem carga e sem recarga. "slot" -1 = um golpe que ele não tem
  public static Execucao deGraca(ServerPlayer quemVe, Pokemon pokemon, Move golpe, int slot, long agora, HitResult mira,
      Origem origem) {
    return new Execucao(quemVe, pokemon, golpe, slot, agora, mira, true, true, origem);
  }

  private Execucao(ServerPlayer jogador, Pokemon pokemon, Move golpe, int slot, long agora, HitResult mirada,
      boolean semCarga, boolean daIa, Origem origem) {
    this.origem = origem;
    this.daIa = daIa;
    this.jogador = jogador;
    this.pokemon = pokemon;
    this.golpe = golpe;
    this.slot = slot;
    this.arquetipo = Arquetipo.classificar(golpe);
    // Lote 3 dos itens: a Cauda Pesada deixa a carga mais lenta; a Garra Rápida e a Baga Custap pulam a carga; a Erva do Poder
    // tira a carga a mais; o Metrônomo conta o golpe seguido
    // 21/10 (habilidades, lote das reações): a prioridade das habilidades (Travessura, Asas de Vendaval, Triagem, Reação
    // Rápida) também pula a carga, antes do item (a Custap não é gasta à toa); o Poder do Micélio carrega mais devagar.
    // 21/10 (lote das formas): o Adiar, como a Cauda Pesada
    double ritmo = ritmoDoCasting(pokemon) * ItensDeCarga.ritmo(pokemon) * HabilidadesDePrioridade.ritmo(pokemon, golpe)
        * HabilidadesDeForma.ritmoDaCarga(pokemon);
    int pulados = semCarga || HabilidadesDePrioridade.pulaACarga(pokemon, golpe) || ItensDeCarga.pulaACarga(pokemon)
        ? arquetipo.ticksDeCarga() : 0;
    // 5b.2: o Raio Solar fora do Sol carrega 2 s a mais
    long cargaDoClima = semCarga ? 0 : ItensDeCarga.ervaDoPoder(pokemon, ClimaNosGolpes.cargaExtra(pokemon, golpe));
    // (o Metrônomo conta a vez do golpe, não cada acerto de um golpe de vários acertos)
    if (!semCarga) {
      EstadoDosItens.comecouGolpe(pokemon, golpe);
      // 21/10 (habilidades, lote das reações): carregado (Electromorfose, Energia Eólica), o golpe Elétrico gasta a carga
      HabilidadesQueReagem.comecouGolpe(pokemon, golpe);
      // 01/11: outro golpe no meio: a próxima Proteção volta a ter chance cheia (Protecao)
      Protecao.comecouGolpe(pokemon, golpe);
      // 01/11: o Laço do Destino e o Rancor acabam; o Aguçar e a Carga valem para este golpe (GolpesQueMarcam)
      GolpesQueMarcam.comecouGolpe(pokemon, golpe);
      // 01/11: o golpe Z que já começou acaba quando outro golpe começa (GolpesZ)
      GolpesZ.comecouGolpe(pokemon, golpe, agora);
    }
    // 7.2d: no selvagem, só a parte até o impacto fica mais longa; o resto do golpe é igual
    long aMaisDoSelvagem = pokemon.isWild()
        ? Math.round((arquetipo.ticksAteImpacto() - pulados) * ritmo * (Balanceamento.CARGA_DO_SELVAGEM - 1))
        : 0;
    this.tickImpacto = agora + Math.round((arquetipo.ticksAteImpacto() - pulados) * ritmo) + aMaisDoSelvagem
        + cargaDoClima;
    this.tickFim = agora + Math.round((arquetipo.ticksTotais() - pulados) * ritmo) + aMaisDoSelvagem + cargaDoClima;
    this.tickImpactoMarcado = tickImpacto;
    this.entidade = pokemon.getEntity();
    // 01/11: o golpe de alvo único pode ir parar num aliado do alvo (Siga-me, Pó de Raiva, Para-Raios, Drenagem)
    this.mirada = Redirecionamento.mira(pokemon, golpe, mirada);
    this.aproximar = Corrida.longeDemais(golpe, entidade, this.mirada);
    this.tickInicio = agora;
    this.barra = new ServerBossEvent(golpe.getDisplayName(), Feedback.corDaBarra(golpe),
        BossEvent.BossBarOverlay.PROGRESS);
  }

  public ServerPlayer getJogador() {
    return jogador;
  }

  public Pokemon getPokemon() {
    return pokemon;
  }

  public Move getGolpe() {
    return golpe;
  }

  public int getSlot() {
    return slot;
  }

  public Arquetipo getArquetipo() {
    return arquetipo;
  }

  public long getTickImpacto() {
    return tickImpacto;
  }

  public long getTickFim() {
    return tickFim;
  }

  public PokemonEntity getEntidade() {
    return entidade;
  }

  public HitResult getMirada() {
    return mirada;
  }

  public long getTickImpactoMarcado() {
    return tickImpactoMarcado;
  }

  // Golpe de contato correndo até o alvo: o impacto (e o fim) andam para frente
  public void adiar(long ticks) {
    tickImpacto += ticks;
    tickFim += ticks;
  }

  // true na primeira vez contra este alvo: a bola sorteia quando chega nele, e o acerto não sorteia de novo
  public boolean sortearContra(Entity alvo) {
    return sorteados.add(alvo.getUUID());
  }

  // 5.6a: o raio que atravessa não pega de novo quem já desviou da bola
  public void marcarDesvio(Entity alvo) {
    desviaram.add(alvo.getUUID());
  }

  public boolean desviou(Entity alvo) {
    return desviaram.contains(alvo.getUUID());
  }

  public boolean ehDaIa() {
    return daIa;
  }

  public Origem getOrigem() {
    return origem;
  }

  // O golpe gasta a vez (a recarga no fim)? O de graça, não
  public boolean temRecarga() {
    return slot >= 0 && origem != Origem.DE_GRACA && origem != Origem.DANCARINO;
  }

  // UsarOutroGolpe: este golpe chama outro, que começa quando este acertar
  public void chamar(Move golpe, HitResult mira) {
    chamado = golpe;
    miraDoChamado = mira;
  }

  public Move getChamado() {
    return chamado;
  }

  public void definirSetinha(int vaga) {
    setinha = vaga;
  }

  public int getSetinha() {
    return setinha;
  }

  // O bicho em que a mira pegou (null = um ponto no chão): no golpe do guarda, o alvo escolhido pela IA
  public Entity alvoMirado() {
    return mirada instanceof EntityHitResult acerto ? acerto.getEntity() : null;
  }

  // 7.4b: o golpe que a IA escolheu para um Pokémon com dono (o guarda); o do selvagem também é da IA, mas não é de guarda.
  // 31/10: nem o do segundo da dupla (combate.Dupla), que luta a batalha como o seu lutador
  public boolean ehDoGuarda() {
    return daIa && !pokemon.isWild() && !Lutadores.ehOSegundo(pokemon.getOwnerPlayer(), pokemon);
  }

  public boolean precisaAproximar() {
    return aproximar;
  }

  // 6.11c: o marcador vem do seu jogo, já conferido (ControleDeGolpes); antes do primeiro tick do golpe
  public void definirMarcador(Vec3 ponto) {
    marcador = ponto;
    aproximar = false;
  }

  public Vec3 getMarcador() {
    return marcador;
  }

  public boolean indoAoMarcador() {
    return marcador != null && !chegouAoMarcador;
  }

  // Chegou (ou desistiu de chegar): dali ele solta; se o alvo ficou longe demais, corre até ele (5.6b)
  public void chegouAoMarcador() {
    chegouAoMarcador = true;
    aproximar = Corrida.longeDemais(golpe, entidade, mirada, true);
  }

  public long getTickInicio() {
    return tickInicio;
  }

  // true só na primeira vez
  public boolean primeiroRecuoDoOrbe() {
    if (recuoDoOrbe) {
      return false;
    }
    recuoDoOrbe = true;
    return true;
  }

  public boolean jaChegou() {
    return chegou;
  }

  public void marcarChegada() {
    chegou = true;
  }

  public boolean jaImpactou() {
    return impactou;
  }

  public void marcarImpacto() {
    impactou = true;
  }

  // A barra de carga lá em cima é do golpe que você mandou; a do selvagem fica na placa dele (7.2c), e o guarda não tem
  public void mostrarBarra() {
    if (daIa) {
      return;
    }
    barra.setProgress(0f);
    barra.addPlayer(jogador);
  }

  public void atualizarBarra(long agora) {
    long duracao = tickImpacto - tickInicio;
    float progresso = duracao <= 0 ? 1f : (float) (agora - tickInicio) / duracao;
    barra.setProgress(Math.min(1f, progresso));
  }

  public void esconderBarra() {
    barra.removeAllPlayers();
  }

  public boolean foiRecolhido() {
    return pokemon.getEntity() != entidade || entidade.getBeamMode() == FEIXE_RECOLHENDO;
  }

  public static double ritmoDoCasting(Pokemon pokemon) {
    // 21/10: no Truque de Agilidade, a velocidade vira do avesso (SalaTrick)
    double velocidade = SalaTrick.velocidade(pokemon, velocidadeDaCarga(pokemon), Execucao::velocidadeDaCarga);
    double ritmo = 1.0 - Balanceamento.LIMITE_DO_CASTING * (velocidade - Balanceamento.VELOCIDADE_DE_REFERENCIA)
        / Balanceamento.FAIXA_DE_VELOCIDADE;
    return Math.clamp(ritmo, 1.0 - Balanceamento.LIMITE_DO_CASTING, 1.0 + Balanceamento.LIMITE_DO_CASTING);
  }

  // A Velocidade base da espécie, com os estágios, a paralisia, os itens, o clima e o Vento de Cauda
  private static double velocidadeDaCarga(Pokemon pokemon) {
    double velocidadeBase = pokemon.getForm().getBaseStats().getOrDefault(Stats.SPEED, 80);
    return velocidadeBase * Estagios.multiplicador(pokemon, "spe") * Paralisia.multiplicador(pokemon)
        * ItensDeDano.velocidade(pokemon) * ClimaNosGolpes.velocidade(pokemon) * CampoEBarreiras.velocidade(pokemon)
        * HabilidadesDaConta.velocidade(pokemon) * Paradoxo.atributo(pokemon, "spe")
        * HabilidadesDeForma.velocidade(pokemon) * AtributosTrocados.multiplicador(pokemon, "spe");
  }
}
