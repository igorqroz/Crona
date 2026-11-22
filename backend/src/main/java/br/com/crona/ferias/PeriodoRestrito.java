package br.com.crona.ferias;

import br.com.crona.compartilhado.EntidadeBase;
import br.com.crona.departamento.Departamento;
import br.com.crona.empresa.Empresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** EMP-05: datas sensíveis da empresa ou de um departamento (ex.: fechamento do mês). */
@Entity
@Table(name = "periodo_restrito")
public class PeriodoRestrito extends EntidadeBase {

    public enum Tipo { ALERTA, BLOQUEIO }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;

    // Nulo = vale para a empresa toda.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departamento_id")
    private Departamento departamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private Tipo tipo;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;

    protected PeriodoRestrito() {
    }

    /** Nulo = vale para a empresa toda. */
    public Departamento getDepartamento() {
        return departamento;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public String getMotivo() {
        return motivo;
    }
}
