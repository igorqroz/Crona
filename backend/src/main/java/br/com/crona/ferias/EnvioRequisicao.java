package br.com.crona.ferias;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** O pedido de férias enviado de verdade: os mesmos dados da simulação, mais o recado para o gestor. */
public record EnvioRequisicao(

        @NotNull(message = "Informe a data de início.")
        LocalDate inicio,

        @NotNull(message = "Informe quantos dias de descanso.")
        @Min(value = 1, message = "Informe pelo menos 1 dia.")
        @Max(value = 30, message = "Um período de férias tem no máximo 30 dias.")
        Integer diasGozo,

        @Min(value = 0, message = "O abono não pode ser negativo.")
        @Max(value = 10, message = "O abono tem no máximo 10 dias.")
        Integer diasAbono,

        Integer periodo,

        @Size(max = 500, message = "O recado pode ter no máximo 500 caracteres.")
        String recado
) {

    SimulacaoRequisicao comoSimulacao() {
        return new SimulacaoRequisicao(inicio, diasGozo, diasAbono, periodo);
    }
}
