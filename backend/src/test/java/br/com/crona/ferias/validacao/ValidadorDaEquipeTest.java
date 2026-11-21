package br.com.crona.ferias.validacao;

import br.com.crona.ferias.validacao.DadosDaEquipe.Ausencia;
import br.com.crona.ferias.validacao.DadosDaEquipe.Departamento;
import br.com.crona.ferias.validacao.DadosDaEquipe.Restricao;
import br.com.crona.ferias.validacao.Verificacao.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD das regras da equipe, com o cenário da tela de Aprovações do redesign:
 * a Engenharia tem 10 pessoas e 70% precisam trabalhar (limite de 3 fora por dia).
 * A Ana pede 17/03 a 31/03. Marina (05–19/03) e Thiago (12–21/03) já estão aprovados,
 * e Lucas (12–19/03) está pendente. De 17 a 19/03 seriam 4 pessoas fora.
 */
class ValidadorDaEquipeTest {

    private static LocalDate marco(int dia) {
        return LocalDate.of(2026, 3, dia);
    }

    private static final Ausencia MARINA = new Ausencia("Marina Jorge", marco(5), marco(19), false);
    private static final Ausencia THIAGO = new Ausencia("Thiago Kang", marco(12), marco(21), false);
    private static final Ausencia LUCAS = new Ausencia("Lucas Prado", marco(12), marco(19), true);
    private static final Ausencia PEDRO = new Ausencia("Pedro Rocha", LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 21), false);

    private Departamento engenharia = new Departamento("Engenharia", 10, 70);
    private List<Ausencia> doDepartamento = List.of(MARINA, THIAGO, LUCAS, PEDRO);
    private Integer limiteDaEmpresa = null;
    private List<Ausencia> daEmpresa = List.of(MARINA, THIAGO, LUCAS, PEDRO);
    private List<Restricao> restricoes = List.of();

    private ValidadorDaEquipe.Resultado validar() {
        return ValidadorDaEquipe.validar(new DadosDaEquipe(marco(17), marco(31), engenharia, doDepartamento,
                limiteDaEmpresa, daEmpresa, restricoes));
    }

    private static Verificacao daRegra(ValidadorDaEquipe.Resultado resultado, String regra) {
        return resultado.verificacoes().stream().filter(v -> v.regra().equals(regra)).findFirst().orElseThrow();
    }

    private static boolean temRegra(ValidadorDaEquipe.Resultado resultado, String regra) {
        return resultado.verificacoes().stream().anyMatch(v -> v.regra().equals(regra));
    }

    @Nested
    @DisplayName("EMP-01: limite de pessoas fora no departamento")
    class LimiteDoDepartamento {

        @Test
        @DisplayName("o caso do redesign: 4 fora de 17 a 19/03, acima do limite de 3 → ponto de atenção")
        void acimaDoLimite() {
            var verificacao = daRegra(validar(), "EMP-01");

            assertThat(verificacao.resultado()).isEqualTo(Resultado.ATENCAO);
            assertThat(verificacao.mensagem()).contains("4 pessoas", "17/03 a 19/03", "limite de 3");
        }

        @Test
        @DisplayName("o pedido PENDENTE do Lucas conta: sem ele, a equipe fica exatamente no limite")
        void pendenteConta() {
            doDepartamento = List.of(MARINA, THIAGO, PEDRO);

            var verificacao = daRegra(validar(), "EMP-01");
            assertThat(verificacao.resultado()).isEqualTo(Resultado.OK);
            assertThat(verificacao.mensagem()).contains("3 de 10");
        }

        @Test
        @DisplayName("regra desligada (sem percentual): nem aparece no checklist")
        void desligada() {
            engenharia = new Departamento("Engenharia", 10, null);

            assertThat(temRegra(validar(), "EMP-01")).isFalse();
        }

        @Test
        @DisplayName("colaborador sem departamento: a regra não se aplica")
        void semDepartamento() {
            engenharia = null;
            doDepartamento = List.of();

            assertThat(temRegra(validar(), "EMP-01")).isFalse();
            assertThat(validar().colegasNoPeriodo()).isEmpty();
        }

        @Test
        @DisplayName("departamento pequeno: o limite nunca é zero (senão ninguém tiraria férias)")
        void departamentoPequeno() {
            engenharia = new Departamento("Jurídico", 2, 70);      // 2 x 30% = 0,6 → arredonda para 1
            doDepartamento = List.of();

            assertThat(daRegra(validar(), "EMP-01").resultado()).isEqualTo(Resultado.OK);
        }
    }

    @Test
    @DisplayName("EMP-02: lista os colegas que estarão fora no período, marcando os pendentes")
    void colegasNoPeriodo() {
        var colegas = validar().colegasNoPeriodo();

        assertThat(colegas).extracting(Ausencia::nome).containsExactly("Marina Jorge", "Thiago Kang", "Lucas Prado");
        assertThat(colegas).filteredOn(Ausencia::pendente).extracting(Ausencia::nome).containsExactly("Lucas Prado");
    }

    @Nested
    @DisplayName("EMP-04: limite da empresa toda")
    class LimiteDaEmpresa {

        @Test
        @DisplayName("acima do limite da empresa: ponto de atenção")
        void acima() {
            limiteDaEmpresa = 3;

            assertThat(daRegra(validar(), "EMP-04").resultado()).isEqualTo(Resultado.ATENCAO);
        }

        @Test
        @DisplayName("dentro do limite: OK")
        void dentro() {
            limiteDaEmpresa = 10;

            assertThat(daRegra(validar(), "EMP-04").resultado()).isEqualTo(Resultado.OK);
        }

        @Test
        @DisplayName("regra desligada: nem aparece")
        void desligada() {
            assertThat(temRegra(validar(), "EMP-04")).isFalse();
        }
    }

    @Nested
    @DisplayName("EMP-05: períodos restritos")
    class PeriodosRestritos {

        @Test
        @DisplayName("restrição do tipo BLOQUEIO no período: impedimento")
        void bloqueio() {
            restricoes = List.of(new Restricao("Fechamento do trimestre", true, marco(30), LocalDate.of(2026, 4, 3)));

            var verificacao = daRegra(validar(), "EMP-05");
            assertThat(verificacao.resultado()).isEqualTo(Resultado.IMPEDIMENTO);
            assertThat(verificacao.mensagem()).contains("Fechamento do trimestre");
        }

        @Test
        @DisplayName("restrição do tipo ALERTA: ponto de atenção")
        void alerta() {
            restricoes = List.of(new Restricao("Alta temporada", false, marco(1), marco(20)));

            assertThat(daRegra(validar(), "EMP-05").resultado()).isEqualTo(Resultado.ATENCAO);
        }

        @Test
        @DisplayName("restrição fora das datas do pedido: não aparece")
        void foraDoPeriodo() {
            restricoes = List.of(new Restricao("Fechamento", true, LocalDate.of(2026, 2, 25), marco(5)));

            assertThat(temRegra(validar(), "EMP-05")).isFalse();
        }
    }
}
