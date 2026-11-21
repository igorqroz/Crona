import { PeriodoAquisitivo } from '../nucleo/modelos';

/*
 * Regras sobre os períodos aquisitivos usadas em mais de uma tela (Início e Solicitar).
 */

/**
 * O período "da vez": o mais antigo que ainda tem dias para usar (livres ou em pedido).
 * Sem nenhum, o que está em aquisição; sem esse, o último.
 * É a mesma ideia do back-end: usar primeiro o período mais antigo, para não deixar vencer.
 */
export function periodoEmFoco(periodos: PeriodoAquisitivo[]): PeriodoAquisitivo | null {
  return (
    periodos.find((p) => p.situacao !== 'EM_AQUISICAO' && p.livres + p.emPedido > 0) ??
    periodos.find((p) => p.situacao === 'EM_AQUISICAO') ??
    periodos.at(-1) ??
    null
  );
}

/** Os dias que ainda não foram aprovados (em pedido + livres). */
export function saldo(periodo: PeriodoAquisitivo): number {
  return periodo.livres + periodo.emPedido;
}

/** CLT-06: dá para vender até 1/3 do direito (10 dias de 30). */
export function abonoMaximo(periodo: PeriodoAquisitivo | null): number {
  return periodo ? Math.floor(periodo.direito / 3) : 0;
}
