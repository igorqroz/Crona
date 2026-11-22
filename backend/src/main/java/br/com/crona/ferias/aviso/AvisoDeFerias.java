package br.com.crona.ferias.aviso;

import java.time.LocalDate;

/**
 * Tudo o que o aviso de férias (CLT, art. 135) mostra, já buscado do banco.
 * O gerador do PDF só recebe isto: não sabe nada de banco nem de Spring, e por isso
 * dá para testá-lo sozinho (GeradorDeAvisoTest).
 */
public record AvisoDeFerias(
        String razaoSocial,
        String cnpj,
        String colaborador,
        String cargo,
        String departamento,
        LocalDate admissao,
        LocalDate inicioAquisitivo,
        LocalDate fimAquisitivo,
        LocalDate inicio,
        LocalDate fim,
        int diasGozo,
        int diasAbono,
        LocalDate voltaAoTrabalho,
        String aprovadoPor,
        LocalDate emitidoEm
) {
}
