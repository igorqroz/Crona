package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.compartilhado.EntidadeBase;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Negociação de troca de férias entre dois colegas da mesma equipe.
 *
 *   AGUARDANDO_COLEGA ◀──contrapropor──▶ AGUARDANDO_SOLICITANTE      (sem limite de rodadas)
 *          │                                      │
 *          └──────────aceitar────┬────aceitar─────┘
 *                                ▼
 *                       AGUARDANDO_GESTOR ──aprovar──▶ APROVADA
 *                                └──────recusar──────▶ RECUSADA
 *
 *   - "recusar" também vale para quem está na vez de responder (o colega ou quem propôs);
 *   - "cancelar": só quem propôs, a qualquer momento antes de o gestor decidir.
 *
 * Quem "está na vez" é quem precisa responder à proposta vigente: ninguém aceita a própria proposta.
 *
 * Regras decididas na Fase 6: só férias APROVADAS entram numa troca, cada um mantém os seus dias
 * (troca de datas, não de saldo) e os dois precisam ter o mesmo gestor, que é quem aprova.
 * As regras de FÉRIAS (CLT e da empresa) das datas novas são conferidas no service, com a mesma
 * avaliação da simulação.
 */
@Entity
@Table(name = "negociacao")
public class Negociacao extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false, updatable = false)
    private Colaborador solicitante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colega_id", nullable = false, updatable = false)
    private Colaborador colega;

    /** As férias do colega que quem propôs quer. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitacao_colega_id", nullable = false, updatable = false)
    private SolicitacaoFerias pedidoDoColega;

    /**
     * As férias que quem propôs oferece em troca. Nulo se ele ainda não tinha férias marcadas:
     * nesse caso, o pedido dele nasce quando o gestor aprovar a troca.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_solicitante_id")
    private SolicitacaoFerias pedidoDoSolicitante;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusNegociacao status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por_id")
    private Colaborador decididoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @Column(name = "mensagem_decisao", length = 500)
    private String mensagemDecisao;

    /*
     * Um-para-muitos: uma negociação tem várias propostas (as rodadas).
     * mappedBy = quem tem a chave estrangeira é a PropostaNegociacao (coluna negociacao_id).
     * cascade ALL = ao salvar a negociação, as propostas novas são salvas junto.
     */
    @OneToMany(mappedBy = "negociacao", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<PropostaNegociacao> propostas = new ArrayList<>();

    protected Negociacao() {
    }

    /**
     * A proposta inicial: "eu fico com as suas férias, e você fica com estas datas".
     *
     * @param pedidoDoSolicitante as minhas férias aprovadas que entram na troca, ou nulo se não tenho
     * @param diasSolicitante     só para quem não tem férias marcadas: quantos dias quer
     */
    public Negociacao(Colaborador solicitante, SolicitacaoFerias pedidoDoColega, SolicitacaoFerias pedidoDoSolicitante,
                      LocalDate inicioSolicitante, Integer diasSolicitante, LocalDate inicioColega, String mensagem) {
        this.colega = pedidoDoColega.getColaborador();
        if (mesmaPessoa(solicitante, colega)) {
            throw new RegraVioladaException("Não dá para trocar férias com você mesmo.");
        }
        if (pedidoDoColega.getStatus() != StatusSolicitacao.APROVADA) {
            throw new RegraVioladaException("Só dá para propor troca de férias já aprovadas.");
        }
        if (solicitante.getGestor() == null || colega.getGestor() == null
                || !mesmaPessoa(solicitante.getGestor(), colega.getGestor())) {
            throw new RegraVioladaException("Só dá para trocar férias com colegas da mesma equipe (com o mesmo gestor).");
        }
        if (pedidoDoSolicitante != null) {
            if (!mesmaPessoa(pedidoDoSolicitante.getColaborador(), solicitante)
                    || pedidoDoSolicitante.getStatus() != StatusSolicitacao.APROVADA) {
                throw new RegraVioladaException("As férias oferecidas na troca precisam ser suas e estar aprovadas.");
            }
            if (diasSolicitante != null && diasSolicitante != pedidoDoSolicitante.getDiasGozo()) {
                throw new RegraVioladaException("Na troca, cada um mantém os dias do próprio pedido: as suas férias têm "
                        + pedidoDoSolicitante.getDiasGozo() + " dias.");
            }
        } else if (diasSolicitante == null || diasSolicitante < 1) {
            throw new RegraVioladaException("Você ainda não tem férias marcadas: informe quantos dias quer.");
        }
        if (inicioColega.equals(pedidoDoColega.getDataInicio())) {
            throw new RegraVioladaException("A proposta precisa mudar as férias do colega. "
                    + "Se as datas dele continuam as mesmas, não é uma troca: faça um pedido normal.");
        }

        this.solicitante = solicitante;
        this.pedidoDoColega = pedidoDoColega;
        this.pedidoDoSolicitante = pedidoDoSolicitante;
        this.status = StatusNegociacao.AGUARDANDO_COLEGA;
        int dias = pedidoDoSolicitante != null ? pedidoDoSolicitante.getDiasGozo() : diasSolicitante;
        propostas.add(new PropostaNegociacao(this, solicitante, inicioSolicitante, dias,
                inicioColega, pedidoDoColega.getDiasGozo(), mensagem));
    }

    // ------------------------------------------------------------------ entre os dois colegas

    /** Quem está na vez responde com outras datas. Os dias de cada um continuam os mesmos. */
    public void contrapropor(Colaborador quem, LocalDate inicioSolicitante, LocalDate inicioColega, String mensagem) {
        exigirAVezDe(quem);
        if (propostaVigente().mesmasDatas(inicioSolicitante, inicioColega)) {
            throw new RegraVioladaException("Essas são as mesmas datas da proposta atual. Para concordar, use aceitar.");
        }
        propostas.add(new PropostaNegociacao(this, quem, inicioSolicitante, diasDoSolicitante(),
                inicioColega, pedidoDoColega.getDiasGozo(), mensagem));
        this.status = mesmaPessoa(quem, colega)
                ? StatusNegociacao.AGUARDANDO_SOLICITANTE
                : StatusNegociacao.AGUARDANDO_COLEGA;
    }

    /** Quem está na vez concorda com a proposta vigente: a troca vai para o gestor. */
    public void aceitar(Colaborador quem) {
        exigirAVezDe(quem);
        this.status = StatusNegociacao.AGUARDANDO_GESTOR;
    }

    /** Quem está na vez não quer a troca. O recado é opcional. */
    public void recusar(Colaborador quem, String mensagem, Instant agora) {
        exigirAVezDe(quem);
        encerrar(StatusNegociacao.RECUSADA, quem, mensagem, agora);
    }

    /** Quem propôs desiste, a qualquer momento antes de o gestor decidir. */
    public void cancelar(Colaborador quem) {
        exigirEmAberto();
        if (!mesmaPessoa(quem, solicitante)) {
            throw new RegraVioladaException("Só quem propôs a troca pode cancelá-la. Para não aceitar, use recusar.");
        }
        this.status = StatusNegociacao.CANCELADA;
    }

    // ------------------------------------------------------------------ gestor

    public void aprovarPeloGestor(Colaborador gestor, String mensagem, Instant agora) {
        exigirDecisaoDoGestor(gestor);
        encerrar(StatusNegociacao.APROVADA, gestor, mensagem, agora);
    }

    public void recusarPeloGestor(Colaborador gestor, String mensagem, Instant agora) {
        exigirDecisaoDoGestor(gestor);
        if (textoOuNulo(mensagem) == null) {
            throw new RegraVioladaException("Informe o motivo da recusa: os dois colegas precisam saber por quê.");
        }
        encerrar(StatusNegociacao.RECUSADA, gestor, mensagem, agora);
    }

    // ------------------------------------------------------------------ consultas

    /** Quem precisa responder agora, ou nulo se não é a vez de nenhum dos dois. */
    public Colaborador vezDe() {
        return switch (status) {
            case AGUARDANDO_COLEGA -> colega;
            case AGUARDANDO_SOLICITANTE -> solicitante;
            default -> null;
        };
    }

    /** Quantos dias de férias quem propôs vai tirar (os do pedido dele, ou os que ele informou). */
    public int diasDoSolicitante() {
        if (pedidoDoSolicitante != null) {
            return pedidoDoSolicitante.getDiasGozo();
        }
        var primeira = propostas.getFirst();
        return (int) (primeira.getFimSolicitante().toEpochDay() - primeira.getInicioSolicitante().toEpochDay()) + 1;
    }

    /** O colaborador participa desta negociação (é quem propôs ou o colega)? */
    public boolean participa(Colaborador quem) {
        return mesmaPessoa(quem, solicitante) || mesmaPessoa(quem, colega);
    }

    /**
     * Quando quem propôs não tinha férias marcadas, o pedido dele nasce na aprovação da troca
     * e fica ligado aqui (para o histórico mostrar de onde ele veio).
     */
    void vincularPedidoNovoDoSolicitante(SolicitacaoFerias pedido) {
        if (pedidoDoSolicitante != null) {
            throw new IllegalStateException("A negociação já tem o pedido de quem propôs.");
        }
        this.pedidoDoSolicitante = pedido;
    }

    // ------------------------------------------------------------------ auxiliares

    private void exigirAVezDe(Colaborador quem) {
        exigirEmAberto();
        var daVez = vezDe();
        if (daVez == null || !mesmaPessoa(quem, daVez)) {
            throw new RegraVioladaException(daVez == null
                    ? "Os dois já concordaram: agora a troca está com o gestor."
                    : "Não é a sua vez de responder: a negociação está esperando " + daVez.getNome() + ".");
        }
    }

    private void exigirEmAberto() {
        if (!status.emAberto()) {
            throw new RegraVioladaException("Esta negociação está " + status + " e não pode mais mudar.");
        }
    }

    private void exigirDecisaoDoGestor(Colaborador gestor) {
        if (status != StatusNegociacao.AGUARDANDO_GESTOR) {
            throw new RegraVioladaException("Esta negociação está " + status
                    + ": o gestor só decide depois que os dois colegas concordam.");
        }
        if (participa(gestor)) {
            throw new RegraVioladaException("Quem participa da troca não pode aprová-la nem recusá-la.");
        }
    }

    private void encerrar(StatusNegociacao novoStatus, Colaborador quem, String mensagem, Instant agora) {
        this.status = novoStatus;
        this.decididoPor = quem;
        this.decididoEm = agora;
        this.mensagemDecisao = textoOuNulo(mensagem);
    }

    /** Compara pelo id (dois objetos Java diferentes podem ser a mesma linha do banco). */
    private static boolean mesmaPessoa(Colaborador a, Colaborador b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    // ------------------------------------------------------------------ leitura

    public PropostaNegociacao propostaVigente() {
        return propostas.getLast();
    }

    public Colaborador getSolicitante() {
        return solicitante;
    }

    public Colaborador getColega() {
        return colega;
    }

    public SolicitacaoFerias getPedidoDoColega() {
        return pedidoDoColega;
    }

    public SolicitacaoFerias getPedidoDoSolicitante() {
        return pedidoDoSolicitante;
    }

    public StatusNegociacao getStatus() {
        return status;
    }

    public Colaborador getDecididoPor() {
        return decididoPor;
    }

    public Instant getDecididoEm() {
        return decididoEm;
    }

    public String getMensagemDecisao() {
        return mensagemDecisao;
    }

    public List<PropostaNegociacao> getPropostas() {
        return Collections.unmodifiableList(propostas);
    }
}
