package br.com.crona.ferias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PeriodoRestritoRepository extends JpaRepository<PeriodoRestrito, Long> {

    /** Restrições da empresa toda ou do departamento informado que encostam nas datas. */
    @Query("""
            select r from PeriodoRestrito r
            where r.empresa.id = :empresaId
              and (r.departamento is null or r.departamento.id = :departamentoId)
              and r.dataInicio <= :fim and r.dataFim >= :inicio
            order by r.dataInicio
            """)
    List<PeriodoRestrito> queEncostam(@Param("empresaId") Long empresaId,
                                     @Param("departamentoId") Long departamentoId,
                                     @Param("inicio") LocalDate inicio,
                                     @Param("fim") LocalDate fim);
}
