package br.com.crona.ferias;

import java.time.LocalDate;

/** Férias de um colega, como vêm do banco: quem, quando, status e de qual departamento. */
public record AusenciaRegistrada(String nome, LocalDate inicio, LocalDate fim, StatusSolicitacao status,
                                 Long departamentoId) {
}
