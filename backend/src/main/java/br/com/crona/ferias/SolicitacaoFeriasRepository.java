package br.com.crona.ferias;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolicitacaoFeriasRepository extends JpaRepository<SolicitacaoFerias, Long> {

    /*
     * "select new ...": em vez de trazer as entidades inteiras, a consulta já monta
     * objetos só com os 3 campos que o cálculo do saldo precisa. Menos dados trafegando.
     */
    @Query("""
            select new br.com.crona.ferias.ConsumoPorPeriodo(
                       s.id, s.periodoAquisitivo.id, s.diasGozo + s.diasAbono, s.status)
            from SolicitacaoFerias s
            where s.colaborador.id = :colaboradorId
            """)
    List<ConsumoPorPeriodo> consumosDoColaborador(@Param("colaboradorId") Long colaboradorId);

    boolean existsByPeriodoAquisitivoId(Long periodoAquisitivoId);

    // Os pedidos de quem está logado, do mais recente para o mais antigo.
    List<SolicitacaoFerias> findByColaboradorIdOrderByDataInicioDesc(Long colaboradorId);

    // ---- aprovações: já trazem colaborador, departamento e período juntos (evita o N+1)

    /** Pedidos num status, da empresa toda (visão do ADMIN/RH). */
    @EntityGraph(attributePaths = {"colaborador", "colaborador.departamento", "periodoAquisitivo"})
    List<SolicitacaoFerias> findByStatusAndColaboradorEmpresaIdOrderByDataInicioAsc(StatusSolicitacao status,
                                                                                   Long empresaId);

    /** Pedidos num status, só da equipe de um gestor. */
    @EntityGraph(attributePaths = {"colaborador", "colaborador.departamento", "periodoAquisitivo"})
    List<SolicitacaoFerias> findByStatusAndColaboradorGestorIdOrderByDataInicioAsc(StatusSolicitacao status,
                                                                                  Long gestorId);

    @EntityGraph(attributePaths = {"colaborador", "colaborador.departamento", "colaborador.gestor", "periodoAquisitivo"})
    Optional<SolicitacaoFerias> findByIdAndColaboradorEmpresaId(Long id, Long empresaId);

    List<SolicitacaoFerias> findByPeriodoAquisitivoIdAndStatusIn(Long periodoAquisitivoId,
                                                                 Collection<StatusSolicitacao> status);

    /**
     * Férias dos OUTROS colaboradores da empresa que encostam nas datas.
     * "left join" no departamento: quem não tem departamento também conta para a empresa (EMP-04).
     */
    @Query("""
            select new br.com.crona.ferias.AusenciaRegistrada(s.id, c.nome, s.dataInicio, s.dataFim, s.status, d.id)
            from SolicitacaoFerias s
                join s.colaborador c
                left join c.departamento d
            where c.empresa.id = :empresaId
              and c.id <> :colaboradorId
              and s.status in :status
              and s.dataInicio <= :fim and s.dataFim >= :inicio
            order by s.dataInicio
            """)
    List<AusenciaRegistrada> ausenciasDosColegas(@Param("empresaId") Long empresaId,
                                                 @Param("colaboradorId") Long colaboradorId,
                                                 @Param("status") Collection<StatusSolicitacao> status,
                                                 @Param("inicio") LocalDate inicio,
                                                 @Param("fim") LocalDate fim);

    /**
     * Fase 6: as férias aprovadas que ainda vão começar, dos colegas que têm o mesmo gestor.
     * São as férias que podem entrar numa proposta de troca.
     * "join fetch" traz o colaborador na mesma consulta (evita o N+1).
     */
    @Query("""
            select s from SolicitacaoFerias s
                join fetch s.colaborador c
            where c.gestor.id = :gestorId
              and c.id <> :colaboradorId
              and s.status = :status
              and s.dataInicio > :hoje
            order by s.dataInicio
            """)
    List<SolicitacaoFerias> feriasDaEquipe(@Param("gestorId") Long gestorId,
                                           @Param("colaboradorId") Long colaboradorId,
                                           @Param("status") StatusSolicitacao status,
                                           @Param("hoje") LocalDate hoje);

    /**
     * Fase 8c: as férias (aprovadas e em análise) que encostam nas datas, com o colaborador e o
     * departamento já carregados. Com gestorId, só a equipe dele; nulo = a empresa toda (RH).
     */
    @Query("""
            select s from SolicitacaoFerias s
                join fetch s.colaborador c
                left join fetch c.departamento d
            where c.empresa.id = :empresaId
              and (:gestorId is null or c.gestor.id = :gestorId)
              and s.status in :status
              and s.dataInicio <= :ate and s.dataFim >= :de
            order by c.nome, s.dataInicio
            """)
    List<SolicitacaoFerias> ausenciasParaOCalendario(@Param("empresaId") Long empresaId,
                                                     @Param("gestorId") Long gestorId,
                                                     @Param("status") Collection<StatusSolicitacao> status,
                                                     @Param("de") LocalDate de,
                                                     @Param("ate") LocalDate ate);
}
