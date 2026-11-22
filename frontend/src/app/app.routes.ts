import { Routes } from '@angular/router';

import { Casca } from './layout/casca';
import { logadoGuard } from './nucleo/logado.guard';
import { perfilGuard } from './nucleo/perfil.guard';

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
      {
        path: 'solicitar',
        title: 'Solicitar férias · Crona',
        loadComponent: () => import('./paginas/solicitar/solicitar').then((m) => m.Solicitar),
      },
      {
        path: 'trocas',
        title: 'Trocas de período · Crona',
        loadComponent: () => import('./paginas/trocas/trocas').then((m) => m.Trocas),
      },
      {
        // Só gestor e RH. O perfilGuard manda os outros de volta para o Início.
        path: 'aprovacoes',
        title: 'Aprovações · Crona',
        canActivate: [perfilGuard('GESTOR', 'ADMIN')],
        loadComponent: () => import('./gestao/aprovacoes').then((m) => m.Aprovacoes),
      },
      {
        path: 'calendario-equipe',
        title: 'Calendário da equipe · Crona',
        canActivate: [perfilGuard('GESTOR', 'ADMIN')],
        loadComponent: () => import('./gestao/calendario-equipe').then((m) => m.CalendarioEquipe),
      },
      // As próximas telas. Por enquanto, um aviso de "em breve".
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
