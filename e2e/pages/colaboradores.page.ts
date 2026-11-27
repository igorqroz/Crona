import { Locator, Page } from '@playwright/test';

/** Os dados do formulário de cadastro (o que uma pessoa do RH digitaria). */
export interface NovaPessoa {
  nome: string;
  email: string;
  senha: string;
  departamento: string;
  /** "2024-01-08" (o formato do campo de data). */
  admissao: string;
}

/**
 * PAGE OBJECT de Cadastros > Colaboradores (só o RH). Ver o comentário de pages/solicitar.page.ts.
 *
 * O cadastro e a edição acontecem num painel lateral, que é um diálogo (role="dialog"):
 * os campos são achados DENTRO dele, pelo rótulo.
 */
export class ColaboradoresPagina {
  constructor(private readonly page: Page) {}

  async abrir(): Promise<void> {
    await this.page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Colaboradores' }).click();
  }

  /** O painel aberto (cadastro ou edição). */
  get painel(): Locator {
    return this.page.getByRole('dialog');
  }

  async cadastrar(pessoa: NovaPessoa): Promise<void> {
    await this.page.getByRole('button', { name: 'Novo colaborador' }).click();
    const painel = this.page.getByRole('dialog', { name: 'Novo colaborador' });
    await painel.getByRole('textbox', { name: 'Nome completo' }).fill(pessoa.nome);
    await painel.getByRole('textbox', { name: 'E-mail de trabalho' }).fill(pessoa.email);
    await painel.getByLabel('Departamento').selectOption({ label: pessoa.departamento });
    await painel.getByLabel('Data de admissão').fill(pessoa.admissao);
    await painel.getByLabel('Senha inicial').fill(pessoa.senha);
    await painel.getByRole('button', { name: 'Cadastrar colaborador' }).click();
  }

  /** Abre o painel de edição pelo botão "Editar X" da tabela. */
  async editar(nome: string): Promise<void> {
    await this.page.getByRole('button', { name: `Editar ${nome}`, exact: true }).click();
  }

  /** Os botões Ativos / Inativos / Todos (o nome começa com a situação: "Inativos · 3"). */
  async filtrarPor(situacao: 'Ativos' | 'Inativos' | 'Todos'): Promise<void> {
    await this.page.getByRole('group', { name: 'Situação' }).getByRole('button', { name: new RegExp(`^${situacao}`) }).click();
  }

  /** Os nomes na tabela (o computador; no celular a mesma lista vira cartões). */
  get nomesNaTabela(): Locator {
    return this.page.locator('tbody button.nome');
  }

  get mensagemDeSucesso(): Locator {
    return this.page.getByRole('status');
  }
}
