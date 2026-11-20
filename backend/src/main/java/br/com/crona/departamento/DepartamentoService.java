package br.com.crona.departamento;

import br.com.crona.compartilhado.erro.ConflitoException;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.empresa.EmpresaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Regras do cadastro de departamentos.
 *
 * O controller só cuida de HTTP; o repository só cuida do banco; as REGRAS ficam aqui.
 * Assim dá para testar as regras sem subir servidor nenhum.
 */
@Service
public class DepartamentoService {

    private final DepartamentoRepository departamentoRepository;
    private final EmpresaRepository empresaRepository;
    private final EmpresaAtual empresaAtual;

    public DepartamentoService(DepartamentoRepository departamentoRepository,
                               EmpresaRepository empresaRepository,
                               EmpresaAtual empresaAtual) {
        this.departamentoRepository = departamentoRepository;
        this.empresaRepository = empresaRepository;
        this.empresaAtual = empresaAtual;
    }

    // readOnly: avisa o banco que é só leitura (mais leve e mais seguro).
    @Transactional(readOnly = true)
    public List<DepartamentoResposta> listar() {
        return departamentoRepository.findByEmpresaIdOrderByNomeAsc(empresaAtual.id())
                .stream()
                .map(DepartamentoResposta::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartamentoResposta buscar(Long id) {
        return DepartamentoResposta.de(buscarDaEmpresa(id));
    }

    // @Transactional: tudo dentro do método vira uma transação. Se der erro no meio,
    // o banco desfaz tudo (o mesmo BEGIN/ROLLBACK que você usou no teste do V1).
    @Transactional
    public DepartamentoResposta criar(DepartamentoRequisicao requisicao) {
        Long empresaId = empresaAtual.id();
        String nome = requisicao.nome().trim();

        if (departamentoRepository.existsByEmpresaIdAndNomeIgnoreCase(empresaId, nome)) {
            throw new ConflitoException("Já existe um departamento chamado \"" + nome + "\".");
        }

        // getReferenceById não vai ao banco: só cria uma "referência" com o id,
        // suficiente para preencher a chave estrangeira empresa_id.
        var empresa = empresaRepository.getReferenceById(empresaId);
        var departamento = departamentoRepository.save(new Departamento(empresa, nome));
        return DepartamentoResposta.de(departamento);
    }

    @Transactional
    public DepartamentoResposta renomear(Long id, DepartamentoRequisicao requisicao) {
        var departamento = buscarDaEmpresa(id);
        String nome = requisicao.nome().trim();

        if (departamentoRepository.existsByEmpresaIdAndNomeIgnoreCaseAndIdNot(empresaAtual.id(), nome, id)) {
            throw new ConflitoException("Já existe um departamento chamado \"" + nome + "\".");
        }

        // Não precisa chamar save(): a entidade veio do banco dentro desta transação,
        // e o Hibernate grava sozinho o que mudou quando a transação termina.
        departamento.renomear(nome);
        return DepartamentoResposta.de(departamento);
    }

    @Transactional
    public void excluir(Long id) {
        var departamento = buscarDaEmpresa(id);
        try {
            departamentoRepository.delete(departamento);
            // flush: manda o DELETE para o banco AGORA, para o erro aparecer aqui dentro
            // do try, e não só no fim da transação.
            departamentoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // O banco recusou por causa de uma chave estrangeira: há colaboradores
            // ou períodos restritos ligados a este departamento.
            throw new ConflitoException("O departamento \"" + departamento.getNome()
                    + "\" está em uso e não pode ser excluído.");
        }
    }

    private Departamento buscarDaEmpresa(Long id) {
        // Mesmo que o id exista, se for de outra empresa a resposta é "não encontrado".
        // Responder "proibido" contaria a um curioso que aquele id existe.
        return departamentoRepository.findByIdAndEmpresaId(id, empresaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Departamento " + id + " não encontrado."));
    }
}
