package br.com.crona.empresa;

import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.ferias.SolicitacaoFeriasRepository;
import br.com.crona.ferias.StatusSolicitacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastros 3: ler e mudar as regras de férias da empresa de quem está logado. */
@Service
public class RegrasDaEmpresaService {

    private final ConfiguracaoFeriasRepository configuracaoRepository;
    private final EmpresaRepository empresaRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final EmpresaAtual empresaAtual;

    public RegrasDaEmpresaService(ConfiguracaoFeriasRepository configuracaoRepository,
                                  EmpresaRepository empresaRepository,
                                  ColaboradorRepository colaboradorRepository,
                                  SolicitacaoFeriasRepository solicitacaoRepository,
                                  EmpresaAtual empresaAtual) {
        this.configuracaoRepository = configuracaoRepository;
        this.empresaRepository = empresaRepository;
        this.colaboradorRepository = colaboradorRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.empresaAtual = empresaAtual;
    }

    /** Sem configuração gravada, a empresa usa os padrões (só a antecedência de 30 dias ligada). */
    @Transactional(readOnly = true)
    public RegrasDaEmpresaResposta buscar() {
        return comContexto(RegrasDaEmpresa.de(configuracaoRepository.findByEmpresaId(empresaAtual.id())
                .orElseGet(ConfiguracaoFerias::padrao)));
    }

    /**
     * Grava as regras. Na primeira vez, cria a configuração da empresa.
     * EMP-06 (recomendação do Design, que é o comportamento natural): desligar a confirmação do RH não
     * mexe nos pedidos que já estão em AGUARDANDO_RH. Eles continuam na fila do RH até ele confirmar ou
     * recusar; só os aprovados pelo gestor DEPOIS de salvar já ficam aprovados de vez.
     */
    @Transactional
    public RegrasDaEmpresaResposta alterar(RegrasDaEmpresa regras) {
        Long empresaId = empresaAtual.id();
        var configuracao = configuracaoRepository.findByEmpresaId(empresaId)
                .orElseGet(() -> configuracaoRepository.save(
                        ConfiguracaoFerias.daEmpresa(empresaRepository.getReferenceById(empresaId))));
        configuracao.alterar(regras.percentualMinimoTrabalhando(), regras.antecedenciaMinimaDias(),
                regras.limiteFeriasSimultaneasEmpresa(), regras.exigeConfirmacaoRh());
        return comContexto(RegrasDaEmpresa.de(configuracao));
    }

    private RegrasDaEmpresaResposta comContexto(RegrasDaEmpresa regras) {
        Long empresaId = empresaAtual.id();
        return RegrasDaEmpresaResposta.de(regras,
                colaboradorRepository.countByEmpresaIdAndAtivoTrue(empresaId),
                solicitacaoRepository.countByStatusAndColaboradorEmpresaId(StatusSolicitacao.AGUARDANDO_RH, empresaId));
    }
}
