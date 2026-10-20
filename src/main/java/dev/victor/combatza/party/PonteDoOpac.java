package dev.victor.combatza.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.victor.combatza.CobblemonCombatZA;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

// Party com o Open Parties and Claims (dele, 20/10: "vai existir duas party? isso vai trazer confusão" → escolheu "usar a do
// OPAC quando houver"): com o OPAC no servidor, a party é a DELE (a mesma que divide o terreno protegido). O nosso /party, a
// tela P, a batalha, o XP, o quadrinho e o brilho usam ela pela API pública do OPAC (javap: OpenPACServerAPI.getPartyManager,
// getPartyByMember, createPartyForOwner, invitePlayer, addMember, removeMember). Ela fica salva pelo OPAC (não acaba ao sair do
// servidor). Tudo o que fala com o OPAC fica AQUI; esta classe só é carregada com ele instalado (Parties confere antes)
final class PonteDoOpac {
  private PonteDoOpac() {
  }

  private static IPartyManagerAPI partidos(MinecraftServer servidor) {
    return OpenPACServerAPI.get(servidor).getPartyManager();
  }

  private static IServerPartyAPI partyDe(MinecraftServer servidor, UUID jogador) {
    return partidos(servidor).getPartyByMember(jogador);
  }

  static boolean saoAliados(Player a, Player b) {
    MinecraftServer servidor = a.getServer();
    if (servidor == null) {
      return false;
    }
    IServerPartyAPI party = partyDe(servidor, a.getUUID());
    return party != null && party.getMemberInfo(b.getUUID()) != null;
  }

  static List<ServerPlayer> outrosOnline(ServerPlayer jogador) {
    List<ServerPlayer> outros = new ArrayList<>();
    IServerPartyAPI party = partyDe(jogador.server, jogador.getUUID());
    if (party != null) {
      party.getOnlineMemberStream().filter(online -> online != jogador).forEach(outros::add);
    }
    return outros;
  }

  // Quem convida e ainda não tem party ganha uma (dele, como dono). O convite fica também no OPAC (a tela dele mostra)
  static int convidar(ServerPlayer de, ServerPlayer para) {
    if (partyDe(de.server, para.getUUID()) != null) {
      return Parties.erro(de, "ja_tem_party", para.getDisplayName());
    }
    IServerPartyAPI party = partyDe(de.server, de.getUUID());
    if (party == null) {
      party = partidos(de.server).createPartyForOwner(de);
    }
    if (party == null) {
      return Parties.erro(de, "opac_desligado");
    }
    if (party.getMemberCount() >= Parties.MAXIMO) {
      return Parties.erro(de, "cheia", Parties.MAXIMO);
    }
    party.invitePlayer(para.getUUID(), para.getGameProfile().getName());
    Parties.anotarConvite(de, para);
    return 1;
  }

  static int aceitar(ServerPlayer quem, UUID quemConvidou) {
    IServerPartyAPI party = partyDe(quem.server, quemConvidou);
    if (party == null) {
      return Parties.erro(quem, "sem_convite");
    }
    if (partyDe(quem.server, quem.getUUID()) != null) {
      return Parties.erro(quem, "ja_tem_party_voce");
    }
    if (party.getMemberCount() >= Parties.MAXIMO) {
      return Parties.erro(quem, "cheia", Parties.MAXIMO);
    }
    party.addMember(quem.getUUID(), PartyMemberRank.MEMBER, quem.getGameProfile().getName());
    party.uninvitePlayer(quem.getUUID());
    avisarTodos(party, Component.translatable(Parties.FRASE + "entrou", quem.getDisplayName()));
    CobblemonCombatZA.LOGGER.info("PARTY (OPAC): {} entrou ({} na party)", quem.getName().getString(),
        party.getMemberCount());
    return 1;
  }

  static void recusou(ServerPlayer quem, UUID quemConvidou) {
    IServerPartyAPI party = partyDe(quem.server, quemConvidou);
    if (party != null) {
      party.uninvitePlayer(quem.getUUID());
    }
  }

  // O dono sozinho desfaz a party; com mais gente, o OPAC pede para passar a liderança antes (a API dele não passa)
  static int sair(ServerPlayer quem) {
    IServerPartyAPI party = partyDe(quem.server, quem.getUUID());
    if (party == null) {
      return Parties.erro(quem, "sem_party");
    }
    if (party.getOwner().getUUID().equals(quem.getUUID())) {
      if (party.getMemberCount() > 1) {
        return Parties.erro(quem, "dono_opac");
      }
      partidos(quem.server).removeParty(party);
    } else {
      party.removeMember(quem.getUUID());
      avisarTodos(party, Component.translatable(Parties.FRASE + "saiu", quem.getDisplayName()));
    }
    quem.sendSystemMessage(Component.translatable(Parties.FRASE + "voce_saiu"));
    return 1;
  }

  static int expulsar(ServerPlayer lider, ServerPlayer alvo) {
    IServerPartyAPI party = partyDe(lider.server, lider.getUUID());
    if (party == null) {
      return Parties.erro(lider, "sem_party");
    }
    if (!party.getOwner().getUUID().equals(lider.getUUID())) {
      return Parties.erro(lider, "so_o_lider");
    }
    if (alvo == lider || party.getMemberInfo(alvo.getUUID()) == null) {
      return Parties.erro(lider, "nao_esta", alvo.getDisplayName());
    }
    party.removeMember(alvo.getUUID());
    avisarTodos(party, Component.translatable(Parties.FRASE + "expulso", alvo.getDisplayName()));
    alvo.sendSystemMessage(Component.translatable(Parties.FRASE + "voce_foi_expulso"));
    return 1;
  }

  static int listar(ServerPlayer quem) {
    IServerPartyAPI party = partyDe(quem.server, quem.getUUID());
    if (party == null) {
      return Parties.erro(quem, "sem_party");
    }
    MutableComponent lista = Component.translatable(Parties.FRASE + "lista", party.getMemberCount(), Parties.MAXIMO);
    party.getMemberInfoStream().forEach(membro -> lista.append(Component.literal(
        "\n " + (membro.isOwner() ? "♛ " : "• ") + membro.getUsername())));
    quem.sendSystemMessage(lista);
    return 1;
  }

  // Os outros membros (o UUID, o nome e se é o dono), para a lista que vai ao seu jogo
  static List<Parties.Membro> outrosMembros(ServerPlayer jogador) {
    List<Parties.Membro> outros = new ArrayList<>();
    IServerPartyAPI party = partyDe(jogador.server, jogador.getUUID());
    if (party != null) {
      for (IPartyMemberAPI membro : party.getMemberInfoStream().toList()) {
        if (!membro.getUUID().equals(jogador.getUUID())) {
          outros.add(new Parties.Membro(membro.getUUID(), membro.getUsername(), membro.isOwner()));
        }
      }
    }
    return outros;
  }

  static boolean ehDono(ServerPlayer jogador) {
    IServerPartyAPI party = partyDe(jogador.server, jogador.getUUID());
    return party != null && party.getOwner().getUUID().equals(jogador.getUUID());
  }

  private static void avisarTodos(IServerPartyAPI party, Component aviso) {
    party.getOnlineMemberStream().forEach(online -> online.sendSystemMessage(aviso));
  }
}
