import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { Sessao } from './sessao';

/**
 * Um INTERCEPTOR passa por toda requisição feita com o HttpClient, antes de ela sair.
 *
 * 1. Coloca o cabeçalho "Authorization: Bearer <token>" nas chamadas para a nossa API.
 *    Assim nenhum serviço precisa lembrar de fazer isso.
 * 2. Se a API responder 401 (token vencido ou inválido), encerra a sessão e volta para o login.
 */
export const autenticacaoInterceptor: HttpInterceptorFn = (requisicao, proximo) => {
  const sessao = inject(Sessao);
  const router = inject(Router);

  const token = sessao.token();
  const comToken =
    token && requisicao.url.startsWith('/api/')
      ? requisicao.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : requisicao;

  return proximo(comToken).pipe(
    catchError((erro: HttpErrorResponse) => {
      // No próprio login, 401 é "senha errada": quem trata é a tela de login.
      if (erro.status === 401 && !requisicao.url.endsWith('/auth/login')) {
        sessao.sair();
        router.navigate(['/login']);
      }
      return throwError(() => erro);
    }),
  );
};
