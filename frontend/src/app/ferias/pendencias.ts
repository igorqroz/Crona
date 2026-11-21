import { inject, Injectable, signal } from '@angular/core';

import { trocasEsperandoMim } from '../paginas/trocas/trocas.regras';
import { Sessao } from '../nucleo/sessao';
import { FeriasApi } from './ferias-api';

/**
 * O que está esperando a pessoa logada, para o número ao lado do item do menu.
 *
 * É ESTADO COMPARTILHADO: o menu (Casca) lê o signal, e as telas que mudam alguma coisa
 * (aceitar ou recusar uma troca, por exemplo) chamam atualizar(). Como o serviço é um só
 * para a aplicação inteira ({providedIn: 'root'}), todos enxergam o mesmo valor.
 */
@Injectable({ providedIn: 'root' })
export class Pendencias {
  private readonly api = inject(FeriasApi);
  private readonly sessao = inject(Sessao);

  /** Quantas trocas estão esperando a minha resposta. */
  readonly trocas = signal(0);

  atualizar(): void {
    const eu = this.sessao.usuario();
    if (!eu) {
      this.trocas.set(0);
      return;
    }
    this.api.negociacoes().subscribe({
      next: (negociacoes) => this.trocas.set(trocasEsperandoMim(negociacoes, eu.id)),
      error: () => {}, // o número do menu é só um aviso: se falhar, a tela continua funcionando
    });
  }
}
