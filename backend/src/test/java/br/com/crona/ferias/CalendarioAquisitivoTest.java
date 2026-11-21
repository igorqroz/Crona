package br.com.crona.ferias;

import br.com.crona.ferias.CalendarioAquisitivo.PeriodoCalculado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD: estes testes foram escritos ANTES do código, direto das regras em
 * docs/regras-de-negocio.md. Cada um cita a regra que verifica.
 */
class CalendarioAquisitivoTest {

    private static LocalDate data(String ddMMaaaa) {
        var p = ddMMaaaa.split("/");
        return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
    }

    @Nested
    @DisplayName("CLT-02: dias de direito conforme as faltas injustificadas")
    class DiasDeDireito {

        // @ParameterizedTest: o mesmo teste roda uma vez para cada linha da tabela.
        // As linhas testam as FRONTEIRAS de cada faixa (5 e 6, 14 e 15...), onde os erros costumam morar.
        @ParameterizedTest(name = "{0} faltas → {1} dias")
        @CsvSource({
                "0, 30", "5, 30",
                "6, 24", "14, 24",
                "15, 18", "23, 18",
                "24, 12", "32, 12",
                "33, 0", "100, 0"
        })
        void conformeFaltas(int faltas, int diasEsperados) {
            assertThat(CalendarioAquisitivo.diasDeDireito(faltas)).isEqualTo(diasEsperados);
        }

        @Test
        @DisplayName("faltas negativas não existem")
        void faltasNegativas() {
            assertThatThrownBy(() -> CalendarioAquisitivo.diasDeDireito(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("CLT-01 e CLT-03: datas dos períodos aquisitivos")
    class Periodos {

        @Test
        @DisplayName("Ana (admitida em 01/02/2024), em 10/02/2026: dois períodos completos e um em andamento")
        void exemploDoRedesign() {
            var periodos = CalendarioAquisitivo.periodos(data("01/02/2024"), data("10/02/2026"));

            assertThat(periodos).containsExactly(
                    new PeriodoCalculado(1, data("01/02/2024"), data("31/01/2025"), data("31/01/2026")),
                    new PeriodoCalculado(2, data("01/02/2025"), data("31/01/2026"), data("31/01/2027")),
                    new PeriodoCalculado(3, data("01/02/2026"), data("31/01/2027"), data("31/01/2028")));
        }

        @Test
        @DisplayName("admitido hoje: um período, em andamento")
        void admitidoHoje() {
            var hoje = data("20/11/2026");
            var periodos = CalendarioAquisitivo.periodos(hoje, hoje);

            assertThat(periodos).hasSize(1);
            assertThat(periodos.getFirst().emAquisicao(hoje)).isTrue();
        }

        @Test
        @DisplayName("admissão no futuro (cadastrado antes de começar): nenhum período ainda")
        void admissaoFutura() {
            assertThat(CalendarioAquisitivo.periodos(data("01/12/2026"), data("20/11/2026"))).isEmpty();
        }

        @Test
        @DisplayName("um período é 'em aquisição' até o último dia, e completo no dia seguinte")
        void emAquisicao() {
            var primeiro = CalendarioAquisitivo.periodos(data("01/02/2024"), data("10/02/2026")).getFirst();

            assertThat(primeiro.emAquisicao(data("31/01/2025"))).isTrue();
            assertThat(primeiro.emAquisicao(data("01/02/2025"))).isFalse();
        }

        @Test
        @DisplayName("os períodos são colados: cada um começa no dia seguinte ao fim do anterior")
        void semBuracosNemSobreposicao() {
            var periodos = CalendarioAquisitivo.periodos(data("15/03/2010"), data("20/11/2026"));

            for (int i = 1; i < periodos.size(); i++) {
                assertThat(periodos.get(i).inicio()).isEqualTo(periodos.get(i - 1).fim().plusDays(1));
                assertThat(periodos.get(i).numero()).isEqualTo(i + 1);
            }
        }

        @Test
        @DisplayName("admissão em 29/02: os aniversários não 'escorregam' com o passar dos anos")
        void anoBissexto() {
            var periodos = CalendarioAquisitivo.periodos(data("29/02/2024"), data("20/11/2028"));

            // Em ano que não é bissexto, o aniversário cai em 28/02...
            assertThat(periodos.get(1).inicio()).isEqualTo(data("28/02/2025"));
            // ...mas em 2028 (bissexto) volta para 29/02. Somar 1 ano em cima do período anterior
            // erraria aqui: 29/02 → 28/02 → 28/02 → 28/02, e o erro se acumula para sempre.
            assertThat(periodos.get(4).inicio()).isEqualTo(data("29/02/2028"));
        }
    }
}
