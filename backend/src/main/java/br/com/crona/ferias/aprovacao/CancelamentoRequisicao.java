package br.com.crona.ferias.aprovacao;

import jakarta.validation.constraints.Size;

/**
 * O pedido do RH para cancelar férias já aprovadas.
 *
 * @param motivo obrigatório (a regra está em SolicitacaoFerias.cancelarAprovada): vai na
 *               notificação do colaborador e fica registrado no pedido
 */
public record CancelamentoRequisicao(
        @Size(max = 500, message = "O motivo pode ter no máximo 500 caracteres.")
        String motivo
) {
}
