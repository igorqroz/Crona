import { inject, Injectable, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';

import { AprovacoesApi } from '../gestao/aprovacoes-api';
import { trocasEsperandoMim } from '../paginas/trocas/trocas.regras';
import { Sessao } from '../nucleo/sessao';
import { FeriasApi } from './ferias-api';

/**
 * O que está esperando a pessoa logada, para os números ao lado dos itens do menu.
 *
 * É ESTADO COMPARTILHADO: o menu (Casca) lê os signals, e as telas que mudam alguma coisa
 * (aceitar uma troca, aprovar um pedido...) chamam atualizar(). Como o serviço é um só
 * para a aplicação inteira ({providedIn: 'root'}), todos enxergam o mesmo valor.
 */
@Injectable({ providedIn: 'root' })
export class Pendencias {
  private readonly ferias = inject(FeriasApi);
  private readonly aprovacoesApi = inject(AprovacoesApi);
  private readonly sessao = inject(Sessao);

  /** Quantas trocas estão esperando a minha resposta (como colega). */
  readonly trocas = signal(0);
  /** Gestor e RH: pedidos + trocas da equipe esperando decisão (+ confirmações do RH, para o ADMIN). */
  readonly aprovacoes = signal(0);

  atualizar(): void {
    const eu = this.sessao.usuario();
    if (!eu) {
      this.trocas.set(0);
      this.aprovacoes.set(0);
      return;
    }

    // Os números do menu são só um aviso: se a API falhar, a tela continua funcionando.
    this.ferias.negociacoes().subscribe({
      next: (negociacoes) => this.trocas.set(trocasEsperandoMim(negociacoes, eu.id)),
      error: () => {},
    });

    if (eu.perfil === 'COLABORADOR') {
      this.aprovacoes.set(0);
      return;
    }
    forkJoin({
      pedidos: this.aprovacoesApi.fila(),
      trocas: this.aprovacoesApi.trocas(),
      rh: eu.perfil === 'ADMIN' ? this.aprovacoesApi.filaDoRh() : of([]),
    }).subscribe({
      next: ({ pedidos, trocas, rh }) => this.aprovacoes.set(pedidos.length + trocas.length + rh.length),
      error: () => {},
    });
  }
}
