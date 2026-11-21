package br.com.crona.ferias;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PeriodoAquisitivoRepository extends JpaRepository<PeriodoAquisitivo, Long> {

    List<PeriodoAquisitivo> findByColaboradorIdOrderByNumeroAsc(Long colaboradorId);

    Optional<PeriodoAquisitivo> findByColaboradorIdAndNumero(Long colaboradorId, int numero);

    /*
     * TRAVA (lock) as linhas dos períodos do colaborador até o fim da transação.
     *
     * O problema que isto resolve: dois pedidos de 30 dias enviados no MESMO instante (dois
     * cliques, duas abas). Sem trava, os dois leem "30 dias livres", os dois passam na
     * validação e os dois são gravados: 60 dias num período de 30. Com a trava, o segundo
     * espera o primeiro terminar e então lê o saldo já atualizado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PeriodoAquisitivo p where p.colaborador.id = :colaboradorId order by p.numero")
    List<PeriodoAquisitivo> travarDoColaborador(@Param("colaboradorId") Long colaboradorId);
}
