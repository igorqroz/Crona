package br.com.crona.ferias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
