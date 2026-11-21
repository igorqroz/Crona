import { barra, diasEntre, montarMes, proximaSelecao, quemEstaFora } from './calendario.regras';

describe('montarMes', () => {
  const contexto = {
    hoje: '2027-03-10',
    feriados: ['2027-03-19'],
    ausencias: [{ nome: 'Marina Jorge', inicio: '2027-03-05', fim: '2027-03-19', pendente: false }],
  };

  it('março de 2027 começa numa segunda: 1 quadrado vazio (o domingo) e 31 dias', () => {
    const mes = montarMes(2027, 2, contexto);

    expect(mes.titulo).toBe('Março 2027');
    expect(mes.vazios).toBe(1);
    expect(mes.dias).toHaveLength(31);
  });

  it('marca fim de semana, feriado, dias que já passaram e quem está fora', () => {
    const dias = montarMes(2027, 2, contexto).dias;
    const dia = (n: number) => dias[n - 1];

    expect(dia(6).fimDeSemana).toBe(true); // sábado
    expect(dia(8).fimDeSemana).toBe(false); // segunda
    expect(dia(19).feriado).toBe(true);
    expect(dia(10).passado).toBe(true); // hoje também não dá
    expect(dia(11).passado).toBe(false);
    expect(dia(12).fora).toEqual(['Marina Jorge']);
    expect(dia(20).fora).toEqual([]);
  });

  it('fevereiro de 2028 (ano bissexto) tem 29 dias', () => {
    expect(montarMes(2028, 1, contexto).dias).toHaveLength(29);
  });
});

describe('quemEstaFora', () => {
  it('conta o primeiro e o último dia das férias', () => {
    const ausencias = [{ nome: 'Ana', inicio: '2027-03-05', fim: '2027-03-07', pendente: true }];

    expect(quemEstaFora(ausencias, '2027-03-05')).toEqual(['Ana']);
    expect(quemEstaFora(ausencias, '2027-03-07')).toEqual(['Ana']);
    expect(quemEstaFora(ausencias, '2027-03-08')).toEqual([]);
  });
});

describe('barra', () => {
  it('cresce com o número de colegas e fica laranja quando mais um passaria do limite', () => {
    expect(barra(0, 3)).toEqual({ largura: 0, tom: 'nenhum' });
    expect(barra(1, 3)).toEqual({ largura: 28, tom: 'um' });
    expect(barra(2, 3)).toEqual({ largura: 52, tom: 'varios' });
    expect(barra(3, 3)).toEqual({ largura: 76, tom: 'limite' });
    expect(barra(9, 3).largura).toBe(100);
  });

  it('sem limite (regra desligada), nunca fica laranja', () => {
    expect(barra(5, null).tom).toBe('varios');
  });
});

describe('proximaSelecao', () => {
  it('1º clique escolhe o início; 2º clique, o fim', () => {
    const inicio = proximaSelecao({ inicio: null, fim: null }, '2027-03-15');
    expect(inicio).toEqual({ inicio: '2027-03-15', fim: null });

    expect(proximaSelecao(inicio, '2027-03-29')).toEqual({ inicio: '2027-03-15', fim: '2027-03-29' });
  });

  it('clicar antes do início troca o início', () => {
    expect(proximaSelecao({ inicio: '2027-03-15', fim: null }, '2027-03-08')).toEqual({ inicio: '2027-03-08', fim: null });
  });

  it('com o intervalo completo, um novo clique recomeça', () => {
    expect(proximaSelecao({ inicio: '2027-03-15', fim: '2027-03-29' }, '2027-04-05')).toEqual({ inicio: '2027-04-05', fim: null });
  });
});

describe('diasEntre', () => {
  it('conta o primeiro e o último dia, inclusive virando o mês', () => {
    expect(diasEntre('2027-03-17', '2027-03-31')).toBe(15);
    expect(diasEntre('2027-03-29', '2027-04-02')).toBe(5);
    expect(diasEntre('2027-03-17', '2027-03-17')).toBe(1);
  });
});
