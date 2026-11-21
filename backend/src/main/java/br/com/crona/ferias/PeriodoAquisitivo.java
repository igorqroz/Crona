package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.compartilhado.EntidadeBase;
import br.com.crona.ferias.CalendarioAquisitivo.PeriodoCalculado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Um período aquisitivo guardado no banco. As DATAS vêm do CalendarioAquisitivo; o que
 * só existe aqui são as FALTAS e o direito que elas geram (ver modelo-de-dominio.md, 3.5).
 */
@Entity
@Table(name = "periodo_aquisitivo")
public class PeriodoAquisitivo extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false, updatable = false)
    private Colaborador colaborador;

    @Column(name = "numero", nullable = false, updatable = false)
    private int numero;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "data_limite_concessao", nullable = false)
    private LocalDate dataLimiteConcessao;

    @Column(name = "faltas_injustificadas", nullable = false)
    private int faltasInjustificadas;

    @Column(name = "dias_direito", nullable = false)
    private int diasDireito = 30;

    protected PeriodoAquisitivo() {
    }

    PeriodoAquisitivo(Colaborador colaborador, PeriodoCalculado calculado) {
        this.colaborador = colaborador;
        this.numero = calculado.numero();
        ajustarDatas(calculado);
    }

    /** Quando a data de admissão é corrigida, as datas do período mudam junto. */
    void ajustarDatas(PeriodoCalculado calculado) {
        this.dataInicio = calculado.inicio();
        this.dataFim = calculado.fim();
        this.dataLimiteConcessao = calculado.limiteConcessao();
    }

    /** CLT-02: o RH lança as faltas, e o direito é recalculado na hora. */
    void registrarFaltas(int faltas) {
        this.diasDireito = CalendarioAquisitivo.diasDeDireito(faltas);   // valida faltas negativas
        this.faltasInjustificadas = faltas;
    }

    public Colaborador getColaborador() {
        return colaborador;
    }

    public int getNumero() {
        return numero;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public LocalDate getDataLimiteConcessao() {
        return dataLimiteConcessao;
    }

    public int getFaltasInjustificadas() {
        return faltasInjustificadas;
    }

    public int getDiasDireito() {
        return diasDireito;
    }
}
