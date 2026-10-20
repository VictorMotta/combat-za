package dev.victor.combatza.client;

import net.minecraft.client.KeyMapping;

// Uma tecla que vale só no APERTO: segurar não repete. O Minecraft conta como um clique novo cada repetição automática do
// teclado (segurar uma tecla, como numa letra de um texto); aqui, enquanto ela continua segurada, as repetições são
// jogadas fora (pedido dele, 18/10: "segurar o botão está rodando direto")
public class ApertoDeTecla {
  private final KeyMapping tecla;
  private boolean segurando = false;

  public ApertoDeTecla(KeyMapping tecla) {
    this.tecla = tecla;
  }

  // Uma vez por tick: true se ela foi apertada agora (e não só continua segurada). Um toque rápido, que começa e acaba
  // entre dois ticks, também vale: o clique fica guardado e a tecla já está solta
  public boolean foiApertada() {
    boolean apertou = false;
    while (tecla.consumeClick()) {
      if (!segurando) {
        apertou = true;
        segurando = true;
      }
    }
    if (!tecla.isDown()) {
      segurando = false;
    }
    return apertou;
  }
}
