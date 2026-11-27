package br.com.crona.empresa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastros 3: ler e mudar as regras de férias da empresa de quem está logado. */
@Service
public class RegrasDaEmpresaService {

    private final ConfiguracaoFeriasRepository configuracaoRepository;
    private final EmpresaRepository empresaRepository;
    private final EmpresaAtual empresaAtual;

    public RegrasDaEmpresaService(ConfiguracaoFeriasRepository configuracaoRepository,
                                  EmpresaRepository empresaRepository,
                                  EmpresaAtual empresaAtual) {
        this.configuracaoRepository = configuracaoRepository;
        this.empresaRepository = empresaRepository;
        this.empresaAtual = empresaAtual;
    }

    /** Sem configuração gravada, a empresa usa os padrões (só a antecedência de 30 dias ligada). */
    @Transactional(readOnly = true)
    public RegrasDaEmpresa buscar() {
        return RegrasDaEmpresa.de(configuracaoRepository.findByEmpresaId(empresaAtual.id())
                .orElseGet(ConfiguracaoFerias::padrao));
    }

    /**
     * Grava as regras. Na primeira vez, cria a configuração da empresa.
     * EMP-06: desligar a confirmação do RH não mexe nos pedidos que já estão em AGUARDANDO_RH:
     * eles continuam na fila do RH até ele confirmar ou recusar. A regra vale para os próximos.
     */
    @Transactional
    public RegrasDaEmpresa alterar(RegrasDaEmpresa regras) {
        Long empresaId = empresaAtual.id();
        var configuracao = configuracaoRepository.findByEmpresaId(empresaId)
                .orElseGet(() -> configuracaoRepository.save(
                        ConfiguracaoFerias.daEmpresa(empresaRepository.getReferenceById(empresaId))));
        configuracao.alterar(regras.percentualMinimoTrabalhando(), regras.antecedenciaMinimaDias(),
                regras.limiteFeriasSimultaneasEmpresa(), regras.exigeConfirmacaoRh());
        return RegrasDaEmpresa.de(configuracao);
    }
}
