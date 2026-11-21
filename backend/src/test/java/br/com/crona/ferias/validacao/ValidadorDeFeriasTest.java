package br.com.crona.ferias.validacao;

import br.com.crona.ferias.SaldoDoPeriodo.Situacao;
import br.com.crona.ferias.validacao.DadosParaValidar.Pedido;
import br.com.crona.ferias.validacao.DadosParaValidar.Periodo;
import br.com.crona.ferias.validacao.Verificacao.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD do validador. Cenário padrão: hoje é terça, 10/02/2026; a Ana pede 15 dias a partir
 * de segunda, 23/03/2026, do período 2 (completo, 30 dias livres, tirar até 31/01/2027).
 * Cada teste muda SÓ o que interessa a ele (o resto continua no padrão).
 */
class ValidadorDeFeriasTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 2, 10);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 3, 23);

    // ---- montador do cenário: começa no padrão e cada método troca uma coisa ----

    private Pedido pedido = new Pedido(SEGUNDA, 15, 0);
    private Periodo periodo = new Periodo(2, LocalDate.of(2026, 1, 31), LocalDate.of(2027, 1, 31), 30, 30, Situacao.DISPONIVEL);
    private List<Integer> partesJaPedidas = List.of();
    private int abonoJaPedido = 0;
    private Set<LocalDate> feriados = Set.of();
    private int antecedenciaMinima = 30;

    private ResultadoDaValidacao validar() {
        return ValidadorDeFerias.validar(new DadosParaValidar(pedido, HOJE, periodo, partesJaPedidas,
                abonoJaPedido, feriados, antecedenciaMinima));
    }

    private Periodo periodoCom(int direito, int livres) {
        return new Periodo(2, periodo.fim(), periodo.limiteConcessao(), direito, livres, Situacao.DISPONIVEL);
    }

    // ------------------------------------------------------------------ o caminho feliz

    @Test
    @DisplayName("pedido ideal: tudo OK, pode enviar, e as datas vêm calculadas")
    void pedidoIdeal() {
        var resultado = validar();

        assertThat(resultado.podeEnviar()).isTrue();
        assertThat(resultado.com(Resultado.ATENCAO)).isEmpty();
        assertThat(resultado.com(Resultado.IMPEDIMENTO)).isEmpty();
        assertThat(resultado.fim()).isEqualTo(LocalDate.of(2026, 4, 6));             // 23/03 + 15 dias - 1
        assertThat(resultado.voltaAoTrabalho()).isEqualTo(LocalDate.of(2026, 4, 7));
    }

    @Test
    @DisplayName("volta ao trabalho pula fim de semana: férias acabando na sexta, volta na segunda")
    void voltaPulaFimDeSemana() {
        pedido = new Pedido(SEGUNDA, 5, 0);       // 23/03 a 27/03 (sexta)

        assertThat(validar().voltaAoTrabalho()).isEqualTo(LocalDate.of(2026, 3, 30));
    }

    // ------------------------------------------------------------------ saldo e período

    @Nested
    @DisplayName("SALDO")
    class Saldo {

        @Test
        @DisplayName("pedir mais do que os dias livres: impedimento")
        void passaDoSaldo() {
            periodo = periodoCom(30, 10);

            var resultado = validar();
            assertThat(resultado.daRegra("SALDO").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
            assertThat(resultado.daRegra("SALDO").mensagem()).contains("5 dias");
            assertThat(resultado.podeEnviar()).isFalse();
        }

        @Test
        @DisplayName("o abono também sai do saldo: 20 + 10 cabem em 30, 25 + 10 não")
        void abonoContaNoSaldo() {
            pedido = new Pedido(SEGUNDA, 20, 10);
            assertThat(validar().daRegra("SALDO").resultado()).isEqualTo(Resultado.OK);

            pedido = new Pedido(SEGUNDA, 25, 10);
            assertThat(validar().daRegra("SALDO").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        }
    }

    @Test
    @DisplayName("CLT-01: período ainda em aquisição não pode ser usado")
    void periodoEmAquisicao() {
        periodo = new Periodo(3, LocalDate.of(2027, 1, 31), LocalDate.of(2028, 1, 31), 30, 30, Situacao.EM_AQUISICAO);

        assertThat(validar().daRegra("CLT-01").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
    }

    @Test
    @DisplayName("CLT-03: terminar depois do prazo de concessão é impedimento (pagamento em dobro)")
    void depoisDoPrazo() {
        periodo = new Periodo(2, LocalDate.of(2025, 3, 31), LocalDate.of(2026, 4, 1), 30, 30, Situacao.A_VENCER);

        var verificacao = validar().daRegra("CLT-03");     // as férias iriam até 06/04
        assertThat(verificacao.resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        assertThat(verificacao.mensagem()).contains("01/04/2026");
    }

    // ------------------------------------------------------------------ CLT-04: divisão

    @Nested
    @DisplayName("CLT-04: divisão em até 3 partes, uma com 14+ e as outras com 5+")
    class Divisao {

        @Test
        @DisplayName("menos de 5 dias: impedimento")
        void menosDeCinco() {
            pedido = new Pedido(SEGUNDA, 4, 0);

            assertThat(validar().daRegra("CLT-04").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        }

        @Test
        @DisplayName("uma 4ª parte no mesmo período: impedimento")
        void quartaParte() {
            partesJaPedidas = List.of(14, 5, 5);
            periodo = periodoCom(30, 6);
            pedido = new Pedido(SEGUNDA, 5, 0);

            assertThat(validar().daRegra("CLT-04").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        }

        @Test
        @DisplayName("10 dias sem parte de 14 ainda, mas sobram 20: pode (a parte de 14 fica para depois)")
        void parteMenorAindaDaTempo() {
            pedido = new Pedido(SEGUNDA, 10, 0);

            var verificacao = validar().daRegra("CLT-04");
            assertThat(verificacao.resultado()).isEqualTo(Resultado.OK);
            assertThat(verificacao.mensagem()).contains("14");
        }

        @Test
        @DisplayName("10 + 10 e sobrariam só 10: nenhuma parte de 14 seria possível, impedimento")
        void semEspacoParaOs14() {
            partesJaPedidas = List.of(10);
            periodo = periodoCom(30, 20);
            pedido = new Pedido(SEGUNDA, 10, 0);

            assertThat(validar().daRegra("CLT-04").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        }

        @Test
        @DisplayName("já existe uma parte de 14: uma de 5 é aceita")
        void jaTemOs14() {
            partesJaPedidas = List.of(14);
            periodo = periodoCom(30, 16);
            pedido = new Pedido(SEGUNDA, 5, 0);

            assertThat(validar().daRegra("CLT-04").resultado()).isEqualTo(Resultado.OK);
        }

        @Test
        @DisplayName("sobrariam de 1 a 4 dias, que não formam uma parte: ponto de atenção")
        void sobraQueNaoFormaParte() {
            pedido = new Pedido(SEGUNDA, 27, 0);       // sobram 3

            assertThat(validar().daRegra("CLT-04").resultado()).isEqualTo(Resultado.ATENCAO);
        }
    }

    // ------------------------------------------------------------------ CLT-05: dia de início

    @Nested
    @DisplayName("CLT-05: não começar nos 2 dias antes de folga ou feriado")
    class DiaDeInicio {

        @ParameterizedTest(name = "{0} ({1}) → {2}")
        @CsvSource({
                "2026-03-23, segunda, OK",
                "2026-03-24, terça,   OK",
                "2026-03-25, quarta,  OK",
                "2026-03-26, quinta,  IMPEDIMENTO",
                "2026-03-27, sexta,   IMPEDIMENTO",
                "2026-03-28, sábado,  IMPEDIMENTO",
                "2026-03-29, domingo, IMPEDIMENTO"
        })
        void diaDaSemana(LocalDate inicio, String dia, Resultado esperado) {
            pedido = new Pedido(inicio, 15, 0);

            assertThat(validar().daRegra("CLT-05").resultado()).isEqualTo(esperado);
        }

        @Test
        @DisplayName("segunda antes do feriado de terça (Tiradentes): impedimento")
        void vesperaDeFeriado() {
            feriados = Set.of(LocalDate.of(2026, 4, 21));
            pedido = new Pedido(LocalDate.of(2026, 4, 20), 15, 0);

            var verificacao = validar().daRegra("CLT-05");
            assertThat(verificacao.resultado()).isEqualTo(Resultado.IMPEDIMENTO);
            assertThat(verificacao.mensagem()).contains("feriado");
        }

        @Test
        @DisplayName("começar no próprio feriado: impedimento")
        void noFeriado() {
            feriados = Set.of(LocalDate.of(2026, 4, 21));
            pedido = new Pedido(LocalDate.of(2026, 4, 21), 15, 0);

            assertThat(validar().daRegra("CLT-05").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        }
    }

    // ------------------------------------------------------------------ CLT-06: abono

    @Nested
    @DisplayName("CLT-06: abono pecuniário")
    class Abono {

        @Test
        @DisplayName("sem abono: a regra nem aparece no checklist")
        void semAbono() {
            assertThat(validar().verificacoes()).noneMatch(v -> v.regra().equals("CLT-06"));
        }

        @Test
        @DisplayName("mais de 1/3 do direito: impedimento (30 → máximo 10; 24 → máximo 8)")
        void acimaDeUmTerco() {
            pedido = new Pedido(SEGUNDA, 15, 11);
            assertThat(validar().daRegra("CLT-06").resultado()).isEqualTo(Resultado.IMPEDIMENTO);

            periodo = periodoCom(24, 24);
            pedido = new Pedido(SEGUNDA, 15, 9);
            assertThat(validar().daRegra("CLT-06").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        }

        @Test
        @DisplayName("abono já pedido antes conta no limite de 1/3")
        void abonoAcumulado() {
            abonoJaPedido = 5;
            pedido = new Pedido(SEGUNDA, 15, 6);

            assertThat(validar().daRegra("CLT-06").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        }

        @Test
        @DisplayName("abono dentro do limite, pedido depois do prazo legal: ponto de atenção (depende da empresa)")
        void foraDoPrazo() {
            pedido = new Pedido(SEGUNDA, 20, 10);

            var resultado = validar();
            assertThat(resultado.daRegra("CLT-06").resultado()).isEqualTo(Resultado.ATENCAO);
            assertThat(resultado.podeEnviar()).isTrue();
        }
    }

    // ------------------------------------------------------------------ CLT-07 / EMP-03: antecedência

    @Nested
    @DisplayName("CLT-07 / EMP-03: antecedência")
    class Antecedencia {

        @Test
        @DisplayName("menos de 30 dias: ponto de atenção, mas pode enviar")
        void menosDe30() {
            pedido = new Pedido(LocalDate.of(2026, 3, 2), 15, 0);      // 20 dias depois de hoje

            var resultado = validar();
            assertThat(resultado.daRegra("CLT-07").resultado()).isEqualTo(Resultado.ATENCAO);
            assertThat(resultado.podeEnviar()).isTrue();
        }

        @Test
        @DisplayName("a empresa exige 45 dias: 41 dias de antecedência vira ponto de atenção")
        void antecedenciaDaEmpresa() {
            antecedenciaMinima = 45;

            var verificacao = validar().daRegra("CLT-07");
            assertThat(verificacao.resultado()).isEqualTo(Resultado.ATENCAO);
            assertThat(verificacao.mensagem()).contains("45");
        }

        @Test
        @DisplayName("começar hoje ou no passado: impedimento")
        void noPassado() {
            pedido = new Pedido(HOJE, 15, 0);

            assertThat(validar().daRegra("CLT-07").resultado()).isEqualTo(Resultado.IMPEDIMENTO);
        }
    }
}
