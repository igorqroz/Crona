package br.com.crona.ferias;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodoAquisitivoRepository extends JpaRepository<PeriodoAquisitivo, Long> {

    List<PeriodoAquisitivo> findByColaboradorIdOrderByNumeroAsc(Long colaboradorId);

    Optional<PeriodoAquisitivo> findByColaboradorIdAndNumero(Long colaboradorId, int numero);
}
