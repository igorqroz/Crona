import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import { Colaborador, DepartamentoResumo, FiltrosDeColaboradores, Pagina } from '../nucleo/modelos';
import { CadastrosApi } from './cadastros-api';
import { Colaboradores } from './colaboradores';

/*
 * Tela Cadastros > Colaboradores, do RH (teste de componente).
 *
 * As frases e as contas (faixa da página, selos, validação) já são testadas em
 * colaboradores.regras.spec.ts, e o painel em painel-colaborador.spec.ts. Aqui o foco é a lista:
 * quais filtros vão para a API, a paginação, os estados da tela e o painel abrindo e fechando.
 */
describe('Colaboradores', () => {
  function pessoa(id: number, nome: string, extra: Partial<Colaborador> = {}): Colaborador {
    return {
      id,
      nome,
      email: `${nome.split(' ')[0].toLowerCase()}@demo.crona`,
      perfil: 'COLABORADOR',
      cargo: 'Desenvolvedora',
      dataAdmissao: '2024-01-08',
      ativo: true,
      departamento: { id: 1, nome: 'Engenharia' },
      gestor: { id: 2, nome: 'Roberto Neves' },
      ...extra,
    };
  }

  const ana = pessoa(11, 'Ana Lima');
  const roberto = pessoa(2, 'Roberto Neves', { perfil: 'GESTOR', cargo: 'Gerente', gestor: null });
  const rafael = pessoa(1, 'Rafael Costa', {
    perfil: 'ADMIN',
    departamento: { id: 5, nome: 'RH' },
    gestor: { id: 2, nome: 'Roberto Neves' },
  });
  const marina = pessoa(12, 'Marina Jorge', { ativo: false, cargo: null });

  const departamentos: DepartamentoResumo[] = [
    { id: 1, nome: 'Engenharia', pessoas: 5, limiteFora: 1 },
    { id: 5, nome: 'RH', pessoas: 1, limiteFora: 1 },
  ];

  function pagina(itens: Colaborador[], total = itens.length, totalPaginas = 1): Pagina<Colaborador> {
    return { itens, pagina: 0, tamanho: 20, total, totalPaginas };
  }

  let api: Record<'colaboradores' | 'contar' | 'departamentos' | 'gestores', ReturnType<typeof vi.fn>> &
    Record<string, ReturnType<typeof vi.fn>>;
  let doLink: Record<string, string>;

  beforeEach(() => {
    doLink = {};
    api = {
      colaboradores: vi.fn(() => of(pagina([rafael, ana, roberto]))),
      // 6 ativos e 1 inativo na empresa; sem "ativo", conta todos.
      contar: vi.fn((f: { ativo?: boolean | null; gestorId?: number }) =>
        of(f.gestorId != null ? 0 : f.ativo === true ? 6 : f.ativo === false ? 1 : 7),
      ),
      departamentos: vi.fn(() => of(departamentos)),
      gestores: vi.fn(() => of([rafael, roberto])),
      cadastrarColaborador: vi.fn(),
      alterarColaborador: vi.fn(() => of(ana)),
      desativarColaborador: vi.fn(),
      reativarColaborador: vi.fn(),
    };
  });

  afterEach(() => vi.useRealTimers());

  /** Espera os setTimeout(0) da tela (o foco vai para o lugar depois que o Angular desenha). */
  const esperar = () => new Promise((r) => setTimeout(r, 0));

  function criar() {
    TestBed.configureTestingModule({
      imports: [Colaboradores],
      providers: [
        { provide: CadastrosApi, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(doLink) } } },
      ],
    });
    const fixture = TestBed.createComponent(Colaboradores);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    const botao = (nome: string) =>
      [...elemento.querySelectorAll('button')].find(
        (b) => b.getAttribute('aria-label') === nome || b.textContent?.trim().startsWith(nome),
      ) as HTMLButtonElement | undefined;
    const clicar = (nome: string) => {
      botao(nome)!.click();
      fixture.detectChanges();
    };
    /** Os nomes das linhas da TABELA (os cartões do celular repetem as mesmas pessoas). */
    const nomes = () => [...elemento.querySelectorAll('tbody button.nome')].map((b) => b.textContent?.trim());
    const situacao = () =>
      [...elemento.querySelectorAll('.segmentado button')].map(
        (b) => `${b.textContent?.trim()}: ${b.getAttribute('aria-pressed')}`,
      );
    /** Os filtros do último pedido da lista. */
    const ultimoFiltro = () => api.colaboradores.mock.lastCall![0] as FiltrosDeColaboradores;
    const buscar = (texto: string) => {
      const campo = elemento.querySelector<HTMLInputElement>('input[type="search"]')!;
      campo.value = texto;
      campo.dispatchEvent(new Event('input'));
    };

    return { fixture, elemento, botao, clicar, nomes, situacao, ultimoFiltro, buscar };
  }

  it('ao abrir: só os ativos, a primeira página, o resumo da empresa e os números de cada situação', () => {
    const { elemento, nomes, situacao, ultimoFiltro } = criar();

    expect(ultimoFiltro()).toEqual({ busca: '', departamentoId: null, ativo: true, pagina: 0 });
    expect(elemento.querySelector('.topo-pagina p')?.textContent).toBe('7 pessoas · 6 com acesso ativo');
    expect(situacao()).toEqual(['Ativos · 6: true', 'Inativos · 1: false', 'Todos · 7: false']);
    expect(nomes()).toEqual(['Rafael Costa', 'Ana Lima', 'Roberto Neves']);
  });

  it('cada linha: cargo, departamento, perfil (com o selo), gestor, admissão e situação', () => {
    const { elemento } = criar();

    const linhaDoRafael = [...elemento.querySelectorAll('tbody tr')][0];
    const colunas = [...linhaDoRafael.querySelectorAll('td')].map((td) => td.textContent?.trim());
    expect(colunas.slice(1, 7)).toEqual([
      'Desenvolvedora',
      'RH',
      'Administrador (RH)',
      'Roberto Neves',
      '08/01/2024',
      'Ativo',
    ]);
    expect(linhaDoRafael.querySelector('.selo')?.className).toBe('selo selo-forte');
  });

  it('sem cargo, sem departamento ou sem gestor: um traço; inativo aparece como Inativo', () => {
    api.colaboradores.mockReturnValue(of(pagina([{ ...marina, departamento: null, gestor: null }])));
    const { elemento } = criar();

    const colunas = [...elemento.querySelectorAll('tbody td')].map((td) => td.textContent?.trim());
    expect(colunas.slice(1, 7)).toEqual(['—', '—', 'Colaborador', '—', '08/01/2024', 'Inativo']);
    expect(elemento.querySelector('tbody tr')!.classList).toContain('inativo');
  });

  it('vindo de "Ver as pessoas" (?departamentoId=1): a lista já abre filtrada, com o departamento escolhido', () => {
    doLink = { departamentoId: '1' };
    const { elemento, ultimoFiltro } = criar();

    expect(ultimoFiltro().departamentoId).toBe(1);
    expect(api.contar).toHaveBeenCalledWith({ busca: '', departamentoId: 1, ativo: true });
    expect(elemento.querySelector<HTMLSelectElement>('.filtros select')!.value).toBe('1');
  });

  it('?departamentoId que não é número: ignora e mostra todos', () => {
    doLink = { departamentoId: 'abc' };
    const { ultimoFiltro } = criar();

    expect(ultimoFiltro().departamentoId).toBeNull();
  });

  describe('filtros', () => {
    it('a busca espera a pessoa parar de digitar (300 ms) e pede uma vez só, na página 1', () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
      const { buscar, ultimoFiltro } = criar();
      api.colaboradores.mockClear();

      buscar('a');
      vi.advanceTimersByTime(200);
      buscar('an');
      vi.advanceTimersByTime(299);
      expect(api.colaboradores).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      expect(api.colaboradores).toHaveBeenCalledTimes(1);
      expect(ultimoFiltro()).toMatchObject({ busca: 'an', pagina: 0 });
    });

    it('escolher o departamento filtra e volta para a primeira página', () => {
      api.colaboradores.mockReturnValue(of(pagina([ana], 45, 3)));
      const { fixture, elemento, clicar, ultimoFiltro } = criar();
      clicar('Próxima');
      expect(ultimoFiltro().pagina).toBe(1);

      const select = elemento.querySelector<HTMLSelectElement>('.filtros select')!;
      select.value = '5';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(ultimoFiltro()).toMatchObject({ departamentoId: 5, pagina: 0 });
    });

    it('Inativos e Todos: mudam o "ativo" pedido e o botão apertado', () => {
      const { clicar, situacao, ultimoFiltro } = criar();

      clicar('Inativos');
      expect(ultimoFiltro().ativo).toBe(false);
      expect(situacao()[1]).toBe('Inativos · 1: true');

      clicar('Todos');
      expect(ultimoFiltro().ativo).toBeNull();
      expect(situacao()[2]).toBe('Todos · 7: true');
    });
  });

  describe('paginação', () => {
    it('a faixa do rodapé, e "Anterior" desabilitado na primeira página', () => {
      api.colaboradores.mockReturnValue(of(pagina([ana, roberto], 42, 3)));
      const { elemento, botao } = criar();

      expect(elemento.querySelector('.paginacao .numero')?.textContent?.trim()).toBe('Mostrando 1–2 de 42');
      expect(botao('Anterior')!.disabled).toBe(true);
      expect(botao('Próxima')!.disabled).toBe(false);
    });

    it('"Próxima" pede a página seguinte; na última, ela fica desabilitada', () => {
      api.colaboradores.mockReturnValue(of(pagina([ana], 42, 2)));
      const { clicar, botao, ultimoFiltro } = criar();

      clicar('Próxima');

      expect(ultimoFiltro().pagina).toBe(1);
      expect(botao('Próxima')!.disabled).toBe(true);
      expect(botao('Anterior')!.disabled).toBe(false);
    });
  });

  describe('lista vazia e erro', () => {
    it('ninguém com o filtro: diz isso, e "Limpar busca e filtros" volta ao começo', () => {
      api.colaboradores.mockReturnValue(of(pagina([])));
      const { elemento, clicar, ultimoFiltro } = criar();
      clicar('Inativos');

      expect(elemento.querySelector('.vazio')?.textContent).toContain(
        'Nenhuma pessoa combina com a busca e os filtros escolhidos.',
      );
      clicar('Limpar busca e filtros');

      expect(ultimoFiltro()).toEqual({ busca: '', departamentoId: null, ativo: true, pagina: 0 });
    });

    it('ninguém cadastrado (sem filtro): convida a cadastrar, sem o botão de limpar', () => {
      api.colaboradores.mockReturnValue(of(pagina([])));
      const { elemento, botao } = criar();

      expect(elemento.querySelector('.vazio')?.textContent).toContain('Ainda não há ninguém cadastrado.');
      expect(botao('Limpar busca e filtros')).toBeUndefined();
    });

    it('erro ao carregar: avisa, e "Tentar de novo" busca outra vez', () => {
      api.colaboradores.mockReturnValueOnce(throwError(() => new Error('500')));
      const { elemento, clicar, nomes } = criar();

      expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
        'Não foi possível carregar os colaboradores.',
      );
      clicar('Tentar de novo');

      expect(nomes()).toHaveLength(3);
    });
  });

  describe('o painel', () => {
    it('"Novo colaborador" abre o painel de cadastro', () => {
      const { elemento, clicar } = criar();

      clicar('Novo colaborador');

      expect(elemento.querySelector('[role="dialog"] h2')?.textContent).toBe('Novo colaborador');
    });

    it('o nome (ou "Editar X") abre o painel da pessoa, e a linha fica marcada', () => {
      const { elemento, clicar } = criar();

      clicar('Editar Ana Lima');

      expect(elemento.querySelector('[role="dialog"] h2')?.textContent).toBe('Ana Lima');
      const linhas = [...elemento.querySelectorAll('tbody tr')];
      expect(linhas.map((l) => l.classList.contains('aberta'))).toEqual([false, true, false]);
    });

    it('fechar devolve o foco para o botão que abriu', async () => {
      const { fixture, elemento, botao, clicar } = criar();
      const editar = botao('Editar Ana Lima')!;
      editar.focus();
      clicar('Editar Ana Lima');

      elemento.querySelector<HTMLButtonElement>('[role="dialog"] button[aria-label="Fechar"]')!.click();
      fixture.detectChanges();
      await esperar();

      expect(elemento.querySelector('[role="dialog"]')).toBeNull();
      expect(document.activeElement).toBe(editar);
    });

    it('o painel salvou: fecha, mostra a faixa (com o foco) e recarrega a lista e os números', async () => {
      const { fixture, elemento, clicar } = criar();
      clicar('Editar Ana Lima');
      api.colaboradores.mockClear();
      api.contar.mockClear();

      elemento.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await esperar();

      expect(elemento.querySelector('[role="dialog"]')).toBeNull();
      expect(elemento.querySelector('#feito')?.textContent?.trim()).toBe('Dados de Ana Lima salvos.');
      expect(document.activeElement?.id).toBe('feito');
      expect(api.colaboradores).toHaveBeenCalledTimes(1);
      expect(api.contar).toHaveBeenCalledTimes(4); // os 2 da situação e os 2 do resumo
    });
  });

  /*
   * Respostas fora de ordem (a mesma família do BUG-03): a pessoa clica em "Inativos" e logo em
   * "Todos". Se a resposta de "Inativos" chegar DEPOIS, a tela não pode mostrá-la com "Todos" apertado.
   */
  it('respostas fora de ordem: vale a do ÚLTIMO filtro escolhido', () => {
    const { fixture, clicar, nomes, situacao } = criar();
    const inativos = new Subject<Pagina<Colaborador>>();
    const todos = new Subject<Pagina<Colaborador>>();
    api.colaboradores.mockImplementation((f: FiltrosDeColaboradores) =>
      (f.ativo === false ? inativos : todos) as Observable<Pagina<Colaborador>>,
    );

    clicar('Inativos');
    clicar('Todos');
    todos.next(pagina([rafael, ana, marina, roberto]));
    todos.complete();
    inativos.next(pagina([marina])); // a resposta velha chega por último
    inativos.complete();
    fixture.detectChanges();

    expect(situacao()[2]).toBe('Todos · 7: true');
    expect(nomes()).toEqual(['Rafael Costa', 'Ana Lima', 'Marina Jorge', 'Roberto Neves']);
  });
});
