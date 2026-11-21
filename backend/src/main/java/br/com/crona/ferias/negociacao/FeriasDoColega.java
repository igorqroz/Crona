package br.com.crona.ferias.negociacao;

import br.com.crona.compartilhado.ReferenciaResposta;

import java.time.LocalDate;

/**
 * Férias aprovadas de um colega da equipe, que podem entrar numa proposta de troca.
 * O {@code pedidoId} é o que vai no campo pedidoDoColegaId da proposta.
 */
public record FeriasDoColega(Long pedidoId, ReferenciaResposta colaborador, LocalDate inicio, LocalDate fim,
                             int diasGozo) {
}
