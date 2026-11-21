package br.com.crona.ferias.aprovacao;

import jakarta.validation.constraints.Size;

/**
 * A decisão do gestor ou do RH.
 *
 * @param mesmoAssim confirma que o gestor viu os pontos de atenção e quer aprovar assim mesmo
 *                   (o botão "Aprovar mesmo assim" do redesign)
 * @param mensagem   obrigatória para recusar e para "aprovar mesmo assim" (fica registrado o porquê)
 */
public record DecisaoRequisicao(
        boolean mesmoAssim,

        @Size(max = 500, message = "A mensagem pode ter no máximo 500 caracteres.")
        String mensagem
) {
}
