package br.com.crona.ferias;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * O que a tela "Solicitar férias" manda a cada data escolhida no calendário.
 *
 * @param periodo número do período aquisitivo. Opcional: sem ele, o sistema usa o mais antigo
 *                que ainda tem saldo (para não deixar férias vencerem).
 */
public record SimulacaoRequisicao(

        @NotNull(message = "Informe a data de início.")
        LocalDate inicio,

        @NotNull(message = "Informe quantos dias de descanso.")
        @Min(value = 1, message = "Informe pelo menos 1 dia.")
        @Max(value = 30, message = "Um período de férias tem no máximo 30 dias.")
        Integer diasGozo,

        @Min(value = 0, message = "O abono não pode ser negativo.")
        @Max(value = 10, message = "O abono tem no máximo 10 dias.")
        Integer diasAbono,

        Integer periodo
) {

    public SimulacaoRequisicao {
        diasAbono = diasAbono == null ? 0 : diasAbono;
    }
}
