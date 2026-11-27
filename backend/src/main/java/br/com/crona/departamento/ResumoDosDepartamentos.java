package br.com.crona.departamento;

import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.empresa.ConfiguracaoFerias;
import br.com.crona.empresa.ConfiguracaoFeriasRepository;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.ferias.validacao.DadosDaEquipe;
import br.com.crona.ferias.validacao.ValidadorDaEquipe;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A lista de departamentos com quantas pessoas cada um tem e o limite da EMP-01.
 *
 * Fica separada do DepartamentoService (que cuida de criar, renomear e excluir) porque só a tela de
 * Cadastros precisa disso, e porque depende de colaboradores e da configuração da empresa.
 */
@Service
public class ResumoDosDepartamentos {

    private final DepartamentoRepository departamentoRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final ConfiguracaoFeriasRepository configuracaoRepository;
    private final EmpresaAtual empresaAtual;

    public ResumoDosDepartamentos(DepartamentoRepository departamentoRepository,
                                  ColaboradorRepository colaboradorRepository,
                                  ConfiguracaoFeriasRepository configuracaoRepository,
                                  EmpresaAtual empresaAtual) {
        this.departamentoRepository = departamentoRepository;
        this.colaboradorRepository = colaboradorRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.empresaAtual = empresaAtual;
    }

    @Transactional(readOnly = true)
    public List<DepartamentoResumo> listar() {
        Long empresaId = empresaAtual.id();
        // Uma consulta só para contar todos os departamentos (em vez de uma por departamento).
        Map<Long, Long> pessoasPorDepartamento = colaboradorRepository.contarAtivosPorDepartamento(empresaId).stream()
                .collect(Collectors.toMap(ColaboradorRepository.PessoasNoDepartamento::getDepartamentoId,
                        ColaboradorRepository.PessoasNoDepartamento::getPessoas));
        Integer percentual = configuracaoRepository.findByEmpresaId(empresaId)
                .orElseGet(ConfiguracaoFerias::padrao)
                .getPercentualMinimoTrabalhando();

        return departamentoRepository.findByEmpresaIdOrderByNomeAsc(empresaId).stream()
                .map(departamento -> {
                    int pessoas = pessoasPorDepartamento.getOrDefault(departamento.getId(), 0L).intValue();
                    // Mesma conta do pedido de férias (ValidadorDaEquipe), para a tela nunca discordar dele.
                    Integer limite = percentual == null ? null
                            : ValidadorDaEquipe.limiteDoDepartamento(
                                    new DadosDaEquipe.Departamento(departamento.getNome(), pessoas, percentual));
                    return new DepartamentoResumo(departamento.getId(), departamento.getNome(), pessoas, limite);
                })
                .toList();
    }
}
