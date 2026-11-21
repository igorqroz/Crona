package br.com.crona.ferias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SolicitacaoFeriasRepository extends JpaRepository<SolicitacaoFerias, Long> {

    /*
     * "select new ...": em vez de trazer as entidades inteiras, a consulta já monta
     * objetos só com os 3 campos que o cálculo do saldo precisa. Menos dados trafegando.
     */
    @Query("""
            select new br.com.crona.ferias.ConsumoPorPeriodo(
                       s.periodoAquisitivo.id, s.diasGozo + s.diasAbono, s.status)
            from SolicitacaoFerias s
            where s.colaborador.id = :colaboradorId
            """)
    List<ConsumoPorPeriodo> consumosDoColaborador(@Param("colaboradorId") Long colaboradorId);

    boolean existsByPeriodoAquisitivoId(Long periodoAquisitivoId);

    List<SolicitacaoFerias> findByPeriodoAquisitivoIdAndStatusIn(Long periodoAquisitivoId,
                                                                 Collection<StatusSolicitacao> status);

    /**
     * Férias dos OUTROS colaboradores da empresa que encostam nas datas.
     * "left join" no departamento: quem não tem departamento também conta para a empresa (EMP-04).
     */
    @Query("""
            select new br.com.crona.ferias.AusenciaRegistrada(c.nome, s.dataInicio, s.dataFim, s.status, d.id)
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
}
