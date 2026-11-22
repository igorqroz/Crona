package br.com.crona.notificacao;

import br.com.crona.colaborador.Colaborador;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Uma notificação para uma pessoa: o texto já pronto e a tela que ela abre.
 *
 * O texto é montado na hora em que a notificação nasce ("Roberto Neves aprovou suas férias de
 * 11/01 a 25/01") e não muda depois: é o registro do que aconteceu naquele momento.
 */
@Entity
@Table(name = "notificacao")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinatario_id", nullable = false, updatable = false)
    private Colaborador destinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, updatable = false)
    private TipoDeNotificacao tipo;

    @Column(name = "mensagem", nullable = false, length = 400, updatable = false)
    private String mensagem;

    @Column(name = "link", length = 200, updatable = false)
    private String link;

    @Column(name = "chave_unica", length = 120, updatable = false)
    private String chaveUnica;

    @Column(name = "lida_em")
    private Instant lidaEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Notificacao() {
    }

    Notificacao(Colaborador destinatario, TipoDeNotificacao tipo, String mensagem, String link, String chaveUnica) {
        this.empresaId = destinatario.getEmpresa().getId();
        this.destinatario = destinatario;
        this.tipo = tipo;
        this.mensagem = mensagem.length() > 400 ? mensagem.substring(0, 397) + "..." : mensagem;
        this.link = link;
        this.chaveUnica = chaveUnica;
    }

    /** Marcar duas vezes não muda a data da primeira leitura. */
    void marcarComoLida(Instant agora) {
        if (lidaEm == null) {
            lidaEm = agora;
        }
    }

    public Long getId() {
        return id;
    }

    public Colaborador getDestinatario() {
        return destinatario;
    }

    public TipoDeNotificacao getTipo() {
        return tipo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getLink() {
        return link;
    }

    public Instant getLidaEm() {
        return lidaEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
