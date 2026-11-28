package br.com.crona.colaborador;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
              and c.perfil in :perfis
              and (:gestorId is null or c.gestor.id = :gestorId)
            """)
    // Pageable = qual página, quantos por página e a ordenação. Page = a página + o total.
    // Cadastros 2: "perfis" nunca vem vazio (sem filtro = todos os perfis), porque "in ()" não é SQL válido.
    Page<Colaborador> pesquisarComFiltros(@Param("empresaId") Long empresaId,
                                          @Param("busca") String busca,
                                          @Param("departamentoId") Long departamentoId,
                                          @Param("ativo") Boolean ativo,
                                          @Param("perfis") List<Perfil> perfis,
                                          @Param("gestorId") Long gestorId,
                                          Pageable paginacao);

    /** A pesquisa sem os filtros de perfil e de gestor (a de antes do Cadastros 2). */
    default Page<Colaborador> pesquisar(Long empresaId, String busca, Long departamentoId, Boolean ativo,
                                        Pageable paginacao) {
        return pesquisarComFiltros(empresaId, busca, departamentoId, ativo, List.of(Perfil.values()), null, paginacao);
    }

    @EntityGraph(attributePaths = {"departamento", "gestor"})
    Optional<Colaborador> findByIdAndEmpresaId(Long id, Long empresaId);

    // O e-mail é o login, então é único no sistema inteiro, não só na empresa.
    boolean existsByEmailIgnoreCase(String email);

    // Usado no login: aqui ainda não se sabe a empresa, ela vem do próprio colaborador.
    Optional<Colaborador> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    // Quantas pessoas ativas o departamento tem (para o limite da EMP-01).
    long countByDepartamentoIdAndAtivoTrue(Long departamentoId);

    // Cadastros 3: quantas pessoas ativas a empresa tem (a dica do limite da empresa, EMP-04).
    long countByEmpresaIdAndAtivoTrue(Long empresaId);

    /** Uma linha do "group by" abaixo. O Spring Data preenche a interface pelos nomes (alias) da consulta. */
    interface PessoasNoDepartamento {
        Long getDepartamentoId();
        Long getPessoas();
    }

    // Cadastros: quantas pessoas ativas cada departamento da empresa tem, numa consulta só.
    @Query("""
            select c.departamento.id as departamentoId, count(c) as pessoas
            from Colaborador c
            where c.empresa.id = :empresaId and c.ativo = true and c.departamento is not null
            group by c.departamento.id
            """)
    List<PessoasNoDepartamento> contarAtivosPorDepartamento(@Param("empresaId") Long empresaId);

    // Este colaborador é gestor de alguém ativo?
    boolean existsByGestorIdAndAtivoTrue(Long gestorId);

    // Fase 8b: quem é do RH (ADMIN) na empresa, para avisar dos pedidos que esperam a confirmação.
    List<Colaborador> findByEmpresaIdAndPerfilAndAtivoTrue(Long empresaId, Perfil perfil);

    // Fase 8b: a rotina diária percorre todos os colaboradores ativos.
    List<Colaborador> findByAtivoTrue();
}
