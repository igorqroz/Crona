import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { Pendencias } from '../ferias/pendencias';
import {
  DetalheDaAprovacao,
  FeriasAprovada,
  ItemDeAprovacao,
  Perfil,
  SituacaoDoPedido,
} from '../nucleo/modelos';
import { Sessao } from '../nucleo/sessao';
import { AprovacoesApi } from './aprovacoes-api';
import { Aprovacoes } from './aprovacoes';

/*
 * Tela Aprovações do gestor (teste de componente).
 *
 * Os filtros e os selos já são testados em aprovacoes.regras.spec.ts, e o detalhe em
 * detalhe-pedido.spec.ts. Aqui o foco é a PÁGINA: carregar a fila, escolher um pedido,
 * mandar a decisão para a API, recarregar e mostrar o resultado (ou o erro).
 *
 * A AprovacoesApi, a Sessao e as Pendencias são dublês (fakes).
 */
describe('Aprovacoes', () => {
  function item(id: number, nome: string, situacao: SituacaoDoPedido): ItemDeAprovacao {
    return {
      id,
      colaborador: { id: 10 + id, nome },
      departamento: 'Engenharia',
      inicio: '2099-03-15',
      fim: '2099-03-29',
      diasGozo: 15,
      diasAbono: 0,
      periodo: 1,
      recado: null,
      enviadoEm: '2026-11-20T13:00:00Z',
      situacao,
      alertas:
        situacao === 'SEM_CONFLITO'
          ? []
          : [
              {
                regra: 'EMP-01',
                resultado: 'ATENCAO',
                mensagem: 'O departamento passaria do limite.',
              },
            ],
    };
  }

  const ana = item(1, 'Ana Lima', 'SEM_CONFLITO');
  const bruno = item(2, 'Bruno Teles', 'COM_ATENCAO');
  const carla = item(3, 'Carla Souza', 'SEM_CONFLITO');

  function detalhe(pedido: ItemDeAprovacao): DetalheDaAprovacao {
    return {
      pedido,
      livresNoPeriodo: 30,
      voltaAoTrabalho: '2099-03-30',
      concederAte: '2099-12-31',
      limiteDoDepartamento: 2,
      verificacoes: pedido.alertas,
      colegasNoPeriodo: [],
    };
  }

  function erroHttp(status: number, corpo: object | null = null): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status, error: corpo }));
  }

  let api: Record<
    | 'fila'
    | 'detalhe'
    | 'trocas'
    | 'filaDoRh'
    | 'aprovar'
    | 'recusar'
    | 'aprovarSemConflito'
    | 'confirmarNoRh'
    | 'recusarNoRh'
    | 'aprovadas'
    | 'cancelarAprovada',
    ReturnType<typeof vi.fn>
  >;
  const pendencias = { atualizar: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    const todos = [ana, bruno, carla];
    api = {
      fila: vi.fn(() => of([ana, bruno, carla])),
      detalhe: vi.fn((id: number) => of(detalhe(todos.find((p) => p.id === id)!))),
      trocas: vi.fn(() => of([])),
      filaDoRh: vi.fn(() => of([])),
      aprovar: vi.fn(() => of({})),
      recusar: vi.fn(() => of({})),
      aprovarSemConflito: vi.fn(() => of([{}, {}])),
      confirmarNoRh: vi.fn(() => of({})),
      recusarNoRh: vi.fn(() => of({})),
      aprovadas: vi.fn(() => of([])),
      cancelarAprovada: vi.fn(() => of({})),
    };
  });

  function criar(perfil: Perfil = 'GESTOR') {
    TestBed.configureTestingModule({
      imports: [Aprovacoes],
      providers: [
        { provide: AprovacoesApi, useValue: api },
        { provide: Pendencias, useValue: pendencias },
        {
          provide: Sessao,
          useValue: { usuario: () => ({ id: 2, nome: 'Roberto Neves', perfil }) },
        },
      ],
    });
    const fixture = TestBed.createComponent(Aprovacoes);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    /** O primeiro botão cujo texto começa com `texto` (dentro de `onde`, se informado). */
    const botao = (texto: string, onde: ParentNode = elemento) =>
      [...onde.querySelectorAll('button')].find((b) =>
        b.textContent?.trim().startsWith(texto),
      ) as HTMLButtonElement;
    const clicar = (texto: string, onde?: ParentNode) => {
      botao(texto, onde).click();
      fixture.detectChanges();
    };
    const nomeNoDetalhe = () => elemento.querySelector('crona-detalhe-pedido h2')?.textContent;
    const nomesNaLista = () =>
      [...elemento.querySelectorAll('.lista .item strong')].map((n) => n.textContent);
    const mensagemDeSucesso = () => elemento.querySelector('[role="status"]')?.textContent?.trim();

    return { fixture, elemento, botao, clicar, nomeNoDetalhe, nomesNaLista, mensagemDeSucesso };
  }

  it('ao abrir, lista a fila e já abre o detalhe do primeiro pedido', () => {
    const { elemento, nomesNaLista, nomeNoDetalhe } = criar();

    expect(elemento.querySelector('header')?.textContent).toContain('3 pedidos aguardando');
    expect(nomesNaLista()).toEqual(['Ana Lima', 'Bruno Teles', 'Carla Souza']);
    expect(api.detalhe).toHaveBeenCalledWith(1);
    expect(nomeNoDetalhe()).toBe('Ana Lima');
  });

  it('clicar num pedido da lista abre o detalhe dele', () => {
    const { fixture, elemento, nomeNoDetalhe } = criar();

    const itemDoBruno = [...elemento.querySelectorAll<HTMLButtonElement>('.lista .item')].find(
      (b) => b.textContent?.includes('Bruno Teles'),
    )!;
    itemDoBruno.click();
    fixture.detectChanges();

    expect(api.detalhe).toHaveBeenLastCalledWith(2);
    expect(nomeNoDetalhe()).toBe('Bruno Teles');
  });

  it('o filtro "Com conflito" mostra só os pedidos com alerta e troca o detalhe', () => {
    const { clicar, nomesNaLista, nomeNoDetalhe, botao } = criar();

    expect(botao('Com conflito').textContent).toContain('1');
    clicar('Com conflito');

    expect(nomesNaLista()).toEqual(['Bruno Teles']);
    expect(nomeNoDetalhe()).toBe('Bruno Teles'); // a Ana sumiu da lista: o detalhe acompanha
  });

  it('aprovar: manda a decisão, mostra a confirmação e recarrega a fila', () => {
    const { elemento, clicar, mensagemDeSucesso } = criar();

    clicar('Aprovar', elemento.querySelector('crona-detalhe-pedido')!);

    expect(api.aprovar).toHaveBeenCalledWith(1, { mesmoAssim: false, mensagem: undefined });
    expect(mensagemDeSucesso()).toBe('Pedido de Ana aprovado.');
    expect(api.fila).toHaveBeenCalledTimes(2);
    expect(pendencias.atualizar).toHaveBeenCalled();
  });

  it('depois de decidir, o pedido sai da fila e o detalhe passa para o próximo', () => {
    const { elemento, clicar, nomesNaLista, nomeNoDetalhe } = criar();
    api.fila.mockReturnValue(of([bruno, carla])); // a Ana já foi aprovada

    clicar('Aprovar', elemento.querySelector('crona-detalhe-pedido')!);

    expect(nomesNaLista()).toEqual(['Bruno Teles', 'Carla Souza']);
    expect(nomeNoDetalhe()).toBe('Bruno Teles');
  });

  it('recusar com motivo: manda a mensagem para a API', () => {
    const { fixture, elemento, clicar, mensagemDeSucesso } = criar();
    const caixa = elemento.querySelector<HTMLTextAreaElement>('crona-detalhe-pedido textarea')!;
    caixa.value = 'A equipe fica descoberta nessa semana.';
    caixa.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    clicar('Recusar', elemento.querySelector('crona-detalhe-pedido')!);

    expect(api.recusar).toHaveBeenCalledWith(1, 'A equipe fica descoberta nessa semana.');
    expect(mensagemDeSucesso()).toBe('Pedido de Ana recusado.');
  });

  it('a API recusa a decisão (422): o erro aparece no detalhe e nada é recarregado', () => {
    api.aprovar.mockReturnValue(
      erroHttp(422, {
        detail: 'O pedido tem impedimento.',
        verificacoes: [
          { regra: 'CLT-03', resultado: 'OK', mensagem: 'Não começa perto de feriado.' },
          {
            regra: 'EMP-01',
            resultado: 'IMPEDIMENTO',
            mensagem: 'O departamento passaria do limite.',
          },
        ],
      }),
    );
    const { elemento, clicar, mensagemDeSucesso } = criar();
    const detalhePedido = elemento.querySelector('crona-detalhe-pedido')!;

    clicar('Aprovar', detalhePedido);

    expect(detalhePedido.querySelector('[role="alert"]')?.textContent).toContain(
      'O pedido tem impedimento.',
    );
    // Só as regras que NÃO passaram vão para o checklist do erro.
    const regrasDoErro = [...detalhePedido.querySelectorAll('crona-checklist li')].map((li) =>
      li.textContent?.trim(),
    );
    expect(regrasDoErro).toContain('O departamento passaria do limite.');
    expect(regrasDoErro).not.toContain('Não começa perto de feriado.');
    expect(mensagemDeSucesso()).toBeUndefined();
    expect(api.fila).toHaveBeenCalledTimes(1);
  });

  it('"Aprovar os 2 sem conflito" aprova em lote e mostra quantos', () => {
    const { clicar, mensagemDeSucesso } = criar();

    clicar('Aprovar os 2 sem conflito');

    expect(api.aprovarSemConflito).toHaveBeenCalled();
    expect(mensagemDeSucesso()).toBe('2 pedidos aprovados.');
    expect(api.fila).toHaveBeenCalledTimes(2);
  });

  it('sem pedido sem conflito, o botão de aprovar em lote não aparece', () => {
    api.fila.mockReturnValue(of([bruno]));
    const { botao } = criar();

    expect(botao('Aprovar o')).toBeUndefined();
  });

  it('fila vazia: diz que não há nada para decidir', () => {
    api.fila.mockReturnValue(of([]));
    const { elemento } = criar();

    expect(elemento.querySelector('.lista')?.textContent).toContain('Nenhum pedido aqui.');
    expect(elemento.querySelector('.detalhe')?.textContent).toContain('Nada para decidir agora.');
    expect(api.detalhe).not.toHaveBeenCalled();
  });

  it('erro ao carregar a fila: avisa, e "Tentar de novo" busca outra vez', () => {
    api.fila.mockReturnValueOnce(erroHttp(500));
    const { fixture, elemento, nomesNaLista } = criar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar a fila.',
    );
    elemento.querySelector<HTMLAnchorElement>('[role="alert"] a')!.click();
    fixture.detectChanges();

    expect(nomesNaLista()).toEqual(['Ana Lima', 'Bruno Teles', 'Carla Souza']);
  });

  it('a aba Trocas sem nada: mostra a frase de lista vazia', () => {
    const { elemento, clicar } = criar();

    clicar('Trocas · 0');

    expect(elemento.textContent).toContain('Nenhuma troca esperando você.');
  });

  /*
   * Acessibilidade (P1 do Design): as abas e os filtros são botões de alternar (aria-pressed),
   * cada conjunto num grupo com nome. Não são "tabs" (role="tab"/aria-selected).
   */
  describe('acessibilidade das abas e dos filtros', () => {
    /** Cada botão do grupo como "texto: aria-pressed". */
    function apertados(elemento: HTMLElement, nomeDoGrupo: string) {
      const grupo = elemento.querySelector(`[role="group"][aria-label="${nomeDoGrupo}"]`)!;
      return [...grupo.querySelectorAll('button')].map(
        (b) => `${b.textContent?.trim()}: ${b.getAttribute('aria-pressed')}`,
      );
    }

    it('as abas são um grupo com nome, e só a aba aberta fica apertada', () => {
      const { elemento, clicar } = criar('ADMIN');

      expect(apertados(elemento, 'O que aprovar')).toEqual([
        'Pedidos · 3: true',
        'Trocas · 0: false',
        'Confirmação do RH · 0: false',
        'Aprovadas · 0: false',
      ]);

      clicar('Trocas · 0');
      expect(apertados(elemento, 'O que aprovar')).toEqual([
        'Pedidos · 3: false',
        'Trocas · 0: true',
        'Confirmação do RH · 0: false',
        'Aprovadas · 0: false',
      ]);
    });

    it('os filtros são um grupo com nome, e só o filtro escolhido fica apertado', () => {
      const { elemento, clicar } = criar();

      expect(apertados(elemento, 'Filtrar os pedidos')).toEqual([
        'Todos · 3: true',
        'Com conflito · 1: false',
        'Sem conflito · 2: false',
      ]);

      clicar('Com conflito');
      expect(apertados(elemento, 'Filtrar os pedidos')).toEqual([
        'Todos · 3: false',
        'Com conflito · 1: true',
        'Sem conflito · 2: false',
      ]);
    });

    it('nada na tela se apresenta como aba (role="tab" ou aria-selected)', () => {
      const { elemento } = criar('ADMIN');

      expect(elemento.querySelectorAll('[role="tab"], [role="tablist"], [aria-selected]')).toHaveLength(0);
    });
  });

  describe('confirmação do RH (EMP-06)', () => {
    it('o GESTOR não vê a aba do RH nem busca a fila dela', () => {
      const { botao } = criar('GESTOR');

      expect(botao('Confirmação do RH')).toBeUndefined();
      expect(api.filaDoRh).not.toHaveBeenCalled();
    });

    it('o ADMIN confirma um pedido aprovado pelo gestor', () => {
      api.filaDoRh.mockReturnValue(of([carla]));
      const { elemento, clicar, mensagemDeSucesso } = criar('ADMIN');

      clicar('Confirmação do RH · 1');
      clicar('Confirmar', elemento.querySelector('.cartao.rh')!);

      expect(api.confirmarNoRh).toHaveBeenCalledWith(3);
      expect(mensagemDeSucesso()).toBe('Pedido confirmado pelo RH.');
    });

    it('recusar no RH sem motivo: pede o motivo e não chama a API', () => {
      api.filaDoRh.mockReturnValue(of([carla]));
      const { elemento, clicar } = criar('ADMIN');
      clicar('Confirmação do RH');
      const cartao = elemento.querySelector('.cartao.rh')!;

      clicar('Recusar', cartao); // abre a caixa do motivo
      clicar('Recusar pedido', cartao);

      expect(cartao.querySelector('[role="alert"]')?.textContent).toContain(
        'Escreva o motivo da recusa.',
      );
      expect(api.recusarNoRh).not.toHaveBeenCalled();
    });

    it('recusar no RH com motivo: manda o motivo sem os espaços das pontas', () => {
      api.filaDoRh.mockReturnValue(of([carla]));
      const { fixture, elemento, clicar, mensagemDeSucesso } = criar('ADMIN');
      clicar('Confirmação do RH');
      const cartao = elemento.querySelector('.cartao.rh')!;
      clicar('Recusar', cartao);

      const motivo = cartao.querySelector('textarea')!;
      motivo.value = '  Período de fechamento contábil.  ';
      motivo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      clicar('Recusar pedido', cartao);

      expect(api.recusarNoRh).toHaveBeenCalledWith(3, 'Período de fechamento contábil.');
      expect(mensagemDeSucesso()).toBe('Pedido recusado pelo RH.');
    });
  });

  /*
   * O RH cancela férias já aprovadas (regras-de-negocio.md, seção 4). "Hoje" fica fixo em
   * 01/03/2027: a Ana sai em 04/03 (faltam 3 dias: a tela avisa do pagamento) e o Bruno em
   * 05/03 (faltam 4: sem aviso).
   */
  describe('férias aprovadas (o RH cancela)', () => {
    function aprovada(
      id: number,
      nome: string,
      inicio: string,
      extra: Partial<FeriasAprovada> = {},
    ): FeriasAprovada {
      return {
        id,
        colaborador: { id: 10 + id, nome },
        departamento: 'Engenharia',
        inicio,
        fim: '2027-03-18',
        diasGozo: 15,
        diasAbono: 0,
        aprovadoPor: { id: 2, nome: 'Roberto Neves' },
        bloqueio: null,
        ...extra,
      };
    }

    const anaPerto = aprovada(20, 'Ana Lima', '2027-03-04');
    const brunoLonge = aprovada(21, 'Bruno Teles', '2027-03-05', { diasAbono: 5 });

    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2027, 2, 1, 10, 0)); // 01/03/2027
      api.aprovadas.mockReturnValue(of([anaPerto, brunoLonge]));
    });

    afterEach(() => vi.useRealTimers());

    /** Abre a aba Aprovadas como ADMIN e devolve o cartão de cada pessoa. */
    function abrirAprovadas() {
      const tela = criar('ADMIN');
      tela.clicar('Aprovadas');
      const cartaoDe = (nome: string) =>
        [...tela.elemento.querySelectorAll('.cartao.rh')].find((c) =>
          c.textContent?.includes(nome),
        )!;
      const escreverMotivo = (cartao: Element, texto: string) => {
        const caixa = cartao.querySelector<HTMLTextAreaElement>('textarea')!;
        caixa.value = texto;
        caixa.dispatchEvent(new Event('input'));
        tela.fixture.detectChanges();
      };
      return { ...tela, cartaoDe, escreverMotivo };
    }

    it('o GESTOR não vê a aba nem busca a lista', () => {
      const { botao } = criar('GESTOR');

      expect(botao('Aprovadas')).toBeUndefined();
      expect(api.aprovadas).not.toHaveBeenCalled();
    });

    it('o ADMIN vê a aba com a contagem, quem sai, as datas e quem aprovou', () => {
      const { botao, cartaoDe } = abrirAprovadas();

      expect(botao('Aprovadas').textContent).toContain('Aprovadas · 2');
      expect(cartaoDe('Ana Lima').textContent).toContain('04/03 – 18/03 · 15 dias');
      expect(cartaoDe('Ana Lima').textContent).toContain('Aprovadas por Roberto Neves');
      expect(cartaoDe('Bruno Teles').textContent).toContain('15 dias + 5 de abono');
    });

    it('cancelar sem motivo: pede o motivo e não chama a API', () => {
      const { clicar, cartaoDe } = abrirAprovadas();
      const cartao = cartaoDe('Bruno Teles');

      clicar('Cancelar férias', cartao);
      clicar('Cancelar as férias de Bruno', cartao);

      expect(cartao.querySelector('[role="alert"]')?.textContent).toContain(
        'Escreva o motivo do cancelamento.',
      );
      expect(api.cancelarAprovada).not.toHaveBeenCalled();
    });

    it('cancelar com motivo: manda o motivo sem espaços, mostra o sucesso e recarrega a lista', () => {
      const { clicar, cartaoDe, escreverMotivo, mensagemDeSucesso } = abrirAprovadas();
      const cartao = cartaoDe('Bruno Teles');

      clicar('Cancelar férias', cartao);
      escreverMotivo(cartao, '  Aprovado por engano: datas trocadas  ');
      clicar('Cancelar as férias de Bruno', cartao);

      expect(api.cancelarAprovada).toHaveBeenCalledWith(21, 'Aprovado por engano: datas trocadas');
      expect(mensagemDeSucesso()).toBe(
        'Férias de Bruno canceladas. Os dias voltaram para o saldo e Bruno foi avisado(a).',
      );
      expect(api.aprovadas).toHaveBeenCalledTimes(2);
    });

    it('faltando 3 dias, avisa que o pagamento pode já ter saído; faltando 4, não avisa', () => {
      const { clicar, cartaoDe } = abrirAprovadas();

      clicar('Cancelar férias', cartaoDe('Ana Lima'));
      clicar('Cancelar férias', cartaoDe('Bruno Teles')); // fecha o da Ana e abre o do Bruno
      expect(cartaoDe('Bruno Teles').querySelector('[role="note"]')).toBeNull();

      clicar('Cancelar férias', cartaoDe('Ana Lima'));
      expect(cartaoDe('Ana Lima').querySelector('[role="note"]')?.textContent).toContain(
        'o pagamento das férias pode já ter saído',
      );
    });

    it('com bloqueio: mostra o motivo e não oferece o botão de cancelar', () => {
      api.aprovadas.mockReturnValue(
        of([
          aprovada(22, 'Carla Souza', '2027-04-05', {
            bloqueio: 'Estas férias estão numa troca em aberto.',
          }),
        ]),
      );
      const { botao, cartaoDe } = abrirAprovadas();

      expect(cartaoDe('Carla Souza').textContent).toContain(
        'Estas férias estão numa troca em aberto.',
      );
      expect(botao('Cancelar férias', cartaoDe('Carla Souza'))).toBeUndefined();
    });

    it('a API recusa (409, troca aberta depois que a lista carregou): mostra o motivo no cartão', () => {
      api.cancelarAprovada.mockReturnValue(
        erroHttp(409, {
          detail: 'Estas férias estão numa troca em aberto. Encerre a troca antes de cancelar.',
        }),
      );
      const { clicar, cartaoDe, escreverMotivo, mensagemDeSucesso } = abrirAprovadas();
      const cartao = cartaoDe('Bruno Teles');

      clicar('Cancelar férias', cartao);
      escreverMotivo(cartao, 'Engano');
      clicar('Cancelar as férias de Bruno', cartao);

      expect(cartao.querySelector('[role="alert"]')?.textContent).toContain(
        'Encerre a troca antes de cancelar.',
      );
      expect(mensagemDeSucesso()).toBeUndefined();
    });

    it('"Voltar" fecha o campo do motivo sem chamar a API', () => {
      const { clicar, cartaoDe } = abrirAprovadas();
      const cartao = cartaoDe('Bruno Teles');

      clicar('Cancelar férias', cartao);
      clicar('Voltar', cartao);

      expect(cartao.querySelector('textarea')).toBeNull();
      expect(api.cancelarAprovada).not.toHaveBeenCalled();
    });

    it('nenhuma férias aprovada: mostra a frase de lista vazia', () => {
      api.aprovadas.mockReturnValue(of([]));
      const { elemento } = abrirAprovadas();

      expect(elemento.textContent).toContain('aprovada para os próximos dias');
    });
  });
});
