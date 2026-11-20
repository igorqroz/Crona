package br.com.crona.empresa;

/**
 * Diz qual é a empresa de quem está usando o sistema agora.
 *
 * Todo service pergunta a empresa para esta interface, e nunca para a requisição
 * (armadilha A-02: a empresa não pode vir de um parâmetro como ?empresaId=2).
 *
 * Fase 2: a implementação devolvia sempre a Empresa Demo.
 * Fase 3: a implementação (EmpresaAtualDoToken) lê a empresa do token de login.
 * Os services só conhecem esta interface, então nenhum deles precisou mudar na troca.
 */
public interface EmpresaAtual {

    Long id();
}
