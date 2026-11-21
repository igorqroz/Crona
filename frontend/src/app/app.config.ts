import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { autenticacaoInterceptor } from './nucleo/autenticacao.interceptor';

// Datas e números no formato brasileiro (21/11/2026, 1.234,56) nos pipes do Angular.
registerLocaleData(localePt);

/**
 * A configuração da aplicação: o que fica disponível para todos os componentes.
 * - o roteador, com as rotas de app.routes.ts;
 * - o HttpClient, passando cada requisição pelo interceptor que coloca o token.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([autenticacaoInterceptor])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
  ],
};
