import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
  CalendarioDaGestao,
  Decisao,
  DetalheDaAprovacao,
  FeriasAprovada,
  ItemDeAprovacao,
  Negociacao,
  Solicitacao,
  TrocaParaAprovar,
} from '../nucleo/modelos';

/**
 * As chamadas do GESTOR e do RH (/aprovacoes, /confirmacoes-rh e /ferias-aprovadas).
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

  // ------------------------------------------------------------------ calendário da equipe (Fase 8c)

  calendario(de: string, ate: string) {
    return this.http.get<CalendarioDaGestao>('/api/gestao/calendario', { params: { de, ate } });
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

  // ------------------------------------------------------------------ RH: cancelar férias aprovadas

  /** As férias aprovadas que ainda não começaram (as únicas que dá para cancelar). */
  aprovadas() {
    return this.http.get<FeriasAprovada[]>('/api/ferias-aprovadas');
  }

  cancelarAprovada(id: number, motivo: string) {
    return this.http.post<Solicitacao>(`/api/ferias-aprovadas/${id}/cancelar`, { motivo });
  }
}
