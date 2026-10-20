package dev.victor.combatza.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import dev.victor.combatza.rede.MeusPontosPayload.Ponto;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

// Os waypoints do conjunto "Party" de cada jogador, guardados JUNTO COM O MUNDO (o arquivo data/cobblemon_combat_za_pontos_da_party.dat
// na pasta do mundo): assim o amigo da party que está offline (a party do OPAC continua existindo) ainda aparece no seu mapa.
// O SavedData é o jeito do Minecraft de guardar dados de um mod no mundo: o setDirty() avisa "mudei", e o jogo salva junto
// com o resto (no autosave e ao fechar)
final class PontosSalvos extends SavedData {
  static final String ARQUIVO = CobblemonCombatZA.MOD_ID + "_pontos_da_party";
  // Sem conversor de versão (null): a Fabric API aceita
  static final SavedData.Factory<PontosSalvos> FABRICA = new SavedData.Factory<>(PontosSalvos::new, PontosSalvos::ler, null);

  // De um jogador: o nome (para quando ele está offline) e os pontos por mundo
  static final class Dono {
    private String nome = "";
    private final Map<String, List<Ponto>> porMundo = new TreeMap<>();

    String nome() {
      return nome;
    }

    Map<String, List<Ponto>> porMundo() {
      return porMundo;
    }
  }

  private final Map<UUID, Dono> donos = new HashMap<>();

  Dono de(UUID jogador) {
    return donos.get(jogador);
  }

  // Os pontos de um mundo trocam pelos novos (vazio: o mundo sai). Até tantos mundos por jogador
  void guardar(UUID jogador, String nome, String mundo, List<Ponto> pontos, int maximoDeMundos) {
    Dono dono = donos.computeIfAbsent(jogador, uuid -> new Dono());
    dono.nome = nome;
    if (pontos.isEmpty()) {
      dono.porMundo.remove(mundo);
    } else if (dono.porMundo.containsKey(mundo) || dono.porMundo.size() < maximoDeMundos) {
      dono.porMundo.put(mundo, List.copyOf(pontos));
    }
    if (dono.porMundo.isEmpty()) {
      donos.remove(jogador);
    }
    setDirty();
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registros) {
    ListTag lista = new ListTag();
    donos.forEach((jogador, dono) -> {
      CompoundTag deUm = new CompoundTag();
      deUm.putUUID("dono", jogador);
      deUm.putString("nome", dono.nome);
      CompoundTag mundos = new CompoundTag();
      dono.porMundo.forEach((mundo, pontos) -> {
        ListTag doMundo = new ListTag();
        for (Ponto ponto : pontos) {
          CompoundTag umPonto = new CompoundTag();
          umPonto.putString("nome", ponto.nome());
          umPonto.putString("iniciais", ponto.iniciais());
          umPonto.putInt("x", ponto.x());
          umPonto.putInt("y", ponto.y());
          umPonto.putInt("z", ponto.z());
          umPonto.putInt("cor", ponto.cor());
          doMundo.add(umPonto);
        }
        mundos.put(mundo, doMundo);
      });
      deUm.put("mundos", mundos);
      lista.add(deUm);
    });
    tag.put("donos", lista);
    return tag;
  }

  private static PontosSalvos ler(CompoundTag tag, HolderLookup.Provider registros) {
    PontosSalvos salvos = new PontosSalvos();
    ListTag lista = tag.getList("donos", Tag.TAG_COMPOUND);
    for (int i = 0; i < lista.size(); i++) {
      CompoundTag deUm = lista.getCompound(i);
      Dono dono = new Dono();
      dono.nome = deUm.getString("nome");
      CompoundTag mundos = deUm.getCompound("mundos");
      for (String mundo : mundos.getAllKeys()) {
        ListTag doMundo = mundos.getList(mundo, Tag.TAG_COMPOUND);
        List<Ponto> pontos = new ArrayList<>();
        for (int j = 0; j < doMundo.size(); j++) {
          CompoundTag umPonto = doMundo.getCompound(j);
          pontos.add(new Ponto(umPonto.getString("nome"), umPonto.getString("iniciais"), umPonto.getInt("x"),
              umPonto.getInt("y"), umPonto.getInt("z"), umPonto.getInt("cor")));
        }
        dono.porMundo.put(mundo, List.copyOf(pontos));
      }
      salvos.donos.put(deUm.getUUID("dono"), dono);
    }
    return salvos;
  }
}
