package br.com.crona.ferias.aprovacao;

import jakarta.validation.constraints.Size;

/**
 * A decisão do gestor ou do RH (e o recado de quem recusa uma troca).
 *
 * @param mesmoAssim confirma que o gestor viu os pontos de atenção e quer aprovar assim mesmo
 *                   (o botão "Aprovar mesmo assim" do redesign). Opcional: sem ele, vale false.
 * @param mensagem   obrigatória para recusar e para "aprovar mesmo assim" (fica registrado o porquê)
 */
public record DecisaoRequisicao(
        Boolean mesmoAssim,

        @Size(max = 500, message = "A mensagem pode ter no máximo 500 caracteres.")
        String mensagem
) {

    /*
     * Boolean (objeto) e não boolean (primitivo): o Jackson 3 recusa, por padrão, um JSON que não
     * traz um campo primitivo (não há "nulo" para boolean). Assim {"mensagem": "..."} funciona,
     * e a ausência vira false aqui.
     */
    public DecisaoRequisicao {
        mesmoAssim = Boolean.TRUE.equals(mesmoAssim);
    }
}
