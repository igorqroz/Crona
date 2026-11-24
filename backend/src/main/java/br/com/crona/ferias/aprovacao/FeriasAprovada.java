package br.com.crona.ferias.aprovacao;

import br.com.crona.compartilhado.ReferenciaResposta;

import java.time.LocalDate;

/**
 * Férias aprovadas que ainda não começaram, como o RH vê na aba "Aprovadas" (onde pode cancelar).
 *
 * @param aprovadoPor quem deu a aprovação final (o RH, com a EMP-06 ligada, ou o gestor)
 * @param bloqueio    por que NÃO dá para cancelar agora (ex.: são as férias de quem está logado,
 *                    ou estão numa troca em aberto). Nulo quando dá para cancelar.
 */
public record FeriasAprovada(
        Long id,
        ReferenciaResposta colaborador,
        String departamento,
        LocalDate inicio,
        LocalDate fim,
        int diasGozo,
        int diasAbono,
        ReferenciaResposta aprovadoPor,
        String bloqueio
) {
}
