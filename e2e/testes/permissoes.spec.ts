import { expect, test } from '@playwright/test';

import { ANA, RAFAEL, ROBERTO } from '../apoio/contas';
import { entrar } from '../apoio/login';

/**
 * Cada perfil vê um menu diferente. O que se testa aqui é a TELA (menu e perfilGuard).
 * Quem barra de verdade é a API; isso já está coberto no back-end (SegurancaApiTest, PermissaoTest).
 */
test.describe('Permissões por perfil', () => {
  test('colaboradora não vê o menu de Gestão', async ({ page }) => {
    await entrar(page, ANA);

    const menu = page.getByRole('navigation', { name: 'Principal' });
    await expect(menu.getByRole('link', { name: 'Solicitar férias' })).toBeVisible();
    await expect(menu.getByRole('link', { name: /Aprovações/ })).toHaveCount(0);
    await expect(menu.getByRole('link', { name: 'Calendário da equipe' })).toHaveCount(0);
  });

  test('colaboradora que digita /aprovacoes volta para o Início (perfilGuard)', async ({ page }) => {
    await entrar(page, ANA);

    await page.goto('/aprovacoes');

    await expect(page).toHaveURL(/\/inicio$/);
    await expect(page.getByRole('heading', { name: 'Olá, Ana' })).toBeVisible();
  });

  // O mesmo teste para dois perfis: um for gera um test() para cada um.
  for (const conta of [ROBERTO, RAFAEL]) {
    test(`${conta.nome} vê o menu de Gestão e abre as Aprovações`, async ({ page }) => {
      await entrar(page, conta);

      const menu = page.getByRole('navigation', { name: 'Principal' });
      await expect(menu.getByRole('link', { name: 'Calendário da equipe' })).toBeVisible();
      await menu.getByRole('link', { name: /Aprovações/ }).click();

      await expect(page).toHaveURL(/\/aprovacoes$/);
      await expect(page.getByRole('heading', { name: 'Aprovações', level: 1 })).toBeVisible();
    });
  }
});
