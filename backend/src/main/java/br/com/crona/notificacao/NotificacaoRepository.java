package br.com.crona.notificacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    /** As 30 mais recentes (o sino não precisa mostrar o histórico inteiro). */
    List<Notificacao> findTop30ByDestinatarioIdOrderByIdDesc(Long destinatarioId);

    long countByDestinatarioIdAndLidaEmIsNull(Long destinatarioId);

    Optional<Notificacao> findByIdAndDestinatarioId(Long id, Long destinatarioId);

    boolean existsByDestinatarioIdAndChaveUnica(Long destinatarioId, String chaveUnica);

    /**
     * "Marcar todas como lidas" num UPDATE só, em vez de carregar cada notificação.
     * @Modifying = esta consulta altera dados (não é um SELECT).
     */
    @Modifying
    @Query("update Notificacao n set n.lidaEm = :agora where n.destinatario.id = :destinatarioId and n.lidaEm is null")
    int marcarTodasComoLidas(@Param("destinatarioId") Long destinatarioId, @Param("agora") Instant agora);
}
