package br.com.crona.colaborador;

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
import java.util.Objects;

/**
 * Quem usa o sistema: dados de trabalho + dados de login (ver modelo-de-dominio.md, 3.3).
 */
@Entity
@Table(name = "colaborador")
public class Colaborador extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    private Empresa empresa;

    // Opcional: um administrador do RH pode não estar em nenhum departamento.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departamento_id")
    private Departamento departamento;

    // AUTO-RELACIONAMENTO: o gestor é outro Colaborador da mesma tabela.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestor_id")
    private Colaborador gestor;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    // Só o hash BCrypt. Não tem getter público de propósito: nada fora deste pacote lê o hash.
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    // EnumType.STRING grava 'GESTOR'. O padrão (ORDINAL) gravaria 1, e é uma armadilha.
    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    private Perfil perfil;

    @Column(name = "cargo", length = 100)
    private String cargo;

    // LocalDate = só a data, sem hora (coluna DATE).
    @Column(name = "data_admissao", nullable = false)
    private LocalDate dataAdmissao;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected Colaborador() {
    }

    public Colaborador(Empresa empresa, String nome, String email, String senhaHash,
                       Perfil perfil, String cargo, LocalDate dataAdmissao) {
        this.empresa = Objects.requireNonNull(empresa, "empresa é obrigatória");
        this.senhaHash = Objects.requireNonNull(senhaHash, "senha é obrigatória");
        atualizarDados(nome, email, perfil, cargo, dataAdmissao);
    }

    public void atualizarDados(String nome, String email, Perfil perfil, String cargo, LocalDate dataAdmissao) {
        this.nome = nome;
        this.email = email;
        this.perfil = perfil;
        this.cargo = cargo;
        this.dataAdmissao = dataAdmissao;
    }

    public void alocar(Departamento departamento, Colaborador gestor) {
        this.departamento = departamento;
        this.gestor = gestor;
    }

    /** Desligar não apaga: o histórico de férias continua existindo (e é exigido por lei). */
    public void desativar() {
        this.ativo = false;
    }

    public void reativar() {
        this.ativo = true;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public Departamento getDepartamento() {
        return departamento;
    }

    public Colaborador getGestor() {
        return gestor;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public String getCargo() {
        return cargo;
    }

    public LocalDate getDataAdmissao() {
        return dataAdmissao;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
