package br.com.crona.ferias;

import br.com.crona.ferias.aprovacao.DecisaoRequisicao;
import br.com.crona.ferias.negociacao.ContrapropostaRequisicao;
import br.com.crona.ferias.negociacao.FeriasDoColega;
import br.com.crona.ferias.negociacao.NegociacaoResposta;
import br.com.crona.ferias.negociacao.PropostaDeTrocaRequisicao;
import br.com.crona.ferias.negociacao.TrocaParaAprovar;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * Colegas (basta estar logado; cada um só mexe nas negociações de que participa):
 *   GET  /minhas-ferias/equipe                              férias aprovadas da equipe que dá para propor trocar
 *   GET  /minhas-ferias/negociacoes                         as que propus e as que recebi
 *   POST /minhas-ferias/negociacoes                         propõe uma troca (201; 422 se as datas têm impedimento)
 *   POST /minhas-ferias/negociacoes/{id}/contrapropor       outras datas
 *   POST /minhas-ferias/negociacoes/{id}/aceitar            concorda: a troca vai para o gestor
 *   POST /minhas-ferias/negociacoes/{id}/recusar            não quer a troca (recado opcional)
 *   POST /minhas-ferias/negociacoes/{id}/cancelar           quem propôs desiste
 *
 * Gestor (SOLICITACAO_APROVAR, a mesma permissão de aprovar pedidos):
 *   GET  /aprovacoes/negociacoes                            as trocas da equipe esperando decisão
 *   GET  /aprovacoes/negociacoes/{id}                       o checklist dos dois lados
 *   POST /aprovacoes/negociacoes/{id}/aprovar               {"mesmoAssim", "mensagem"}, como no pedido
 *   POST /aprovacoes/negociacoes/{id}/recusar               {"mensagem"}, sempre com motivo
 */
@RestController
public class NegociacaoController {

    private final NegociacaoService service;

    public NegociacaoController(NegociacaoService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ colegas

    @GetMapping("/minhas-ferias/equipe")
    public List<FeriasDoColega> feriasDaEquipe() {
        return service.feriasDaEquipe();
    }

    @GetMapping("/minhas-ferias/negociacoes")
    public List<NegociacaoResposta> minhas() {
        return service.minhas();
    }

    @PostMapping("/minhas-ferias/negociacoes")
    public ResponseEntity<NegociacaoResposta> propor(@Valid @RequestBody PropostaDeTrocaRequisicao proposta) {
        var criada = service.propor(proposta);
        var localizacao = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/minhas-ferias/negociacoes/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(localizacao).body(criada);
    }

    @PostMapping("/minhas-ferias/negociacoes/{id}/contrapropor")
    public NegociacaoResposta contrapropor(@PathVariable Long id,
                                           @Valid @RequestBody ContrapropostaRequisicao contraproposta) {
        return service.contrapropor(id, contraproposta);
    }

    @PostMapping("/minhas-ferias/negociacoes/{id}/aceitar")
    public NegociacaoResposta aceitar(@PathVariable Long id) {
        return service.aceitar(id);
    }

    @PostMapping("/minhas-ferias/negociacoes/{id}/recusar")
    public NegociacaoResposta recusar(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) DecisaoRequisicao recado) {
        return service.recusar(id, recado == null ? new DecisaoRequisicao(false, null) : recado);
    }

    @PostMapping("/minhas-ferias/negociacoes/{id}/cancelar")
    public NegociacaoResposta cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    // ------------------------------------------------------------------ gestor

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @GetMapping("/aprovacoes/negociacoes")
    public List<TrocaParaAprovar> fila() {
        return service.filaDoGestor();
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @GetMapping("/aprovacoes/negociacoes/{id}")
    public TrocaParaAprovar detalhe(@PathVariable Long id) {
        return service.detalheParaOGestor(id);
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @PostMapping("/aprovacoes/negociacoes/{id}/aprovar")
    public NegociacaoResposta aprovar(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) DecisaoRequisicao decisao) {
        return service.aprovar(id, decisao == null ? new DecisaoRequisicao(false, null) : decisao);
    }

    @PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")
    @PostMapping("/aprovacoes/negociacoes/{id}/recusar")
    public NegociacaoResposta recusarPeloGestor(@PathVariable Long id, @Valid @RequestBody DecisaoRequisicao decisao) {
        return service.recusarPeloGestor(id, decisao);
    }
}
