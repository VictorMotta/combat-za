package dev.victor.combatza.combate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement;
import com.cobblemon.mod.common.api.pokemon.stats.SidemodEvSource;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.OriginalTrainerType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.evolution.progress.DefeatEvolutionProgress;
import com.cobblemon.mod.common.pokemon.requirements.DefeatRequirement;
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.captura.Desmaio;
import dev.victor.combatza.ia.Parceiro;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 8.2 (o XP; decisões dele: em 11/10, "o XP completo de uma batalha normal do Cobblemon, sem precisar de item"; em 20/10,
// "quem tiver usando o compartilhador de XP deve compartilhar o XP igual o Cobblemon faz", ganha "quem foi lutador contra
// ele" e "capturar dá XP, como no Z-A"). Desde a 7.6, sem o Fight or Flight, derrotar um selvagem não dava XP nenhum.
// Copiado do próprio Cobblemon (javap no PokemonBattle.end, no StandardExperienceCalculator e no Generation8EvCalculator):
// - para cada Pokémon do seu time: quem ENFRENTOU o selvagem (foi o lutador enquanto ele estava na batalha, mesmo sem acertar)
//   ganha o XP inteiro; quem não enfrentou mas segura o Compartilhador de Exp. ganha o XP vezes o multiplicador da
//   configuração do Cobblemon (mesmo na bola ou de guarda); os outros, nada;
// - a conta: ((XP base × nível dele ÷ 5) × parte × ((2N + 10) ÷ (N + n + 10))^2,5 + 1) × 1,5 se o Pokémon é trocado × o Ovo da
//   Sorte × 1,2 se já passou do nível de evoluir × 1,2 com amizade 220 ou mais × o multiplicador geral do Cobblemon;
// - os EVs da espécie dele (+8 com o item de força do atributo), e, para quem enfrentou e derrotou, o progresso das evoluções
//   "derrotar X";
// - a mensagem e a subida de nível são as do Cobblemon (addExperienceWithPlayer).
// Diferença da batalha por turnos: o XP sai na hora em que CADA selvagem desmaia (ou é capturado), não no fim da batalha
public class Experiencia {
  private static final double PARTE_DE_QUEM_ENFRENTOU = 1.0;
  private static final double SEM_PARTE = 0.0;
  private static final double DIVISOR_DO_NIVEL = 5.0;
  private static final double EXPOENTE = 2.5;
  private static final double BONUS_DE_TROCADO = 1.5;
  private static final double BONUS_DE_EVOLUCAO = 1.2;
  private static final double BONUS_DE_AMIZADE = 1.2;
  private static final int AMIZADE_DO_BONUS = 220;
  private static final int EV_DO_ITEM_DE_FORCA = 8;
  private static final Map<Stat, TagKey<Item>> ITENS_DE_FORCA = Map.of(
      Stats.HP, CobblemonItemTags.POWER_WEIGHT, Stats.ATTACK, CobblemonItemTags.POWER_BRACER,
      Stats.DEFENCE, CobblemonItemTags.POWER_BELT, Stats.SPECIAL_ATTACK, CobblemonItemTags.POWER_LENS,
      Stats.SPECIAL_DEFENCE, CobblemonItemTags.POWER_BAND, Stats.SPEED, CobblemonItemTags.POWER_ANKLET);

  // Quem enfrentou cada selvagem (a chave é o UUID do Pokémon dele): para cada jogador da batalha, os Pokémon dele que foram
  // o lutador. Jogador sem lutador também entra, com a lista vazia: quem segura o Compartilhador dele ganha
  private record Registro(PokemonEntity selvagem, Map<UUID, Set<UUID>> enfrentaram) {
  }

  private static final Map<UUID, Registro> REGISTROS = new HashMap<>();

  // O que cada Pokémon ganhou na batalha até agora, por jogador (na ordem em que ganharam)
  private static final class Ganho {
    private final Component nome;
    private final int nivelAntes;
    private int xp;
    private int nivelDepois;
    private boolean soCompartilhador = true;

    private Ganho(Component nome, int nivelAntes) {
      this.nome = nome;
      this.nivelAntes = nivelAntes;
      this.nivelDepois = nivelAntes;
    }
  }

  private static final String FRASE = "cobblemon_combat_za.batalha.";
  private static final Map<UUID, Map<UUID, Ganho>> GANHOS = new HashMap<>();

  private Experiencia() {
  }

  public static void registrar() {
    // O selvagem morreu (até no golpe que mata de uma vez, que não passa pelo AFTER_DAMAGE: achado da 4.4b-4)
    ServerLivingEntityEvents.AFTER_DEATH.register((entidade, fonte) -> {
      if (entidade instanceof PokemonEntity selvagem && selvagem.getPokemon().isWild() && entidade.getServer() != null) {
        distribuir(entidade.getServer(), selvagem.getPokemon(), true, null);
      }
    });
    CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, Experiencia::aoCapturar);
  }

  private static void aoCapturar(PokemonCapturedEvent evento) {
    // Etapa 8: capturado depois de cair no chão, o XP já saiu na queda
    if (Desmaio.jaDeuOXp(evento.getPokemon())) {
      return;
    }
    distribuir(evento.getPlayer().server, evento.getPokemon(), false, evento.getPlayer());
  }

  // Etapa 8: o selvagem desmaiou e ficou caído no chão (Desmaio): o XP de derrotado sai agora, como se ele tivesse morrido
  public static void derrotado(MinecraftServer servidor, Pokemon selvagem) {
    distribuir(servidor, selvagem, true, null);
  }

  // 21/10 (PvP): um Pokémon do adversário desmaiou no duelo (pvp.Duelo). O XP é o de derrotar, na mesma conta do Cobblemon
  // (que também dá XP no PvP, com a opção allowExperienceFromPvP ligada): inteiro para quem o enfrentou, e o Compartilhador
  public static void derrotadoNoDuelo(ServerPlayer jogador, Pokemon derrotado, Set<UUID> enfrentaram) {
    darAoTime(jogador, derrotado, enfrentaram, true, PARTE_DE_QUEM_ENFRENTOU);
  }

  // GerenciarCombates, a cada tick: quem está em cada batalha, e o lutador de cada um, anotados para cada selvagem dela
  public static void anotar(List<Combate> combates) {
    for (Combate combate : combates) {
      for (PokemonEntity selvagem : combate.getSelvagens()) {
        Registro registro = REGISTROS.computeIfAbsent(selvagem.getPokemon().getUuid(),
            uuid -> new Registro(selvagem, new HashMap<>()));
        anotarJogadores(combate, registro.enfrentaram());
      }
    }
    // Sumiu do mundo sem desmaiar nem ser capturado (descarregou, sumiu): esquece
    REGISTROS.values().removeIf(registro -> registro.selvagem().isRemoved());
  }

  public static void limpar() {
    REGISTROS.clear();
    GANHOS.clear();
  }

  private static void anotarJogadores(Combate combate, Map<UUID, Set<UUID>> enfrentaram) {
    for (ServerPlayer jogador : combate.getJogadores()) {
      Set<UUID> deste = enfrentaram.computeIfAbsent(jogador.getUUID(), uuid -> new HashSet<>());
      Pokemon lutador = Lutadores.de(jogador);
      if (lutador != null) {
        deste.add(lutador.getUuid());
      }
    }
  }

  // O selvagem desmaiou (derrotado) ou foi capturado. A batalha dele é anotada de novo agora: o golpe que começa a batalha e
  // derruba o selvagem acontece no mesmo tick, antes da anotação do fim do tick
  private static void distribuir(MinecraftServer servidor, Pokemon selvagem, boolean derrotado, ServerPlayer capturador) {
    Registro registro = REGISTROS.remove(selvagem.getUuid());
    Map<UUID, Set<UUID>> enfrentaram = registro != null ? registro.enfrentaram() : new HashMap<>();
    Combate combate = GerenciarCombates.combateDoSelvagem(selvagem.getUuid());
    if (combate != null) {
      anotarJogadores(combate, enfrentaram);
    }
    if (capturador != null) {
      Set<UUID> doCapturador = enfrentaram.computeIfAbsent(capturador.getUUID(), uuid -> new HashSet<>());
      // 8.2b (dele: "quem tiver fora e selecionado também"): captura sem batalha, sem ninguém ter enfrentado o selvagem: o
      // seu Pokémon selecionado que está fora ganha o XP inteiro (com "todos de guarda", ninguém está selecionado)
      Pokemon selecionado = Parceiro.de(capturador);
      if (registro == null && combate == null && selecionado != null) {
        doCapturador.add(selecionado.getUuid());
      }
    }
    // Party (dele, 20/10: "o XP é dividido entre os lutadores da party, como num MMO"): o XP de cada um é dividido pelos
    // jogadores da batalha (sozinho, inteiro)
    long jogadores = enfrentaram.keySet().stream().filter(uuid -> servidor.getPlayerList().getPlayer(uuid) != null).count();
    double divisao = 1.0 / Math.max(1, jogadores);
    for (Map.Entry<UUID, Set<UUID>> par : enfrentaram.entrySet()) {
      ServerPlayer jogador = servidor.getPlayerList().getPlayer(par.getKey());
      if (jogador == null) {
        continue;
      }
      darAoTime(jogador, selvagem, par.getValue(), derrotado, divisao);
      // Sem batalha nenhuma para terminar (a captura pelas costas, ou o selvagem de uma batalha que já acabou), o resumo do
      // que cada um ganhou sai agora; na batalha, ele sai no aviso do fim dela
      if (!GerenciarCombates.estaEmCombate(jogador)) {
        AvisoDaBatalha.semBatalha(jogador, selvagem, jogador == capturador, tirarOsGanhos(jogador));
      }
    }
  }

  private static void darAoTime(ServerPlayer jogador, Pokemon selvagem, Set<UUID> enfrentaram, boolean derrotado,
      double divisao) {
    for (Pokemon meu : Cobblemon.INSTANCE.getStorage().getParty(jogador)) {
      boolean enfrentou = enfrentaram.contains(meu.getUuid());
      double parte = parte(meu, enfrentou);
      if (parte <= SEM_PARTE) {
        continue;
      }
      if (derrotado && enfrentou) {
        progressoDeDerrotar(meu, selvagem);
      }
      int xp = xp(meu, selvagem, parte * divisao, jogador);
      int nivelAntes = meu.getLevel();
      int ganhou = 0;
      if (xp > 0) {
        ganhou = meu.addExperienceWithPlayer(jogador, new SidemodExperienceSource(CobblemonCombatZA.MOD_ID), xp)
            .getExperienceAdded();
      }
      darEvs(meu, selvagem);
      anotarGanho(jogador, meu, ganhou, nivelAntes, enfrentou);
      CobblemonCombatZA.LOGGER.info("XP: {} ganhou {} por {} {} Nv. {} ({})", meu.getSpecies().getName(), ganhou,
          derrotado ? "derrotar" : "capturar", selvagem.getSpecies().getName(), selvagem.getLevel(),
          enfrentou ? "enfrentou" : "Compartilhador de Exp. x" + parte);
    }
  }

  // 8.2b (dele: "prefiro que fale quanto cada um ganhou no fim da batalha também"): o XP de cada Pokémon somado na batalha
  // inteira (o nível de antes é o do primeiro ganho), para o aviso do fim
  private static void anotarGanho(ServerPlayer jogador, Pokemon meu, int xp, int nivelAntes, boolean enfrentou) {
    if (xp <= 0) {
      return;
    }
    Ganho ganho = GANHOS.computeIfAbsent(jogador.getUUID(), uuid -> new LinkedHashMap<>())
        .computeIfAbsent(meu.getUuid(), uuid -> new Ganho(meu.getDisplayName(false), nivelAntes));
    ganho.xp += xp;
    ganho.nivelDepois = meu.getLevel();
    ganho.soCompartilhador &= !enfrentou;
  }

  // As linhas do aviso ("Growlithe: +120 XP (Nv. 25 → 26)"), e o jogador começa a próxima batalha sem nada somado
  public static List<Component> tirarOsGanhos(ServerPlayer jogador) {
    Map<UUID, Ganho> ganhos = GANHOS.remove(jogador.getUUID());
    List<Component> linhas = new ArrayList<>();
    if (ganhos == null) {
      return linhas;
    }
    for (Ganho ganho : ganhos.values()) {
      // Quem subiu de nível fica em verde
      MutableComponent linha = ganho.nivelDepois > ganho.nivelAntes
          ? Component.translatable(FRASE + "xp_nivel", ganho.nome, ganho.xp, ganho.nivelAntes, ganho.nivelDepois)
              .withStyle(ChatFormatting.GREEN)
          : Component.translatable(FRASE + "xp", ganho.nome, ganho.xp);
      if (ganho.soCompartilhador) {
        linha.append(Component.translatable(FRASE + "xp_compartilhador"));
      }
      linhas.add(linha);
    }
    return linhas;
  }

  public static void esquecer(ServerPlayer jogador) {
    GANHOS.remove(jogador.getUUID());
  }

  private static double parte(Pokemon meu, boolean enfrentou) {
    if (enfrentou) {
      return PARTE_DE_QUEM_ENFRENTOU;
    }
    return meu.heldItem().is(CobblemonItemTags.EXPERIENCE_SHARE)
        ? Cobblemon.INSTANCE.getConfig().getExperienceShareMultiplier()
        : SEM_PARTE;
  }

  // O StandardExperienceCalculator do Cobblemon, conta por conta
  private static int xp(Pokemon meu, Pokemon selvagem, double parte, ServerPlayer dono) {
    int nivelDele = selvagem.getLevel();
    double base = selvagem.getForm().getBaseExperienceYield() * nivelDele / DIVISOR_DO_NIVEL;
    double escala = Math.pow((2.0 * nivelDele + 10) / (nivelDele + meu.getLevel() + 10), EXPOENTE);
    boolean doDono = meu.getOriginalTrainerType() == OriginalTrainerType.PLAYER
        && Objects.equals(meu.getOriginalTrainer(), dono.getUUID().toString());
    double trocado = doDono ? 1.0 : BONUS_DE_TROCADO;
    double ovo = meu.heldItem().is(CobblemonItemTags.LUCKY_EGG)
        ? Cobblemon.INSTANCE.getConfig().getLuckyEggMultiplier()
        : 1.0;
    double evolucao = passouDoNivelDeEvoluir(meu) ? BONUS_DE_EVOLUCAO : 1.0;
    double amizade = meu.getFriendship() >= AMIZADE_DO_BONUS ? BONUS_DE_AMIZADE : 1.0;
    double total = (base * parte * escala + 1) * trocado * ovo * evolucao * amizade
        * Cobblemon.INSTANCE.getConfig().getExperienceMultiplier();
    return (int) Math.round(total);
  }

  // Uma evolução por nível com todos os requisitos já cumpridos (ele podia ter evoluído e não evoluiu): o bônus de 1,2
  private static boolean passouDoNivelDeEvoluir(Pokemon meu) {
    for (Object item : meu.getEvolutionProxy().server()) {
      Set<Requirement> requisitos = ((Evolution) item).getRequirements();
      boolean porNivel = requisitos.stream().anyMatch(LevelRequirement.class::isInstance);
      if (porNivel && requisitos.stream().allMatch(requisito -> requisito.check(meu))) {
        return true;
      }
    }
    return false;
  }

  // Os EVs da espécie do selvagem; com o item de força do atributo, +8 (Generation8EvCalculator)
  private static void darEvs(Pokemon meu, Pokemon selvagem) {
    ItemStack item = meu.heldItem();
    for (Map.Entry<Stat, Integer> par : selvagem.getForm().getEvYield().entrySet()) {
      int valor = par.getValue();
      TagKey<Item> deForca = ITENS_DE_FORCA.get(par.getKey());
      if (!item.isEmpty() && deForca != null && item.is(deForca)) {
        valor += EV_DO_ITEM_DE_FORCA;
      }
      if (valor > 0) {
        meu.getEvs().add(par.getKey(), valor, new SidemodEvSource(CobblemonCombatZA.MOD_ID, meu));
      }
    }
  }

  // As evoluções "derrotar X" (como o Kingambit): cada vitória sobre o alvo conta uma
  private static void progressoDeDerrotar(Pokemon meu, Pokemon selvagem) {
    for (Evolution evolucao : meu.getLockedEvolutions()) {
      for (Requirement requisito : evolucao.getRequirements()) {
        if (requisito instanceof DefeatRequirement derrotar && derrotar.getTarget().matches(selvagem)) {
          DefeatEvolutionProgress progresso = meu.getEvolutionProxy().current().progressFirstOrCreate(
              atual -> atual instanceof DefeatEvolutionProgress deDerrotar
                  && Objects.equals(deDerrotar.currentProgress().getTarget(), derrotar.getTarget()),
              DefeatEvolutionProgress::new);
          progresso.updateProgress(new DefeatEvolutionProgress.Progress(derrotar.getTarget(),
              progresso.currentProgress().getAmount() + 1));
        }
      }
    }
  }
}
