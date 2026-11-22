import { CalendarioDaGestao } from '../nucleo/modelos';
import { montarGrade, rotulo, segundaDaSemana, somarDias } from './calendario-equipe.regras';

describe('segundaDaSemana e somarDias', () => {
  it('volta para a segunda-feira da semana (o domingo é o fim da semana anterior)', () => {
    expect(segundaDaSemana(new Date(2027, 2, 17))).toBe('2027-03-15'); // quarta → segunda
    expect(segundaDaSemana(new Date(2027, 2, 15))).toBe('2027-03-15'); // a própria segunda
    expect(segundaDaSemana(new Date(2027, 2, 21))).toBe('2027-03-15'); // domingo → segunda anterior
  });

  it('soma dias virando o mês', () => {
    expect(somarDias('2027-02-26', 3)).toBe('2027-03-01');
  });
});

describe('rotulo', () => {
  it('mesmo mês: "10–21/02"; meses diferentes: "24/02–10/03"', () => {
    expect(rotulo('2027-02-10', '2027-02-21')).toBe('10–21/02');
    expect(rotulo('2027-02-24', '2027-03-10')).toBe('24/02–10/03');
  });
});

describe('montarGrade', () => {
  // Janela de 2 semanas: segunda 01/03/2027 a domingo 14/03/2027. Engenharia com limite 1.
  const dados: CalendarioDaGestao = {
    de: '2027-03-01',
    ate: '2027-03-14',
    departamentos: [
      {
        id: 1,
        nome: 'Engenharia',
        ativos: 6,
        limite: 1,
        pessoas: [
          { id: 3, nome: 'Ana Lima', ausencias: [{ pedidoId: 10, inicio: '2027-02-22', fim: '2027-03-03', pendente: false }] },
          { id: 4, nome: 'Bruno Teles', ausencias: [{ pedidoId: 11, inicio: '2027-03-03', fim: '2027-03-20', pendente: true }] },
        ],
      },
    ],
    restricoes: [
      { motivo: 'Fechamento do mês', departamentoId: null, inicio: '2027-03-08', fim: '2027-03-09', bloqueia: false },
      { motivo: 'Outro departamento', departamentoId: 99, inicio: '2027-03-08', fim: '2027-03-09', bloqueia: true },
    ],
    feriados: ['2027-03-02'],
  };

  it('uma coluna por dia, com fim de semana, feriado e a etiqueta da semana nas segundas', () => {
    const grade = montarGrade(dados, '2027-03-04');

    expect(grade.dias).toHaveLength(14);
    expect(grade.dias[0].semana).toBe('1 mar');
    expect(grade.dias[7].semana).toBe('8 mar');
    expect(grade.dias[1].feriado).toBe(true);
    expect(grade.dias[5].fimDeSemana).toBe(true); // sábado 06/03
    expect(grade.hoje).toBe(3);
  });

  it('as barras são cortadas para caber na janela e mantêm o rótulo com as datas reais', () => {
    const [ana, bruno] = montarGrade(dados, '2027-03-04').departamentos[0].pessoas;

    expect(ana.barras).toEqual([{ de: 0, ate: 2, pendente: false, rotulo: '22/02–03/03' }]);
    expect(bruno.barras).toEqual([{ de: 2, ate: 13, pendente: true, rotulo: '03–20/03' }]);
  });

  it('marca os dias acima do limite, contando pessoas', () => {
    const bloco = montarGrade(dados, '2027-03-04').departamentos[0];

    expect(bloco.subtitulo).toBe('6 pessoas · limite 1');
    expect(bloco.acima).toEqual([{ coluna: 2, total: 2 }]); // só em 03/03 os dois estão fora
  });

  it('os períodos restritos: os da empresa toda e os do próprio departamento (não os de outro)', () => {
    const bloco = montarGrade(dados, '2027-03-04').departamentos[0];

    expect(bloco.restricoes).toEqual([{ de: 7, ate: 8, motivo: 'Fechamento do mês', bloqueia: false }]);
  });

  it('hoje fora da janela: sem linha de hoje', () => {
    expect(montarGrade(dados, '2027-04-01').hoje).toBeNull();
  });
});
