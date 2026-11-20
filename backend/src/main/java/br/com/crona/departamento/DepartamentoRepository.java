package br.com.crona.departamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acesso ao banco para departamentos.
 *
 * É só uma interface: o Spring Data cria a implementação sozinho.
 * - JpaRepository já traz save, findById, delete...
 * - Os métodos abaixo são "query methods": o Spring lê o NOME do método e monta o SQL.
 *
 * Todo método filtra pela empresa (empresaId). Buscar um departamento só pelo id
 * deixaria uma empresa ver dados de outra (armadilha A-02).
 */
public interface DepartamentoRepository extends JpaRepository<Departamento, Long> {

    // SELECT ... WHERE empresa_id = ? ORDER BY nome
    List<Departamento> findByEmpresaIdOrderByNomeAsc(Long empresaId);

    // SELECT ... WHERE id = ? AND empresa_id = ?
    Optional<Departamento> findByIdAndEmpresaId(Long id, Long empresaId);

    // SELECT count(*) > 0 ... WHERE empresa_id = ? AND upper(nome) = upper(?)
    boolean existsByEmpresaIdAndNomeIgnoreCase(Long empresaId, String nome);

    // Igual ao anterior, mas ignorando o próprio departamento (usado ao renomear)
    boolean existsByEmpresaIdAndNomeIgnoreCaseAndIdNot(Long empresaId, String nome, Long id);
}
