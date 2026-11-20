package br.com.crona.empresa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * TEMPORÁRIA, só até a Fase 3 (login).
 * Sempre devolve a empresa configurada em crona.dev.empresa-cnpj (a Empresa Demo).
 *
 * {@code @Profile("dev")}: só existe no perfil de desenvolvimento. Em produção esta
 * classe nem é carregada.
 */
@Component
@Profile("dev")
class EmpresaAtualDesenvolvimento implements EmpresaAtual {

    private final EmpresaRepository empresaRepository;
    private final String cnpj;
    private Long idEmCache;

    // Injeção pelo construtor: o Spring entrega o repository e o valor da configuração.
    EmpresaAtualDesenvolvimento(EmpresaRepository empresaRepository,
                                @Value("${crona.dev.empresa-cnpj}") String cnpj) {
        this.empresaRepository = empresaRepository;
        this.cnpj = cnpj;
    }

    @Override
    public Long id() {
        if (idEmCache == null) {
            idEmCache = empresaRepository.findByCnpj(cnpj)
                    .orElseThrow(() -> new IllegalStateException(
                            "Empresa de desenvolvimento não encontrada (CNPJ " + cnpj
                                    + "). Confira o R__dados_desenvolvimento.sql."))
                    .getId();
        }
        return idEmCache;
    }
}
