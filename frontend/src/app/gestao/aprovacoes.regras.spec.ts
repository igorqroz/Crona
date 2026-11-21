import { ItemDeAprovacao, Verificacao } from '../nucleo/modelos';
import { acaoPrincipal, contagens, filtrar, linhaDoTempo, selo } from './aprovacoes.regras';

function item(id: number, situacao: ItemDeAprovacao['situacao']): ItemDeAprovacao {
  return {
    id,
    colaborador: { id, nome: `Pessoa ${id}` },
    departamento: 'Engenharia',
    inicio: '2027-03-15',
    fim: '2027-03-29',
    diasGozo: 15,
    diasAbono: 0,
    periodo: 1,
    recado: null,
    enviadoEm: '2027-02-01T10:00:00Z',
    situacao,
    alertas: [],
  };
}

const atencao = (regra: string): Verificacao => ({ regra, resultado: 'ATENCAO', mensagem: '...' });

describe('filtrar e contagens', () => {
  const fila = [item(1, 'SEM_CONFLITO'), item(2, 'COM_ATENCAO'), item(3, 'COM_IMPEDIMENTO'), item(4, 'SEM_CONFLITO')];

  it('"com conflito" junta atenção e impedimento', () => {
    expect(filtrar(fila, 'COM_CONFLITO').map((i) => i.id)).toEqual([2, 3]);
    expect(filtrar(fila, 'SEM_CONFLITO').map((i) => i.id)).toEqual([1, 4]);
    expect(filtrar(fila, 'TODOS')).toHaveLength(4);
  });

  it('as contagens das abas', () => {
    expect(contagens(fila)).toEqual({ TODOS: 4, COM_CONFLITO: 2, SEM_CONFLITO: 2 });
  });
});

describe('selo', () => {
  it('sem conflito', () => {
    expect(selo('SEM_CONFLITO', [])).toEqual({ texto: 'Sem conflito', tom: 'ok' });
  });

  it('usa o nome curto da regra, e avisa quando há mais alertas', () => {
    expect(selo('COM_ATENCAO', [atencao('EMP-01')])).toEqual({ texto: 'Acima do limite', tom: 'atencao' });
    expect(selo('COM_ATENCAO', [atencao('EMP-01'), atencao('CLT-03')]).texto).toBe('Acima do limite +1');
  });

  it('a regra da equipe ganha das outras, mesmo vindo depois (o que mais importa para o gestor)', () => {
    expect(selo('COM_ATENCAO', [atencao('CLT-03'), atencao('EMP-01')]).texto).toBe('Acima do limite +1');
  });

  it('com impedimento, o impedimento aparece primeiro', () => {
    const alertas: Verificacao[] = [atencao('EMP-01'), { regra: 'CLT-05', resultado: 'IMPEDIMENTO', mensagem: '...' }];

    expect(selo('COM_IMPEDIMENTO', alertas)).toEqual({ texto: 'Impedimento +1', tom: 'impedimento' });
  });
});

describe('acaoPrincipal', () => {
  it('aprovar, aprovar mesmo assim ou nada', () => {
    expect(acaoPrincipal('SEM_CONFLITO')).toBe('aprovar');
    expect(acaoPrincipal('COM_ATENCAO')).toBe('aprovar-mesmo-assim');
    expect(acaoPrincipal('COM_IMPEDIMENTO')).toBe('bloqueado');
  });
});

describe('linhaDoTempo', () => {
  const pedido = { nome: 'Ana Lima', inicio: '2027-03-15', fim: '2027-03-19' }; // 5 dias

  it('uma coluna por dia do pedido; colegas cortados para caber no período', () => {
    const linha = linhaDoTempo(
      pedido,
      [
        { nome: 'Marina', inicio: '2027-03-10', fim: '2027-03-16', pendente: false }, // começa antes
        { nome: 'Lucas', inicio: '2027-03-18', fim: '2027-03-30', pendente: true }, // termina depois
        { nome: 'Bruno', inicio: '2027-04-01', fim: '2027-04-10', pendente: false }, // não encosta
      ],
      2,
    );

    expect(linha.dias).toEqual(['2027-03-15', '2027-03-16', '2027-03-17', '2027-03-18', '2027-03-19']);
    expect(linha.linhas.map((l) => [l.nome, l.de, l.ate])).toEqual([
      ['Marina', 0, 1],
      ['Lucas', 3, 4],
      ['Ana Lima', 0, 4], // o próprio pedido, por último
    ]);
  });

  it('"fora no dia" conta o pedido e marca quando passa do limite', () => {
    const linha = linhaDoTempo(
      pedido,
      [
        { nome: 'Marina', inicio: '2027-03-15', fim: '2027-03-16', pendente: false },
        { nome: 'Lucas', inicio: '2027-03-15', fim: '2027-03-15', pendente: true },
      ],
      2,
    );

    expect(linha.foraNoDia.map((d) => d.total)).toEqual([3, 2, 1, 1, 1]);
    expect(linha.foraNoDia.map((d) => d.acima)).toEqual([true, false, false, false, false]);
  });

  it('sem limite (regra desligada), nada fica acima', () => {
    const linha = linhaDoTempo(pedido, [{ nome: 'Marina', inicio: '2027-03-15', fim: '2027-03-19', pendente: false }], null);

    expect(linha.foraNoDia.every((d) => !d.acima)).toBe(true);
  });
});
