import { classeDoSelo } from './selo';

/*
 * P2: a ponte entre o "tom" das regras (aprovacoes.regras.ts, trocas.regras.ts) e as classes
 * globais de selo do styles.css (teste unitário).
 */
describe('classeDoSelo', () => {
  it.each([
    ['ok', 'selo selo-sucesso'],
    ['sucesso', 'selo selo-sucesso'],
    ['atencao', 'selo selo-alerta'],
    ['impedimento', 'selo selo-perigo'],
    ['acao', 'selo selo-forte'],
    ['espera', 'selo selo-info'],
    ['encerrada', 'selo'],
  ])('tom "%s" → "%s"', (tom, classe) => {
    expect(classeDoSelo(tom)).toBe(classe);
  });

  it('um tom que a ponte não conhece vira o selo cinza (nunca some nem quebra)', () => {
    expect(classeDoSelo('novo-tom')).toBe('selo');
    expect(classeDoSelo('')).toBe('selo');
  });
});
