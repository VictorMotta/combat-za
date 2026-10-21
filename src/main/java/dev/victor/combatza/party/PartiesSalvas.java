package dev.victor.combatza.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

// A party nossa guardada JUNTO COM O MUNDO (data/cobblemon_combat_za_parties.dat na pasta do mundo; dele, 21/10: "a party não
// deveria ser desfeita saindo do jogo, deveria continuar mesmo em servidor dedicado"). O mesmo jeito dos waypoints da party
// (PontosSalvos): o SavedData do Minecraft, que o jogo salva junto com o resto. Cada party: o líder e os membros; e o último
// nome de cada um, para mostrar quem está offline
final class PartiesSalvas extends SavedData {
  static final String ARQUIVO = CobblemonCombatZA.MOD_ID + "_parties";
  // Sem conversor de versão (null): a Fabric API aceita
  static final SavedData.Factory<PartiesSalvas> FABRICA = new SavedData.Factory<>(PartiesSalvas::new, PartiesSalvas::ler, null);

  record Salva(UUID lider, List<UUID> membros) {
  }

  private final List<Salva> parties = new ArrayList<>();
  private final Map<UUID, String> nomes = new HashMap<>();

  List<Salva> parties() {
    return parties;
  }

  Map<UUID, String> nomes() {
    return nomes;
  }

  void trocar(List<Salva> novas, Map<UUID, String> novosNomes) {
    parties.clear();
    parties.addAll(novas);
    nomes.clear();
    nomes.putAll(novosNomes);
    setDirty();
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registros) {
    ListTag lista = new ListTag();
    for (Salva party : parties) {
      CompoundTag deUma = new CompoundTag();
      deUma.putUUID("lider", party.lider());
      ListTag membros = new ListTag();
      for (UUID membro : party.membros()) {
        membros.add(NbtUtils.createUUID(membro));
      }
      deUma.put("membros", membros);
      lista.add(deUma);
    }
    tag.put("parties", lista);
    CompoundTag deNomes = new CompoundTag();
    nomes.forEach((jogador, nome) -> deNomes.putString(jogador.toString(), nome));
    tag.put("nomes", deNomes);
    return tag;
  }

  private static PartiesSalvas ler(CompoundTag tag, HolderLookup.Provider registros) {
    PartiesSalvas salvas = new PartiesSalvas();
    ListTag lista = tag.getList("parties", Tag.TAG_COMPOUND);
    for (int i = 0; i < lista.size(); i++) {
      CompoundTag deUma = lista.getCompound(i);
      ListTag membros = deUma.getList("membros", Tag.TAG_INT_ARRAY);
      List<UUID> lidos = new ArrayList<>();
      for (Tag membro : membros) {
        lidos.add(NbtUtils.loadUUID(membro));
      }
      salvas.parties.add(new Salva(deUma.getUUID("lider"), lidos));
    }
    CompoundTag deNomes = tag.getCompound("nomes");
    for (String chave : deNomes.getAllKeys()) {
      salvas.nomes.put(UUID.fromString(chave), deNomes.getString(chave));
    }
    return salvas;
  }
}
