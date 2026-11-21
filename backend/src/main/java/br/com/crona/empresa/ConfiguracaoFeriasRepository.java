package br.com.crona.empresa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracaoFeriasRepository extends JpaRepository<ConfiguracaoFerias, Long> {

    Optional<ConfiguracaoFerias> findByEmpresaId(Long empresaId);
}
