package br.com.crona.departamento;

import br.com.crona.compartilhado.EntidadeBase;
import br.com.crona.empresa.Empresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Setor da empresa. Usado no limite de pessoas de férias por setor (EMP-01).
 */
@Entity
@Table(name = "departamento")
public class Departamento extends EntidadeBase {

    // Muitos departamentos para uma empresa (N:1).
    // LAZY: a empresa só é buscada no banco se alguém realmente usar getEmpresa().
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    protected Departamento() {
    }

    public Departamento(Empresa empresa, String nome) {
        this.empresa = Objects.requireNonNull(empresa, "empresa é obrigatória");
        this.nome = nome;
    }

    /**
     * Em vez de um setNome() genérico, um método com nome do negócio.
     * A empresa não tem método para mudar: um departamento nunca troca de empresa.
     */
    public void renomear(String novoNome) {
        this.nome = novoNome;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public String getNome() {
        return nome;
    }
}
