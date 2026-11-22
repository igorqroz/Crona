import { expect, test } from '@playwright/test';

import { ANA } from '../apoio/contas';
import { entrar } from '../apoio/login';

/**
 * Login e saída. Cada test() começa com um navegador "zerado" (sem cookies, sem localStorage):
 * um teste não depende do que o outro fez.
 */
test.describe('Login', () => {
  test('entra com e-mail e senha certos e cai no Início', async ({ page }) => {
    await entrar(page, ANA);

    // O menu lateral mostra quem entrou.
    await expect(page.getByRole('button', { name: 'Sair' })).toBeVisible();
    await expect(page.getByRole('complementary')).toContainText('Ana Lima');
  });

  test('senha errada mostra o erro e continua no login', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('E-mail').fill(ANA.email);
    await page.getByLabel('Senha').fill('senha-errada');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    // role="alert": a mensagem de erro é anunciada pelo leitor de tela.
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('campos vazios mostram as validações do formulário', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    await expect(page.getByText('Informe um e-mail válido.')).toBeVisible();
    await expect(page.getByText('Informe a senha.')).toBeVisible();
  });

  test('sem login, uma página interna manda de volta para o login (logadoGuard)', async ({ page }) => {
    await page.goto('/solicitar');

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible();
  });

  test('"Sair" encerra a sessão: voltar para o Início pede login de novo', async ({ page }) => {
    await entrar(page, ANA);

    await page.getByRole('button', { name: 'Sair' }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.goto('/inicio');
    await expect(page).toHaveURL(/\/login$/);
  });
});
