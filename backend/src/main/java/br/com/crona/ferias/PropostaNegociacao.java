package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Uma rodada da negociação: a proposta inicial ou uma contraproposta.
 *
 * Não estende a EntidadeBase porque uma proposta NUNCA muda depois de feita (a tabela nem tem
 * atualizado_em). Quem quer outra data faz uma proposta nova, e o histórico da conversa fica todo
 * guardado. A vigente é sempre a mais recente.
 */
@Entity
@Table(name = "proposta_negociacao")
public class PropostaNegociacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negociacao_id", nullable = false, updatable = false)
    private Negociacao negociacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false, updatable = false)
    private Colaborador autor;

    @Column(name = "inicio_solicitante", nullable = false, updatable = false)
    private LocalDate inicioSolicitante;

    @Column(name = "fim_solicitante", nullable = false, updatable = false)
    private LocalDate fimSolicitante;

    @Column(name = "inicio_colega", nullable = false, updatable = false)
    private LocalDate inicioColega;

    @Column(name = "fim_colega", nullable = false, updatable = false)
    private LocalDate fimColega;

    @Column(name = "mensagem", length = 500, updatable = false)
    private String mensagem;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected PropostaNegociacao() {
    }

    PropostaNegociacao(Negociacao negociacao, Colaborador autor, LocalDate inicioSolicitante, int diasSolicitante,
                       LocalDate inicioColega, int diasColega, String mensagem) {
        this.negociacao = negociacao;
        this.autor = autor;
        this.inicioSolicitante = inicioSolicitante;
        this.fimSolicitante = inicioSolicitante.plusDays(diasSolicitante - 1L);
        this.inicioColega = inicioColega;
        this.fimColega = inicioColega.plusDays(diasColega - 1L);
        this.mensagem = mensagem == null || mensagem.isBlank() ? null : mensagem.trim();
    }

    /** As datas são as mesmas desta proposta? (Contrapropor a mesma coisa é, na verdade, aceitar.) */
    boolean mesmasDatas(LocalDate outroInicioSolicitante, LocalDate outroInicioColega) {
        return inicioSolicitante.equals(outroInicioSolicitante) && inicioColega.equals(outroInicioColega);
    }

    public Long getId() {
        return id;
    }

    public Colaborador getAutor() {
        return autor;
    }

    public LocalDate getInicioSolicitante() {
        return inicioSolicitante;
    }

    public LocalDate getFimSolicitante() {
        return fimSolicitante;
    }

    public LocalDate getInicioColega() {
        return inicioColega;
    }

    public LocalDate getFimColega() {
        return fimColega;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
