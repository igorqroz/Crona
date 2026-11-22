import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { Pendencias } from '../ferias/pendencias';
import { Notificacoes } from '../nucleo/modelos';
import { Sino } from './sino';

/*
 * O primeiro teste de COMPONENTE do Crona: o sino é criado de verdade (com o template), mas o
 * serviço Pendencias é trocado por um falso. Assim o teste não depende da API.
 * É a mesma ideia do @MockitoBean no Spring.
 */
describe('Sino', () => {
  const notificacoes = signal<Notificacoes>({
    naoLidas: 2,
    itens: [
      { id: 1, tipo: 'SOLICITACAO_PENDENTE', mensagem: 'Ana Lima pediu férias de 11/01 a 25/01.', link: '/aprovacoes', lida: false, criadaEm: new Date().toISOString() },
      { id: 2, tipo: 'TROCA_RECEBIDA', mensagem: 'Bruno Teles propôs trocar férias com você.', link: '/trocas', lida: false, criadaEm: new Date().toISOString() },
      { id: 3, tipo: 'SOLICITACAO_DECIDIDA', mensagem: 'O RH confirmou suas férias.', link: '/inicio', lida: true, criadaEm: new Date().toISOString() },
    ],
  });
  const falso = {
    notificacoes,
    atualizar: vi.fn(),
    marcarComoLida: vi.fn(),
    marcarTodasComoLidas: vi.fn(),
  };

  function criar() {
    TestBed.configureTestingModule({
      imports: [Sino],
      providers: [provideRouter([]), { provide: Pendencias, useValue: falso }],
    });
    const fixture = TestBed.createComponent(Sino);
    fixture.detectChanges();
    return { fixture, elemento: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => vi.clearAllMocks());

  it('mostra o número de não lidas no sino', () => {
    const { elemento } = criar();

    expect(elemento.querySelector('.contador')?.textContent?.trim()).toBe('2');
    expect(elemento.querySelector('.painel')).toBeNull(); // fechado
  });

  it('ao clicar, abre o painel com as notificações (as não lidas destacadas) e busca as mais recentes', () => {
    const { fixture, elemento } = criar();

    (elemento.querySelector('.botao-sino') as HTMLButtonElement).click();
    fixture.detectChanges();

    const itens = elemento.querySelectorAll('.item');
    expect(itens).toHaveLength(3);
    expect(elemento.querySelectorAll('.item.nao-lida')).toHaveLength(2);
    expect(itens[0].textContent).toContain('Ana Lima pediu férias');
    expect(falso.atualizar).toHaveBeenCalled();
  });

  it('clicar numa notificação marca como lida e abre a tela dela', () => {
    const { fixture, elemento } = criar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    (elemento.querySelector('.botao-sino') as HTMLButtonElement).click();
    fixture.detectChanges();

    (elemento.querySelectorAll('.item')[1] as HTMLButtonElement).click();

    expect(falso.marcarComoLida).toHaveBeenCalledWith(2);
    expect(navegar).toHaveBeenCalledWith('/trocas');
  });

  it('"marcar todas como lidas"', () => {
    const { fixture, elemento } = criar();
    (elemento.querySelector('.botao-sino') as HTMLButtonElement).click();
    fixture.detectChanges();

    (elemento.querySelector('.marcar') as HTMLButtonElement).click();

    expect(falso.marcarTodasComoLidas).toHaveBeenCalled();
  });
});
