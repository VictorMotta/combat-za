package dev.victor.combatza.ia;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.combate.Captura;
import dev.victor.combatza.combate.Combate;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.combate.Lutadores;
import dev.victor.combatza.config.Configuracao;
import dev.victor.combatza.golpes.AgendaDeGolpes;
import dev.victor.combatza.golpes.ControleDeGolpes;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.Feedback;
import dev.victor.combatza.golpes.Execucao;
import dev.victor.combatza.golpes.FormaDoGolpe;
import dev.victor.combatza.golpes.Furia;
import dev.victor.combatza.golpes.Jatos;
import dev.victor.combatza.golpes.Mira;
import dev.victor.combatza.golpes.Preso;
import dev.victor.combatza.golpes.Projetil;
import dev.victor.combatza.golpes.SonoECongelado;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// 7.2a (decisões dele, 19/10): o selvagem LUTA com os 4 golpes dele, pelo mesmo caminho do seu Pokémon (a carga, a
// recarga, o projétil, quem entra na frente leva). Durante a luta, o Fight or Flight não manda mais nele.
// - ALVO (7.2b, dele: "tá focando demais em mim"): o seu lutador; sem lutador, o seu Pokémon fora mais perto dele, que
//   vira o lutador. Com Pokémon seu ali, os golpes de contato NUNCA vão em você, e os de longe às vezes (1 em 5). Sem
//   nenhum Pokémon seu fora, tudo vai em você
// - GOLPE (esperto): o que mais machuca o alvo (o super efetivo pesa), o de contato quando está perto e o de longe quando
//   está longe; os de status e de reforço no começo da luta, uma vez cada; a cura quando a vida cai da metade
// - 7.2d (dele: "estão tacando habilidade muito rápido"; escolheu respiro de 2 a 3,5 s, carga 50% mais longa e "um de cada
//   vez em você"): só um selvagem por vez executa golpe em você; os outros vão no seu Pokémon ou esperam se posicionando
// - ENTRE UM GOLPE E OUTRO: um respiro, e ele se posiciona: quem luta de perto chega perto, quem luta de longe guarda
//   distância, andando de lado em volta do alvo. Às vezes sai da frente de um projétil
// - COM MENOS DE 1/3 DA VIDA, SÓ O ARISCO FOGE (a 16 blocos de todo mundo, sai da batalha: Combate)
public class IaDoSelvagem {
  // O tempo de reação no começo da luta e o respiro depois de cada golpe (2 a 3,5 s, 7.2d; era 0,75 a 1,5 s)
  private static final int REACAO_NO_COMECO = 20;
  private static final int PAUSA_MINIMA = 40;
  private static final int PAUSA_MAXIMA = 70;
  // 7.2d: depois que um selvagem acaba o golpe em você, o próximo ainda espera meio segundo
  private static final int FOLGA_ENTRE_ATACANTES = 10;
  // Onde ficar: perto (quem luta com golpes de contato) ou longe, e quanto pode variar antes de corrigir
  private static final int SE_MEXER_A_CADA = 20;
  private static final double DE_PERTO = 2.5;
  private static final double DE_LONGE = 7.0;
  private static final double FOLGA_DA_DISTANCIA = 1.5;
  private static final double GIRO_MINIMO = 25;
  private static final double GIRO_A_MAIS = 20;
  private static final float VELOCIDADE = 1.2f;
  // A fuga do arisco
  private static final double FUGA_ABAIXO_DE = 1.0 / 3;
  private static final double DISTANCIA_DA_FUGA = 12;
  private static final float VELOCIDADE_DA_FUGA = 1.5f;
  // O desvio do projétil: só os que vêm na direção dele, a até 6 blocos
  private static final double CHANCE_DE_DESVIAR = 0.3;
  private static final double OLHAR_PROJETEIS_ATE = 6.0;
  private static final double FORA_DA_LINHA = 1.0;
  private static final double PULO_PARA_O_LADO = 0.6;
  private static final double PULO_PARA_CIMA = 0.35;
  private static final int NUVENS_DO_PULO = 6;
  private static final float GIRO_DA_CABECA = 30f;

  private static final class Cerebro {
    private final Temperamento.Tipo temperamento;
    private final long entrouNoTick;
    private long pensarNoTick;
    private long seMexerNoTick;
    private boolean estavaOcupado;
    private boolean fugindo;
    // Os golpes que ele já usou nesta luta (o status e o reforço, só uma vez)
    private final Set<String> usados = new HashSet<>();

    private Cerebro(PokemonEntity selvagem, long agora) {
      this.temperamento = Temperamento.de(selvagem);
      this.entrouNoTick = agora;
      this.pensarNoTick = agora + REACAO_NO_COMECO;
    }
  }

  private static final Map<UUID, Cerebro> CEREBROS = new HashMap<>();
  // 7.2d: a "vez" de atacar cada jogador: até que tick um selvagem está executando golpe nele
  private static final Map<UUID, Long> VEZ_EM_VOCE = new HashMap<>();

  private IaDoSelvagem() {
  }

  // A cada tick, cada selvagem de cada batalha. Cópias das listas: um golpe pode mexer nelas
  public static void atualizar(MinecraftServer servidor) {
    long agora = servidor.getTickCount();
    Set<UUID> naLuta = new HashSet<>();
    for (Combate combate : List.copyOf(GerenciarCombates.todos())) {
      List<ServerPlayer> jogadores = List.copyOf(combate.getJogadores());
      for (PokemonEntity selvagem : List.copyOf(combate.getSelvagens())) {
        naLuta.add(selvagem.getUUID());
        Cerebro cerebro = CEREBROS.computeIfAbsent(selvagem.getUUID(), uuid -> new Cerebro(selvagem, agora));
        lutar(selvagem, jogadores, cerebro, agora);
      }
    }
    CEREBROS.keySet().retainAll(naLuta);
  }

  public static void limpar() {
    CEREBROS.clear();
    VEZ_EM_VOCE.clear();
  }

  private static void lutar(PokemonEntity selvagem, List<ServerPlayer> jogadores, Cerebro cerebro, long agora) {
    Pokemon pokemon = selvagem.getPokemon();
    // Dentro da Pokébola (a captura), ele não faz nada: se escapar, volta a lutar
    if (!selvagem.isAlive() || pokemon.isFainted() || Captura.dentroDaBola(selvagem) || Desmaio.esta(selvagem)) {
      return;
    }
    // O Fight or Flight (e o "se defender" do Cobblemon) mandam pelo alvo do Minecraft: na nossa luta, quem escolhe é aqui
    selvagem.setTarget(null);
    Bandos.largarOLider(selvagem);
    ServerPlayer adversario = maisPerto(selvagem, jogadores);
    if (adversario == null) {
      return;
    }
    if (cerebro.fugindo || comecouAFugir(selvagem, jogadores, cerebro)) {
      if (agora >= cerebro.seMexerNoTick) {
        cerebro.seMexerNoTick = agora + SharedConstants.TICKS_PER_SECOND;
        Andar.longeDe(selvagem, adversario, DISTANCIA_DA_FUGA, VELOCIDADE_DA_FUGA);
      }
      return;
    }
    if (ocupado(pokemon)) {
      cerebro.estavaOcupado = true;
      return;
    }
    if (cerebro.estavaOcupado) {
      cerebro.estavaOcupado = false;
      cerebro.pensarNoTick = Math.max(cerebro.pensarNoTick,
          agora + PAUSA_MINIMA + selvagem.getRandom().nextInt(PAUSA_MAXIMA - PAUSA_MINIMA + 1));
    }
    desviarDeProjeteis(selvagem);
    Entity principal = principal(adversario, selvagem);
    selvagem.getLookControl().setLookAt(principal, GIRO_DA_CABECA, GIRO_DA_CABECA);
    if (agora >= cerebro.pensarNoTick && atacar(selvagem, principal, adversario, cerebro, agora)) {
      return;
    }
    seMexer(selvagem, principal, cerebro, agora);
  }

  // Executando um golpe, jorrando, em fúria, preso, dormindo ou congelado: não escolhe nada agora
  static boolean ocupado(Pokemon pokemon) {
    return AgendaDeGolpes.estaOcupado(pokemon) || Jatos.estaJorrando(pokemon) || Furia.emFuria(pokemon)
        || Preso.estaPreso(pokemon) || SonoECongelado.estaParado(pokemon);
  }

  private static ServerPlayer maisPerto(PokemonEntity selvagem, List<ServerPlayer> jogadores) {
    ServerPlayer maisPerto = null;
    for (ServerPlayer jogador : jogadores) {
      if (jogador.isAlive() && !jogador.isSpectator() && jogador.level() == selvagem.level()
          && (maisPerto == null || selvagem.distanceToSqr(jogador) < selvagem.distanceToSqr(maisPerto))) {
        maisPerto = jogador;
      }
    }
    return maisPerto;
  }

  // Com quem ele luta (e se posiciona, e para quem olha): o seu lutador; sem lutador, o seu Pokémon fora mais perto dele,
  // que vira o lutador (7.2b); sem nenhum seu fora, você
  private static Entity principal(ServerPlayer adversario, PokemonEntity selvagem) {
    Pokemon lutador = Lutadores.de(adversario);
    PokemonEntity entidade = lutador == null ? null : lutador.getEntity();
    if (entidade != null && entidade.isAlive() && !lutador.isFainted()) {
      return entidade;
    }
    Pokemon maisPerto = Lutadores.oMaisPertoDe(adversario, selvagem, Mira.ALCANCE);
    if (maisPerto == null) {
      return adversario;
    }
    Lutadores.atacadoPeloSelvagem(adversario, maisPerto);
    return maisPerto.getEntity();
  }

  // O golpe é escolhido contra o Pokémon. Com Pokémon seu ali, só um golpe de dano DE LONGE (único ou em área no alvo)
  // pode ir em você, 1 vez em 5; o de contato, nunca (7.2b, dele: "os ataques de perto não deveriam vir em mim")
  private static boolean atacar(PokemonEntity selvagem, Entity principal, ServerPlayer adversario, Cerebro cerebro,
      long agora) {
    int slot = EscolhaDoGolpe.melhor(selvagem, principal, cerebro.usados, cerebro.entrouNoTick, agora, false);
    if (slot < 0) {
      return false;
    }
    Move golpe = selvagem.getPokemon().getMoveSet().get(slot);
    Entity alvo = principal;
    FormaDoGolpe forma = FormaDoGolpe.de(golpe);
    if (principal != adversario && golpe.getDamageCategory() != DamageCategories.INSTANCE.getSTATUS()
        && !EfeitosDosGolpes.fazContato(golpe) && (forma == FormaDoGolpe.UNICO || forma == FormaDoGolpe.AREA_INIMIGOS)
        && selvagem.getRandom().nextDouble() < Configuracao.chanceDeMirarEmVoce()) {
      alvo = adversario;
    }
    // 7.2d: outro selvagem está com a vez em você: este vai no seu Pokémon; sem Pokémon seu fora, espera
    if (alvo == adversario && agora < VEZ_EM_VOCE.getOrDefault(adversario.getUUID(), 0L)) {
      if (principal == adversario) {
        return false;
      }
      alvo = principal;
    }
    Andar.parar(selvagem);
    if (!ControleDeGolpes.usarGolpeDaIa(selvagem, slot, alvo, adversario, agora)) {
      return false;
    }
    Execucao execucao = AgendaDeGolpes.emExecucao(selvagem.getPokemon());
    if (alvo == adversario && execucao != null) {
      VEZ_EM_VOCE.put(adversario.getUUID(), execucao.getTickFim() + FOLGA_ENTRE_ATACANTES);
    }
    cerebro.usados.add(golpe.getName());
    cerebro.estavaOcupado = true;
    return true;
  }

  // A cada segundo, mais ou menos: longe demais ou perto demais da distância dele, corrige; na distância certa, anda de
  // lado em volta do alvo (gira a direção do alvo até ele de 25 a 45 graus, para um lado ou para o outro)
  private static void seMexer(PokemonEntity selvagem, Entity principal, Cerebro cerebro, long agora) {
    if (agora < cerebro.seMexerNoTick) {
      return;
    }
    cerebro.seMexerNoTick = agora + SE_MEXER_A_CADA + selvagem.getRandom().nextInt(SE_MEXER_A_CADA / 2);
    double preferida = lutaDeLonge(selvagem.getPokemon()) ? DE_LONGE : DE_PERTO;
    Vec3 dele = principal.position();
    Vec3 paraMim = selvagem.position().subtract(dele).multiply(1, 0, 1);
    if (paraMim.lengthSqr() < 1.0E-4) {
      paraMim = new Vec3(1, 0, 0);
    }
    double distancia = paraMim.length();
    paraMim = paraMim.normalize();
    if (Math.abs(distancia - preferida) <= FOLGA_DA_DISTANCIA) {
      double giro = Math.toRadians((GIRO_MINIMO + selvagem.getRandom().nextDouble() * GIRO_A_MAIS)
          * (selvagem.getRandom().nextBoolean() ? 1 : -1));
      paraMim = new Vec3(paraMim.x * Math.cos(giro) - paraMim.z * Math.sin(giro), 0,
          paraMim.x * Math.sin(giro) + paraMim.z * Math.cos(giro));
    }
    // 8.6: em volta de VOCÊ (nenhum Pokémon seu fora), mais devagar que você correndo: dá para fugir
    float velocidade = principal instanceof Player jogador ? Andar.contraVoce(selvagem, jogador, VELOCIDADE) : VELOCIDADE;
    Andar.ate(selvagem, dele.add(paraMim.scale(preferida)), velocidade);
  }

  // Luta de longe quem tem mais golpes de dano sem contato do que com contato
  private static boolean lutaDeLonge(Pokemon pokemon) {
    int deContato = 0;
    int deLonge = 0;
    for (Move golpe : pokemon.getMoveSet().getMoves()) {
      if (golpe.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) {
        continue;
      }
      if (EfeitosDosGolpes.fazContato(golpe)) {
        deContato++;
      } else {
        deLonge++;
      }
    }
    return deLonge > deContato;
  }

  // Lote 3 dos itens (o Cartão Vermelho): o selvagem sai correndo, como o arisco que foge (Combate tira da luta quem se afasta)
  public static void fazerFugir(PokemonEntity selvagem) {
    Cerebro cerebro = CEREBROS.get(selvagem.getUUID());
    if (cerebro != null) {
      cerebro.fugindo = true;
      CobblemonCombatZA.LOGGER.info("IA: {} foi expulso da batalha (Cartão Vermelho)", selvagem.getPokemon().getSpecies().getName());
    }
  }

  // Só o arisco, e uma vez: a partir daí ele só corre (Combate tira da luta quem passa de 24 blocos de todos)
  private static boolean comecouAFugir(PokemonEntity selvagem, List<ServerPlayer> jogadores, Cerebro cerebro) {
    Pokemon pokemon = selvagem.getPokemon();
    if (cerebro.temperamento != Temperamento.Tipo.ARISCO
        || pokemon.getCurrentHealth() >= pokemon.getMaxHealth() * FUGA_ABAIXO_DE) {
      return false;
    }
    cerebro.fugindo = true;
    CobblemonCombatZA.LOGGER.info("IA: {} (arisco) está fugindo com {}/{} de vida", pokemon.getSpecies().getName(),
        pokemon.getCurrentHealth(), pokemon.getMaxHealth());
    for (ServerPlayer jogador : jogadores) {
      Feedback.historico(jogador, pokemon,
          Component.translatable("cobblemon_combat_za.batalha.fugindo", pokemon.getDisplayName(false)));
    }
    return true;
  }

  // Um projétil do outro lado vindo na direção dele (a linha do voo passa a até 1 bloco da casca dele): uma chance, uma
  // vez por projétil, de pular para o lado de fora da linha. Os teleguiados perseguem: deles não adianta
  private static void desviarDeProjeteis(PokemonEntity selvagem) {
    Vec3 eu = selvagem.getBoundingBox().getCenter();
    for (Projetil projetil : selvagem.level().getEntitiesOfClass(Projetil.class,
        selvagem.getBoundingBox().inflate(OLHAR_PROJETEIS_ATE))) {
      PokemonEntity deQuem = projetil.atacante();
      Vec3 voo = projetil.getDeltaMovement();
      if (deQuem == null || projetil.ehTeleguiado() || !Lados.ehInimigoDe(deQuem, selvagem) || voo.lengthSqr() < 1.0E-6) {
        continue;
      }
      Vec3 direcao = voo.normalize();
      Vec3 ateMim = eu.subtract(projetil.getBoundingBox().getCenter());
      double naFrente = ateMim.dot(direcao);
      Vec3 foraDaLinha = ateMim.subtract(direcao.scale(naFrente));
      if (naFrente <= 0 || foraDaLinha.length() > selvagem.getBbWidth() / 2 + FORA_DA_LINHA
          || !projetil.primeiraOlhadaDe(selvagem) || selvagem.getRandom().nextDouble() >= CHANCE_DE_DESVIAR) {
        continue;
      }
      pularDaLinha(selvagem, direcao, foraDaLinha);
      CobblemonCombatZA.LOGGER.info("IA: {} pulou para fora do caminho de {}",
          selvagem.getPokemon().getSpecies().getName(), projetil.golpe());
      return;
    }
  }

  // De lado ao voo (a perpendicular no chão), para o lado em que ele já está; bem na linha, sorteia o lado
  private static void pularDaLinha(PokemonEntity selvagem, Vec3 direcao, Vec3 foraDaLinha) {
    Vec3 lado = new Vec3(-direcao.z, 0, direcao.x);
    if (lado.lengthSqr() < 1.0E-4) {
      lado = new Vec3(1, 0, 0);
    }
    lado = lado.normalize();
    double qualLado = foraDaLinha.dot(lado);
    if (qualLado < 0 || (Math.abs(qualLado) < 1.0E-3 && selvagem.getRandom().nextBoolean())) {
      lado = lado.scale(-1);
    }
    selvagem.setDeltaMovement(lado.x * PULO_PARA_O_LADO, PULO_PARA_CIMA, lado.z * PULO_PARA_O_LADO);
    selvagem.hurtMarked = true;
    Feedback.particulas(selvagem, ParticleTypes.CLOUD, NUVENS_DO_PULO);
  }
}
