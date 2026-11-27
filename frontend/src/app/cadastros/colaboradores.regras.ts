import { HttpErrorResponse } from '@angular/common/http';

import { Colaborador, DadosDoColaborador, Perfil, Problema } from '../nucleo/modelos';

/*
 * As regras da tela de Colaboradores, sem Angular: funções puras, fáceis de testar.
 */

export const PERFIS: { valor: Perfil; nome: string; descricao: string }[] = [
  { valor: 'COLABORADOR', nome: 'Colaborador', descricao: 'Pede férias, propõe trocas e baixa os avisos.' },
  { valor: 'GESTOR', nome: 'Gestor', descricao: 'Também aprova as férias e as trocas da equipe.' },
  { valor: 'ADMIN', nome: 'Administrador (RH)', descricao: 'Também cuida dos cadastros e confirma as férias.' },
];

export function nomeDoPerfil(perfil: Perfil): string {
  return PERFIS.find((p) => p.valor === perfil)?.nome ?? perfil;
}

/** A classe do selo de cada perfil (peças do P2): Colaborador cinza, Gestor azul claro, RH escuro. */
export function seloDoPerfil(perfil: Perfil): string {
  return perfil === 'ADMIN' ? 'selo selo-forte' : perfil === 'GESTOR' ? 'selo selo-info' : 'selo';
}

/** "7 pessoas · 6 com acesso ativo" (o subtítulo da tela). */
export function resumoDaEquipe(total: number, ativos: number): string {
  const pessoas = total === 1 ? '1 pessoa' : `${total} pessoas`;
  return `${pessoas} · ${ativos} com acesso ativo`;
}

/** "Mostrando 21–40 de 142" (o rodapé da tabela). A página começa em 0. */
export function faixaDaPagina(pagina: number, porPagina: number, quantosNaPagina: number, total: number): string {
  if (total === 0) {
    return 'Nenhuma pessoa';
  }
  const de = pagina * porPagina + 1;
  const ate = pagina * porPagina + quantosNaPagina;
  return `Mostrando ${de}–${ate} de ${total}`;
}

/** Os erros de cada campo, com as mesmas mensagens da API (ColaboradorCadastro.java). */
export type ErrosDoFormulario = Partial<Record<'nome' | 'email' | 'senha' | 'dataAdmissao' | 'cargo', string>>;

/** Confere o formulário antes de mandar para a API. Vazio = tudo certo. */
export function validar(dados: DadosDoColaborador, novo: boolean): ErrosDoFormulario {
  const erros: ErrosDoFormulario = {};
  if (!dados.nome.trim()) {
    erros.nome = 'Informe o nome.';
  } else if (dados.nome.trim().length > 150) {
    erros.nome = 'O nome pode ter no máximo 150 caracteres.';
  }
  if (!dados.email.trim()) {
    erros.email = 'Informe o e-mail.';
  } else if (!/^[^\s@]+@[^\s@]+$/.test(dados.email.trim())) {
    erros.email = 'E-mail inválido.';
  }
  if ((dados.cargo ?? '').length > 100) {
    erros.cargo = 'O cargo pode ter no máximo 100 caracteres.';
  }
  if (!dados.dataAdmissao) {
    erros.dataAdmissao = 'Informe a data de admissão.';
  }
  if (novo) {
    const senha = dados.senha ?? '';
    if (!senha) {
      erros.senha = 'Informe a senha inicial.';
    } else if (senha.length < 8 || senha.length > 72) {
      erros.senha = 'A senha deve ter entre 8 e 72 caracteres.';
    }
  }
  return erros;
}

/**
 * Lê a recusa da API. Os 400 trazem um erro por campo; o 409 de e-mail repetido vai para o campo de
 * e-mail; o resto (422 das regras do gestor, por exemplo) vira uma frase geral no fim do painel.
 */
export function errosDaApi(resposta: HttpErrorResponse): { campos: ErrosDoFormulario; geral: string | null } {
  const problema = resposta.error as Problema | null;
  if (problema?.erros) {
    return { campos: problema.erros as ErrosDoFormulario, geral: null };
  }
  if (resposta.status === 409 && problema?.detail?.includes('e-mail')) {
    return { campos: { email: problema.detail }, geral: null };
  }
  return { campos: {}, geral: problema?.detail ?? 'Não foi possível salvar. Tente de novo.' };
}

/** Quem pode ser gestor desta pessoa: tira ela mesma da lista (a API também recusa). */
export function gestoresPossiveis(gestores: Colaborador[], idDaPessoa: number | null): Colaborador[] {
  return gestores.filter((g) => g.id !== idDaPessoa);
}

/** "Roberto tem 4 pessoas na equipe. Para desativar, ..." (a mesma regra do back-end). */
export function avisoDeEquipe(nome: string, pessoas: number): string {
  const quantas = pessoas === 1 ? '1 pessoa' : `${pessoas} pessoas`;
  const primeiro = nome.trim().split(/\s+/)[0];
  return `${primeiro} tem ${quantas} na equipe. Para desativar, passe essas pessoas para outro gestor antes.`;
}

/** O primeiro nome, para os botões ("Desativar acesso de Ana"). */
export function primeiroNome(nome: string): string {
  return nome.trim().split(/\s+/)[0];
}
