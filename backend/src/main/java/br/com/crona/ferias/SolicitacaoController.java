package br.com.crona.ferias;

import jakarta.validation.Valid;
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
 *
 * Sem @PreAuthorize: basta estar logado, e cada um só mexe nos próprios pedidos.
 */
@RestController
@RequestMapping("/minhas-ferias/solicitacoes")
public class SolicitacaoController {

    private final SolicitacaoService service;

    public SolicitacaoController(SolicitacaoService service) {
        this.service = service;
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
}
