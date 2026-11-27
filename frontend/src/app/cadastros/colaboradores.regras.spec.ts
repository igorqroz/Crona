import { HttpErrorResponse } from '@angular/common/http';

import { Colaborador, DadosDoColaborador } from '../nucleo/modelos';
import {
  avisoDeEquipe,
  errosDaApi,
  faixaDaPagina,
  gestoresPossiveis,
  nomeDoPerfil,
  primeiroNome,
  resumoDaEquipe,
  seloDoPerfil,
  validar,
} from './colaboradores.regras';

/*
 * As regras da tela Cadastros > Colaboradores (teste unitário: funções puras, sem Angular).
 */
describe('colaboradores.regras', () => {
  it('nomeDoPerfil e seloDoPerfil: cada perfil com o nome por extenso e o selo dele', () => {
    expect(nomeDoPerfil('COLABORADOR')).toBe('Colaborador');
    expect(nomeDoPerfil('GESTOR')).toBe('Gestor');
    expect(nomeDoPerfil('ADMIN')).toBe('Administrador (RH)');
    expect(seloDoPerfil('COLABORADOR')).toBe('selo');
    expect(seloDoPerfil('GESTOR')).toBe('selo selo-info');
    expect(seloDoPerfil('ADMIN')).toBe('selo selo-forte');
  });

  it('resumoDaEquipe: o subtítulo, no singular e no plural', () => {
    expect(resumoDaEquipe(7, 6)).toBe('7 pessoas · 6 com acesso ativo');
    expect(resumoDaEquipe(1, 1)).toBe('1 pessoa · 1 com acesso ativo');
  });

  describe('faixaDaPagina (a página começa em 0)', () => {
    it.each([
      [0, 20, 20, 142, 'Mostrando 1–20 de 142'],
      [1, 20, 20, 142, 'Mostrando 21–40 de 142'],
      [7, 20, 2, 142, 'Mostrando 141–142 de 142'], // a última página, incompleta
      [0, 20, 1, 1, 'Mostrando 1–1 de 1'],
    ])('página %i, %i por página, %i nela, total %i: "%s"', (pagina, porPagina, nela, total, esperado) => {
      expect(faixaDaPagina(pagina, porPagina, nela, total)).toBe(esperado);
    });

    it('nada encontrado: "Nenhuma pessoa"', () => {
      expect(faixaDaPagina(0, 20, 0, 0)).toBe('Nenhuma pessoa');
    });
  });

  describe('validar (as mesmas mensagens da API)', () => {
    const certo: DadosDoColaborador = {
      nome: 'Hana Teste',
      email: 'hana@empresa.com',
      senha: 'segredo123',
      perfil: 'COLABORADOR',
      cargo: null,
      dataAdmissao: '2024-01-08',
      departamentoId: null,
      gestorId: null,
    };

    it('tudo certo: nenhum erro', () => {
      expect(validar(certo, true)).toEqual({});
    });

    it('formulário vazio de um cadastro novo: os 4 obrigatórios', () => {
      const vazio = { ...certo, nome: ' ', email: '', senha: '', dataAdmissao: '' };
      expect(validar(vazio, true)).toEqual({
        nome: 'Informe o nome.',
        email: 'Informe o e-mail.',
        senha: 'Informe a senha inicial.',
        dataAdmissao: 'Informe a data de admissão.',
      });
    });

    it('ao editar, a senha não é pedida', () => {
      expect(validar({ ...certo, senha: undefined }, false)).toEqual({});
    });

    it('e-mail sem @, ou com espaço: inválido', () => {
      expect(validar({ ...certo, email: 'hana.empresa.com' }, true).email).toBe('E-mail inválido.');
      expect(validar({ ...certo, email: 'ha na@empresa.com' }, true).email).toBe('E-mail inválido.');
    });

    it('os limites: nome 150, cargo 100, senha de 8 a 72', () => {
      expect(validar({ ...certo, nome: 'a'.repeat(150) }, true).nome).toBeUndefined();
      expect(validar({ ...certo, nome: 'a'.repeat(151) }, true).nome).toBe(
        'O nome pode ter no máximo 150 caracteres.',
      );
      expect(validar({ ...certo, cargo: 'a'.repeat(100) }, true).cargo).toBeUndefined();
      expect(validar({ ...certo, cargo: 'a'.repeat(101) }, true).cargo).toBe(
        'O cargo pode ter no máximo 100 caracteres.',
      );
      expect(validar({ ...certo, senha: '1234567' }, true).senha).toBe(
        'A senha deve ter entre 8 e 72 caracteres.',
      );
      expect(validar({ ...certo, senha: '12345678' }, true).senha).toBeUndefined();
      expect(validar({ ...certo, senha: 'a'.repeat(72) }, true).senha).toBeUndefined();
      expect(validar({ ...certo, senha: 'a'.repeat(73) }, true).senha).toBe(
        'A senha deve ter entre 8 e 72 caracteres.',
      );
    });
  });

  describe('errosDaApi', () => {
    const erro = (status: number, corpo: object | null) => new HttpErrorResponse({ status, error: corpo });

    it('400: um erro por campo, sem frase geral', () => {
      expect(errosDaApi(erro(400, { erros: { email: 'E-mail inválido.' } }))).toEqual({
        campos: { email: 'E-mail inválido.' },
        geral: null,
      });
    });

    it('409 de e-mail repetido: vai para o campo de e-mail', () => {
      const detail = 'O e-mail hana@empresa.com já está em uso.';
      expect(errosDaApi(erro(409, { detail }))).toEqual({ campos: { email: detail }, geral: null });
    });

    it('422 de uma regra (ex.: o gestor): frase geral', () => {
      const detail = 'Roberto Neves é gestor de outras pessoas. Transfira a equipe antes de desativar.';
      expect(errosDaApi(erro(422, { detail }))).toEqual({ campos: {}, geral: detail });
    });

    it('sem corpo (rede caiu): a frase genérica', () => {
      expect(errosDaApi(erro(0, null))).toEqual({
        campos: {},
        geral: 'Não foi possível salvar. Tente de novo.',
      });
    });
  });

  it('gestoresPossiveis: a própria pessoa não aparece como gestora dela mesma', () => {
    const pessoa = (id: number, nome: string) => ({ id, nome }) as Colaborador;
    const gestores = [pessoa(1, 'Rafael Costa'), pessoa(2, 'Roberto Neves')];

    expect(gestoresPossiveis(gestores, 2).map((g) => g.nome)).toEqual(['Rafael Costa']);
    expect(gestoresPossiveis(gestores, null)).toHaveLength(2); // cadastro novo: todos
  });

  describe('avisoDeEquipe', () => {
    it('plural: quantas pessoas e o que fazer, com o primeiro nome', () => {
      expect(avisoDeEquipe('Roberto Neves', 5)).toBe(
        'Roberto tem 5 pessoas na equipe. Para desativar, passe essas pessoas para outro gestor antes.',
      );
    });

    // O mesmo cuidado do aviso de Departamentos: com 1 pessoa, a frase fica no singular.
    it('singular: "essa pessoa"', () => {
      expect(avisoDeEquipe('Roberto Neves', 1)).toBe(
        'Roberto tem 1 pessoa na equipe. Para desativar, passe essa pessoa para outro gestor antes.',
      );
    });
  });

  it('primeiroNome: ignora os espaços das pontas e entre os nomes', () => {
    expect(primeiroNome('  Ana   Lima ')).toBe('Ana');
    expect(primeiroNome('Rafael')).toBe('Rafael');
  });
});
