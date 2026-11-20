package br.com.crona.empresa;

/**
 * Diz qual é a empresa de quem está usando o sistema agora.
 *
 * Todo service pergunta a empresa para esta interface, e nunca para a requisição
 * (armadilha A-02: a empresa não pode vir de um parâmetro como ?empresaId=2).
 *
 * Fase 2: a implementação é {@link EmpresaAtualDesenvolvimento}, que sempre devolve a Empresa Demo.
 * Fase 3: uma nova implementação vai ler a empresa do token de login. Como os services
 * só conhecem esta interface, nenhum deles vai precisar mudar.
 */
public interface EmpresaAtual {

    Long id();
}
