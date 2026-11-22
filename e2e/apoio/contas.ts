/**
 * As contas dos dados de exemplo (backend/src/main/resources/db/dev/R__dados_desenvolvimento.sql).
 * Só existem no Crona da sua máquina, no docker compose e no CI.
 */
export interface Conta {
  nome: string;
  email: string;
  senha: string;
}

const SENHA = 'crona123';

export const ANA: Conta = { nome: 'Ana Lima', email: 'ana.lima@demo.crona', senha: SENHA }; // colaboradora
export const ROBERTO: Conta = { nome: 'Roberto Neves', email: 'roberto.neves@demo.crona', senha: SENHA }; // gestor da Ana
export const RAFAEL: Conta = { nome: 'Rafael Costa', email: 'rafael.costa@demo.crona', senha: SENHA }; // RH (ADMIN)

/** "Ana Lima" → "Ana" (é como o Início cumprimenta: "Olá, Ana"). */
export function primeiroNome(conta: Conta): string {
  return conta.nome.split(' ')[0];
}
