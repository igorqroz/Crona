package br.com.crona.colaborador;

/**
 * EVENTO: "a data de admissão deste colaborador foi definida ou mudou".
 *
 * O ColaboradorService só AVISA que isso aconteceu; quem se interessa (hoje, quem gera
 * os períodos aquisitivos) escuta com @EventListener. Assim o cadastro de colaboradores
 * não precisa conhecer as regras de férias, e cada pacote cuida do seu assunto.
 */
public record AdmissaoDefinida(Long colaboradorId) {
}
