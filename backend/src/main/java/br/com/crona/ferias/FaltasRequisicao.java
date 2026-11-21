package br.com.crona.ferias;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** O RH informa as faltas injustificadas de um período (CLT-02). */
public record FaltasRequisicao(

        @NotNull(message = "Informe a quantidade de faltas.")
        @Min(value = 0, message = "As faltas não podem ser negativas.")
        @Max(value = 366, message = "Um período tem no máximo 366 dias.")
        Integer faltas
) {
}
