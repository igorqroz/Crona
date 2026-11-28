import { Locator, Page } from '@playwright/test';

/**
 * PAGE OBJECT de Cadastros > Departamentos (só o RH). Ver o comentário de pages/solicitar.page.ts.
 *
 * Os botões de cada linha têm o nome do departamento no nome acessível ("Excluir Engenharia"),
 * então dá para achar o botão certo sem procurar a linha.
 */
export class DepartamentosPagina {
  constructor(private readonly page: Page) {}

  async abrir(): Promise<void> {
    await this.page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Departamentos' }).click();
  }

  async criar(nome: string): Promise<void> {
    await this.page.getByRole('button', { name: 'Novo departamento' }).click();
    await this.page.getByRole('textbox', { name: 'Nome do novo departamento' }).fill(nome);
    await this.page.getByRole('button', { name: 'Criar departamento' }).click();
  }

  /** Clica em "Excluir X": abre o aviso (se houver gente) ou a confirmação. */
  async pedirParaExcluir(nome: string): Promise<void> {
    await this.page.getByRole('button', { name: `Excluir ${nome}`, exact: true }).click();
  }

  /** A linha do departamento (com o resumo: "1 pessoa · até 1 fora por dia"). */
  linha(nome: string): Locator {
    return this.page.locator('li.departamento').filter({ has: this.page.getByText(nome, { exact: true }) });
  }

  get mensagemDeSucesso(): Locator {
    return this.page.getByRole('status');
  }
}
