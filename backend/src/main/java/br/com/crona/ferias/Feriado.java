package br.com.crona.ferias;

import br.com.crona.compartilhado.EntidadeBase;
import br.com.crona.empresa.Empresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Feriado usado na CLT-05. Empresa nula = feriado nacional. */
@Entity
@Table(name = "feriado")
public class Feriado extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "descricao", nullable = false, length = 100)
    private String descricao;

    protected Feriado() {
    }

    public LocalDate getData() {
        return data;
    }

    public String getDescricao() {
        return descricao;
    }
}
