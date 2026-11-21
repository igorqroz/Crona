package br.com.crona.ferias;

import br.com.crona.ferias.SaldoDoPeriodo.Consumo;
import br.com.crona.ferias.SaldoDoPeriodo.Situacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static br.com.crona.ferias.StatusSolicitacao.AGUARDANDO_GESTOR;
import static br.com.crona.ferias.StatusSolicitacao.AGUARDANDO_RH;
import static br.com.crona.ferias.StatusSolicitacao.APROVADA;
import static br.com.crona.ferias.StatusSolicitacao.CANCELADA;
import static br.com.crona.ferias.StatusSolicitacao.RECUSADA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD do saldo. Período de referência: o 1º período da Ana no redesign,
 * de 01/02/2024 a 31/01/2025, com férias até 31/01/2026.
 */
class SaldoDoPeriodoTest {

    private static final LocalDate FIM = LocalDate.of(2025, 1, 31);
    private static final LocalDate LIMITE = LocalDate.of(2026, 1, 31);
    private static final LocalDate HOJE = LocalDate.of(2025, 6, 10);   // período completo, longe do limite

    private static SaldoDoPeriodo saldo(int direito, List<Consumo> consumos, LocalDate hoje) {
        return SaldoDoPeriodo.calcular(FIM, LIMITE, direito, consumos, hoje);
    }

    @Test
    @DisplayName("sem pedidos: os 30 dias estão livres")
    void semPedidos() {
        assertThat(saldo(30, List.of(), HOJE))
                .isEqualTo(new SaldoDoPeriodo(30, 0, 0, 30, Situacao.DISPONIVEL));
    }

    @Test
    @DisplayName("a tela da Ana: 15 em pedido, 15 livres para planejar")
    void exemploDoRedesign() {
        var resultado = saldo(30, List.of(new Consumo(15, AGUARDANDO_GESTOR)), HOJE);

        assertThat(resultado.emPedido()).isEqualTo(15);
        assertThat(resultado.aprovados()).isZero();
        assertThat(resultado.livres()).isEqualTo(15);
    }

    @Test
    @DisplayName("pedido esperando o RH também reserva os dias")
    void aguardandoRh() {
        assertThat(saldo(30, List.of(new Consumo(10, AGUARDANDO_RH)), HOJE).livres()).isEqualTo(20);
    }

    @Test
    @DisplayName("recusados e cancelados devolvem os dias")
    void recusadosECanceladosNaoContam() {
        var consumos = List.of(new Consumo(10, APROVADA), new Consumo(15, RECUSADA), new Consumo(5, CANCELADA));

        assertThat(saldo(30, consumos, HOJE))
                .isEqualTo(new SaldoDoPeriodo(30, 10, 0, 20, Situacao.DISPONIVEL));
    }

    @Test
    @DisplayName("20 de descanso + 10 de abono usam o período inteiro")
    void esgotado() {
        assertThat(saldo(30, List.of(new Consumo(30, APROVADA)), HOJE).situacao()).isEqualTo(Situacao.ESGOTADO);
    }

    @Test
    @DisplayName("faltas lançadas depois reduzem o direito abaixo do já usado: o saldo para em zero, nunca negativo")
    void direitoMenorQueOUsado() {
        var resultado = saldo(12, List.of(new Consumo(15, APROVADA)), HOJE);

        assertThat(resultado.livres()).isZero();
        assertThat(resultado.situacao()).isEqualTo(Situacao.ESGOTADO);
    }

    @Test
    @DisplayName("período ainda em aquisição: ainda não dá para tirar, mesmo com 30 dias")
    void emAquisicao() {
        assertThat(saldo(30, List.of(), FIM).situacao()).isEqualTo(Situacao.EM_AQUISICAO);
        assertThat(saldo(30, List.of(), FIM.plusDays(1)).situacao()).isEqualTo(Situacao.DISPONIVEL);
    }

    @Test
    @DisplayName("faltando até 60 dias para o limite, com saldo: A_VENCER (o painel do gestor mostra)")
    void aVencer() {
        assertThat(saldo(30, List.of(), LIMITE.minusDays(60)).situacao()).isEqualTo(Situacao.A_VENCER);
        assertThat(saldo(30, List.of(), LIMITE.minusDays(61)).situacao()).isEqualTo(Situacao.DISPONIVEL);
        assertThat(saldo(30, List.of(), LIMITE).situacao()).isEqualTo(Situacao.A_VENCER);
    }

    @Test
    @DisplayName("passou do limite com saldo: VENCIDO (CLT-03, a empresa paga em dobro)")
    void vencido() {
        assertThat(saldo(30, List.of(), LIMITE.plusDays(1)).situacao()).isEqualTo(Situacao.VENCIDO);
    }

    @Test
    @DisplayName("passou do limite sem saldo: ESGOTADO, não vencido (tirou tudo a tempo)")
    void limitePassadoSemSaldo() {
        assertThat(saldo(30, List.of(new Consumo(30, APROVADA)), LIMITE.plusDays(1)).situacao())
                .isEqualTo(Situacao.ESGOTADO);
    }
}
