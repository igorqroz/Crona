import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { LoginResposta } from './modelos';
import { Sessao } from './sessao';

/*
 * Testes da sessão. O HttpTestingController faz o papel da API: o teste vê qual requisição
 * saiu e decide a resposta, sem precisar do back-end rodando (como o Mockito no Java).
 */
describe('Sessao', () => {
  const daqui8Horas = () => new Date(Date.now() + 8 * 3600_000).toISOString();

  const resposta = (expiraEm: string): LoginResposta => ({
    token: 'abc.def.ghi',
    tipo: 'Bearer',
    expiraEm,
    usuario: { id: 3, nome: 'Ana Lima', perfil: 'COLABORADOR' },
  });

  function criar(): { sessao: Sessao; api: HttpTestingController } {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    return { sessao: TestBed.inject(Sessao), api: TestBed.inject(HttpTestingController) };
  }

  beforeEach(() => localStorage.clear());

  it('entrar: chama o login da API, guarda a sessão e expõe o usuário e o token', () => {
    const { sessao, api } = criar();

    sessao.entrar('ana.lima@demo.crona', 'crona123').subscribe();
    const requisicao = api.expectOne('/api/auth/login');
    expect(requisicao.request.body).toEqual({ email: 'ana.lima@demo.crona', senha: 'crona123' });
    requisicao.flush(resposta(daqui8Horas()));

    expect(sessao.usuario()?.nome).toBe('Ana Lima');
    expect(sessao.token()).toBe('abc.def.ghi');
    expect(localStorage.getItem('crona.sessao')).not.toBeNull();
  });

  it('um F5 não perde o login: a sessão guardada no navegador é lida de novo', () => {
    localStorage.setItem('crona.sessao', JSON.stringify(resposta(daqui8Horas())));

    expect(criar().sessao.usuario()?.nome).toBe('Ana Lima');
  });

  it('sessão vencida guardada no navegador é descartada', () => {
    localStorage.setItem('crona.sessao', JSON.stringify(resposta('2020-01-01T00:00:00Z')));

    const { sessao } = criar();
    expect(sessao.usuario()).toBeNull();
    expect(sessao.token()).toBeNull();
  });

  it('sair: limpa a sessão e o navegador', () => {
    localStorage.setItem('crona.sessao', JSON.stringify(resposta(daqui8Horas())));
    const { sessao } = criar();

    sessao.sair();

    expect(sessao.usuario()).toBeNull();
    expect(localStorage.getItem('crona.sessao')).toBeNull();
  });
});
