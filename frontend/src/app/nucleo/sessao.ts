import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { LoginResposta } from './modelos';

const CHAVE = 'crona.sessao';

/**
 * A sessão de quem está logado: o token e os dados do usuário.
 *
 * O estado fica num SIGNAL: quem lê `usuario()` num template é atualizado sozinho quando a
 * sessão muda (login ou logout). E fica guardado no localStorage do navegador, para o login
 * sobreviver a um F5. O token vale 8 horas (definido no back-end); depois disso, a sessão
 * é descartada.
 *
 * {providedIn: 'root'} = existe uma instância só, compartilhada pela aplicação inteira.
 */
@Injectable({ providedIn: 'root' })
export class Sessao {
  private readonly http = inject(HttpClient);
  private readonly atual = signal<LoginResposta | null>(lerDoNavegador());

  /** Quem está logado, ou null. */
  readonly usuario = computed(() => this.atual()?.usuario ?? null);

  /** O token para o cabeçalho Authorization, ou null se não há sessão (ou ela expirou). */
  token(): string | null {
    const sessao = this.atual();
    if (!sessao) {
      return null;
    }
    if (expirou(sessao)) {
      this.sair();
      return null;
    }
    return sessao.token;
  }

  entrar(email: string, senha: string): Observable<LoginResposta> {
    return this.http.post<LoginResposta>('/api/auth/login', { email, senha }).pipe(
      tap((resposta) => {
        localStorage.setItem(CHAVE, JSON.stringify(resposta));
        this.atual.set(resposta);
      }),
    );
  }

  sair(): void {
    localStorage.removeItem(CHAVE);
    this.atual.set(null);
  }
}

function lerDoNavegador(): LoginResposta | null {
  try {
    const salva = JSON.parse(localStorage.getItem(CHAVE) ?? 'null') as LoginResposta | null;
    return salva && !expirou(salva) ? salva : null;
  } catch {
    return null; // algo estranho guardado: começa sem sessão
  }
}

function expirou(sessao: LoginResposta): boolean {
  return new Date(sessao.expiraEm).getTime() <= Date.now();
}
