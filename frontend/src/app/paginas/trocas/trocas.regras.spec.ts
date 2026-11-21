import { Negociacao, Solicitacao } from '../../nucleo/modelos';
import { feriasQuePossoOferecer, ordenar, paraContraproposta, trocasEsperandoMim, visaoDaTroca } from './trocas.regras';

/*
 * O cenário: a Ana (3) propôs ao Bruno (4) "eu fico com o seu março, você fica com o meu abril".
 * O Bruno contrapropôs outras datas, então agora é a vez da Ana.
 */
const ANA = 3;
const BRUNO = 4;

function negociacao(extra: Partial<Negociacao> = {}): Negociacao {
  return {
    id: 7,
    status: 'AGUARDANDO_SOLICITANTE',
    solicitante: { id: ANA, nome: 'Ana Lima' },
    colega: { id: BRUNO, nome: 'Bruno Teles' },
    vezDe: { id: ANA, nome: 'Ana Lima' },
    pedidoDoColega: { id: 20, inicio: '2027-03-01', fim: '2027-03-15', diasGozo: 15 },
    pedidoDoSolicitante: { id: 21, inicio: '2027-04-05', fim: '2027-04-19', diasGozo: 15 },
    propostas: [
      {
        autor: { id: ANA, nome: 'Ana Lima' },
        inicioSolicitante: '2027-03-01',
        fimSolicitante: '2027-03-15',
        inicioColega: '2027-04-05',
        fimColega: '2027-04-19',
        mensagem: 'Casamento da minha irmã',
        feitaEm: '2027-01-10T09:00:00Z',
      },
      {
        autor: { id: BRUNO, nome: 'Bruno Teles' },
        inicioSolicitante: '2027-03-08',
        fimSolicitante: '2027-03-22',
        inicioColega: '2027-04-12',
        fimColega: '2027-04-26',
        mensagem: 'Só a partir do dia 8',
        feitaEm: '2027-01-10T12:00:00Z',
      },
    ],
    decididoPor: null,
    mensagemDecisao: null,
    criadaEm: '2027-01-10T09:00:00Z',
    ...extra,
  };
}

describe('visaoDaTroca', () => {
  it('para quem propôs (Ana): "minhas" são as datas do solicitante, e é a vez dela', () => {
    const v = visaoDaTroca(negociacao(), ANA);

    expect(v.souQuemPropos).toBe(true);
    expect(v.outro.nome).toBe('Bruno Teles');
    expect(v.minhasHoje).toBe('05/04 – 19/04');
    expect(v.minhasNovas).toBe('08/03 – 22/03'); // a proposta vigente é a contraproposta do Bruno
    expect(v.delesNovas).toBe('12/04 – 26/04');
    expect(v.minhaVez).toBe(true);
    expect(v.rotulo).toBe('Sua vez de responder');
    expect(v.possoCancelar).toBe(true);
    expect(v.meuInicioProposto).toBe('2027-03-08');
    expect(v.inicioDoOutroProposto).toBe('2027-04-12');
  });

  it('para quem recebeu (Bruno): as datas trocam de lado, e ele espera a Ana', () => {
    const v = visaoDaTroca(negociacao(), BRUNO);

    expect(v.souQuemPropos).toBe(false);
    expect(v.minhasHoje).toBe('01/03 – 15/03');
    expect(v.minhasNovas).toBe('12/04 – 26/04');
    expect(v.minhaVez).toBe(false);
    expect(v.rotulo).toBe('Esperando Ana Lima');
    expect(v.possoCancelar).toBe(false); // só quem propôs cancela
  });

  it('o histórico mostra cada rodada do ponto de vista de quem lê ("Você" no lugar do próprio nome)', () => {
    const rodadas = visaoDaTroca(negociacao(), ANA).rodadas;

    expect(rodadas.map((r) => r.autor)).toEqual(['Você', 'Bruno Teles']);
    expect(rodadas[0].minhas).toBe('01/03 – 15/03');
  });

  it('quem propôs sem ter férias marcadas: "Sem férias" do lado dela', () => {
    expect(visaoDaTroca(negociacao({ pedidoDoSolicitante: null }), ANA).minhasHoje).toBeNull();
  });

  it('encerrada: ninguém responde nem cancela', () => {
    const v = visaoDaTroca(negociacao({ status: 'RECUSADA', vezDe: null }), ANA);

    expect(v.minhaVez).toBe(false);
    expect(v.possoCancelar).toBe(false);
    expect(v.tom).toBe('encerrada');
  });
});

describe('ordenar', () => {
  it('a minha vez primeiro, depois as em aberto, por último as encerradas', () => {
    const minhaVez = visaoDaTroca(negociacao({ id: 1 }), ANA);
    const comGestor = visaoDaTroca(negociacao({ id: 2, status: 'AGUARDANDO_GESTOR', vezDe: null }), ANA);
    const encerrada = visaoDaTroca(negociacao({ id: 3, status: 'CANCELADA', vezDe: null }), ANA);

    expect(ordenar([encerrada, comGestor, minhaVez]).map((v) => v.id)).toEqual([1, 2, 3]);
  });
});

describe('trocasEsperandoMim', () => {
  it('conta só as que são a minha vez', () => {
    const lista = [negociacao({ id: 1 }), negociacao({ id: 2, vezDe: { id: BRUNO, nome: 'Bruno Teles' } })];

    expect(trocasEsperandoMim(lista, ANA)).toBe(1);
    expect(trocasEsperandoMim(lista, BRUNO)).toBe(1);
  });
});

describe('feriasQuePossoOferecer', () => {
  const pedido = (id: number, status: Solicitacao['status'], inicio: string): Solicitacao => ({
    id,
    status,
    inicio,
    fim: inicio,
    diasGozo: 15,
    diasAbono: 0,
    periodo: 1,
    recado: null,
    enviadoEm: '2027-01-01T00:00:00Z',
    decididoPor: null,
    mensagemDecisao: null,
  });

  it('só as aprovadas que ainda não começaram, na ordem das datas', () => {
    const lista = [
      pedido(1, 'APROVADA', '2027-06-01'),
      pedido(2, 'AGUARDANDO_GESTOR', '2027-05-01'),
      pedido(3, 'APROVADA', '2026-12-01'), // já começou
      pedido(4, 'APROVADA', '2027-04-01'),
    ];

    expect(feriasQuePossoOferecer(lista, '2027-01-10').map((p) => p.id)).toEqual([4, 1]);
  });
});

describe('paraContraproposta', () => {
  it('traduz "minhas datas / as do outro" para solicitante/colega, dos dois lados', () => {
    expect(paraContraproposta(true, '2027-03-08', '2027-04-12')).toEqual({
      inicioSolicitante: '2027-03-08',
      inicioColega: '2027-04-12',
      mensagem: undefined,
    });
    expect(paraContraproposta(false, '2027-04-12', '2027-03-08', 'ok?')).toEqual({
      inicioSolicitante: '2027-03-08',
      inicioColega: '2027-04-12',
      mensagem: 'ok?',
    });
  });
});
