import { HttpErrorResponse } from '@angular/common/http';

import { DepartamentoResumo, Problema } from '../nucleo/modelos';

/*
 * As regras da tela de Departamentos, sem Angular: funções puras, fáceis de testar.
 */

/** "5 pessoas · até 1 fora por dia", "1 pessoa", "Ninguém ainda". */
export function resumo(departamento: DepartamentoResumo): string {
  const { pessoas, limiteFora } = departamento;
  if (pessoas === 0) {
    return 'Ninguém ainda';
  }
  const quantas = pessoas === 1 ? '1 pessoa' : `${pessoas} pessoas`;
  // Sem a EMP-01 ligada na empresa, não há limite para mostrar.
  return limiteFora === null ? quantas : `${quantas} · até ${limiteFora} fora por dia`;
}

/** "Engenharia ainda tem 5 pessoas. Para excluir, ..." (só quando há gente no departamento). */
export function avisoDeExclusao(departamento: DepartamentoResumo): string | null {
  const { pessoas, nome } = departamento;
  if (pessoas === 0) {
    return null;
  }
  return pessoas === 1
    ? `${nome} ainda tem 1 pessoa. Para excluir, essa pessoa precisa antes mudar de departamento.`
    : `${nome} ainda tem ${pessoas} pessoas. Para excluir, essas pessoas precisam antes mudar de departamento.`;
}

/** O nome digitado, sem espaços nas pontas; ou a mensagem de erro (a mesma da API). */
export function validarNome(nome: string): { nome: string } | { erro: string } {
  const limpo = nome.trim();
  if (!limpo) {
    return { erro: 'Informe o nome do departamento.' };
  }
  if (limpo.length > 100) {
    return { erro: 'O nome pode ter no máximo 100 caracteres.' };
  }
  return { nome: limpo };
}

/**
 * A frase para mostrar quando a API recusa. Ordem: o erro do campo "nome" (400),
 * depois o detail (409 "Já existe um departamento chamado..."), e por fim uma frase genérica.
 */
export function mensagemDoErro(resposta: HttpErrorResponse, generica: string): string {
  const problema = resposta.error as Problema | null;
  return problema?.erros?.['nome'] ?? problema?.detail ?? generica;
}
