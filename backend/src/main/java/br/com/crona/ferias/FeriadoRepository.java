package br.com.crona.ferias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {

    /** Feriados nacionais + os da empresa, entre duas datas. */
    @Query("""
            select f.data from Feriado f
            where (f.empresa is null or f.empresa.id = :empresaId)
              and f.data between :de and :ate
            """)
    List<LocalDate> datasEntre(@Param("empresaId") Long empresaId,
                               @Param("de") LocalDate de,
                               @Param("ate") LocalDate ate);
}
