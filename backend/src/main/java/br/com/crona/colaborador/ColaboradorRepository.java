package br.com.crona.colaborador;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ColaboradorRepository extends JpaRepository<Colaborador, Long> {

    /*
     * O PROBLEMA N+1: departamento e gestor são LAZY. Listar 20 colaboradores e depois
     * ler o departamento de cada um faria 1 consulta para a lista + 20 para departamentos
     * + 20 para gestores = 41 consultas.
     *
     * @EntityGraph manda o Hibernate trazer departamento e gestor JUNTO, num JOIN só.
     */
    @EntityGraph(attributePaths = {"departamento", "gestor"})
    @Query("""
            select c from Colaborador c
            where c.empresa.id = :empresaId
              and (lower(c.nome) like lower(concat('%', :busca, '%'))
                   or lower(c.email) like lower(concat('%', :busca, '%')))
              and (:departamentoId is null or c.departamento.id = :departamentoId)
              and (:ativo is null or c.ativo = :ativo)
            """)
    // Pageable = qual página, quantos por página e a ordenação. Page = a página + o total.
    Page<Colaborador> pesquisar(@Param("empresaId") Long empresaId,
                                @Param("busca") String busca,
                                @Param("departamentoId") Long departamentoId,
                                @Param("ativo") Boolean ativo,
                                Pageable paginacao);

    @EntityGraph(attributePaths = {"departamento", "gestor"})
    Optional<Colaborador> findByIdAndEmpresaId(Long id, Long empresaId);

    // O e-mail é o login, então é único no sistema inteiro, não só na empresa.
    boolean existsByEmailIgnoreCase(String email);

    // Usado no login: aqui ainda não se sabe a empresa, ela vem do próprio colaborador.
    Optional<Colaborador> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    // Este colaborador é gestor de alguém ativo?
    boolean existsByGestorIdAndAtivoTrue(Long gestorId);
}
