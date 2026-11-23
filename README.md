# Crona — Gestão de Férias

[![CI](https://github.com/igorqroz/Crona/actions/workflows/ci.yml/badge.svg)](https://github.com/igorqroz/Crona/actions/workflows/ci.yml)

O Crona é um sistema web para **planejar, solicitar, negociar e aprovar férias** dentro das regras da CLT e das regras de cada empresa.

> ✅ **V1 no ar: https://crona.onrender.com** (logins de exemplo em [No ar](#no-ar-demonstração)). O fluxo principal funciona de ponta a ponta: o colaborador pede ou negocia uma troca, e o gestor aprova, com aviso em PDF e notificações. Veja o [roadmap](#roadmap).

## O problema

Em muitas empresas, marcar férias ainda depende de conversas, planilhas e e-mails. O colaborador precisa consultar uma ou mais pessoas (gestor, RH, Financeiro) e respeitar regras internas que costumam não estar escritas em lugar nenhum.

Com isso:

- **o colaborador** não sabe quando as férias vencem nem quais datas estão livres;
- **o gestor** precisa conferir tudo na mão antes de aprovar;
- **a empresa** corre o risco de deixar férias vencerem, o que pela CLT obriga o pagamento em dobro.

## A solução

O Crona coloca as regras dentro do sistema. Quando o pedido chega ao gestor, ele já foi validado, e o gestor só precisa aprovar.

### Para o colaborador
- Ver o saldo de cada período aquisitivo e **quando as férias vencem**
- Ver **quais datas estão disponíveis** e quem do setor já marcou férias
- Solicitar férias, inteiras ou divididas, com ou sem abono pecuniário
- **Propor uma troca de período** com um colega e negociar pelo sistema
- Consultar o histórico e o recibo de férias

### Para o gestor
- Aprovar ou recusar solicitações e trocas da própria equipe
- Ver quem da equipe está ou vai estar de férias

### Para o RH / administrador
- Cadastrar colaboradores e departamentos
- **Configurar as regras da empresa**
- Acompanhar conflitos e férias perto de vencer

## Regras: fixas e configuráveis

Cada empresa organiza as férias do seu jeito, então o Crona separa as regras em dois grupos:

| Tipo | Exemplos | Pode mudar? |
|---|---|---|
| **Regras da CLT** | Direito a 30 dias, prazo de vencimento, divisão em até 3 períodos, abono de 1/3 | Não, valem para todos |
| **Regras da empresa** | Limite de pessoas de férias por setor ou na empresa toda, antecedência mínima, períodos bloqueados | Sim: cada empresa liga, desliga e ajusta as suas |

Todas as regras estão detalhadas em [docs/regras-de-negocio.md](docs/regras-de-negocio.md), e o modelo de dados em [docs/modelo-de-dominio.md](docs/modelo-de-dominio.md). Padrões de sistemas de gestão em produção que o Crona vai seguir (e armadilhas a evitar) estão em [docs/padroes-de-referencia.md](docs/padroes-de-referencia.md).

## Stack

| Camada | Tecnologia | Para quê |
|---|---|---|
| Back-end | Java 21 + Spring Boot 4 | API REST com as regras de negócio |
| Banco | PostgreSQL 18 + Flyway | Dados relacionais, com o esquema versionado como código |
| Persistência | Spring Data JPA / Hibernate | Mapeamento entre objetos Java e tabelas |
| Segurança | Spring Security + JWT | Login e permissões por perfil |
| Testes | JUnit 5 + Mockito | Cada regra de negócio vira um teste |
| Front-end | Angular 22 + TypeScript | Interface web (signals, componentes standalone, testes com Vitest) |
| Relatórios | JasperReports 7 | Aviso de férias em PDF (CLT, art. 135) |

## Roadmap

O projeto é construído em fases, e cada fase entrega algo que funciona.

- [x] **Fase 0 — Fundação:** repositório, documentação e regras de negócio
- [x] **Fase 1 — Modelo de domínio:** entidades, relacionamentos e banco PostgreSQL com Flyway
- [x] **Fase 2 — Primeira API:** projeto Spring Boot, cadastro de departamentos e colaboradores, primeiros testes
- [x] **Fase 3 — Autenticação:** login com Spring Security + JWT e permissões por perfil
- [x] **Fase 4 — Regras de férias:** períodos aquisitivos, saldo, divisão das férias e regras da empresa (com TDD)
- [x] **Fase 5 — Solicitação e aprovação:** fluxo completo do pedido até a aprovação do gestor
- [x] **Fase 6 — Negociação:** proposta de troca entre colegas, contraproposta e aprovação
- [x] **Fase 7 — Front-end Angular:** telas do [redesign](docs/redesign.md), versão para computador (celular fica para depois)
  - [x] 7a: projeto Angular 22, login e tela Início do colaborador
  - [x] 7b: solicitar férias (calendário com os colegas fora, checklist das regras e envio)
  - [x] 7c: trocas de período (propor, contrapropor, aceitar, recusar e cancelar, com o histórico das rodadas)
  - [x] 7d: aprovações do gestor (fila com filtro, detalhe com a equipe no período, aprovar mesmo assim, aprovação em lote, trocas e confirmação do RH)
- [x] **Fase 8 — Extras:** aviso de férias em PDF, notificações e calendário do departamento
  - [x] 8a: aviso de férias em PDF (JasperReports), para baixar na tela Início
  - [x] 8b: notificações dentro do Crona (sino no menu, avisos do fluxo e rotina diária de férias a vencer)
  - [x] 8c: calendário da equipe (quem está fora em 2 semanas, 6 semanas ou um trimestre, com os dias acima do limite e os períodos restritos)
- [x] **Fase 9 — Entrega:** Docker, CI com GitHub Actions e deploy
  - [x] 9a: Docker (banco, API e site com nginx no `docker compose`)
  - [x] 9b: CI no GitHub Actions (testes, build e teste de fumaça no Docker a cada PR)
  - [x] 9c: deploy no Render (site e API) com o banco no Neon

## Como rodar

### No ar (demonstração)

**Acesse: https://crona.onrender.com**

O Crona está publicado no [Render](https://render.com) (o site e a API, que roda no Docker) com o banco no [Neon](https://neon.tech) (PostgreSQL). A configuração está em [`render.yaml`](render.yaml), e cada merge na `main` publica a versão nova.

A versão no ar usa os mesmos dados de exemplo: entre com `ana.lima@demo.crona` (colaboradora), `roberto.neves@demo.crona` (gestor) ou `rafael.costa@demo.crona` (RH), todos com a senha `crona123` (a tela de login mostra essas contas: um clique e você entra). Ela roda no plano grátis: depois de 15 minutos sem uso a API "dorme", e o primeiro login seguinte pode levar até um minuto.

### O jeito mais rápido: Docker

Com o [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado, na pasta do projeto:
```bash
docker compose up --build
```
Abra http://localhost:8088 e entre com `ana.lima@demo.crona` (colaboradora), `roberto.neves@demo.crona` (gestor) ou `rafael.costa@demo.crona` (RH), todos com a senha `crona123`.

Sobem 3 contêineres: o PostgreSQL, a API (perfil `demo`, com os dados de exemplo) e o site (nginx, que serve o Angular e repassa `/api` para a API). Para parar: `Ctrl+C`; para apagar também os dados: `docker compose down -v`.

### Sem Docker (para desenvolver)

**Pré-requisitos:** Java 21 e PostgreSQL rodando na porta 5432.

1. Crie o banco e o usuário (uma vez só, como o superusuário `postgres`):
   ```bash
   psql -U postgres -f scripts/criar-banco-local.sql
   ```
2. Suba a API. Na primeira vez, o Flyway cria as tabelas e os dados de exemplo:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
3. Teste fazendo login e usando o token (exemplo no PowerShell):
   ```powershell
   $login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType "application/json" -Body '{"email":"ana.lima@demo.crona","senha":"crona123"}'
   Invoke-RestMethod -Uri http://localhost:8080/minhas-ferias/periodos-aquisitivos -Headers @{ Authorization = "Bearer $($login.token)" }
   ```

Para rodar os testes automatizados: `./mvnw test` (dentro de `backend/`). A cobertura (JaCoCo) sai junto, em `backend/target/site/jacoco/index.html`; `./mvnw verify` também confere o mínimo de cobertura (ver [Cobertura dos testes](#cobertura-dos-testes)).

### Front-end (Angular)

**Pré-requisito:** Node.js 24.15 ou mais novo.

Com a API rodando (passo 2 acima), em outro terminal:
```bash
cd frontend
npm install     # só na primeira vez
npm start       # abre em http://localhost:4200
```
Entre com um dos usuários dos dados de exemplo: `ana.lima@demo.crona` (colaboradora), `roberto.neves@demo.crona` (gestor, vê o menu **Aprovações**) ou `rafael.costa@demo.crona` (RH), todos com a senha `crona123`. Em desenvolvimento, o Angular repassa as chamadas `/api/...` para a API na porta 8080 ([`proxy.conf.json`](frontend/proxy.conf.json)), então não é preciso configurar CORS.

Testes do front (Vitest): `npm test` (dentro de `frontend/`). Com cobertura: `npm run test:cobertura` (relatório em `frontend/coverage/crona/index.html`).

### Cobertura dos testes

**Cobertura** é quanto do código os testes realmente executam: se uma linha (ou um lado de um `if`) nunca roda em nenhum teste, um bug ali passa sem ninguém ver. Ela mostra **onde faltam testes**; não prova que os testes são bons (um teste sem `assert` também "cobre" o código).

| Onde | Ferramenta | Comando | Mínimo exigido (a "catraca") |
|---|---|---|---|
| Back-end | [JaCoCo](https://www.jacoco.org/jacoco/) | `./mvnw verify` | 90% das linhas e 80% dos desvios (`if/else`) |
| Front-end | Vitest (v8) | `npm run test:cobertura` | 25% das linhas e 45% dos desvios |

Abaixo do mínimo, o CI fica vermelho: assim código novo sem teste não entra sem ninguém ver. Os mínimos ficam um pouco abaixo do que temos hoje e sobem conforme os testes aumentam. O front ainda está baixo porque as regras (`*.regras.ts`) estão 100% testadas, mas as páginas (Solicitar, Trocas, Aprovações...) ainda não têm testes de componente: elas são cobertas só pelos testes E2E, que não entram nesta conta.

A cada execução do CI, a cobertura aparece no resumo da página da execução, e o relatório completo (com cada linha pintada de verde ou vermelho) fica nos artefatos `cobertura-backend` e `cobertura-frontend`.

### Testes E2E (Playwright)

Testam o Crona inteiro pelo navegador, como uma pessoa usaria. Com o Crona no ar no Docker (porta 8088):
```bash
cd e2e
npm install                          # só na primeira vez
npx playwright install chromium      # só na primeira vez (baixa o navegador)
npx playwright test
```
Detalhes (ver o navegador, modo UI, relatório, rodar contra o `npm start`) em [`e2e/README.md`](e2e/README.md).

### Integração contínua (CI)

A cada Pull Request e a cada push na `main`, o [GitHub Actions](.github/workflows/ci.yml) roda numa máquina limpa:

1. os testes do back-end, contra um PostgreSQL 18 de verdade;
2. os testes do front-end e o build de produção;
3. se os dois passarem, o Crona inteiro no `docker compose` e um teste de fumaça pelo nginx (a API está viva, o login funciona, uma rota protegida responde e o site abre);
4. com o Crona ainda no ar, os testes E2E com o [Playwright](https://playwright.dev): um navegador de verdade faz login, confere as permissões de cada perfil e segue o fluxo "Ana pede férias, Roberto aprova". O relatório (com prints, vídeos e traces das falhas) fica nos artefatos da execução.

O selo no topo deste README mostra se a `main` está passando.

**Dados de exemplo** (só no perfil `dev`): a *Empresa Demo*, 5 departamentos e 6 colaboradores com os nomes do redesign, cada um com os seus períodos aquisitivos. Todos usam a senha `crona123`: Rafael Costa (ADMIN), Roberto Neves (GESTOR), Ana Lima, Bruno Teles, Marina Jorge e Lucas Prado (COLABORADOR). Os e-mails seguem o padrão `nome.sobrenome@demo.crona`.

### Endpoints

Toda rota, menos o login, exige o cabeçalho `Authorization: Bearer <token>`. O token vem do login e vale 8 horas.

| Método | Caminho | O que faz | Permissão |
|---|---|---|---|
| `POST` | `/auth/login` | Recebe `{"email", "senha"}` e devolve o token | Nenhuma (aberta) |
| `GET` | `/departamentos`, `/departamentos/{id}` | Lista e busca | `DEPARTAMENTO_VER` |
| `POST` `PUT` `DELETE` | `/departamentos`, `/departamentos/{id}` | Cadastra, renomeia e exclui | `DEPARTAMENTO_GERENCIAR` |
| `GET` | `/colaboradores?busca=&departamentoId=&ativo=&page=&size=&sort=` | Pesquisa paginada | `COLABORADOR_VER` |
| `GET` | `/colaboradores/{id}` | Busca | `COLABORADOR_VER` |
| `POST` `PUT` | `/colaboradores`, `/colaboradores/{id}` | Cadastra e altera | `COLABORADOR_GERENCIAR` |
| `POST` | `/colaboradores/{id}/desativar`, `/colaboradores/{id}/reativar` | Desativa e reativa (colaborador nunca é apagado) | `COLABORADOR_GERENCIAR` |
| `GET` | `/minhas-ferias/periodos-aquisitivos` | Os meus períodos, com saldo e situação | Estar logado |
| `POST` | `/minhas-ferias/simulacao` | Confere um pedido (`{"inicio", "diasGozo", "diasAbono"}`) sem gravar e devolve o checklist das regras | Estar logado |
| `GET` | `/minhas-ferias/calendario?de=&ate=` | Para o calendário: quem do meu departamento está fora, o limite (EMP-01), o meu gestor e os feriados (até ~4 meses por vez) | Estar logado |
| `GET` | `/colaboradores/{id}/periodos-aquisitivos` | Os períodos de um colaborador | `COLABORADOR_VER` |
| `PUT` | `/colaboradores/{id}/periodos-aquisitivos/{numero}/faltas` | Lança faltas injustificadas (CLT-02) | `COLABORADOR_GERENCIAR` |
| `POST` | `/minhas-ferias/solicitacoes` | Envia um pedido (`{"inicio", "diasGozo", "diasAbono", "recado"}`). Com impedimento, responde `422` com o checklist e nada é gravado | Estar logado |
| `GET` | `/minhas-ferias/solicitacoes` | Os meus pedidos, com status e decisão | Estar logado |
| `POST` | `/minhas-ferias/solicitacoes/{id}/cancelar` | Desiste de um pedido ainda em análise | Estar logado |
| `GET` | `/minhas-ferias/solicitacoes/{id}/aviso` | O aviso de férias em PDF (só de férias aprovadas) | Estar logado |
| `GET` | `/aprovacoes?filtro=TODOS\|COM_CONFLITO\|SEM_CONFLITO` | A fila de aprovação (o gestor vê a própria equipe; o ADMIN, a empresa toda) | `SOLICITACAO_APROVAR` |
| `GET` | `/aprovacoes/{id}` | O checklist completo de um pedido e quem estará fora | `SOLICITACAO_APROVAR` |
| `POST` | `/aprovacoes/{id}/aprovar` | Aprova. Com pontos de atenção, exige `{"mesmoAssim": true, "mensagem": "motivo"}` | `SOLICITACAO_APROVAR` |
| `POST` | `/aprovacoes/{id}/recusar` | Recusa, sempre com `{"mensagem"}` | `SOLICITACAO_APROVAR` |
| `POST` | `/aprovacoes/aprovar-sem-conflito` | Aprova de uma vez os pedidos sem alerta | `SOLICITACAO_APROVAR` |
| `GET` `POST` | `/confirmacoes-rh`, `/confirmacoes-rh/{id}/confirmar`, `/confirmacoes-rh/{id}/recusar` | A etapa do RH, quando a EMP-06 está ligada | `SOLICITACAO_CONFIRMAR_RH` |
| `GET` | `/notificacoes` | As minhas notificações: o número de não lidas e as 30 mais recentes | Estar logado |
| `POST` | `/notificacoes/{id}/lida`, `/notificacoes/lidas` | Marca uma, ou todas, como lidas | Estar logado |
| `GET` | `/gestao/calendario?de=&ate=` | Quem está fora, por departamento, com o limite e os períodos restritos (o gestor vê a equipe; o ADMIN, a empresa toda) | `COLABORADOR_VER` |

**Notificações:** cada passo do fluxo avisa quem precisa agir ou saber (o gestor recebe o pedido, o colaborador recebe a decisão, o colega recebe a proposta de troca...), e ninguém é avisado da própria ação. Uma rotina diária, às 7h, avisa quem tem férias para tirar quando faltam 90, 60 e 30 dias para o prazo (CLT-03), sem repetir o aviso. Ver [PAD-03](docs/padroes-de-referencia.md).
| `GET` | `/minhas-ferias/equipe` | Férias aprovadas dos colegas da minha equipe que ainda vão começar (as que dá para propor trocar) | Estar logado |
| `GET` | `/minhas-ferias/negociacoes` | As trocas que propus e as que recebi, com o histórico de propostas | Estar logado |
| `POST` | `/minhas-ferias/negociacoes` | Propõe uma troca (`{"pedidoDoColegaId", "pedidoDoSolicitanteId", "inicioSolicitante", "diasSolicitante", "inicioColega", "mensagem"}`). Com impedimento, responde `422` com o checklist dos dois | Estar logado |
| `POST` | `/minhas-ferias/negociacoes/{id}/contrapropor`, `/aceitar`, `/recusar`, `/cancelar` | Responde à proposta (só quem está na vez) ou desiste dela (só quem propôs) | Estar logado |
| `GET` | `/aprovacoes/negociacoes`, `/aprovacoes/negociacoes/{id}` | As trocas da equipe esperando decisão, com o checklist das datas novas dos dois | `SOLICITACAO_APROVAR` |
| `POST` | `/aprovacoes/negociacoes/{id}/aprovar`, `/aprovacoes/negociacoes/{id}/recusar` | Aprova (as datas dos dois mudam) ou recusa com motivo | `SOLICITACAO_APROVAR` |

**Ciclo de vida de um pedido:** `AGUARDANDO_GESTOR` → (`AGUARDANDO_RH`, se a EMP-06 estiver ligada) → `APROVADA`. Ele também pode terminar `RECUSADA` (pelo gestor ou pelo RH, sempre com motivo) ou `CANCELADA` (pelo colaborador, enquanto está em análise). Na hora de aprovar, o pedido é reavaliado com as regras do momento, porque o cenário pode ter mudado desde o envio.

**Troca de férias entre colegas:** a Ana propõe ao Bruno "eu fico com as suas férias e você fica com estas datas". A negociação vai e volta entre `AGUARDANDO_COLEGA` e `AGUARDANDO_SOLICITANTE` enquanto houver contrapropostas, sem limite de rodadas. Quando os dois concordam, ela vai para o gestor (`AGUARDANDO_GESTOR`), que aprova ou recusa. Só entram férias já aprovadas, cada um mantém a quantidade de dias que tinha, e os dois precisam ter o mesmo gestor. As datas novas passam pelas mesmas regras do pedido, avaliadas como se a troca já tivesse acontecido. Detalhes em [regras-de-negocio.md](docs/regras-de-negocio.md#negociação-de-troca).

As regras de férias ficam em classes de domínio puras, testadas com TDD: [`CalendarioAquisitivo`](backend/src/main/java/br/com/crona/ferias/CalendarioAquisitivo.java), [`SaldoDoPeriodo`](backend/src/main/java/br/com/crona/ferias/SaldoDoPeriodo.java), [`ValidadorDeFerias`](backend/src/main/java/br/com/crona/ferias/validacao/ValidadorDeFerias.java) (CLT) e [`ValidadorDaEquipe`](backend/src/main/java/br/com/crona/ferias/validacao/ValidadorDaEquipe.java) (regras da empresa). Cada verificação cita o código da regra em [regras-de-negocio.md](docs/regras-de-negocio.md).

**Permissões de cada perfil** (definidas em [`Permissao.java`](backend/src/main/java/br/com/crona/seguranca/Permissao.java)):

| Perfil | Permissões |
|---|---|
| Colaborador | `DEPARTAMENTO_VER` |
| Gestor | `DEPARTAMENTO_VER`, `COLABORADOR_VER`, `SOLICITACAO_APROVAR` |
| Administrador | Todas |

Os erros seguem o padrão ProblemDetail (RFC 9457): `400` dado inválido, `401` sem login ou token inválido, `403` sem permissão, `404` não encontrado, `409` conflito, `422` regra de negócio.

Em produção, a chave que assina os tokens vem da variável de ambiente `JWT_SEGREDO` (mínimo de 32 caracteres). Sem ela, a API não sobe.

## Protótipo

Antes do código, a ideia foi validada com um protótipo navegável, feito em HTML e JavaScript, sem back-end. Para ver, abra [`docs/prototipo/index.html`](docs/prototipo/index.html) no navegador e entre com `adm@teste.com` / `1234`.

## Sobre o projeto

O Crona nasceu no 1º semestre da faculdade, a partir de uma sugestão de projeto de gestão de férias e da experiência real de marcar férias de forma manual. Ele é desenvolvido como projeto de estudo, com o objetivo de virar um produto usado de verdade.

Desenvolvido por **Igor Queiroz**.
