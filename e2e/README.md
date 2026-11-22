# Testes E2E do Crona (Playwright)

**E2E** (*end-to-end*, ponta a ponta) é o teste que usa o sistema inteiro do jeito que uma pessoa usa:
abre o navegador, digita, clica e confere o que aparece na tela. Por baixo, passa por tudo: o Angular,
o nginx, a API (Spring), o banco (PostgreSQL). Se uma peça não conversa direito com a outra, é aqui que aparece.

O [Playwright](https://playwright.dev) é a ferramenta que controla o navegador (Chromium, Firefox ou WebKit)
a partir de um código TypeScript.

## O que está coberto

| Arquivo | O que testa |
|---|---|
| [`testes/login.spec.ts`](testes/login.spec.ts) | Login certo, senha errada, campos vazios, `logadoGuard` (sem login volta para o login) e "Sair" |
| [`testes/permissoes.spec.ts`](testes/permissoes.spec.ts) | Menu por perfil: colaboradora não vê Gestão; `perfilGuard` em `/aprovacoes`; gestor e RH abrem as Aprovações |
| [`testes/ferias.spec.ts`](testes/ferias.spec.ts) | O fluxo principal com **duas pessoas**: Ana escolhe as datas no calendário e envia; Roberto acha o pedido e aprova; Ana recebe a notificação |

Quem barra o acesso de verdade é a API. Isso já está nos testes do back-end (`SegurancaApiTest`, `PermissaoTest`).
Aqui, o foco é o que a pessoa **vê**.

## Como rodar

1. Suba o Crona no Docker (na raiz do projeto). Usar um nome de projeto próprio (`-p crona-e2e`) deixa o
   banco dos testes separado do seu `docker compose` normal:
   ```bash
   docker compose -p crona-e2e up -d --build --wait
   ```
2. Na pasta `e2e/`:
   ```bash
   npm install                        # só na primeira vez
   npx playwright install chromium    # só na primeira vez
   npx playwright test
   ```

Outros jeitos de rodar:

| Comando | Para quê |
|---|---|
| `npx playwright test --headed` | Mostra o navegador enquanto testa |
| `npx playwright test --ui` | Modo UI: escolhe o teste, roda passo a passo e "volta no tempo" em cada ação |
| `npx playwright test testes/login.spec.ts` | Só um arquivo |
| `npx playwright show-report` | Abre o relatório HTML da última execução |
| `npx playwright show-trace test-results/.../trace.zip` | Abre o *trace* de um teste que falhou (tela, rede e console a cada passo) |
| `npx playwright codegen http://localhost:8088` | Grava o que você clica e gera o código do teste (bom para aprender os seletores) |

**Contra o `npm start`** (front na 4200 + API na 8080), em vez do Docker:
```powershell
$env:E2E_URL = "http://localhost:4200"; npx playwright test
```
Atenção: nesse caso os testes usam o seu banco de desenvolvimento (`crona`) e deixam pedidos nele.

**Para começar do zero** (apaga só o banco dos testes):
```bash
docker compose -p crona-e2e down -v
```

## Os dados

O teste do fluxo **cria e aprova um pedido de férias de verdade** no banco. Por isso:

- a configuração só aceita rodar em `localhost` (nunca no site público);
- as datas não são fixas: o teste pergunta à API (a mesma simulação que a tela usa) quais são as próximas
  datas que passam em todas as regras. Assim ele não "envelhece" e roda várias vezes seguidas;
- no CI, o banco é sempre novo (o contêiner nasce a cada execução).

## Como os testes acham os elementos

Do jeito que uma pessoa (e um leitor de tela) enxerga a página, e não pelo CSS:

```ts
page.getByRole('button', { name: 'Entrar' })   // um botão com esse texto
page.getByLabel('E-mail')                       // o campo com esse rótulo
page.getByRole('region', { name: 'Resumo' })    // a <section> com esse título
```

Se alguém mudar uma classe CSS, o teste continua passando. Se alguém tirar o rótulo de um campo, o teste
falha, e devia mesmo: o campo ficou inacessível.

O `expect` do Playwright **espera sozinho** (até 5 s) a tela chegar no estado esperado. Por isso não há
`sleep` nos testes: eles esperam a coisa certa (um texto, uma URL, uma resposta da API).

## Organização

```
e2e/
├── playwright.config.ts     endereço, navegador, idioma, fuso, o que guardar quando falha
├── apoio/
│   ├── contas.ts            as contas de exemplo (Ana, Roberto, Rafael)
│   ├── login.ts             entrar(page, conta): o login pela tela
│   ├── api.ts               ajudantes que falam direto com a API (preparar dados)
│   └── solicitar.pagina.ts  Page Object da tela "Solicitar férias" (o calendário)
└── testes/                  os testes (*.spec.ts)
```
