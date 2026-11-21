package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.compartilhado.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Pedido de férias. Por enquanto só com o necessário para calcular o SALDO.
 * O fluxo completo (enviar, aprovar, recusar, confirmar no RH) é a Fase 5.
 *
 * Colunas da tabela que ainda não estão aqui (recado, decisão do gestor...) não atrapalham:
 * o Hibernate só confere as colunas que estão mapeadas.
 */
@Entity
@Table(name = "solicitacao_ferias")
public class SolicitacaoFerias extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false, updatable = false)
    private Colaborador colaborador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "periodo_aquisitivo_id", nullable = false)
    private PeriodoAquisitivo periodoAquisitivo;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "dias_gozo", nullable = false)
    private int diasGozo;

    @Column(name = "dias_abono", nullable = false)
    private int diasAbono;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusSolicitacao status;

    protected SolicitacaoFerias() {
    }

    public PeriodoAquisitivo getPeriodoAquisitivo() {
        return periodoAquisitivo;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public int getDiasGozo() {
        return diasGozo;
    }

    public int getDiasAbono() {
        return diasAbono;
    }

    public StatusSolicitacao getStatus() {
        return status;
    }
}
