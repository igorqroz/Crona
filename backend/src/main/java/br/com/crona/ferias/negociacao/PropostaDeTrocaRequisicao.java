package br.com.crona.ferias.negociacao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A proposta inicial de troca: "eu fico com as suas férias, e você fica com estas datas".
 *
 * Os nomes dos campos são sempre do ponto de vista da negociação (solicitante = quem propôs,
 * colega = quem recebeu a proposta), e não "meu/seu": assim a contraproposta, que pode vir de
 * qualquer um dos dois, usa os mesmos nomes.
 *
 * @param pedidoDoColegaId      as férias aprovadas do colega que eu quero
 * @param pedidoDoSolicitanteId as minhas férias aprovadas que ofereço (opcional: posso não ter)
 * @param diasSolicitante       só se eu não tenho férias marcadas: quantos dias quero
 * @param inicioColega          para quando as férias do colega vão (os dias dele continuam os mesmos)
 */
public record PropostaDeTrocaRequisicao(

        @NotNull(message = "Informe as férias do colega que você quer.")
        Long pedidoDoColegaId,

        Long pedidoDoSolicitanteId,

        @NotNull(message = "Informe o início das suas férias.")
        LocalDate inicioSolicitante,

        @Min(value = 1, message = "Informe pelo menos 1 dia.")
        @Max(value = 30, message = "Um período de férias tem no máximo 30 dias.")
        Integer diasSolicitante,

        @NotNull(message = "Informe o novo início das férias do colega.")
        LocalDate inicioColega,

        @Size(max = 500, message = "A mensagem pode ter no máximo 500 caracteres.")
        String mensagem
) {
}
