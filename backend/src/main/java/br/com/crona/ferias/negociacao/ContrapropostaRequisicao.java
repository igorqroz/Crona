package br.com.crona.ferias.negociacao;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Outras datas para os dois. Os dias de cada um não mudam, então só vão os inícios. */
public record ContrapropostaRequisicao(

        @NotNull(message = "Informe o início das férias de quem propôs a troca.")
        LocalDate inicioSolicitante,

        @NotNull(message = "Informe o início das férias do colega.")
        LocalDate inicioColega,

        @Size(max = 500, message = "A mensagem pode ter no máximo 500 caracteres.")
        String mensagem
) {
}
