package br.com.crona.ferias;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras de datas dos períodos aquisitivos (CLT-01, CLT-02, CLT-03).
 *
 * Classe de DOMÍNIO pura: sem Spring, sem banco, sem "hoje" escondido (a data de hoje
 * sempre entra como parâmetro). Por isso é fácil de testar com qualquer data.
 */
public final class CalendarioAquisitivo {

    private CalendarioAquisitivo() {
    }

    /**
     * Todos os períodos aquisitivos que já começaram até {@code hoje}: os completos e o
     * que está em andamento. Uma admissão no futuro ainda não tem período nenhum.
     */
    public static List<PeriodoCalculado> periodos(LocalDate admissao, LocalDate hoje) {
        var periodos = new ArrayList<PeriodoCalculado>();
        for (int numero = 1; ; numero++) {
            // Sempre a partir da ADMISSÃO (admissao + n anos), e nunca somando 1 ano ao
            // período anterior. Assim o 29/02 não "escorrega" para 28/02 para sempre.
            LocalDate inicio = admissao.plusYears(numero - 1);
            if (inicio.isAfter(hoje)) {
                return periodos;
            }
            LocalDate fim = admissao.plusYears(numero).minusDays(1);
            // CLT-03: as férias de um período precisam ser tiradas nos 12 meses seguintes ao fim dele.
            LocalDate limiteConcessao = admissao.plusYears(numero + 1L).minusDays(1);
            periodos.add(new PeriodoCalculado(numero, inicio, fim, limiteConcessao));
        }
    }

    /** CLT-02 (art. 130): os dias de direito diminuem conforme as faltas injustificadas. */
    public static int diasDeDireito(int faltasInjustificadas) {
        if (faltasInjustificadas < 0) {
            throw new IllegalArgumentException("Faltas não podem ser negativas: " + faltasInjustificadas);
        }
        if (faltasInjustificadas <= 5) return 30;
        if (faltasInjustificadas <= 14) return 24;
        if (faltasInjustificadas <= 23) return 18;
        if (faltasInjustificadas <= 32) return 12;
        return 0;   // mais de 32 faltas: perde o direito às férias daquele período
    }

    public record PeriodoCalculado(int numero, LocalDate inicio, LocalDate fim, LocalDate limiteConcessao) {

        /** Ainda está sendo "acumulado": o colaborador ainda não completou os 12 meses. */
        public boolean emAquisicao(LocalDate hoje) {
            return !hoje.isAfter(fim);
        }
    }
}
