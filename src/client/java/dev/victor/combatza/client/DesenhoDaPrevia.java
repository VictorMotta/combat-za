package dev.victor.combatza.client;

import java.util.List;

import org.joml.Matrix4f;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.victor.combatza.golpes.AreaSemQueda;
import dev.victor.combatza.golpes.Armadilhas;
import dev.victor.combatza.golpes.Arquetipo;
import dev.victor.combatza.golpes.Balanceamento;
import dev.victor.combatza.golpes.CampoDeBolhas;
import dev.victor.combatza.golpes.CoresDosTipos;
import dev.victor.combatza.golpes.EfeitosDosGolpes;
import dev.victor.combatza.golpes.FormaDoGolpe;
import dev.victor.combatza.golpes.Mira;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 6.11b (a prévia do golpe, decidida por ele em 19/10: "mostrar uma área que causaria dano dessas habilidades"): enquanto a
// tecla está segurada (PreviaDoGolpe), o que o golpe vai pegar aparece DESENHADO NO CHÃO, na cor do tipo (cinza na recarga):
// - área no alvo (Bolha, Cauda Chicote...): o círculo de 3 blocos no ponto mirado, com o degradê da queda do dano (4.3c: forte
//   no meio, metade na borda); sem queda (o campo de bolhas e os da AreaSemQueda), uma cor só;
// - área em volta (Terremoto, Surfar): o círculo de 5 blocos em volta do Pokémon;
// - o que voa: uma faixa no chão do Pokémon até o alvo; o de contato: um traço tracejado até o alvo; os dois com um anel
//   embaixo do alvo;
// - armadilha: o círculo de 3 blocos onde ela vai ficar; os de si mesmo: um anel no próprio Pokémon.
// Quem vai levar ganha um anel branco embaixo. A mira é a MESMA do servidor (Mira.mirarDe): o foco, ou o meio da sua visão.
// Tudo é feito de quadradinhos colados no chão (cada ponta procura o chão: laje, degrau, morro), desenhados depois das
// entidades (WorldRenderEvents) com o "debugStructureQuads" do Minecraft: transparente, sem esconder o verso
public class DesenhoDaPrevia {
  private static final int FATIAS = 40;
  private static final double ACIMA_DO_CHAO = 0.03;
  private static final double PASSO_DA_FAIXA = 0.5;
  private static final double LARGURA_DO_PROJETIL = 0.5;
  private static final double LARGURA_DO_CANHAO = 0.8;
  private static final double LARGURA_DO_TRACO = 0.15;
  private static final double ESPESSURA_DO_CONTORNO = 0.08;
  private static final double FOLGA_DO_ANEL = 0.25;
  private static final double ESPESSURA_DO_ANEL = 0.12;
  // O degradê da queda, do meio para a borda (4 faixas), a cor uniforme e o contorno
  private static final float[] QUEDA = { 0.42f, 0.34f, 0.26f, 0.18f };
  private static final float SEM_QUEDA = 0.3f;
  private static final float CONTORNO = 0.85f;
  private static final float FAIXA = 0.35f;
  private static final float ANEL_DE_QUEM_LEVA = 0.6f;
  private static final int CINZA = 0xFF9A9A9A;
  // 21/10 (dele: "no gelo, a marcação era quase da mesma tonalidade do gelo"): todo anel e toda faixa ganham um contorno duplo,
  // escuro por fora e claro por dentro (CoresDaPrevia: o claro e a cor de dentro, você escolhe), e aparecem em qualquer chão
  private static final double BORDA = 0.07;
  private static final float ALFA_DO_ESCURO = 0.85f;
  private static final float ALFA_DO_CLARO = 0.9f;
  private static final int BRANCO = 0xFFFFFFFF;
  // O marcador, nas cores do seu lado (azul, como no Z-A); vermelho quando dali ele não vê o alvo
  private static final int MARCADOR = 0xFF5AA0FF;
  private static final int MARCADOR_SEM_VISAO = 0xFFFF5A5A;

  private DesenhoDaPrevia() {
  }

  public static void registrar() {
    WorldRenderEvents.AFTER_ENTITIES.register(DesenhoDaPrevia::desenhar);
  }

  private static void desenhar(WorldRenderContext contexto) {
    // 23/10: na batalha por turnos do Cobblemon, sem prévia (BatalhaPorTurnos)
    if (BatalhaPorTurnos.agora()) {
      return;
    }
    int slot = PreviaDoGolpe.mostrando();
    Minecraft cliente = Minecraft.getInstance();
    if (slot < 0 || cliente.player == null || cliente.level == null || contexto.consumers() == null
        || contexto.matrixStack() == null) {
      return;
    }
    int vaga = LutadorNoCliente.vagaDoComandado();
    Pokemon pokemon = vaga < 0 ? null : CobblemonClient.INSTANCE.getStorage().getParty().get(vaga);
    if (pokemon == null || !(pokemon.getState() instanceof SentOutState fora) || fora.getEntity() == null) {
      return;
    }
    Move golpe = pokemon.getMoveSet().get(slot);
    if (golpe == null) {
      return;
    }
    PokemonEntity entidade = fora.getEntity();
    float parcial = contexto.tickCounter().getGameTimeDeltaPartialTick(false);
    int cor = RecargasNoCliente.ticksQueFaltam(pokemon.getUuid(), slot) > 0 ? CINZA
        : CoresDaPrevia.dentro(0xFF000000 | CoresDosTipos.de(golpe.getType()));
    Pincel pincel = new Pincel(cliente.level, contexto.consumers().getBuffer(RenderType.debugStructureQuads()),
        contexto.matrixStack().last().pose(), contexto.camera().getPosition());

    // O alvo: o do foco, ou o que está no meio da sua visão (a mesma mira do servidor)
    Entity alvo = Foco.alvo();
    Vec3 ponto;
    if (alvo != null) {
      ponto = alvo.getPosition(parcial);
    } else {
      HitResult mira = Mira.mirarDe(cliente.player, cliente.player.getEyePosition(parcial),
          cliente.player.getViewVector(parcial), Mira.alcanceDo(golpe), DesenhoDaPrevia::inimigo);
      alvo = mira instanceof EntityHitResult acerto ? acerto.getEntity() : null;
      ponto = alvo != null ? alvo.getPosition(parcial) : mira.getLocation();
    }
    Vec3 origem = PreviaDoGolpe.ondeOPokemonVai(entidade, parcial);
    desenharForma(pincel, golpe, entidade, origem, alvo, ponto, cor);
    desenharMarcador(pincel, entidade, alvo, parcial);

    if (contexto.consumers() instanceof MultiBufferSource.BufferSource fonte) {
      fonte.endBatch(RenderType.debugStructureQuads());
    }
  }

  private static void desenharForma(Pincel pincel, Move golpe, PokemonEntity entidade, Vec3 origem, Entity alvo, Vec3 ponto,
      int cor) {
    boolean semQueda = AreaSemQueda.inclui(golpe) || CampoDeBolhas.ehCampo(golpe);
    switch (FormaDoGolpe.de(golpe)) {
      case AREA_INIMIGOS -> {
        pincel.traco(origem, ponto, Formas.comAlfa(cor, FAIXA));
        pincel.circulo(ponto, Balanceamento.RAIO_NO_IMPACTO, cor, semQueda);
        quemLeva(pincel, entidade, ponto, Balanceamento.RAIO_NO_IMPACTO);
      }
      case AREA_TODOS -> {
        pincel.circulo(origem, Balanceamento.RAIO_EM_VOLTA_DO_POKEMON, cor, semQueda);
        quemLeva(pincel, entidade, origem, Balanceamento.RAIO_EM_VOLTA_DO_POKEMON);
      }
      case ARMADILHA -> pincel.circulo(ponto, Armadilhas.RAIO, cor, true);
      case SEM_ALVO -> pincel.anelComContorno(origem, entidade.getBbWidth() / 2 + FOLGA_DO_ANEL,
          entidade.getBbWidth() / 2 + FOLGA_DO_ANEL + ESPESSURA_DO_ANEL * 2, Formas.comAlfa(cor, CONTORNO));
      default -> {
        if (EfeitosDosGolpes.fazContato(golpe)) {
          pincel.traco(origem, ponto, Formas.comAlfa(cor, CONTORNO));
        } else {
          double largura = Arquetipo.classificar(golpe) == Arquetipo.CANHAO ? LARGURA_DO_CANHAO : LARGURA_DO_PROJETIL;
          pincel.faixaComContorno(origem, ponto, largura, Formas.comAlfa(cor, FAIXA));
        }
        if (alvo != null) {
          double raio = alvo.getBbWidth() / 2 + FOLGA_DO_ANEL;
          pincel.anelComContorno(ponto, raio, raio + ESPESSURA_DO_ANEL, Formas.comAlfa(cor, CONTORNO));
        }
      }
    }
  }

  // 6.11c: o marcador, um anel azul do tamanho do Pokémon e um traço dele até lá; vermelho se dali ele não vê o alvo
  private static void desenharMarcador(Pincel pincel, PokemonEntity entidade, Entity alvo, float parcial) {
    Vec3 marcador = PreviaDoGolpe.marcadorNoMundo(parcial);
    if (marcador == null) {
      return;
    }
    boolean ve = alvo == null || entidade.level().clip(new ClipContext(marcador.add(0, entidade.getBbHeight() * 0.6, 0),
        alvo.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entidade))
        .getType() == HitResult.Type.MISS;
    int cor = ve ? MARCADOR : MARCADOR_SEM_VISAO;
    double raio = entidade.getBbWidth() / 2 + FOLGA_DO_ANEL;
    pincel.anelComContorno(marcador, raio, raio + ESPESSURA_DO_ANEL * 1.5, Formas.comAlfa(cor, CONTORNO));
    pincel.traco(entidade.getPosition(parcial), marcador, Formas.comAlfa(cor, FAIXA));
  }

  // Um anel branco embaixo de cada inimigo dentro da área
  private static void quemLeva(Pincel pincel, Entity quemLanca, Vec3 centro, double raio) {
    List<LivingEntity> dentro = quemLanca.level().getEntitiesOfClass(LivingEntity.class,
        new AABB(centro, centro).inflate(raio), e -> inimigo(e) && e.position().distanceToSqr(centro) <= raio * raio);
    for (LivingEntity quem : dentro) {
      double r = quem.getBbWidth() / 2 + FOLGA_DO_ANEL;
      pincel.anel(quem.position(), r, r + ESPESSURA_DO_ANEL, Formas.comAlfa(BRANCO, ANEL_DE_QUEM_LEVA));
      pincel.anel(quem.position(), r + ESPESSURA_DO_ANEL, r + ESPESSURA_DO_ANEL + BORDA,
          Formas.comAlfa(CoresDaPrevia.contornoDeFora(), ALFA_DO_ESCURO));
    }
  }

  // No seu jogo, inimigo = um Pokémon sem dono (o selvagem; no duelo, o do adversário: Foco.ehAlvo) ou um monstro
  private static boolean inimigo(Entity entidade) {
    if (entidade instanceof PokemonEntity pokemon) {
      return pokemon.isAlive() && Foco.ehAlvo(pokemon);
    }
    return entidade instanceof Enemy && entidade.isAlive();
  }

  // Quem desenha: quadradinhos no chão, cada ponta na altura do chão embaixo dela
  private static final class Pincel {
    private final Level nivel;
    private final VertexConsumer vertices;
    private final Matrix4f matriz;
    private final Vec3 camera;

    private Pincel(Level nivel, VertexConsumer vertices, Matrix4f matriz, Vec3 camera) {
      this.nivel = nivel;
      this.vertices = vertices;
      this.matriz = matriz;
      this.camera = camera;
    }

    // O círculo cheio: com queda, 4 faixas do meio (forte) para a borda (fraca); sem queda, uma cor só. E o contorno: a cor dele
    // e, em volta, o claro por dentro e o escuro por fora
    void circulo(Vec3 centro, double raio, int cor, boolean semQueda) {
      for (int i = 0; i < QUEDA.length; i++) {
        float alfa = semQueda ? SEM_QUEDA : QUEDA[i];
        anel(centro, raio * i / QUEDA.length, raio * (i + 1) / QUEDA.length, Formas.comAlfa(cor, alfa));
      }
      anelComContorno(centro, raio - ESPESSURA_DO_CONTORNO, raio, Formas.comAlfa(cor, CONTORNO));
    }

    // Um anel com o contorno duplo: o claro na borda de dentro e o escuro na de fora
    void anelComContorno(Vec3 centro, double dentro, double fora, int cor) {
      anel(centro, dentro, fora, cor);
      anel(centro, Math.max(0, dentro - BORDA), dentro, Formas.comAlfa(CoresDaPrevia.contornoDeDentro(), ALFA_DO_CLARO));
      anel(centro, fora, fora + BORDA, Formas.comAlfa(CoresDaPrevia.contornoDeFora(), ALFA_DO_ESCURO));
    }

    // A faixa (o caminho do projétil) com o contorno nos dois lados: o claro encostado nela e o escuro por fora
    void faixaComContorno(Vec3 de, Vec3 ate, double largura, int cor) {
      faixa(de, ate, largura, cor, false, 0);
      for (int lado : new int[] { -1, 1 }) {
        faixa(de, ate, BORDA, Formas.comAlfa(CoresDaPrevia.contornoDeDentro(), ALFA_DO_CLARO), false,
            lado * (largura / 2 + BORDA / 2));
        faixa(de, ate, BORDA, Formas.comAlfa(CoresDaPrevia.contornoDeFora(), ALFA_DO_ESCURO), false,
            lado * (largura / 2 + BORDA * 1.5));
      }
    }

    // O traço tracejado (contato, área no alvo, marcador): por baixo, um traço escuro mais largo, que aparece em qualquer chão
    void traco(Vec3 de, Vec3 ate, int cor) {
      faixa(de, ate, LARGURA_DO_TRACO + 2 * BORDA, Formas.comAlfa(CoresDaPrevia.contornoDeFora(), ALFA_DO_ESCURO), true, 0);
      faixa(de, ate, LARGURA_DO_TRACO, cor, true, 0);
    }

    // A coroa entre dois raios, em FATIAS pedaços
    void anel(Vec3 centro, double dentro, double fora, int cor) {
      for (int i = 0; i < FATIAS; i++) {
        double a = 2 * Math.PI * i / FATIAS;
        double b = 2 * Math.PI * (i + 1) / FATIAS;
        quadrado(centro, ponta(centro, fora, a), ponta(centro, fora, b), ponta(centro, dentro, b), ponta(centro, dentro, a),
            cor);
      }
    }

    // Uma faixa no chão de "de" até "ate"; tracejada = um pedaço sim, um não; "deslocamento" a empurra para o lado (o contorno)
    void faixa(Vec3 de, Vec3 ate, double largura, int cor, boolean tracejada, double deslocamento) {
      Vec3 caminho = new Vec3(ate.x - de.x, 0, ate.z - de.z);
      double comprimento = caminho.length();
      if (comprimento < 1.0E-3) {
        return;
      }
      Vec3 frente = caminho.normalize();
      Vec3 paraOLado = new Vec3(-frente.z, 0, frente.x);
      de = de.add(paraOLado.scale(deslocamento));
      Vec3 lado = paraOLado.scale(largura / 2);
      int pedacos = (int) Math.ceil(comprimento / PASSO_DA_FAIXA);
      for (int i = 0; i < pedacos; i++) {
        if (tracejada && i % 2 == 1) {
          continue;
        }
        Vec3 inicio = de.add(frente.scale(comprimento * i / pedacos));
        Vec3 fim = de.add(frente.scale(comprimento * (i + 1) / pedacos));
        quadrado(de, inicio.add(lado), fim.add(lado), fim.subtract(lado), inicio.subtract(lado), cor);
      }
    }

    private static Vec3 ponta(Vec3 centro, double raio, double angulo) {
      return new Vec3(centro.x + raio * Math.cos(angulo), centro.y, centro.z + raio * Math.sin(angulo));
    }

    // As 4 pontas vão para o chão (procurado a partir da altura de "referencia")
    private void quadrado(Vec3 referencia, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int cor) {
      vertice(referencia, a, cor);
      vertice(referencia, b, cor);
      vertice(referencia, c, cor);
      vertice(referencia, d, cor);
    }

    private void vertice(Vec3 referencia, Vec3 ponto, int cor) {
      double y = Mira.noChao(nivel, new Vec3(ponto.x, referencia.y + 0.5, ponto.z)).y + ACIMA_DO_CHAO;
      vertices.addVertex(matriz, (float) (ponto.x - camera.x), (float) (y - camera.y), (float) (ponto.z - camera.z))
          .setColor(cor);
    }
  }
}
