package br.com.crona.ferias;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NegociacaoRepository extends JpaRepository<Negociacao, Long> {

    /*
     * Os @EntityGraph dizem o que trazer junto na mesma consulta (evita o N+1): sem eles, cada
     * negociação da lista faria mais consultas para o colega, os pedidos e as propostas.
     */

    /** As negociações de que participo: as que propus e as que recebi, das mais novas para as mais antigas. */
    @EntityGraph(attributePaths = {"solicitante", "colega", "pedidoDoColega", "pedidoDoSolicitante", "propostas",
            "propostas.autor", "decididoPor"})
    @Query("select n from Negociacao n where n.solicitante.id = :id or n.colega.id = :id order by n.id desc")
    List<Negociacao> minhas(@Param("id") Long colaboradorId);

    @EntityGraph(attributePaths = {"solicitante", "solicitante.gestor", "solicitante.departamento", "colega",
            "colega.departamento", "pedidoDoColega", "pedidoDoColega.periodoAquisitivo", "pedidoDoSolicitante",
            "pedidoDoSolicitante.periodoAquisitivo", "propostas", "propostas.autor", "decididoPor"})
    Optional<Negociacao> findByIdAndSolicitanteEmpresaId(Long id, Long empresaId);

    /** A fila de trocas do gestor: as da equipe dele. */
    @EntityGraph(attributePaths = {"solicitante", "colega", "pedidoDoColega", "pedidoDoSolicitante", "propostas",
            "propostas.autor"})
    List<Negociacao> findByStatusAndSolicitanteGestorIdOrderByIdAsc(StatusNegociacao status, Long gestorId);

    /** A fila de trocas do ADMIN: as da empresa toda. */
    @EntityGraph(attributePaths = {"solicitante", "colega", "pedidoDoColega", "pedidoDoSolicitante", "propostas",
            "propostas.autor"})
    List<Negociacao> findByStatusAndSolicitanteEmpresaIdOrderByIdAsc(StatusNegociacao status, Long empresaId);

    /**
     * Este pedido já está numa negociação em aberto (de um lado ou do outro)?
     * "left join": o pedido de quem propôs pode ser nulo, e um join comum descartaria essas linhas.
     */
    @Query("""
            select count(n) > 0 from Negociacao n
                left join n.pedidoDoSolicitante ps
            where n.status in :status
              and (n.pedidoDoColega.id = :pedidoId or ps.id = :pedidoId)
            """)
    boolean pedidoEmNegociacao(@Param("pedidoId") Long pedidoId,
                               @Param("status") Collection<StatusNegociacao> status);
}
