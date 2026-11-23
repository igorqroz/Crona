import { defineConfig, devices } from '@playwright/test';

/**
 * Configuração dos testes E2E do Crona.
 *
 * Os testes abrem um navegador de verdade e usam o Crona INTEIRO (site + API + banco).
 * Por padrão, o endereço é o do docker compose (http://localhost:8088). Para usar outro,
 * por exemplo o `npm start` do Angular (com a API rodando):
 *
 *     $env:E2E_URL = "http://localhost:4200"; npx playwright test
 */
const ENDERECO = process.env.E2E_URL ?? 'http://localhost:8088';

// As contas de datas dos testes (Node) no mesmo fuso do navegador (ver timezoneId abaixo).
// Sem isto, no CI (que roda em UTC) o "hoje" do teste e o da tela podiam ser dias diferentes.
process.env.TZ = 'America/Sao_Paulo';

// Trava de segurança: os testes CRIAM e APROVAM pedidos de férias. Eles só podem rodar
// num Crona da sua máquina (ou do CI), nunca no site público.
const hostDoTeste = new URL(ENDERECO).hostname;
if (!['localhost', '127.0.0.1'].includes(hostDoTeste)) {
  throw new Error(`E2E só roda em localhost (recebi "${hostDoTeste}"). Os testes mudam os dados do banco.`);
}

export default defineConfig({
  // Onde estão os testes (os cenários). As telas ficam em pages/ e os dados em fixtures/.
  testDir: './specs',

  // Os arquivos de teste rodam em paralelo (cada um no seu navegador, isolado).
  fullyParallel: true,

  // No CI: um test.only esquecido faz o CI falhar (senão só aquele teste rodaria).
  forbidOnly: !!process.env.CI,

  // No CI, um teste que falhou roda mais uma vez. Se passar na 2ª, o relatório marca
  // como "flaky" (instável): é um aviso para investigar, não para ignorar.
  retries: process.env.CI ? 1 : 0,

  // O relatório: na tela (list) e em HTML (abre com `npm run relatorio`).
  reporter: process.env.CI
    ? [['github'], ['list'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL: ENDERECO,
    // O navegador "mora" no Brasil: datas e nomes de meses em português, horário de Brasília.
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    // Quando um teste falha, guarda o "trace" (um filme passo a passo, com a tela, a rede e
    // o console), um print e um vídeo. Ver com: npx playwright show-trace <arquivo>.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  // Em quais navegadores rodar. Começamos só com o Chromium (o motor do Chrome e do Edge);
  // dá para somar Firefox e WebKit (Safari) aqui depois.
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
