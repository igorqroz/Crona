package br.com.crona.notificacao;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.seguranca.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Criar notificações (usado pelo Notificador e pela rotina diária) e o lado de quem as recebe:
 * ver as minhas e marcar como lidas.
 *
 * Cada pessoa só enxerga as próprias: toda busca filtra pelo destinatário = quem está logado.
 */
@Service
public class Notificacoes {

    private final NotificacaoRepository repository;
    private final UsuarioAtual usuarioAtual;
    private final Clock relogio;

    public Notificacoes(NotificacaoRepository repository, UsuarioAtual usuarioAtual, Clock relogio) {
        this.repository = repository;
        this.usuarioAtual = usuarioAtual;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ criar

    /** Uma notificação de um evento do fluxo (pode haver várias iguais: cada evento é um fato). */
    @Transactional
    public void criar(Colaborador destinatario, TipoDeNotificacao tipo, String mensagem, String link) {
        repository.save(new Notificacao(destinatario, tipo, mensagem, link, null));
    }

    /**
     * Uma notificação que só pode sair UMA vez (rotina diária). Confere antes; a restrição única
     * do banco (V4) é a garantia final, caso duas execuções corram ao mesmo tempo.
     *
     * @return true se criou; false se já existia
     */
    @Transactional
    public boolean criarUmaVez(Colaborador destinatario, TipoDeNotificacao tipo, String mensagem, String link,
                               String chaveUnica) {
        if (repository.existsByDestinatarioIdAndChaveUnica(destinatario.getId(), chaveUnica)) {
            return false;
        }
        repository.save(new Notificacao(destinatario, tipo, mensagem, link, chaveUnica));
        return true;
    }

    // ------------------------------------------------------------------ quem recebe

    @Transactional(readOnly = true)
    public NotificacoesResposta minhas() {
        Long eu = usuarioAtual.id();
        return new NotificacoesResposta(repository.countByDestinatarioIdAndLidaEmIsNull(eu),
                repository.findTop30ByDestinatarioIdOrderByIdDesc(eu).stream().map(NotificacoesResposta.Item::de).toList());
    }

    @Transactional
    public void marcarComoLida(Long id) {
        repository.findByIdAndDestinatarioId(id, usuarioAtual.id())
                // A notificação de outra pessoa responde "não encontrada", e não "proibido".
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação " + id + " não encontrada."))
                .marcarComoLida(Instant.now(relogio));
    }

    @Transactional
    public void marcarTodasComoLidas() {
        repository.marcarTodasComoLidas(usuarioAtual.id(), Instant.now(relogio));
    }
}
