package br.com.crona.ferias;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * POST /minhas-ferias/solicitacoes                  envia um pedido (201; 422 com o checklist se tiver impedimento)
 * GET  /minhas-ferias/solicitacoes                  os meus pedidos
 * POST /minhas-ferias/solicitacoes/{id}/cancelar    desiste de um pedido ainda em análise
 * GET  /minhas-ferias/solicitacoes/{id}/aviso       o aviso de férias em PDF (só de férias aprovadas)
 *
 * Sem @PreAuthorize: basta estar logado, e cada um só mexe nos próprios pedidos.
 */
@RestController
@RequestMapping("/minhas-ferias/solicitacoes")
public class SolicitacaoController {

    private final SolicitacaoService service;
    private final AvisoDeFeriasService avisoService;

    public SolicitacaoController(SolicitacaoService service, AvisoDeFeriasService avisoService) {
        this.service = service;
        this.avisoService = avisoService;
    }

    @PostMapping
    public ResponseEntity<SolicitacaoResposta> enviar(@Valid @RequestBody EnvioRequisicao envio) {
        var criada = service.enviar(envio);
        var localizacao = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/minhas-ferias/solicitacoes/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(localizacao).body(criada);
    }

    @GetMapping
    public List<SolicitacaoResposta> minhas() {
        return service.minhas();
    }

    @PostMapping("/{id}/cancelar")
    public SolicitacaoResposta cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    /**
     * Fase 8a: o aviso de férias em PDF (só de férias aprovadas).
     * Content-Disposition: attachment = o navegador baixa o arquivo com este nome, em vez de abrir.
     */
    @GetMapping(value = "/{id}/aviso", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> aviso(@PathVariable Long id) {
        var arquivo = avisoService.gerar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .body(arquivo.conteudo());
    }
}
