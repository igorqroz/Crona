package br.com.crona.notificacao;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET  /notificacoes                as minhas (o número de não lidas + as 30 mais recentes)
 * POST /notificacoes/{id}/lida      marca uma como lida
 * POST /notificacoes/lidas          marca todas como lidas
 *
 * Sem @PreAuthorize: basta estar logado, e cada um só vê as próprias.
 */
@RestController
@RequestMapping("/notificacoes")
public class NotificacaoController {

    private final Notificacoes notificacoes;

    public NotificacaoController(Notificacoes notificacoes) {
        this.notificacoes = notificacoes;
    }

    @GetMapping
    public NotificacoesResposta minhas() {
        return notificacoes.minhas();
    }

    // 204 No Content: deu certo e não há nada para devolver.
    @PostMapping("/{id}/lida")
    public ResponseEntity<Void> marcarComoLida(@PathVariable Long id) {
        notificacoes.marcarComoLida(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lidas")
    public ResponseEntity<Void> marcarTodasComoLidas() {
        notificacoes.marcarTodasComoLidas();
        return ResponseEntity.noContent().build();
    }
}
