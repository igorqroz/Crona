import { HttpErrorResponse } from '@angular/common/http';

import { DepartamentoResumo } from '../nucleo/modelos';
import { avisoDeExclusao, mensagemDoErro, resumo, validarNome } from './departamentos.regras';

/*
 * As regras da tela Cadastros > Departamentos (teste unitário: funções puras, sem Angular).
 */
describe('departamentos.regras', () => {
  const departamento = (pessoas: number, limiteFora: number | null = 1): DepartamentoResumo => ({
    id: 1,
    nome: 'Engenharia',
    pessoas,
    limiteFora,
  });

  describe('resumo', () => {
    it.each([
      [0, 1, 'Ninguém ainda'],
      [1, 1, '1 pessoa · até 1 fora por dia'],
      [5, 2, '5 pessoas · até 2 fora por dia'],
    ])('%i pessoa(s), limite %i: "%s"', (pessoas, limite, esperado) => {
      expect(resumo(departamento(pessoas, limite))).toBe(esperado);
    });

    it('com a EMP-01 desligada (limite nulo), só a quantidade de pessoas', () => {
      expect(resumo(departamento(5, null))).toBe('5 pessoas');
      expect(resumo(departamento(1, null))).toBe('1 pessoa');
    });
  });

  describe('avisoDeExclusao', () => {
    it('sem ninguém no departamento, não há aviso (pode excluir)', () => {
      expect(avisoDeExclusao(departamento(0))).toBeNull();
    });

    it('com gente, diz quantas pessoas e o que fazer, no singular e no plural', () => {
      expect(avisoDeExclusao(departamento(1))).toBe(
        'Engenharia ainda tem 1 pessoa. Para excluir, mude essas pessoas de departamento em Colaboradores.',
      );
      expect(avisoDeExclusao(departamento(5))).toBe(
        'Engenharia ainda tem 5 pessoas. Para excluir, mude essas pessoas de departamento em Colaboradores.',
      );
    });
  });

  describe('validarNome', () => {
    it('tira os espaços das pontas', () => {
      expect(validarNome('  Jurídico  ')).toEqual({ nome: 'Jurídico' });
    });

    it('vazio ou só espaços: pede o nome', () => {
      expect(validarNome('')).toEqual({ erro: 'Informe o nome do departamento.' });
      expect(validarNome('   ')).toEqual({ erro: 'Informe o nome do departamento.' });
    });

    it('100 caracteres pode; 101 não (o mesmo limite da API)', () => {
      expect(validarNome('a'.repeat(100))).toEqual({ nome: 'a'.repeat(100) });
      expect(validarNome('a'.repeat(101))).toEqual({
        erro: 'O nome pode ter no máximo 100 caracteres.',
      });
    });

    it('os espaços das pontas não contam para o limite', () => {
      expect(validarNome(`  ${'a'.repeat(100)}  `)).toEqual({ nome: 'a'.repeat(100) });
    });
  });

  describe('mensagemDoErro', () => {
    const erro = (status: number, corpo: object | null) =>
      new HttpErrorResponse({ status, error: corpo });

    it('400 de validação: usa o erro do campo "nome"', () => {
      const resposta = erro(400, {
        detail: 'Dados inválidos.',
        erros: { nome: 'Informe o nome do departamento.' },
      });
      expect(mensagemDoErro(resposta, 'genérica')).toBe('Informe o nome do departamento.');
    });

    it('409 de nome repetido: usa o detail', () => {
      const resposta = erro(409, { detail: 'Já existe um departamento chamado "Engenharia".' });
      expect(mensagemDoErro(resposta, 'genérica')).toBe(
        'Já existe um departamento chamado "Engenharia".',
      );
    });

    it('sem corpo (rede caiu, 500 sem JSON): a frase genérica', () => {
      expect(mensagemDoErro(erro(0, null), 'Não foi possível criar o departamento.')).toBe(
        'Não foi possível criar o departamento.',
      );
      expect(mensagemDoErro(erro(500, {}), 'genérica')).toBe('genérica');
    });
  });
});
