import { Routes } from '@angular/router';

import { Casca } from './layout/casca';
import { logadoGuard } from './nucleo/logado.guard';

/**
 * As rotas do Crona.
 *
 * - /login fica fora da casca (não tem menu lateral).
 * - Tudo o mais fica DENTRO da casca e protegido pelo logadoGuard: sem login, volta para /login.
 * - loadComponent = "lazy loading": o código de cada página só é baixado quando ela é aberta,
 *   então a primeira carga do site fica menor.
 */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Entrar · Crona',
    loadComponent: () => import('./paginas/login/login').then((m) => m.Login),
  },
  {
    path: '',
    component: Casca,
    canActivate: [logadoGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      {
        path: 'inicio',
        title: 'Início · Crona',
        loadComponent: () => import('./paginas/inicio/inicio').then((m) => m.Inicio),
      },
      // As próximas telas da Fase 7 (7b, 7c...). Por enquanto, um aviso de "em breve".
      {
        path: 'solicitar',
        title: 'Solicitar férias · Crona',
        loadComponent: () => import('./paginas/em-breve').then((m) => m.EmBreve),
        data: { titulo: 'Solicitar férias' },
      },
      {
        path: 'trocas',
        title: 'Trocas de período · Crona',
        loadComponent: () => import('./paginas/em-breve').then((m) => m.EmBreve),
        data: { titulo: 'Trocas de período' },
      },
      {
        path: 'historico',
        title: 'Histórico · Crona',
        loadComponent: () => import('./paginas/em-breve').then((m) => m.EmBreve),
        data: { titulo: 'Histórico e avisos' },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
