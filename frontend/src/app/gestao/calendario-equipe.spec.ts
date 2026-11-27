import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { CalendarioDaGestao } from '../nucleo/modelos';
import { AprovacoesApi } from './aprovacoes-api';
import { CalendarioEquipe } from './calendario-equipe';

/*
 * Tela "Calendário da equipe" do gestor e do RH (teste de componente).
 *
 * A grade (colunas, barras, "acima do limite") já é testada em calendario-equipe.regras.spec.ts.
 * Aqui o foco é a tela: qual período ela pede para a API, a navegação e o que aparece.
 *
 * "Hoje" é quarta, 03/03/2027: a janela começa na segunda, 01/03.
 */
describe('CalendarioEquipe', () => {
  /** 6 semanas a partir de 01/03. Na Engenharia (limite 1), a Ana e o Bruno se cruzam de 10 a 12/03. */
  const calendario: CalendarioDaGestao = {
    de: '2027-03-01',
    ate: '2027-04-11',
    departamentos: [
      {
        id: 1,
        nome: 'Engenharia',
        ativos: 5,
        limite: 1,
        pessoas: [
          {
            id: 11,
            nome: 'Ana Lima',
            ausencias: [{ pedidoId: 1, inicio: '2027-03-08', fim: '2027-03-12', pendente: false }],
          },
          {
            id: 12,
            nome: 'Bruno Teles',
            ausencias: [{ pedidoId: 2, inicio: '2027-03-10', fim: '2027-03-12', pendente: true }],
          },
        ],
      },
    ],
    restricoes: [
      {
        motivo: 'Fechamento contábil',
        departamentoId: null,
        inicio: '2027-03-29',
        fim: '2027-03-31',
        bloqueia: true,
      },
    ],
    feriados: [],
  };

  let api: { calendario: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2027, 2, 3, 10, 0)); // quarta, 03/03/2027
    api = { calendario: vi.fn(() => of(calendario)) };
  });

  afterEach(() => vi.useRealTimers());

  function criar() {
    TestBed.configureTestingModule({
      imports: [CalendarioEquipe],
      providers: [{ provide: AprovacoesApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(CalendarioEquipe);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    /** Clica no botão pelo texto ("Hoje") ou, nas setas (só ícone), pelo nome acessível. */
    const clicar = (texto: string) => {
      const botao = [...elemento.querySelectorAll('button')].find(
        (b) => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
      )!;
      botao.click();
      fixture.detectChanges();
    };
    const linhaDe = (nome: string) =>
      [...elemento.querySelectorAll('.linha.pessoa')].find((l) => l.textContent?.includes(nome))!;
    return { fixture, elemento, clicar, linhaDe };
  }

  describe('o período pedido para a API', () => {
    it('ao abrir: 6 semanas a partir da segunda-feira desta semana', () => {
      criar();

      expect(api.calendario).toHaveBeenCalledWith('2027-03-01', '2027-04-11');
    });

    it('trocar a janela: 2 semanas e trimestre', () => {
      const { clicar, elemento } = criar();

      clicar('2 semanas');
      expect(api.calendario).toHaveBeenLastCalledWith('2027-03-01', '2027-03-14');
      expect(elemento.querySelector('[aria-pressed="true"]')?.textContent?.trim()).toBe(
        '2 semanas',
      );

      clicar('Trimestre');
      expect(api.calendario).toHaveBeenLastCalledWith('2027-03-01', '2027-05-30'); // 13 semanas
    });

    /*
     * Acessibilidade (P1 do Design): as janelas são botões de alternar, num grupo com nome. O
     * leitor de tela diz "Tamanho da janela, grupo" e, em cada botão, se está apertado ou não.
     */
    it('as janelas são um grupo com nome, e só a janela escolhida fica apertada', () => {
      const { clicar, elemento } = criar();
      const grupo = elemento.querySelector('[role="group"][aria-label="Tamanho da janela"]')!;
      const apertados = () =>
        [...grupo.querySelectorAll('button')].map(
          (b) => `${b.textContent?.trim()}: ${b.getAttribute('aria-pressed')}`,
        );

      expect(apertados()).toEqual(['2 semanas: false', '6 semanas: true', 'Trimestre: false']);

      clicar('Trimestre');
      expect(apertados()).toEqual(['2 semanas: false', '6 semanas: false', 'Trimestre: true']);
    });

    it('as setas andam uma janela inteira, e "Hoje" volta para esta semana', () => {
      const { clicar } = criar();

      clicar('Próximo período');
      expect(api.calendario).toHaveBeenLastCalledWith('2027-04-12', '2027-05-23');

      clicar('Período anterior');
      clicar('Período anterior');
      expect(api.calendario).toHaveBeenLastCalledWith('2027-01-18', '2027-02-28');

      clicar('Hoje');
      expect(api.calendario).toHaveBeenLastCalledWith('2027-03-01', '2027-04-11');
    });
  });

  describe('o que aparece', () => {
    it('o departamento com quantas pessoas e o limite, e cada pessoa com a sua barra', () => {
      const { elemento, linhaDe } = criar();

      const departamento = elemento.querySelector('.linha.departamento .nome')!;
      expect(departamento.textContent).toContain('Engenharia');
      expect(departamento.textContent).toContain('5 pessoas · limite 1');
      expect(linhaDe('Ana Lima').querySelector('.barra')?.textContent?.trim()).toBe('08–12/03');
    });

    it('um pedido em análise aparece diferente, e o título explica', () => {
      const { linhaDe } = criar();

      const barra = linhaDe('Bruno Teles').querySelector('.barra')!;
      expect(barra.classList).toContain('pendente');
      expect(barra.getAttribute('title')).toBe('Bruno Teles: 10–12/03 (em análise)');
      expect(linhaDe('Ana Lima').querySelector('.barra')!.classList).not.toContain('pendente');
    });

    it('os dias acima do limite aparecem com o total de pessoas fora', () => {
      const { elemento } = criar();

      const acima = [...elemento.querySelectorAll('.acima')];
      expect(acima.map((a) => a.textContent?.trim())).toEqual(['2', '2', '2']); // 10, 11 e 12/03
      expect(acima[0].getAttribute('title')).toBe('2 pessoas fora: acima do limite');
    });

    it('o período restrito aparece, dizendo se bloqueia pedidos', () => {
      const { elemento } = criar();

      const restricao = elemento.querySelector('.restricao')!;
      expect(restricao.textContent?.trim()).toBe('Fechamento contábil');
      expect(restricao.classList).toContain('bloqueia');
      expect(restricao.getAttribute('title')).toContain('(bloqueia pedidos)');
    });

    it('a coluna de hoje fica marcada (a 3ª da janela: quarta)', () => {
      const { linhaDe } = criar();

      const dias = [...linhaDe('Ana Lima').querySelectorAll('.dia')];
      expect(dias.findIndex((d) => d.classList.contains('hoje'))).toBe(2);
    });

    it('ninguém fora no período: diz isso', () => {
      api.calendario.mockReturnValue(of({ ...calendario, departamentos: [] }));
      const { elemento } = criar();

      expect(elemento.textContent).toContain('Ninguém da equipe está fora neste período.');
    });

    it('erro ao carregar: avisa, e "Tentar de novo" busca outra vez', () => {
      api.calendario.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
      const { fixture, elemento, linhaDe } = criar();

      expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
        'Não foi possível carregar o calendário.',
      );
      elemento.querySelector<HTMLAnchorElement>('[role="alert"] a')!.click();
      fixture.detectChanges();

      expect(linhaDe('Ana Lima')).toBeDefined();
    });
  });
});
