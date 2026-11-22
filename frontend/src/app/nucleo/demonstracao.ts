import { InjectionToken, isDevMode } from '@angular/core';

import { DEMONSTRACAO_NO_BUILD } from './demonstracao.build';

/**
 * Modo de demonstração: a tela de login mostra as contas de exemplo para quem abre o site
 * pela primeira vez (ex.: quem chega pelo link do LinkedIn e não sabe o que digitar).
 *
 * Fica ligado:
 *  - no `npm start` (isDevMode), como antes;
 *  - nos builds feitos com a configuração "demo" (Render e docker compose), que trocam o
 *    arquivo demonstracao.build.ts pelo demonstracao.build.demo.ts (ver angular.json).
 * Um build normal (`ng build`) fica sem: uma empresa de verdade não veria essas contas.
 *
 * É um InjectionToken (e não uma constante solta) para o teste poder ligar e desligar.
 */
export const DEMONSTRACAO = new InjectionToken<boolean>('DEMONSTRACAO', {
  providedIn: 'root',
  factory: () => isDevMode() || DEMONSTRACAO_NO_BUILD,
});

export interface ContaDeDemonstracao {
  nome: string;
  perfil: string;
  email: string;
}

/** As contas dos dados de exemplo (backend/src/main/resources/db/dev). */
export const CONTAS_DE_DEMONSTRACAO: readonly ContaDeDemonstracao[] = [
  { nome: 'Ana Lima', perfil: 'Colaboradora', email: 'ana.lima@demo.crona' },
  { nome: 'Roberto Neves', perfil: 'Gestor', email: 'roberto.neves@demo.crona' },
  { nome: 'Rafael Costa', perfil: 'RH', email: 'rafael.costa@demo.crona' },
];

export const SENHA_DE_DEMONSTRACAO = 'crona123';
