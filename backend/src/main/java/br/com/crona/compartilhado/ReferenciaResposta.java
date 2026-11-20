package br.com.crona.compartilhado;

/**
 * Versão resumida de outro registro dentro de uma resposta: só id e nome.
 * Ex.: o colaborador mostra o departamento como { "id": 1, "nome": "Engenharia" }.
 */
public record ReferenciaResposta(Long id, String nome) {
}
