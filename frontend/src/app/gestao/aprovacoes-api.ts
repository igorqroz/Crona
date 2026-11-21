import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { Decisao, DetalheDaAprovacao, ItemDeAprovacao, Negociacao, Solicitacao, TrocaParaAprovar } from '../nucleo/modelos';

/**
 * As chamadas do GESTOR e do RH (/aprovacoes e /confirmacoes-rh).
 * Quem pode chamar quem é decidido na API (@PreAuthorize): aqui é só HTTP.
 */
@Injectable({ providedIn: 'root' })
export class AprovacoesApi {
  private readonly http = inject(HttpClient);

  // ------------------------------------------------------------------ pedidos

  /** A fila inteira; a tela filtra (com/sem conflito) sem pedir de novo. */
  fila() {
    return this.http.get<ItemDeAprovacao[]>('/api/aprovacoes');
  }

  detalhe(id: number) {
    return this.http.get<DetalheDaAprovacao>(`/api/aprovacoes/${id}`);
  }

  aprovar(id: number, decisao: Decisao) {
    return this.http.post<Solicitacao>(`/api/aprovacoes/${id}/aprovar`, decisao);
  }

  recusar(id: number, mensagem: string) {
    return this.http.post<Solicitacao>(`/api/aprovacoes/${id}/recusar`, { mensagem });
  }

  aprovarSemConflito() {
    return this.http.post<Solicitacao[]>('/api/aprovacoes/aprovar-sem-conflito', null);
  }

  // ------------------------------------------------------------------ trocas

  trocas() {
    return this.http.get<TrocaParaAprovar[]>('/api/aprovacoes/negociacoes');
  }

  aprovarTroca(id: number, decisao: Decisao) {
    return this.http.post<Negociacao>(`/api/aprovacoes/negociacoes/${id}/aprovar`, decisao);
  }

  recusarTroca(id: number, mensagem: string) {
    return this.http.post<Negociacao>(`/api/aprovacoes/negociacoes/${id}/recusar`, { mensagem });
  }

  // ------------------------------------------------------------------ RH (EMP-06)

  filaDoRh() {
    return this.http.get<ItemDeAprovacao[]>('/api/confirmacoes-rh');
  }

  confirmarNoRh(id: number) {
    return this.http.post<Solicitacao>(`/api/confirmacoes-rh/${id}/confirmar`, null);
  }

  recusarNoRh(id: number, mensagem: string) {
    return this.http.post<Solicitacao>(`/api/confirmacoes-rh/${id}/recusar`, { mensagem });
  }
}
