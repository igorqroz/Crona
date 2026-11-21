import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Perfil } from './modelos';
import { Sessao } from './sessao';

/**
 * Guard que só deixa entrar quem tem um dos perfis: perfilGuard('GESTOR', 'ADMIN').
 * É uma FÁBRICA: uma função que devolve o guard já configurado com os perfis.
 *
 * Como o logadoGuard, é conforto: quem barra de verdade é a API (@PreAuthorize responde 403).
 */
export function perfilGuard(...perfis: Perfil[]): CanActivateFn {
  return () => {
    const usuario = inject(Sessao).usuario();
    return usuario && perfis.includes(usuario.perfil) ? true : inject(Router).createUrlTree(['/inicio']);
  };
}
