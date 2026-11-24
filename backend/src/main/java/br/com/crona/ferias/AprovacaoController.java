package br.com.crona.ferias;

import br.com.crona.ferias.AprovacaoService.Filtro;
import br.com.crona.ferias.aprovacao.CancelamentoRequisicao;
import br.com.crona.ferias.aprovacao.DecisaoRequisicao;
import br.com.crona.ferias.aprovacao.FeriasAprovada;
import br.com.crona.ferias.aprovacao.ItemDeAprovacao;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestor (SOLICITACAO_APROVAR):
 *   GET  /aprovacoes?filtro=TODOS|COM_CONFLITO|SEM_CONFLITO     a fila da equipe
 *   GET  /aprovacoes/{id}                                       o checklist completo de um pedido
 *   POST /aprovacoes/{id}/aprovar   {"mesmoAssim", "mensagem"}  aprovar (ou aprovar mesmo assim)
 *   POST /aprovacoes/{id}/recusar   {"mensagem"}                recusar, sempre com motivo
 *   POST /aprovacoes/aprovar-sem-conflito                       aprova de uma vez os que não têm alerta
 *
 * RH (SOLICITACAO_CONFIRMAR_RH), quando a EMP-06 estiver ligada:
 *   GET  /confirmacoes-rh
 *   POST /confirmacoes-rh/{id}/confirmar
 *   POST /confirmacoes-rh/{id}/recusar   {"mensagem"}
 *
 * RH (SOLICITACAO_CANCELAR_APROVADA), para corrigir um erro humano:
 *   GET  /ferias-aprovadas                               aprovadas que ainda não começaram
 *   POST /ferias-aprovadas/{id}/cancelar   {"motivo"}    cancela (até a véspera), sempre com motivo
 */
@RestController
public class AprovacaoController {

    private final AprovacaoService service;
    private final CancelamentoService cancelamento;

    public AprovacaoController(AprovacaoService service, CancelamentoService cancelamento) {
        this.cancelamento = cancelamento;
        this.service = service;
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @GetMapping("/aprovacoes")
    public List<ItemDeAprovacao> fila(@RequestParam(defaultValue = "TODOS") Filtro filtro) {
        return service.fila(filtro);
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @GetMapping("/aprovacoes/{id}")
    public ItemDeAprovacao.Detalhe detalhe(@PathVariable Long id) {
        return service.detalhe(id);
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @PostMapping("/aprovacoes/{id}/aprovar")
    public SolicitacaoResposta aprovar(@PathVariable Long id,
                                       @Valid @RequestBody(required = false) DecisaoRequisicao decisao) {
        return service.aprovar(id, decisao == null ? new DecisaoRequisicao(false, null) : decisao);
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @PostMapping("/aprovacoes/{id}/recusar")
    public SolicitacaoResposta recusar(@PathVariable Long id, @Valid @RequestBody DecisaoRequisicao decisao) {
        return service.recusar(id, decisao);
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @PostMapping("/aprovacoes/aprovar-sem-conflito")
    public List<SolicitacaoResposta> aprovarSemConflito() {
        return service.aprovarSemConflito();
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_CONFIRMAR_RH')")
    @GetMapping("/confirmacoes-rh")
    public List<ItemDeAprovacao> filaDoRh() {
        return service.filaDoRh();
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_CONFIRMAR_RH')")
    @PostMapping("/confirmacoes-rh/{id}/confirmar")
    public SolicitacaoResposta confirmar(@PathVariable Long id) {
        return service.confirmarNoRh(id);
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_CONFIRMAR_RH')")
    @PostMapping("/confirmacoes-rh/{id}/recusar")
    public SolicitacaoResposta recusarNoRh(@PathVariable Long id, @Valid @RequestBody DecisaoRequisicao decisao) {
        return service.recusarNoRh(id, decisao);
    }

    // ------------------------------------------------------------------ cancelar férias aprovadas

    @PreAuthorize("hasAuthority('SOLICITACAO_CANCELAR_APROVADA')")
    @GetMapping("/ferias-aprovadas")
    public List<FeriasAprovada> aprovadas() {
        return cancelamento.aprovadasQueAindaVaoComecar();
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_CANCELAR_APROVADA')")
    @PostMapping("/ferias-aprovadas/{id}/cancelar")
    public SolicitacaoResposta cancelarAprovada(@PathVariable Long id,
                                                @Valid @RequestBody CancelamentoRequisicao requisicao) {
        return cancelamento.cancelar(id, requisicao);
    }
}
