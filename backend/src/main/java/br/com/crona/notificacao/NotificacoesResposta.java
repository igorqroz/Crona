package br.com.crona.notificacao;

import java.time.Instant;
import java.util.List;

/** O que o sino mostra: o número de não lidas e as notificações mais recentes. */
public record NotificacoesResposta(long naoLidas, List<Item> itens) {

    public record Item(Long id, TipoDeNotificacao tipo, String mensagem, String link, boolean lida, Instant criadaEm) {

        static Item de(Notificacao n) {
            return new Item(n.getId(), n.getTipo(), n.getMensagem(), n.getLink(), n.getLidaEm() != null, n.getCriadoEm());
        }
    }
}
