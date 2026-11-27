import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { Pendencias } from '../ferias/pendencias';
import { Perfil } from '../nucleo/modelos';
import { Sessao } from '../nucleo/sessao';
import { Casca } from './casca';

/** Uma página vazia, só para o roteador ter para onde navegar no teste. */
@Component({ template: '' })
class PaginaVazia {}

/*
 * A "casca" das telas de quem está logado: o menu lateral, os números de pendências,
 * quem está logado e o botão Sair (teste de componente).
 *
 * As permissões de verdade ficam na API e nos guards; aqui o teste confere que o MENU
 * mostra a cada perfil só o que ele pode usar.
 */
describe('Casca', () => {
  const pendencias = {
    trocas: signal(0),
    aprovacoes: signal(0),
    notificacoes: signal({ naoLidas: 0, itens: [] }),
    atualizar: vi.fn(),
    marcarComoLida: vi.fn(),
    marcarTodasComoLidas: vi.fn(),
  };
  const sessao = {
    usuario: signal<{ id: number; nome: string; perfil: Perfil } | null>(null),
    sair: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    pendencias.trocas.set(0);
    pendencias.aprovacoes.set(0);
  });

  afterEach(() => vi.useRealTimers());

  function criar(perfil: Perfil, nome = 'Ana Lima') {
    sessao.usuario.set({ id: 1, nome, perfil });
    TestBed.configureTestingModule({
      imports: [Casca],
      providers: [
        provideRouter([
          { path: 'inicio', component: PaginaVazia },
          { path: 'login', component: PaginaVazia },
        ]),
        { provide: Pendencias, useValue: pendencias },
        { provide: Sessao, useValue: sessao },
      ],
    });
    const fixture = TestBed.createComponent(Casca);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const menu = () =>
      [...elemento.querySelectorAll('nav[aria-label="Principal"] a')].map((a) =>
        a.querySelector('span')?.textContent?.trim(),
      );
    const linkPara = (texto: string) =>
      [...elemento.querySelectorAll('nav a')].find((a) => a.textContent?.includes(texto))!;
    return { fixture, elemento, menu, linkPara };
  }

  describe('o menu de cada perfil', () => {
    it('colaborador: só as telas dele, sem a seção Gestão', () => {
      const { elemento, menu } = criar('COLABORADOR');

      expect(menu()).toEqual([
        'Início',
        'Solicitar férias',
        'Trocas de período',
        'Histórico e avisos',
      ]);
      expect(elemento.textContent).not.toContain('Gestão');
    });

    it('gestor: as telas de colaborador E a seção Gestão (Aprovações e Calendário da equipe)', () => {
      const { elemento, menu } = criar('GESTOR', 'Roberto Neves');

      expect(menu()).toEqual([
        'Início',
        'Solicitar férias',
        'Trocas de período',
        'Histórico e avisos',
        'Aprovações',
        'Calendário da equipe',
      ]);
      expect(elemento.querySelector('.titulo-do-grupo')?.textContent).toBe('Gestão');
    });

    it('RH (ADMIN): também vê a seção Gestão', () => {
      const { menu } = criar('ADMIN', 'Rafael Costa');

      expect(menu()).toContain('Aprovações');
      expect(menu()).toContain('Calendário da equipe');
    });

    it('RH (ADMIN): só ele vê a seção Cadastros, com Departamentos e Colaboradores', () => {
      const { elemento, menu, linkPara } = criar('ADMIN', 'Rafael Costa');

      const grupos = [...elemento.querySelectorAll('.titulo-do-grupo')].map((g) => g.textContent);
      expect(grupos).toEqual(['Gestão', 'Cadastros']);
      expect(menu().slice(-2)).toEqual(['Departamentos', 'Colaboradores']);
      expect(linkPara('Departamentos').getAttribute('href')).toBe('/cadastros/departamentos');
      expect(linkPara('Colaboradores').getAttribute('href')).toBe('/cadastros/colaboradores');
    });

    it.each(['COLABORADOR', 'GESTOR'] as const)('%s: não vê a seção Cadastros', (perfil) => {
      const { elemento, menu } = criar(perfil);

      expect(elemento.textContent).not.toContain('Cadastros');
      expect(menu()).not.toContain('Departamentos');
      expect(menu()).not.toContain('Colaboradores');
    });
  });

  describe('quem está logado', () => {
    it('mostra o nome e o perfil por extenso', () => {
      const { elemento } = criar('ADMIN', 'Rafael Costa');

      expect(elemento.querySelector('.eu-nome')?.textContent).toBe('Rafael Costa');
      expect(elemento.querySelector('.eu-perfil')?.textContent).toBe('Administrador (RH)');
    });

    it('"Sair" encerra a sessão e vai para o login', async () => {
      const { elemento } = criar('COLABORADOR');
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate');

      elemento.querySelector<HTMLButtonElement>('button.sair')!.click();

      expect(sessao.sair).toHaveBeenCalled();
      expect(navegar).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('os números de pendências', () => {
    it('sem nada esperando, não aparece número nenhum', () => {
      const { elemento } = criar('GESTOR');

      expect(elemento.querySelectorAll('.contador')).toHaveLength(0);
    });

    it('trocas e aprovações esperando: o número aparece no item certo, com um texto para o leitor de tela', () => {
      pendencias.trocas.set(2);
      pendencias.aprovacoes.set(5);
      const { linkPara } = criar('GESTOR');

      expect(linkPara('Trocas de período').querySelector('.contador')?.textContent).toBe('2');
      expect(
        linkPara('Trocas de período').querySelector('.contador')?.getAttribute('aria-label'),
      ).toBe('2 esperando você');
      expect(linkPara('Aprovações').querySelector('.contador')?.textContent).toBe('5');
      expect(linkPara('Início').querySelector('.contador')).toBeNull();
    });

    it('o número muda sozinho quando a pendência muda (é um signal)', () => {
      const { fixture, linkPara } = criar('COLABORADOR');

      pendencias.trocas.set(1);
      fixture.detectChanges();

      expect(linkPara('Trocas de período').querySelector('.contador')?.textContent).toBe('1');
    });

    it('a cada troca de tela, busca as pendências de novo', async () => {
      criar('COLABORADOR');

      await TestBed.inject(Router).navigateByUrl('/inicio');

      expect(pendencias.atualizar).toHaveBeenCalledTimes(1);
    });

    it('e a cada minuto, mesmo sem trocar de tela (para uma notificação nova aparecer)', () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
      criar('COLABORADOR');

      vi.advanceTimersByTime(60_000);
      expect(pendencias.atualizar).toHaveBeenCalledTimes(1);

      vi.advanceTimersByTime(120_000);
      expect(pendencias.atualizar).toHaveBeenCalledTimes(3);
    });
  });
});
