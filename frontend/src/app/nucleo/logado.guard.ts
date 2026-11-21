import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Sessao } from './sessao';

/**
 * Um GUARD decide se uma rota pode ser aberta. Este deixa passar só quem está logado;
 * os outros são mandados para /login.
 *
 * Importante: isto é conforto para o usuário, não segurança. Quem protege os dados de verdade
 * é a API (Spring Security), que confere o token em toda requisição.
 */
export const logadoGuard: CanActivateFn = () => {
  return inject(Sessao).token() ? true : inject(Router).createUrlTree(['/login']);
};
