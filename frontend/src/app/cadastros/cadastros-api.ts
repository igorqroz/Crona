import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';

import {
  Colaborador,
  DadosDoColaborador,
  DepartamentoResumo,
  FiltrosDeColaboradores,
  Pagina,
  Referencia,
} from '../nucleo/modelos';

/** Quantos colaboradores por página (o mesmo padrão da API). */
export const POR_PAGINA = 20;

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

  // ------------------------------------------------------------------ colaboradores

  /** A lista paginada, com os filtros da tela. */
  colaboradores(filtros: FiltrosDeColaboradores) {
    let params = new HttpParams()
      .set('page', filtros.pagina)
      .set('size', POR_PAGINA)
      .set('sort', 'nome,asc');
    if (filtros.busca.trim()) {
      params = params.set('busca', filtros.busca.trim());
    }
    if (filtros.departamentoId !== null) {
      params = params.set('departamentoId', filtros.departamentoId);
    }
    if (filtros.ativo !== null) {
      params = params.set('ativo', filtros.ativo);
    }
    return this.http.get<Pagina<Colaborador>>('/api/colaboradores', { params });
  }

  /**
   * Só o total de uma pesquisa (pede uma página de 1 item e lê o "total").
   * Usado nos contadores "Ativos · 6" e para saber quantas pessoas alguém tem na equipe.
   */
  contar(filtros: { busca?: string; departamentoId?: number | null; ativo?: boolean | null; gestorId?: number }) {
    let params = new HttpParams().set('size', 1);
    if (filtros.busca?.trim()) {
      params = params.set('busca', filtros.busca.trim());
    }
    if (filtros.departamentoId != null) {
      params = params.set('departamentoId', filtros.departamentoId);
    }
    if (filtros.ativo != null) {
      params = params.set('ativo', filtros.ativo);
    }
    if (filtros.gestorId != null) {
      params = params.set('gestorId', filtros.gestorId);
    }
    return this.http.get<Pagina<Colaborador>>('/api/colaboradores', { params }).pipe(map((p) => p.total));
  }

  /** Quem pode ser gestor: GESTOR e ADMIN ativos (para o select do painel). */
  gestores() {
    const params = new HttpParams()
      .append('perfil', 'GESTOR')
      .append('perfil', 'ADMIN')
      .set('ativo', true)
      .set('size', 200)
      .set('sort', 'nome,asc');
    return this.http.get<Pagina<Colaborador>>('/api/colaboradores', { params }).pipe(map((p) => p.itens));
  }

  cadastrarColaborador(dados: DadosDoColaborador) {
    return this.http.post<Colaborador>('/api/colaboradores', dados);
  }

  alterarColaborador(id: number, dados: DadosDoColaborador) {
    return this.http.put<Colaborador>(`/api/colaboradores/${id}`, dados);
  }

  desativarColaborador(id: number) {
    return this.http.post<Colaborador>(`/api/colaboradores/${id}/desativar`, {});
  }

  reativarColaborador(id: number) {
    return this.http.post<Colaborador>(`/api/colaboradores/${id}/reativar`, {});
  }
}
