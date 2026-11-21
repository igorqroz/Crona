package br.com.crona.ferias;

import java.time.LocalDate;

/**
 * Férias de um colega, como vêm do banco: qual pedido, quem, quando, status e de qual departamento.
 *
 * @param solicitacaoId o pedido de origem (nulo para férias que ainda só existem numa proposta de troca)
 */
public record AusenciaRegistrada(Long solicitacaoId, String nome, LocalDate inicio, LocalDate fim,
                                 StatusSolicitacao status, Long departamentoId) {
}
