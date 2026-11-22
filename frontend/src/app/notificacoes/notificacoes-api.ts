import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { Notificacoes } from '../nucleo/modelos';

/** As chamadas de /notificacoes (Fase 8b). */
@Injectable({ providedIn: 'root' })
export class NotificacoesApi {
  private readonly http = inject(HttpClient);

  minhas() {
    return this.http.get<Notificacoes>('/api/notificacoes');
  }

  marcarComoLida(id: number) {
    return this.http.post<void>(`/api/notificacoes/${id}/lida`, null);
  }

  marcarTodasComoLidas() {
    return this.http.post<void>('/api/notificacoes/lidas', null);
  }
}
