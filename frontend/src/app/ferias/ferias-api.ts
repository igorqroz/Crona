import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { FeriasDoColega, Negociacao, PeriodoAquisitivo, Solicitacao } from '../nucleo/modelos';

/**
 * As chamadas da API de férias do colaborador (/minhas-ferias/...).
 *
 * Cada método devolve um Observable: a requisição só sai quando alguém faz subscribe.
 * O token não aparece aqui: quem coloca é o autenticacaoInterceptor.
 * O prefixo /api é tirado pelo proxy do Angular (proxy.conf.json) antes de chegar ao Spring.
 */
@Injectable({ providedIn: 'root' })
export class FeriasApi {
  private readonly http = inject(HttpClient);

  periodos() {
    return this.http.get<PeriodoAquisitivo[]>('/api/minhas-ferias/periodos-aquisitivos');
  }

  solicitacoes() {
    return this.http.get<Solicitacao[]>('/api/minhas-ferias/solicitacoes');
  }

  negociacoes() {
    return this.http.get<Negociacao[]>('/api/minhas-ferias/negociacoes');
  }

  feriasDaEquipe() {
    return this.http.get<FeriasDoColega[]>('/api/minhas-ferias/equipe');
  }

  aceitarTroca(id: number) {
    return this.http.post<Negociacao>(`/api/minhas-ferias/negociacoes/${id}/aceitar`, null);
  }

  recusarTroca(id: number, mensagem?: string) {
    return this.http.post<Negociacao>(`/api/minhas-ferias/negociacoes/${id}/recusar`, { mensagem });
  }
}
