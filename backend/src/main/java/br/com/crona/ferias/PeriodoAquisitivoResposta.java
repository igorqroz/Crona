package br.com.crona.ferias;

import java.time.LocalDate;

/** Um período aquisitivo com o saldo já calculado: o que a tela "Seu saldo de férias" precisa. */
public record PeriodoAquisitivoResposta(
        int numero,
        LocalDate inicio,
        LocalDate fim,
        LocalDate tirarAte,
        int faltasInjustificadas,
        int direito,
        int aprovados,
        int emPedido,
        int livres,
        SaldoDoPeriodo.Situacao situacao
) {

    static PeriodoAquisitivoResposta de(PeriodoAquisitivo periodo, SaldoDoPeriodo saldo) {
        return new PeriodoAquisitivoResposta(periodo.getNumero(), periodo.getDataInicio(), periodo.getDataFim(),
                periodo.getDataLimiteConcessao(), periodo.getFaltasInjustificadas(), saldo.direito(),
                saldo.aprovados(), saldo.emPedido(), saldo.livres(), saldo.situacao());
    }
}
