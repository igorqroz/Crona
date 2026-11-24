package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.compartilhado.EntidadeBase;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Pedido de férias e o seu ciclo de vida:
 *
 *   AGUARDANDO_GESTOR ──aprovar──▶ AGUARDANDO_RH ──confirmar──▶ APROVADA
 *          │                (se a EMP-06 estiver ligada;        ▲
 *          │                 senão vai direto para APROVADA) ───┘
 *          ├──recusar──▶ RECUSADA
 *          └──cancelar─▶ CANCELADA   (o colaborador desiste; também vale em AGUARDANDO_RH)
 *
 * Os campos não têm "setters": o status só muda pelos métodos de transição, que conferem se a
 * mudança é permitida. Assim é impossível, por exemplo, aprovar um pedido já recusado.
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

    @Column(name = "recado_colaborador", length = 500)
    private String recadoColaborador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestor_decidiu_por_id")
    private Colaborador gestorDecidiuPor;

    @Column(name = "gestor_decidiu_em")
    private Instant gestorDecidiuEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rh_confirmou_por_id")
    private Colaborador rhConfirmouPor;

    @Column(name = "rh_confirmou_em")
    private Instant rhConfirmouEm;

    @Column(name = "mensagem_decisao", length = 500)
    private String mensagemDecisao;

    /** Preenchidos só quando o RH cancela férias que já estavam aprovadas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelado_por_id")
    private Colaborador canceladoPor;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    @Column(name = "motivo_cancelamento", length = 500)
    private String motivoCancelamento;

    protected SolicitacaoFerias() {
    }

    /** Um pedido novo sempre começa esperando o gestor. */
    public SolicitacaoFerias(Colaborador colaborador, PeriodoAquisitivo periodo, LocalDate inicio, LocalDate fim,
                             int diasGozo, int diasAbono, String recado) {
        this.colaborador = colaborador;
        this.periodoAquisitivo = periodo;
        this.dataInicio = inicio;
        this.dataFim = fim;
        this.diasGozo = diasGozo;
        this.diasAbono = diasAbono;
        this.recadoColaborador = recado;
        this.status = StatusSolicitacao.AGUARDANDO_GESTOR;
    }

    // ------------------------------------------------------------------ transições

    /**
     * O gestor aprova. Com a EMP-06 ligada o pedido ainda passa pelo RH; senão já fica aprovado.
     * As REGRAS de férias (saldo, CLT, equipe) são conferidas antes, no service. Aqui só cuidamos
     * do que é do próprio pedido: de qual status ele pode sair e quem pode mexer nele.
     */
    public void aprovarPeloGestor(Colaborador gestor, String mensagem, boolean exigeConfirmacaoRh, Instant agora) {
        exigirStatus(StatusSolicitacao.AGUARDANDO_GESTOR, "aprovado");
        if (gestor.getId().equals(colaborador.getId())) {
            throw new RegraVioladaException("Ninguém pode aprovar o próprio pedido de férias.");
        }
        this.gestorDecidiuPor = gestor;
        this.gestorDecidiuEm = agora;
        this.mensagemDecisao = textoOuNulo(mensagem);
        this.status = exigeConfirmacaoRh ? StatusSolicitacao.AGUARDANDO_RH : StatusSolicitacao.APROVADA;
    }

    /** O gestor (ou o RH, se o pedido estiver com ele) recusa, sempre explicando o motivo. */
    public void recusar(Colaborador quemRecusou, String mensagem, Instant agora) {
        if (status != StatusSolicitacao.AGUARDANDO_GESTOR && status != StatusSolicitacao.AGUARDANDO_RH) {
            throw new RegraVioladaException("Este pedido está " + status + " e não pode mais ser recusado.");
        }
        if (textoOuNulo(mensagem) == null) {
            throw new RegraVioladaException("Informe o motivo da recusa: o colaborador precisa saber por quê.");
        }
        if (status == StatusSolicitacao.AGUARDANDO_GESTOR) {
            this.gestorDecidiuPor = quemRecusou;
            this.gestorDecidiuEm = agora;
        } else {
            this.rhConfirmouPor = quemRecusou;
            this.rhConfirmouEm = agora;
        }
        this.mensagemDecisao = mensagem.trim();
        this.status = StatusSolicitacao.RECUSADA;
    }

    /** EMP-06: o RH confirma um pedido que o gestor já aprovou. */
    public void confirmarNoRh(Colaborador rh, Instant agora) {
        exigirStatus(StatusSolicitacao.AGUARDANDO_RH, "confirmado pelo RH");
        this.rhConfirmouPor = rh;
        this.rhConfirmouEm = agora;
        this.status = StatusSolicitacao.APROVADA;
    }

    /**
     * O colaborador desiste enquanto o pedido ainda está em análise.
     * Depois de aprovado, só o RH cancela: ver {@link #cancelarAprovada}.
     */
    public void cancelar() {
        if (status != StatusSolicitacao.AGUARDANDO_GESTOR && status != StatusSolicitacao.AGUARDANDO_RH) {
            throw new RegraVioladaException("Só dá para cancelar um pedido que ainda está em análise. "
                    + "Este está " + status + ".");
        }
        this.status = StatusSolicitacao.CANCELADA;
    }

    /**
     * O RH cancela férias que JÁ estavam aprovadas (ex.: aprovadas por engano). Os dias voltam
     * para o saldo, porque CANCELADA não consome saldo, e o aviso de férias deixa de valer.
     *
     * Regras (decididas pelo Igor, ver "Cancelamento de férias aprovadas" em regras-de-negocio.md):
     * - só férias APROVADAS e que ainda não começaram: dá até a véspera do início;
     * - ninguém cancela as próprias férias por aqui (o mesmo princípio da aprovação);
     * - o motivo é obrigatório e fica registrado, junto com quem cancelou e quando.
     * Quem pode (o RH) e a troca em aberto são conferidos no service.
     */
    public void cancelarAprovada(Colaborador rh, String motivo, LocalDate hoje, Instant agora) {
        exigirStatus(StatusSolicitacao.APROVADA, "cancelado pelo RH");
        if (!hoje.isBefore(dataInicio)) {
            throw new RegraVioladaException("Estas férias começam em "
                    + dataInicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + ". Só dá para cancelar até a véspera do início.");
        }
        if (rh.getId().equals(colaborador.getId())) {
            throw new RegraVioladaException("Ninguém pode cancelar as próprias férias aprovadas.");
        }
        if (textoOuNulo(motivo) == null) {
            throw new RegraVioladaException("Informe o motivo do cancelamento: o colaborador precisa saber por quê.");
        }
        this.canceladoPor = rh;
        this.canceladoEm = agora;
        this.motivoCancelamento = motivo.trim();
        this.status = StatusSolicitacao.CANCELADA;
    }

    /**
     * Fase 6: o gestor aprovou uma troca, e estas férias mudam de data. Só datas: os dias
     * continuam os mesmos, então o saldo não muda e o pedido continua aprovado.
     */
    public void remarcarPorTroca(LocalDate novoInicio, LocalDate novoFim) {
        exigirStatus(StatusSolicitacao.APROVADA, "remarcado por troca");
        long dias = novoFim.toEpochDay() - novoInicio.toEpochDay() + 1;
        if (dias != diasGozo) {
            throw new RegraVioladaException("Na troca, as férias mantêm os " + diasGozo + " dias (vieram " + dias + ").");
        }
        this.dataInicio = novoInicio;
        this.dataFim = novoFim;
    }

    private void exigirStatus(StatusSolicitacao esperado, String acao) {
        if (status != esperado) {
            throw new RegraVioladaException("Este pedido está " + status + " e não pode ser " + acao + ".");
        }
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    // ------------------------------------------------------------------ leitura

    public Colaborador getColaborador() {
        return colaborador;
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

    public String getRecadoColaborador() {
        return recadoColaborador;
    }

    public Colaborador getGestorDecidiuPor() {
        return gestorDecidiuPor;
    }

    public Instant getGestorDecidiuEm() {
        return gestorDecidiuEm;
    }

    public Colaborador getRhConfirmouPor() {
        return rhConfirmouPor;
    }

    public Instant getRhConfirmouEm() {
        return rhConfirmouEm;
    }

    public String getMensagemDecisao() {
        return mensagemDecisao;
    }

    public Colaborador getCanceladoPor() {
        return canceladoPor;
    }

    public Instant getCanceladoEm() {
        return canceladoEm;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }
}
