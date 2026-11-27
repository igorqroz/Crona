import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { DepartamentoResumo, Referencia } from '../nucleo/modelos';

/**
 * As chamadas das telas de Cadastros (só o RH, perfil ADMIN).
 * Quem pode chamar é decidido na API (@PreAuthorize): aqui é só HTTP.
 */
@Injectable({ providedIn: 'root' })
export class CadastrosApi {
  private readonly http = inject(HttpClient);

  // ------------------------------------------------------------------ departamentos

  departamentos() {
    return this.http.get<DepartamentoResumo[]>('/api/departamentos');
  }

  criarDepartamento(nome: string) {
    return this.http.post<Referencia>('/api/departamentos', { nome });
  }

  renomearDepartamento(id: number, nome: string) {
    return this.http.put<Referencia>(`/api/departamentos/${id}`, { nome });
  }

  excluirDepartamento(id: number) {
    return this.http.delete<void>(`/api/departamentos/${id}`);
  }
}
