import { DepartamentoResumo, RegrasDaEmpresa } from '../nucleo/modelos';
import {
  departamentosVazios,
  limiteDoDepartamento,
  mudou,
  previaDosDepartamentos,
  primeiroDiaPossivel,
  validarRegras,
} from './regras.regras';

/*
 * As contas e a validação da tela Cadastros > Regras de férias (teste unitário, sem Angular).
 */
describe('regras.regras', () => {
  describe('limiteDoDepartamento (EMP-01, a mesma conta do back-end)', () => {
    it.each([
      [10, 70, 3], // 10 × 30% = 3 fora
      [5, 70, 1], // 5 × 30% = 1,5 → 1 (arredonda para baixo)
      [5, 100, 1], // ninguém poderia sair: o mínimo é 1
      [1, 70, 1],
      [0, 70, 1],
      [10, 0, 10], // 0% trabalhando: todos podem sair
      [7, 50, 3], // 3,5 → 3
    ])('%i pessoas, %i%% trabalhando: até %i fora', (pessoas, pct, limite) => {
      expect(limiteDoDepartamento(pessoas, pct)).toBe(limite);
    });
  });

  const dep = (id: number, nome: string, pessoas: number): DepartamentoResumo => ({ id, nome, pessoas, limiteFora: 1 });

  it('previaDosDepartamentos: só os departamentos com gente, no singular e no plural', () => {
    const lista = [dep(1, 'Engenharia', 10), dep(2, 'Comercial', 0), dep(3, 'RH', 1)];

    expect(previaDosDepartamentos(lista, 70)).toEqual([
      'Engenharia · 10 pessoas → até 3 fora por dia',
      'RH · 1 pessoa → até 1 fora por dia',
    ]);
  });

  describe('departamentosVazios', () => {
    it('três vazios: "A, B e C ainda não têm ninguém."', () => {
      const lista = [dep(1, 'Comercial', 0), dep(2, 'Financeiro', 0), dep(3, 'Suporte', 0), dep(4, 'RH', 1)];
      expect(departamentosVazios(lista)).toBe('Comercial, Financeiro e Suporte ainda não têm ninguém.');
    });

    it('dois vazios: "A e B"', () => {
      expect(departamentosVazios([dep(1, 'Comercial', 0), dep(2, 'Suporte', 0)])).toBe(
        'Comercial e Suporte ainda não têm ninguém.',
      );
    });

    it('um vazio: no singular ("não tem")', () => {
      expect(departamentosVazios([dep(1, 'Comercial', 0), dep(2, 'RH', 1)])).toBe('Comercial ainda não tem ninguém.');
    });

    it('nenhum vazio: nada a dizer', () => {
      expect(departamentosVazios([dep(1, 'RH', 1)])).toBeNull();
    });
  });

  describe('primeiroDiaPossivel (EMP-03)', () => {
    it('hoje + a antecedência, no formato da tela', () => {
      expect(primeiroDiaPossivel(30, new Date(2026, 10, 27))).toBe('27/12/2026');
    });

    it('vira o ano e o mês certos (dias contados no calendário)', () => {
      expect(primeiroDiaPossivel(45, new Date(2026, 11, 20))).toBe('03/02/2027');
      expect(primeiroDiaPossivel(365, new Date(2027, 0, 1))).toBe('01/01/2028');
    });
  });

  describe('validarRegras (as mesmas regras da API)', () => {
    const certas: RegrasDaEmpresa = {
      percentualMinimoTrabalhando: 70,
      antecedenciaMinimaDias: 30,
      limiteFeriasSimultaneasEmpresa: null,
      exigeConfirmacaoRh: false,
    };

    it('tudo certo, inclusive as regras desligadas (nulo): nenhum erro', () => {
      expect(validarRegras(certas)).toEqual({});
      expect(validarRegras({ ...certas, percentualMinimoTrabalhando: null })).toEqual({});
    });

    it('antecedência: 30 e 365 podem; 29, 366 e quebrados não', () => {
      expect(validarRegras({ ...certas, antecedenciaMinimaDias: 365 })).toEqual({});
      expect(validarRegras({ ...certas, antecedenciaMinimaDias: 29 }).antecedencia).toBe(
        'A antecedência mínima é de 30 dias.',
      );
      expect(validarRegras({ ...certas, antecedenciaMinimaDias: 366 }).antecedencia).toBe(
        'A antecedência pode ser de no máximo 365 dias.',
      );
      expect(validarRegras({ ...certas, antecedenciaMinimaDias: 30.5 }).antecedencia).toBe(
        'A antecedência mínima é de 30 dias.',
      );
    });

    it('percentual: 0 e 100 podem; -1, 101 e quebrados não', () => {
      expect(validarRegras({ ...certas, percentualMinimoTrabalhando: 0 })).toEqual({});
      expect(validarRegras({ ...certas, percentualMinimoTrabalhando: 100 })).toEqual({});
      for (const pct of [-1, 101, 50.5]) {
        expect(validarRegras({ ...certas, percentualMinimoTrabalhando: pct }).percentual).toBe(
          'Informe um percentual de 0 a 100.',
        );
      }
    });

    it('limite da empresa: 1 pode; 0 e quebrados não', () => {
      expect(validarRegras({ ...certas, limiteFeriasSimultaneasEmpresa: 1 })).toEqual({});
      for (const limite of [0, 1.5]) {
        expect(validarRegras({ ...certas, limiteFeriasSimultaneasEmpresa: limite }).limite).toBe(
          'Informe pelo menos 1 pessoa.',
        );
      }
    });
  });

  describe('mudou', () => {
    const salvas: RegrasDaEmpresa = {
      percentualMinimoTrabalhando: 70,
      antecedenciaMinimaDias: 30,
      limiteFeriasSimultaneasEmpresa: null,
      exigeConfirmacaoRh: false,
    };

    it('iguais às salvas: não mudou', () => {
      expect(mudou({ ...salvas }, salvas)).toBe(false);
    });

    it.each([
      ['a antecedência', { antecedenciaMinimaDias: 31 }],
      ['o percentual', { percentualMinimoTrabalhando: 60 }],
      ['desligar o percentual', { percentualMinimoTrabalhando: null }],
      ['o limite da empresa', { limiteFeriasSimultaneasEmpresa: 5 }],
      ['a confirmação do RH', { exigeConfirmacaoRh: true }],
    ])('mudar %s: mudou', (_, troca) => {
      expect(mudou({ ...salvas, ...troca }, salvas)).toBe(true);
    });
  });
});
