import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { FeriasApi } from '../../ferias/ferias-api';
import { Pendencias } from '../../ferias/pendencias';
import { FeriasDoColega, Negociacao, PeriodoAquisitivo, Solicitacao } from '../../nucleo/modelos';
import { Sessao } from '../../nucleo/sessao';
import { Inicio } from './inicio';

/*
 * Tela Início do colaborador (teste de componente).
 *
 * As decisões (qual pedido mostrar, qual proposta é para mim...) já são testadas em
 * inicio.regras.spec.ts. Aqui o foco é a tela: o que aparece com cada resposta da API,
 * o que os botões chamam e o que acontece quando algo dá errado.
 *
 * A FeriasApi, a Sessao e as Pendencias são dublês (fakes). A Ana (id 1) é quem está logada.
 */
describe('Inicio', () => {
  const periodo: PeriodoAquisitivo = {
    numero: 1,
    inicio: '2026-01-10',
    fim: '2027-01-09',
    tirarAte: '2027-12-09',
    faltasInjustificadas: 0,
    direito: 30,
    aprovados: 10,
    emPedido: 5,
    livres: 15,
    situacao: 'A_VENCER',
  };

  function solicitacao(extra: Partial<Solicitacao> = {}): Solicitacao {
    return {
      id: 7,
      status: 'AGUARDANDO_GESTOR',
      inicio: '2099-03-15',
      fim: '2099-03-19',
      diasGozo: 5,
      diasAbono: 0,
      periodo: 1,
      recado: null,
      enviadoEm: '2026-11-20T13:00:00Z',
      decididoPor: null,
      mensagemDecisao: null,
      ...extra,
    };
  }

  /** O Bruno (id 3) propõe trocar as férias com a Ana (id 1): é a vez da Ana responder. */
  const trocaParaAna: Negociacao = {
    id: 40,
    status: 'AGUARDANDO_COLEGA',
    solicitante: { id: 3, nome: 'Bruno Teles' },
    colega: { id: 1, nome: 'Ana Lima' },
    vezDe: { id: 1, nome: 'Ana Lima' },
    pedidoDoColega: { id: 7, inicio: '2099-03-15', fim: '2099-03-19', diasGozo: 5 },
    pedidoDoSolicitante: { id: 8, inicio: '2099-04-05', fim: '2099-04-09', diasGozo: 5 },
    propostas: [
      {
        autor: { id: 3, nome: 'Bruno Teles' },
        inicioSolicitante: '2099-03-15',
        fimSolicitante: '2099-03-19',
        inicioColega: '2099-04-05',
        fimColega: '2099-04-09',
        mensagem: 'Topa trocar? Meu filho nasce em março.',
        feitaEm: '2026-11-20T13:00:00Z',
      },
    ],
    decididoPor: null,
    mensagemDecisao: null,
    criadaEm: '2026-11-20T13:00:00Z',
  };

  const colega: FeriasDoColega = {
    pedidoId: 9,
    colaborador: { id: 4, nome: 'Carla Souza' },
    inicio: '2099-05-03',
    fim: '2099-05-17',
    diasGozo: 15,
  };

  function erroHttp(status: number, corpo: object | null = null): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status, error: corpo }));
  }

  let api: Record<
    | 'periodos'
    | 'solicitacoes'
    | 'negociacoes'
    | 'feriasDaEquipe'
    | 'aviso'
    | 'aceitarTroca'
    | 'recusarTroca',
    ReturnType<typeof vi.fn>
  >;
  const pendencias = { atualizar: vi.fn() };
  const sessao = { usuario: () => ({ id: 1, nome: 'Ana Lima', perfil: 'COLABORADOR' }) };

  beforeEach(() => {
    vi.clearAllMocks();
    api = {
      periodos: vi.fn(() => of([periodo])),
      solicitacoes: vi.fn(() => of([solicitacao()])),
      negociacoes: vi.fn(() => of([])),
      feriasDaEquipe: vi.fn(() => of([colega])),
      aviso: vi.fn(() => of(new Blob(['%PDF']))),
      aceitarTroca: vi.fn(() => of({})),
      recusarTroca: vi.fn(() => of({})),
    };
  });

  afterEach(() => vi.restoreAllMocks());

  function criar() {
    TestBed.configureTestingModule({
      imports: [Inicio],
      providers: [
        provideRouter([]),
        { provide: FeriasApi, useValue: api },
        { provide: Sessao, useValue: sessao },
        { provide: Pendencias, useValue: pendencias },
      ],
    });
    const fixture = TestBed.createComponent(Inicio);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;
    const clicar = (seletor: string) => {
      elemento.querySelector<HTMLElement>(seletor)!.click();
      fixture.detectChanges();
    };
    const botao = (texto: string) =>
      [...elemento.querySelectorAll('button')].find(
        (b) => b.textContent?.trim() === texto,
      ) as HTMLButtonElement;
    return { fixture, elemento, clicar, botao };
  }

  it('cumprimenta pelo primeiro nome e mostra o saldo (livres + em pedido)', () => {
    const { elemento } = criar();

    expect(elemento.querySelector('h1')?.textContent).toBe('Olá, Ana');
    expect(elemento.querySelector('.saldo-numero strong')?.textContent).toBe('20'); // 15 livres + 5 em pedido
    expect(elemento.querySelector('.saldo')?.textContent).toContain('Vence em breve');
    expect(elemento.querySelector('.saldo-datas')?.textContent).toContain('09/12/2027'); // "Tire até"
    expect(elemento.querySelectorAll('.blocos span')).toHaveLength(30); // um quadradinho por dia de direito
  });

  it('mostra o pedido em andamento, com a etapa atual', () => {
    const { elemento } = criar();

    const pedido = elemento.querySelector('.cartao-pedido')!;
    expect(pedido.textContent).toContain('15/03 – 19/03 · 5 dias');
    expect(pedido.querySelector('li.atual')?.textContent).toContain('Com o seu gestor');
  });

  it('sem pedido em andamento, convida a planejar', () => {
    api.solicitacoes.mockReturnValue(of([]));
    const { elemento } = criar();

    expect(elemento.querySelector('.cartao-pedido')?.textContent).toContain(
      'Nenhum pedido em andamento.',
    );
    expect(elemento.querySelector('.cartao-pedido a')?.getAttribute('href')).toBe('/solicitar');
  });

  it('pedido que o gestor já aprovou: a etapa atual é o RH, e a mensagem do gestor aparece', () => {
    api.solicitacoes.mockReturnValue(
      of([solicitacao({ status: 'AGUARDANDO_RH', mensagemDecisao: 'Bom descanso!' })]),
    );
    const { elemento } = criar();

    expect(elemento.querySelector('.cartao-pedido li.atual')?.textContent).toContain('Com o RH');
    expect(elemento.querySelector('.cartao-pedido .recado')?.textContent).toContain(
      'Bom descanso!',
    );
  });

  it('lista as férias marcadas na equipe', () => {
    const { elemento } = criar();

    expect(elemento.querySelector('.equipe li')?.textContent).toContain('Carla Souza');
    expect(elemento.querySelector('.equipe li')?.textContent).toContain('03/05 – 17/05');
  });

  it('ninguém da equipe de férias: mostra a frase de lista vazia', () => {
    api.feriasDaEquipe.mockReturnValue(of([]));
    const { elemento } = criar();

    expect(elemento.querySelector('.equipe')?.textContent).toContain(
      'Ninguém da equipe tem férias marcadas.',
    );
  });

  it('erro ao carregar: avisa, e "Tentar de novo" busca tudo outra vez', () => {
    api.periodos.mockReturnValueOnce(erroHttp(500));
    const { elemento, clicar } = criar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar as suas férias.',
    );
    clicar('[role="alert"] a');

    expect(api.periodos).toHaveBeenCalledTimes(2);
    expect(elemento.querySelector('[role="alert"]')).toBeNull();
    expect(elemento.querySelector('h2#titulo-saldo')).not.toBeNull();
  });

  describe('proposta de troca', () => {
    beforeEach(() => api.negociacoes.mockReturnValue(of([trocaParaAna])));

    it('mostra quem propôs, as datas de hoje e as novas, e o recado', () => {
      const { elemento } = criar();

      const cartao = elemento.querySelector('[aria-labelledby="titulo-troca"]')!;
      expect(cartao.textContent).toContain('Bruno Teles');
      expect(cartao.querySelector('.caixa')?.textContent).toContain('15/03 – 19/03'); // Você hoje
      expect(cartao.querySelector('.caixa-nova')?.textContent).toContain('05/04 – 09/04'); // Você ficaria com
      expect(cartao.textContent).toContain('Topa trocar? Meu filho nasce em março.');
    });

    it('aceitar chama a API, recarrega a tela e atualiza os números do menu', () => {
      const { botao, fixture } = criar();

      botao('Aceitar troca').click();
      fixture.detectChanges();

      expect(api.aceitarTroca).toHaveBeenCalledWith(40);
      expect(api.negociacoes).toHaveBeenCalledTimes(2); // carregou de novo
      expect(pendencias.atualizar).toHaveBeenCalled();
    });

    it('recusar chama a API de recusa', () => {
      const { botao, fixture } = criar();

      botao('Recusar').click();
      fixture.detectChanges();

      expect(api.recusarTroca).toHaveBeenCalledWith(40);
      expect(api.aceitarTroca).not.toHaveBeenCalled();
    });

    it('a API recusa a resposta: mostra o motivo e reabilita os botões', () => {
      api.aceitarTroca.mockReturnValue(
        erroHttp(422, { detail: 'As novas datas passam do limite do departamento.' }),
      );
      const { elemento, botao, fixture } = criar();

      botao('Aceitar troca').click();
      fixture.detectChanges();

      expect(
        elemento.querySelector('[aria-labelledby="titulo-troca"] [role="alert"]')?.textContent,
      ).toContain('As novas datas passam do limite do departamento.');
      expect(botao('Aceitar troca').disabled).toBe(false);
      expect(pendencias.atualizar).not.toHaveBeenCalled();
    });
  });

  describe('avisos de férias (PDF)', () => {
    const aprovada = solicitacao({
      id: 12,
      status: 'APROVADA',
      inicio: '2099-01-04',
      fim: '2099-01-18',
      diasGozo: 15,
    });
    const urlOriginal = {
      createObjectURL: URL.createObjectURL,
      revokeObjectURL: URL.revokeObjectURL,
    };
    afterEach(() => Object.assign(URL, urlOriginal)); // devolve o que o teste de download trocou

    it('sem férias aprovadas, explica quando o aviso aparece', () => {
      const { elemento } = criar();

      expect(elemento.querySelector('.avisos')?.textContent).toContain(
        'O aviso aparece aqui quando as suas férias forem aprovadas.',
      );
    });

    it('baixar o aviso pede o PDF e salva com o nome certo', () => {
      api.solicitacoes.mockReturnValue(of([aprovada]));
      // O jsdom não sabe criar URL de arquivo nem "clicar" em download: o teste faz esse papel.
      URL.createObjectURL = vi.fn(() => 'blob:aviso');
      URL.revokeObjectURL = vi.fn();
      const clique = vi
        .spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(function (this: HTMLAnchorElement) {});
      const { elemento, clicar } = criar();

      expect(elemento.querySelector('.avisos')?.textContent).toContain('Janeiro 2099');
      clicar('[aria-label="Baixar o aviso de Janeiro 2099"]');

      expect(api.aviso).toHaveBeenCalledWith(12);
      expect(clique).toHaveBeenCalledTimes(1);
      expect((clique.mock.contexts[0] as HTMLAnchorElement).download).toBe(
        'aviso-de-ferias-2099-01-04.pdf',
      );
    });

    it('erro ao baixar: avisa e deixa tentar de novo', () => {
      api.solicitacoes.mockReturnValue(of([aprovada]));
      api.aviso.mockReturnValue(erroHttp(500));
      const { elemento, clicar } = criar();

      clicar('[aria-label="Baixar o aviso de Janeiro 2099"]');

      expect(elemento.querySelector('.avisos [role="alert"]')?.textContent).toContain(
        'Não foi possível baixar o aviso.',
      );
      expect(elemento.querySelector<HTMLButtonElement>('.baixar')!.disabled).toBe(false);
    });
  });
});
