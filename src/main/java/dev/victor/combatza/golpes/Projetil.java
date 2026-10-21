package dev.victor.combatza.golpes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.joml.Vector3f;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.combate.GerenciarCombates;
import dev.victor.combatza.combate.Lados;
import dev.victor.combatza.habilidades.QuebraMolde;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 5.2: o golpe que voa, de verdade (escolhas dele, 18/10: 20 blocos por segundo; reto, mesmo com o foco; a bola na cor do
// tipo com as partículas do Cobblemon). Sai da boca do Pokémon quando a carga termina, voa em linha reta até onde estava o
// alvo (ou até o ponto mirado) e acerta o PRIMEIRO inimigo que cruzar o caminho (3.5f: atravessa os aliados; em batalha,
// também os monstros, 6.5d). Bateu num bloco: para ali. Passou do alcance sem pegar ninguém: errou (o alvo saiu da frente).
// É uma entidade nossa, registrada com o nome "cobblemon_combat_za:projetil". Não é salva no mundo (noSave): se o jogo
// fechar com uma no ar, ela some.
// 5.2c (dele: "tem que ter a animação e ver um projétil de fogo indo até bater nele"): a bola aparece com um rastro da cara
// do tipo (chamas no fogo, faíscas no elétrico...).
// 5.2d (dele: "o revólver d'água tinha uma aguinha saindo, agora virou uma bola; ele só tava dessincronizado"): com a mira
// num Pokémon à vista e um golpe com a parte que voa na batalha, sai também o visual da BATALHA, mirando no selvagem, e a
// bola voa no MESMO TEMPO da animação (a Brasa leva 0,5 s): o dano entra quando o visual chega. Ela continua reta: um muro
// ou alguém no caminho ainda barram. Uma partícula nossa do Snowstorm (ProjeteisNossos) faz o papel da bola.
// 5.2e (dele: "por que fora do combate sai um projétil e dentro só o mijinho?"; escolheu "bola + animação"): a bola não se
// esconde mais atrás da animação da batalha, e ficou menor ("a bola não precisa ser enorme")
public class Projetil extends Entity {
  public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CobblemonCombatZA.MOD_ID, "projetil");
  public static EntityType<Projetil> TIPO;

  // 1 bloco por tick = 20 blocos por segundo. Com o visual da batalha, a velocidade é a que chega no tempo da animação, até 8
  // blocos por tick (os raios, como o Raio Congelante, chegam quase na hora)
  private static final double VELOCIDADE = 1.0;
  private static final double VELOCIDADE_MAXIMA = 8.0;
  private static final double TICKS_POR_SEGUNDO = 20.0;
  private static final float RAIO_DE_ACERTO = 0.3f;
  // Só o tamanho do desenho (quem decide o acerto é o RAIO_DE_ACERTO). Menores desde a 5.2e: "a bola não precisa ser enorme"
  private static final float RAIO_DO_PROJETIL = 0.16f;
  private static final float RAIO_DO_CANHAO = 0.26f;
  // O rastro do tipo, no seu jogo: partículas do próprio Minecraft com a cara de cada tipo (as que faltam ficam só com o
  // pó colorido)
  private static final Map<String, ParticleOptions> RASTRO_DO_TIPO = Map.ofEntries(
      Map.entry("fire", ParticleTypes.FLAME), Map.entry("water", ParticleTypes.SPLASH),
      Map.entry("electric", ParticleTypes.ELECTRIC_SPARK), Map.entry("ice", ParticleTypes.SNOWFLAKE),
      Map.entry("grass", ParticleTypes.HAPPY_VILLAGER), Map.entry("psychic", ParticleTypes.WITCH),
      Map.entry("ghost", ParticleTypes.REVERSE_PORTAL), Map.entry("dark", ParticleTypes.SQUID_INK),
      Map.entry("dragon", ParticleTypes.DRAGON_BREATH), Map.entry("fairy", ParticleTypes.CHERRY_LEAVES),
      Map.entry("normal", ParticleTypes.END_ROD), Map.entry("fighting", ParticleTypes.CRIT),
      Map.entry("flying", ParticleTypes.POOF));
  private static final int PARTICULAS_DO_RASTRO = 2;
  private static final double ESPALHAR_O_RASTRO = 0.12;
  private static final float TAMANHO_DO_PO = 0.6f;
  // Com o tempo da animação da batalha, a bola anda até 8 blocos por tick: o rastro sai a cada bloco do caminho, e não
  // só onde ela parou, para não virar pontinhos soltos
  private static final double PASSO_DO_RASTRO = 1.0;
  private static final double DISTANCIA_PARA_VER = 64.0;
  // 5.3: o teleguiado vira um pouco a cada tick na direção do alvo (0 = reto, 1 = vira de uma vez) e vai mais longe
  private static final double CURVA_DO_TELEGUIADO = 0.35;
  private static final double ALCANCE_A_MAIS_DO_TELEGUIADO = 1.5;
  // 8.5 (dele, 20/10: "tem vezes que o bicho tá perto demais e parece que o poder não pega", o Charizard contra um
  // Zigzagoon; escolheu "a bola segue o alvo"). O log mostrou a bola do Chama Furacão caindo no chão logo atrás do bicho:
  // ela saía do OLHO (2,2 blocos de altura no Charizard) e ia reta para onde ele estava, com 0,3 de margem num alvo de
  // 0,5 × 0,3 que ainda pula para o lado. Agora: sai do peito; toda bola mirada em alguém vira um pouco por tick atrás
  // dele (só enquanto ele está à frente: depois de passar, ela não volta); e o alvo marcado pequeno conta como se tivesse
  // pelo menos 1 bloco
  private static final double ALTURA_DO_PEITO = 0.6;
  private static final double CURVA_LEVE = 0.15;
  private static final double TAMANHO_MINIMO_DO_ALVO = 1.0;
  // A cor e o tamanho vão para o cliente, que desenha a bola (RenderDoProjetil)
  private static final EntityDataAccessor<Integer> COR = SynchedEntityData.defineId(Projetil.class,
      EntityDataSerializers.INT);
  private static final EntityDataAccessor<Float> RAIO = SynchedEntityData.defineId(Projetil.class,
      EntityDataSerializers.FLOAT);
  // O golpe e o tipo vão para o cliente: o rastro do tipo e a procura de uma partícula nossa
  private static final EntityDataAccessor<String> GOLPE = SynchedEntityData.defineId(Projetil.class,
      EntityDataSerializers.STRING);
  private static final EntityDataAccessor<String> TIPO_DO_GOLPE = SynchedEntityData.defineId(Projetil.class,
      EntityDataSerializers.STRING);

  // O cliente troca este (CobblemonCombatZAClient): o servidor não conhece as classes de desenho do Cobblemon
  public static Consumer<Projetil> AO_APARECER_NO_CLIENTE = projetil -> {
  };

  private boolean apareceuNoCliente = false;
  private boolean comParticulaNossa = false;

  // Só no servidor
  private Execucao execucao;
  private Entity marcado;
  private double alcance;
  private double percorrido;
  // Com o visual da batalha, o som do golpe saindo já tocou junto com ele
  private boolean visualDeBatalha;
  // 5.3: os golpes que nunca erram perseguem o alvo marcado; quem desviou a bola passa direto por ele
  private boolean teleguiado;
  private final Set<Entity> desviaram = new HashSet<>();
  // 7.2: quem já pensou em sair da frente desta bola (IaDoSelvagem): uma chance só por bola
  private final Set<UUID> olharam = new HashSet<>();

  public Projetil(EntityType<? extends Projetil> tipo, Level nivel) {
    super(tipo, nivel);
    noPhysics = true;
  }

  public static void registrar() {
    TIPO = Registry.register(BuiltInRegistries.ENTITY_TYPE, ID,
        EntityType.Builder.<Projetil>of(Projetil::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8)
            .updateInterval(1).noSave().noSummon().build(ID.toString()));
  }

  // Chamado no fim da carga (ControleDeGolpes.aplicarImpacto), no lugar do acerto na hora
  public static void lancar(Execucao execucao, PokemonEntity atacante) {
    HitResult mirada = execucao.getMirada();
    Entity marcado = mirada instanceof EntityHitResult acerto && acerto.getEntity().isAlive() ? acerto.getEntity() : null;
    Vec3 inicio = atacante.position().add(0, atacante.getBbHeight() * ALTURA_DO_PEITO, 0);
    Vec3 destino = marcado != null ? marcado.getBoundingBox().getCenter() : mirada.getLocation();
    Vec3 caminho = destino.subtract(inicio);
    if (caminho.lengthSqr() < 1.0E-4) {
      caminho = atacante.getLookAngle();
    }
    Projetil projetil = new Projetil(TIPO, atacante.level());
    projetil.execucao = execucao;
    projetil.marcado = marcado;
    // Mirando em alguém, voa até o fim do alcance (se ele sair da frente, passa); mirando num ponto, para no ponto
    double alcanceDoGolpe = Mira.alcanceDo(execucao.getGolpe());
    projetil.teleguiado = marcado != null && Precisao.nuncaErra(execucao.getGolpe());
    if (projetil.teleguiado) {
      alcanceDoGolpe *= ALCANCE_A_MAIS_DO_TELEGUIADO;
    }
    projetil.alcance = marcado != null ? alcanceDoGolpe : Math.min(alcanceDoGolpe, caminho.length());
    projetil.irPara(inicio);
    // 5.4c: o golpe de prender voa devagar e sem o visual da batalha, que chegaria antes da bola
    boolean prende = Preso.ehDePrender(execucao.getGolpe());
    boolean visualDeBatalha = !prende && marcado instanceof PokemonEntity
        && AnimacoesDeBatalha.voa(execucao.getGolpe(), caminho.length())
        && atacante.hasLineOfSight(marcado);
    double velocidade = prende ? VELOCIDADE * Preso.MULTIPLICADOR_DA_VELOCIDADE : VELOCIDADE;
    if (visualDeBatalha) {
      double ticks = Math.max(1, Math.round(AnimacoesDeBatalha.tempoAteOAlvo(execucao.getGolpe()) * TICKS_POR_SEGUNDO));
      velocidade = Math.min(VELOCIDADE_MAXIMA, caminho.length() / ticks);
      AnimacoesDeBatalha.voar(execucao.getGolpe(), atacante, marcado);
    }
    projetil.visualDeBatalha = visualDeBatalha;
    projetil.setDeltaMovement(caminho.normalize().scale(velocidade));
    projetil.entityData.set(COR, CoresDosTipos.de(execucao.getGolpe().getType()));
    projetil.entityData.set(RAIO, execucao.getArquetipo() == Arquetipo.CANHAO ? RAIO_DO_CANHAO : RAIO_DO_PROJETIL);
    projetil.entityData.set(GOLPE, execucao.getGolpe().getName());
    projetil.entityData.set(TIPO_DO_GOLPE, execucao.getGolpe().getType().getShowdownId());
    atacante.level().addFreshEntity(projetil);
  }

  // A partícula do rastro de um golpe: a do tipo, ou o pó na cor do tipo. O jato (Jatos) usa a mesma
  public static ParticleOptions particulaDoTipo(Move golpe) {
    ParticleOptions doTipo = RASTRO_DO_TIPO.get(golpe.getType().getShowdownId());
    if (doTipo != null) {
      return doTipo;
    }
    int cor = CoresDosTipos.de(golpe.getType());
    return new DustParticleOptions(
        new Vector3f(((cor >> 16) & 0xFF) / 255f, ((cor >> 8) & 0xFF) / 255f, (cor & 0xFF) / 255f), TAMANHO_DO_PO);
  }

  // 7.2: para o selvagem desviar (IaDoSelvagem): quem lançou, se ela persegue, e se ele já pensou nela
  public PokemonEntity atacante() {
    return execucao == null ? null : execucao.getEntidade();
  }

  public boolean ehTeleguiado() {
    return teleguiado;
  }

  public boolean primeiraOlhadaDe(Entity quem) {
    return olharam.add(quem.getUUID());
  }

  public int cor() {
    return entityData.get(COR);
  }

  public float raio() {
    return entityData.get(RAIO);
  }

  public String golpe() {
    return entityData.get(GOLPE);
  }

  public String tipoDoGolpe() {
    return entityData.get(TIPO_DO_GOLPE);
  }

  // A bola aparece sempre, até junto da animação da batalha do Cobblemon (5.2e, escolha dele: "bola + animação"). Só some
  // com uma partícula nossa do Snowstorm, que é desenhada no lugar dela
  public boolean mostraABola() {
    return !comParticulaNossa;
  }

  public void usarParticulaNossa() {
    comParticulaNossa = true;
  }

  // No servidor, ele voa e procura em quem bater. No cliente, só anda junto (o servidor corrige a posição a cada tick) e
  // solta o pó colorido do rastro
  @Override
  public void tick() {
    super.tick();
    if (level().isClientSide()) {
      if (!apareceuNoCliente) {
        apareceuNoCliente = true;
        AO_APARECER_NO_CLIENTE.accept(this);
      }
      Vec3 antes = centro();
      setPos(position().add(getDeltaMovement()));
      if (mostraABola()) {
        rastro(antes, centro());
      }
      return;
    }
    // 21/10 (habilidades, lote da conta do dano): o projétil chega "durante o golpe" (o Quebra-Molde e o Infiltrado valem)
    QuebraMolde.durante(execucao, this::voar);
  }

  // Um passo de 1 bloco: primeiro o bloco no caminho (clip), depois quem cruza o pedaço até ele. O primeiro que conta
  // ganha o golpe. Se quem lançou sumiu (voltou para a bola, desmaiou), o golpe se desfaz no ar
  private void voar() {
    PokemonEntity atacante = execucao == null ? null : execucao.getPokemon().getEntity();
    if (atacante == null || atacante != execucao.getEntidade() || !atacante.isAlive()) {
      sumir();
      return;
    }
    // O som do golpe saindo, o mesmo da batalha (com o visual da batalha, ele já tocou no lançamento)
    if (tickCount == 1 && !visualDeBatalha) {
      AnimacoesDeBatalha.somDeQuemAtaca(execucao.getGolpe(), atacante);
    }
    seguirOAlvo();
    boolean emBatalha = GerenciarCombates.estaEmCombate(execucao.getPokemon());
    Vec3 de = centro();
    double passo = Math.min(getDeltaMovement().length(), alcance - percorrido);
    Vec3 ate = de.add(getDeltaMovement().normalize().scale(passo));
    BlockHitResult bloco = level().clip(new ClipContext(de, ate, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
        this));
    Vec3 fim = bloco.getType() == HitResult.Type.MISS ? ate : bloco.getLocation();
    EntityHitResult pessoa = ProjectileUtil.getEntityHitResult(level(), this, de, fim,
        getBoundingBox().expandTowards(fim.subtract(de)).inflate(1.0), e -> podeAcertar(e, atacante, emBatalha),
        RAIO_DE_ACERTO);
    if (pessoa == null) {
      pessoa = noAlvoMarcado(de, fim);
    }
    if (pessoa != null) {
      Entity atingido = pessoa.getEntity();
      // 5.3: ele desviou (Precisao): o golpe único passa direto, e ele não conta mais para esta bola
      if (FormaDoGolpe.de(execucao.getGolpe()) == FormaDoGolpe.UNICO && Precisao.desviou(execucao, atacante, atingido)) {
        desviaram.add(atingido);
      } else {
        chegar(atacante, atingido, atingido.getBoundingBox().getCenter());
        return;
      }
    }
    if (bloco.getType() == HitResult.Type.BLOCK) {
      bateuNoBloco(atacante, bloco.getLocation());
      return;
    }
    irPara(ate);
    percorrido += passo;
    if (percorrido >= alcance - 1.0E-3) {
      acabouOCaminho(atacante, ate);
      return;
    }
  }

  // As mesmas regras da mira (3.5f e 6.5d): o alvo marcado sempre conta; os outros, se são inimigos e entram na luta
  // (7.4b: no golpe do guarda, só os monstros; Lados.pegaNoGolpe)
  private boolean podeAcertar(Entity entidade, PokemonEntity atacante, boolean emBatalha) {
    return entidade != atacante && entidade instanceof LivingEntity && entidade.isAlive() && !entidade.isSpectator()
        && entidade.isPickable()
        && !desviaram.contains(entidade)
        && (entidade == marcado || Lados.pegaNoGolpe(execucao, atacante, entidade, emBatalha));
  }

  // 5.3: o golpe que nunca erra faz curva atrás do alvo marcado, um pouco a cada tick, na mesma velocidade. 8.5: as outras
  // bolas miradas em alguém também, mais de leve e só com ele à frente; quem desviou (Precisao) não é seguido
  private void seguirOAlvo() {
    if (marcado == null || !marcado.isAlive() || desviaram.contains(marcado)) {
      return;
    }
    Vec3 atual = getDeltaMovement();
    Vec3 paraOAlvo = marcado.getBoundingBox().getCenter().subtract(centro()).normalize();
    if (!teleguiado && paraOAlvo.dot(atual) <= 0) {
      return;
    }
    Vec3 nova = atual.normalize().lerp(paraOAlvo, teleguiado ? CURVA_DO_TELEGUIADO : CURVA_LEVE);
    setDeltaMovement((nova.lengthSqr() < 1.0E-6 ? paraOAlvo : nova.normalize()).scale(atual.length()));
  }

  // 8.5: o Pokémon marcado com a caixa de pelo menos 1 bloco (um Zigzagoon tem 0,5 × 0,3) mais a margem de acerto. Vale
  // também a bola que já começa dentro dela (o "clip" da caixa não conta quem começa dentro)
  private EntityHitResult noAlvoMarcado(Vec3 de, Vec3 fim) {
    if (!(marcado instanceof PokemonEntity) || !marcado.isAlive() || desviaram.contains(marcado)) {
      return null;
    }
    AABB caixa = marcado.getBoundingBox();
    AABB maior = caixa.inflate(aMais(caixa.getXsize()), aMais(caixa.getYsize()), aMais(caixa.getZsize()));
    return maior.contains(de) || maior.clip(de, fim).isPresent() ? new EntityHitResult(marcado) : null;
  }

  private static double aMais(double tamanho) {
    return Math.max(0, (TAMANHO_MINIMO_DO_ALVO - tamanho) / 2) + RAIO_DE_ACERTO;
  }

  // Num Pokémon, o efeito de impacto da batalha, no ponto "target" dele; senão (monstro, chão), a partícula num ponto
  private void chegar(PokemonEntity atacante, Entity alvo, Vec3 ponto) {
    discard();
    if (!AnimacoesDeBatalha.noAlvo(execucao.getGolpe(), alvo)) {
      ParticulasDosGolpes.impacto(execucao.getGolpe(), (ServerLevel) level(), ponto);
    }
    ControleDeGolpes.projetilChegou(execucao, atacante, alvo, ponto);
  }

  // Num bloco: mirando em alguém, o golpe único errou (tinha algo no caminho); mirando num ponto, ou golpe de área, o golpe
  // acontece ali
  private void bateuNoBloco(PokemonEntity atacante, Vec3 ponto) {
    if (marcado != null && FormaDoGolpe.de(execucao.getGolpe()) == FormaDoGolpe.UNICO) {
      discard();
      Feedback.particulas((ServerLevel) level(), ParticleTypes.POOF, ponto, 6);
      ControleDeGolpes.projetilErrou(execucao, ControleDeGolpes.TEM_ALGO_CAMINHO);
      return;
    }
    chegar(atacante, null, ponto);
  }

  // O fim do caminho: mirando num ponto, chegou nele; mirando em alguém, ele saiu da frente
  private void acabouOCaminho(PokemonEntity atacante, Vec3 ponto) {
    if (marcado == null) {
      chegar(atacante, null, ponto);
      return;
    }
    discard();
    Feedback.particulas((ServerLevel) level(), ParticleTypes.POOF, ponto, 6);
    // Se ele desviou, o "desviou!" já apareceu
    if (!desviaram.contains(marcado)) {
      ControleDeGolpes.projetilErrou(execucao, "alvo_saiu_da_frente");
    }
  }

  private void sumir() {
    if (level() instanceof ServerLevel nivel) {
      Feedback.particulas(nivel, ParticleTypes.SMOKE, centro(), 4);
    }
    discard();
  }

  // No seu jogo: o pó colorido e as partículas do tipo, a cada bloco do pedaço que a bola andou neste tick
  private void rastro(Vec3 de, Vec3 ate) {
    Vector3f cor = new Vector3f(((cor() >> 16) & 0xFF) / 255f, ((cor() >> 8) & 0xFF) / 255f, (cor() & 0xFF) / 255f);
    ParticleOptions doTipo = RASTRO_DO_TIPO.get(tipoDoGolpe());
    int pontos = Math.max(1, (int) Math.ceil(de.distanceTo(ate) / PASSO_DO_RASTRO));
    for (int p = 1; p <= pontos; p++) {
      Vec3 ponto = de.lerp(ate, (double) p / pontos);
      level().addParticle(new DustParticleOptions(cor, TAMANHO_DO_PO), ponto.x, ponto.y, ponto.z, 0, 0, 0);
      for (int i = 0; doTipo != null && i < PARTICULAS_DO_RASTRO; i++) {
        level().addParticle(doTipo, ponto.x + espalhar(), ponto.y + espalhar(), ponto.z + espalhar(), 0, 0, 0);
      }
    }
  }

  private double espalhar() {
    return (random.nextDouble() - 0.5) * 2 * ESPALHAR_O_RASTRO;
  }

  // A posição de uma entidade é a dos pés; o projétil anda pelo meio dele
  private Vec3 centro() {
    return position().add(0, getBbHeight() / 2, 0);
  }

  private void irPara(Vec3 centro) {
    setPos(centro.subtract(0, getBbHeight() / 2, 0));
  }

  // A correnteza empurra as entidades do Minecraft; o golpe segue reto
  @Override
  public boolean isPushedByFluid() {
    return false;
  }

  @Override
  public boolean shouldRenderAtSqrDistance(double distancia) {
    return distancia < DISTANCIA_PARA_VER * DISTANCIA_PARA_VER;
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder dados) {
    dados.define(COR, 0xFFFFFF);
    dados.define(RAIO, RAIO_DO_PROJETIL);
    dados.define(GOLPE, "");
    dados.define(TIPO_DO_GOLPE, "");
  }

  @Override
  protected void readAdditionalSaveData(CompoundTag dados) {
  }

  @Override
  protected void addAdditionalSaveData(CompoundTag dados) {
  }
}
