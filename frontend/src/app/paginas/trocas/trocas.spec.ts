import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { FeriasApi } from '../../ferias/ferias-api';
import { Pendencias } from '../../ferias/pendencias';
import { FeriasDoColega, Negociacao, Solicitacao } from '../../nucleo/modelos';
import { Sessao } from '../../nucleo/sessao';
import { Trocas } from './trocas';

/*
 * Tela "Trocas de período" + o cartão de cada negociação (teste de componente).
 *
 * A tradução "solicitante/colega" ↔ "você/o outro" já é testada em trocas.regras.spec.ts.
 * Aqui o foco é a tela: os botões certos em cada situação, o que cada um manda para a API
 * (do ponto de vista certo!) e o que acontece quando a API recusa.
 *
 * A Ana (id 1) está logada. "Hoje" é 01/03/2027.
 */
describe('Trocas', () => {
  const ANA = { id: 1, nome: 'Ana Lima' };
  const BRUNO = { id: 3, nome: 'Bruno Teles' };
  const CARLA = { id: 4, nome: 'Carla Souza' };

  /** Uma negociação com uma proposta. As datas do SOLICITANTE e do COLEGA são as da proposta. */
  function negociacao(
    extra: Partial<Negociacao> & Pick<Negociacao, 'id' | 'solicitante' | 'colega'>,
  ): Negociacao {
    return {
      status: 'AGUARDANDO_COLEGA',
      vezDe: extra.colega,
      pedidoDoColega: { id: 100 + extra.id, inicio: '2027-04-05', fim: '2027-04-19', diasGozo: 15 },
      pedidoDoSolicitante: {
        id: 200 + extra.id,
        inicio: '2027-06-07',
        fim: '2027-06-21',
        diasGozo: 15,
      },
      propostas: [
        {
          autor: extra.solicitante,
          inicioSolicitante: '2027-04-05',
          fimSolicitante: '2027-04-19',
          inicioColega: '2027-06-07',
          fimColega: '2027-06-21',
          mensagem: 'Topa trocar? Casamento da minha irmã.',
          feitaEm: '2027-02-28T13:00:00Z',
        },
      ],
      decididoPor: null,
      mensagemDecisao: null,
      criadaEm: '2027-02-28T13:00:00Z',
      ...extra,
    };
  }

  /** O Bruno propôs à Ana: é a vez dela responder. */
  const recebida = negociacao({ id: 40, solicitante: BRUNO, colega: ANA });
  /** A Ana propôs à Carla: esperando a Carla. */
  const enviada = negociacao({ id: 41, solicitante: ANA, colega: CARLA, vezDe: CARLA });
  /** Uma troca já aprovada pelo gestor. */
  const encerrada = negociacao({
    id: 39,
    solicitante: ANA,
    colega: BRUNO,
    status: 'APROVADA',
    vezDe: null,
    decididoPor: { id: 2, nome: 'Roberto Neves' },
    mensagemDecisao: 'Combinado, boas férias aos dois.',
  });

  const feriasDaCarla: FeriasDoColega = {
    pedidoId: 300,
    colaborador: CARLA,
    inicio: '2027-07-05',
    fim: '2027-07-19',
    diasGozo: 15,
  };

  function minhas(
    id: number,
    inicio: string,
    fim: string,
    status: Solicitacao['status'] = 'APROVADA',
  ): Solicitacao {
    return {
      id,
      status,
      inicio,
      fim,
      diasGozo: 15,
      diasAbono: 0,
      periodo: 1,
      recado: null,
      enviadoEm: '2027-01-10T13:00:00Z',
      decididoPor: null,
      mensagemDecisao: null,
    };
  }

  function erroHttp(status: number, corpo: object | null = null): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status, error: corpo }));
  }

  let api: Record<
    | 'negociacoes'
    | 'feriasDaEquipe'
    | 'solicitacoes'
    | 'aceitarTroca'
    | 'recusarTroca'
    | 'cancelarTroca'
    | 'contrapropor'
    | 'proporTroca',
    ReturnType<typeof vi.fn>
  >;
  const pendencias = { atualizar: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2027, 2, 1, 10, 0)); // 01/03/2027
    api = {
      negociacoes: vi.fn(() => of([encerrada, enviada, recebida])),
      feriasDaEquipe: vi.fn(() => of([feriasDaCarla])),
      solicitacoes: vi.fn(() =>
        of([
          minhas(501, '2027-06-07', '2027-06-21'), // aprovada e futura: dá para oferecer
          minhas(502, '2027-02-01', '2027-02-15'), // já passou: não
          minhas(503, '2027-09-06', '2027-09-20', 'AGUARDANDO_GESTOR'), // em análise: não
        ]),
      ),
      aceitarTroca: vi.fn(() => of({})),
      recusarTroca: vi.fn(() => of({})),
      cancelarTroca: vi.fn(() => of({})),
      contrapropor: vi.fn(() => of({})),
      proporTroca: vi.fn(() => of({})),
    };
  });

  afterEach(() => vi.useRealTimers());

  function criar() {
    TestBed.configureTestingModule({
      imports: [Trocas],
      providers: [
        { provide: FeriasApi, useValue: api },
        { provide: Pendencias, useValue: pendencias },
        { provide: Sessao, useValue: { usuario: () => ({ ...ANA, perfil: 'COLABORADOR' }) } },
      ],
    });
    const fixture = TestBed.createComponent(Trocas);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    const cartoes = () => [...elemento.querySelectorAll('crona-cartao-troca')];
    const cartaoCom = (nome: string) =>
      cartoes().find((c) => c.textContent?.includes(`Troca com ${nome}`))!;
    const botao = (texto: string, onde: ParentNode = elemento) =>
      [...onde.querySelectorAll('button')].find((b) =>
        b.textContent?.trim().startsWith(texto),
      ) as HTMLButtonElement;
    const clicar = (texto: string, onde?: ParentNode) => {
      botao(texto, onde).click();
      fixture.detectChanges();
    };
    /** Digita num campo (input, textarea) ou escolhe num select, e avisa o Angular. */
    const preencher = (
      campo: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement,
      valor: string,
    ) => {
      campo.value = valor;
      campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
      fixture.detectChanges();
    };
    const formulario = () => elemento.querySelector<HTMLFormElement>('form.formulario');

    return { fixture, elemento, cartoes, cartaoCom, botao, clicar, preencher, formulario };
  }

  describe('as negociações', () => {
    it('a que espera a minha resposta vem primeiro; depois as em aberto; as encerradas por último', () => {
      const { cartoes } = criar();

      const selos = cartoes().map((c) => c.querySelector('.selo')?.textContent?.trim());
      expect(selos).toEqual(['Sua vez de responder', 'Esperando Carla Souza', 'Aprovada']);
    });

    it('mostra as datas do MEU ponto de vista: hoje e como ficariam', () => {
      const { cartaoCom } = criar();

      // A Ana é a COLEGA nesta troca: as dela hoje são as do "pedidoDoColega".
      const lados = cartaoCom('Bruno Teles').querySelectorAll('.lado');
      expect(lados[0].textContent).toContain('Você');
      expect(lados[0].textContent).toContain('05/04 – 19/04'); // hoje
      expect(lados[0].textContent).toContain('07/06 – 21/06'); // ficaria com
      expect(cartaoCom('Bruno Teles').textContent).toContain(
        'Topa trocar? Casamento da minha irmã.',
      );
    });

    it('na minha vez: Aceitar, Contrapropor e Recusar; numa que eu propus: só Cancelar proposta', () => {
      const { cartaoCom, botao } = criar();

      const recebidaNaTela = cartaoCom('Bruno Teles');
      expect(botao('Aceitar troca', recebidaNaTela)).toBeDefined();
      expect(botao('Contrapropor', recebidaNaTela)).toBeDefined();
      expect(botao('Recusar', recebidaNaTela)).toBeDefined();
      expect(botao('Cancelar proposta', recebidaNaTela)).toBeUndefined();

      const enviadaNaTela = cartaoCom('Carla Souza');
      expect(botao('Cancelar proposta', enviadaNaTela)).toBeDefined();
      expect(botao('Aceitar troca', enviadaNaTela)).toBeUndefined();
    });

    it('uma troca encerrada mostra a decisão do gestor e nenhum botão', () => {
      const { cartoes } = criar();
      const aprovada = cartoes()[2];

      expect(aprovada.textContent).toContain(
        'Aprovada por Roberto Neves: “Combinado, boas férias aos dois.”',
      );
      expect(aprovada.querySelectorAll('button')).toHaveLength(0);
    });

    it('aceitar: chama a API, recarrega a tela e o número do menu', () => {
      const { cartaoCom, clicar } = criar();

      clicar('Aceitar troca', cartaoCom('Bruno Teles'));

      expect(api.aceitarTroca).toHaveBeenCalledWith(40);
      expect(api.negociacoes).toHaveBeenCalledTimes(2);
      expect(pendencias.atualizar).toHaveBeenCalledTimes(2); // ao abrir e depois de aceitar
    });

    it('recusar com recado manda o recado, sem os espaços das pontas', () => {
      const { cartaoCom, clicar, preencher } = criar();

      clicar('Recusar', cartaoCom('Bruno Teles'));
      preencher(cartaoCom('Bruno Teles').querySelector('textarea')!, '  Não posso nessas datas.  ');
      clicar('Recusar a troca', cartaoCom('Bruno Teles'));

      expect(api.recusarTroca).toHaveBeenCalledWith(40, 'Não posso nessas datas.');
    });

    it('recusar sem recado manda undefined (o recado é opcional)', () => {
      const { cartaoCom, clicar } = criar();

      clicar('Recusar', cartaoCom('Bruno Teles'));
      clicar('Recusar a troca', cartaoCom('Bruno Teles'));

      expect(api.recusarTroca).toHaveBeenCalledWith(40, undefined);
    });

    it('contrapropor: começa com as datas da proposta e manda do ponto de vista da API (eu sou a colega)', () => {
      const { cartaoCom, clicar, preencher } = criar();
      const cartao = cartaoCom('Bruno Teles');

      clicar('Contrapropor', cartao);
      const [meuInicio, inicioDoBruno] = [
        ...cartao.querySelectorAll<HTMLInputElement>('input[type="date"]'),
      ];
      expect(meuInicio.value).toBe('2027-06-07'); // o que a proposta me dava
      expect(inicioDoBruno.value).toBe('2027-04-05');

      preencher(meuInicio, '2027-06-14'); // a Ana prefere uma semana depois
      preencher(cartao.querySelector('textarea')!, 'Pode ser uma semana depois?');
      clicar('Enviar contraproposta', cartao);

      // A Ana é a COLEGA: as datas dela vão em inicioColega, as do Bruno em inicioSolicitante.
      expect(api.contrapropor).toHaveBeenCalledWith(40, {
        inicioSolicitante: '2027-04-05',
        inicioColega: '2027-06-14',
        mensagem: 'Pode ser uma semana depois?',
      });
    });

    it('a API recusa a contraproposta (422): mostra o motivo e o formulário continua com o que foi digitado', () => {
      api.contrapropor.mockReturnValue(
        erroHttp(422, {
          detail: 'As novas datas não passam nas regras.',
          verificacoes: [
            { regra: 'CLT-03', resultado: 'OK', mensagem: 'Dentro do prazo.' },
            {
              regra: 'EMP-01',
              resultado: 'IMPEDIMENTO',
              mensagem: 'A Engenharia passaria do limite.',
            },
          ],
        }),
      );
      const { cartaoCom, clicar, preencher } = criar();
      const cartao = cartaoCom('Bruno Teles');
      clicar('Contrapropor', cartao);
      preencher(cartao.querySelectorAll<HTMLInputElement>('input[type="date"]')[0], '2027-06-14');

      clicar('Enviar contraproposta', cartao);

      expect(cartao.querySelector('[role="alert"]')?.textContent).toContain(
        'As novas datas não passam nas regras.',
      );
      const regras = [...cartao.querySelectorAll('crona-checklist li')].map((li) =>
        li.textContent?.trim(),
      );
      expect(regras).toEqual(['A Engenharia passaria do limite.']); // só as que não passaram
      expect(cartao.querySelectorAll<HTMLInputElement>('input[type="date"]')[0].value).toBe(
        '2027-06-14',
      );
    });

    it('cancelar a minha proposta pede confirmação e então chama a API', () => {
      const { cartaoCom, clicar } = criar();
      const cartao = cartaoCom('Carla Souza');

      clicar('Cancelar proposta', cartao);
      expect(api.cancelarTroca).not.toHaveBeenCalled();
      expect(cartao.textContent).toContain('Cancelar esta proposta?');

      clicar('Sim, cancelar', cartao);
      expect(api.cancelarTroca).toHaveBeenCalledWith(41);
    });

    it('"Voltar" fecha o formulário pequeno sem chamar a API', () => {
      const { cartaoCom, clicar } = criar();
      const cartao = cartaoCom('Carla Souza');

      clicar('Cancelar proposta', cartao);
      clicar('Voltar', cartao);

      expect(cartao.textContent).not.toContain('Cancelar esta proposta?');
      expect(api.cancelarTroca).not.toHaveBeenCalled();
    });

    it('com mais de uma rodada, mostra o histórico', () => {
      const comContraproposta = negociacao({
        id: 42,
        solicitante: BRUNO,
        colega: ANA,
        status: 'AGUARDANDO_SOLICITANTE',
        vezDe: BRUNO,
      });
      comContraproposta.propostas = [
        ...comContraproposta.propostas,
        { ...comContraproposta.propostas[0], autor: ANA, mensagem: 'Uma semana depois?' },
      ];
      api.negociacoes.mockReturnValue(of([comContraproposta]));
      const { cartoes } = criar();

      expect(cartoes()[0].querySelector('summary')?.textContent).toContain(
        'Histórico (2 propostas)',
      );
      expect(cartoes()[0].textContent).toContain('“Uma semana depois?” — Você');
    });

    it('sem negociações: explica como propor uma', () => {
      api.negociacoes.mockReturnValue(of([]));
      const { elemento } = criar();

      // O estado vazio do P2: um título curto e a explicação.
      const vazio = elemento.querySelector('.negociacoes .vazio')!;
      expect(vazio.querySelector('strong')?.textContent?.trim()).toBe('Nenhuma troca ainda');
      expect(vazio.querySelector('p')?.textContent?.trim()).toBe(
        'Para propor uma, escolha as férias de um colega ao lado.',
      );
    });
  });

  /*
   * BUG-04 (registro em docs/plano-de-testes.md): uma ação num cartão recarrega a lista, e todas
   * as negociações chegam como objetos novos. O formulário aberto em OUTRO cartão não pode fechar
   * (nem perder o que foi digitado). Só fecha o do cartão cuja negociação mudou de verdade.
   */
  describe('BUG-04: a lista recarrega depois de uma ação', () => {
    it('a contraproposta aberta no cartão do Bruno continua aberta quando cancelo a proposta da Carla', () => {
      // Depois de cancelar, a API devolve a proposta da Carla CANCELADA; a do Bruno, igual (objeto novo).
      api.negociacoes
        .mockReturnValueOnce(of([encerrada, enviada, recebida]))
        .mockReturnValueOnce(
          of([
            structuredClone(encerrada),
            { ...structuredClone(enviada), status: 'CANCELADA', vezDe: null },
            structuredClone(recebida),
          ]),
        );
      const { cartaoCom, clicar, preencher } = criar();
      clicar('Contrapropor', cartaoCom('Bruno Teles'));
      preencher(
        cartaoCom('Bruno Teles').querySelectorAll<HTMLInputElement>('input[type="date"]')[0],
        '2027-06-14',
      );

      clicar('Cancelar proposta', cartaoCom('Carla Souza'));
      clicar('Sim, cancelar', cartaoCom('Carla Souza'));

      expect(api.cancelarTroca).toHaveBeenCalledWith(41);
      const datas =
        cartaoCom('Bruno Teles').querySelectorAll<HTMLInputElement>('input[type="date"]');
      expect(datas).toHaveLength(2); // o formulário do Bruno continua aberto...
      expect(datas[0].value).toBe('2027-06-14'); // ...com a data que a Ana digitou
    });

    it('guarda: quando a ação do PRÓPRIO cartão dá certo, o formulário dele fecha', () => {
      // Depois da contraproposta, a negociação do Bruno volta com uma rodada a mais e a vez dele.
      const depois: Negociacao = {
        ...structuredClone(recebida),
        status: 'AGUARDANDO_SOLICITANTE',
        vezDe: BRUNO,
        propostas: [
          ...recebida.propostas,
          { ...recebida.propostas[0], autor: ANA, inicioColega: '2027-06-14' },
        ],
      };
      api.negociacoes
        .mockReturnValueOnce(of([encerrada, enviada, recebida]))
        .mockReturnValueOnce(of([structuredClone(encerrada), structuredClone(enviada), depois]));
      const { cartaoCom, clicar } = criar();
      clicar('Contrapropor', cartaoCom('Bruno Teles'));

      clicar('Enviar contraproposta', cartaoCom('Bruno Teles'));

      expect(cartaoCom('Bruno Teles').querySelectorAll('input[type="date"]')).toHaveLength(0);
    });
  });

  describe('propor uma troca', () => {
    it('"Quero essas" abre o formulário já preenchido: eu fico com as datas da Carla e ofereço as minhas', () => {
      const { clicar, formulario } = criar();

      clicar('Quero essas');

      const form = formulario()!;
      expect(form.querySelector('h3')?.textContent).toBe('Propor a Carla');
      // Só as minhas férias aprovadas e futuras aparecem para oferecer.
      const opcoes = [...form.querySelectorAll('option')].map((o) => o.textContent?.trim());
      expect(opcoes).toEqual([
        'Minhas férias de 07/06 – 21/06 (15 dias)',
        'Ainda não tenho férias marcadas',
      ]);
      const [meuInicio, inicioDaCarla] = [
        ...form.querySelectorAll<HTMLInputElement>('input[type="date"]'),
      ];
      expect(meuInicio.value).toBe('2027-07-05');
      expect(inicioDaCarla.value).toBe('2027-06-07');
    });

    it('enviar: manda a proposta com as minhas férias oferecidas e o recado', () => {
      const { clicar, formulario, preencher } = criar();
      clicar('Quero essas');
      preencher(formulario()!.querySelector('textarea')!, '  Viagem em julho  ');

      clicar('Enviar proposta', formulario()!);

      expect(api.proporTroca).toHaveBeenCalledWith({
        pedidoDoColegaId: 300,
        pedidoDoSolicitanteId: 501,
        inicioSolicitante: '2027-07-05',
        diasSolicitante: undefined,
        inicioColega: '2027-06-07',
        mensagem: 'Viagem em julho',
      });
      expect(formulario()).toBeNull(); // deu certo: o formulário fecha e a tela recarrega
      expect(api.negociacoes).toHaveBeenCalledTimes(2);
    });

    it('sem férias para oferecer: pede quantos dias e para onde vão as da Carla', () => {
      const { clicar, formulario, preencher, botao } = criar();
      clicar('Quero essas');

      preencher(formulario()!.querySelector('select')!, ''); // "Ainda não tenho férias marcadas"

      expect(formulario()!.textContent).toContain('Quantos dias');
      // As da Carla ainda não têm para onde ir: não dá para enviar.
      expect(botao('Enviar proposta', formulario()!).disabled).toBe(true);

      preencher(
        formulario()!.querySelectorAll<HTMLInputElement>('input[type="date"]')[1],
        '2027-08-02',
      );
      preencher(formulario()!.querySelector<HTMLInputElement>('input[type="number"]')!, '10');
      clicar('Enviar proposta', formulario()!);

      expect(api.proporTroca).toHaveBeenCalledWith(
        expect.objectContaining({
          pedidoDoSolicitanteId: undefined,
          diasSolicitante: 10,
          inicioColega: '2027-08-02',
        }),
      );
    });

    it('a API recusa a proposta: mostra o motivo, e o formulário continua aberto', () => {
      api.proporTroca.mockReturnValue(
        erroHttp(409, { detail: 'Já existe uma troca em aberto com essas férias.' }),
      );
      const { clicar, formulario } = criar();
      clicar('Quero essas');

      clicar('Enviar proposta', formulario()!);

      expect(formulario()!.querySelector('[role="alert"]')?.textContent).toContain(
        'Já existe uma troca em aberto com essas férias.',
      );
    });

    it('"Desistir" fecha o formulário', () => {
      const { clicar, formulario } = criar();
      clicar('Quero essas');

      clicar('Desistir', formulario()!);

      expect(formulario()).toBeNull();
      expect(api.proporTroca).not.toHaveBeenCalled();
    });

    it('ninguém da equipe com férias aprovadas: diz que não há o que trocar', () => {
      api.feriasDaEquipe.mockReturnValue(of([]));
      const { elemento } = criar();

      expect(elemento.querySelector('.propor')?.textContent).toContain(
        'Ninguém da equipe tem férias aprovadas',
      );
    });
  });

  it('erro ao carregar: avisa, e "Tentar de novo" busca outra vez', () => {
    api.negociacoes.mockReturnValueOnce(erroHttp(500));
    const { fixture, elemento, cartoes } = criar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar as trocas.',
    );
    elemento.querySelector<HTMLButtonElement>('[role="alert"] button')!.click(); // P2: um botão, não um link
    fixture.detectChanges();

    expect(cartoes()).toHaveLength(3);
  });
});
