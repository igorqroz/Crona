# Plano de testes do Crona

Este documento diz **o que** é testado no Crona, **onde** (em qual camada e com qual ferramenta), **o que fica de fora** e **quais regras** valem para todo PR. Ele é o mapa da qualidade do projeto: quem chega ao repositório lê aqui como os testes estão organizados antes de abrir o código.

Situação em 22/11/2026: **236 testes no back-end, 106 no front-end e 10 E2E**, todos rodando no CI a cada Pull Request.

## 1. Objetivo

Garantir que as regras de férias (CLT e da empresa) e os fluxos principais funcionem sempre:

- o colaborador vê o saldo, simula e pede férias;
- o gestor aprova ou recusa, sozinho ou em lote;
- dois colegas negociam uma troca de férias;
- o RH confirma, quando a empresa liga a EMP-06;
- cada perfil só vê e faz o que pode.

Quando algo quebra, o objetivo é descobrir **no PR**, antes do merge, e não em produção.

## 2. As camadas de teste (a pirâmide)

Quanto mais baixo na pirâmide, mais rápido e barato é o teste. Por isso há muitos testes embaixo e poucos em cima. Cada camada testa o que só ela consegue testar bem.

```
              /\        E2E (Playwright)             10    o sistema inteiro, no navegador
             /  \       Componente (Vitest)          52    uma tela, com a API falsa
            /    \      Integração da API (JUnit)    ~95   HTTP + banco de verdade
           /      \     Unidade (JUnit e Vitest)     ~195  regras puras, em milissegundos
          ----------                                       (~140 no back + 54 no front)
```

| Camada | Ferramenta | Onde fica | O que testa | Exemplo |
|---|---|---|---|---|
| **Unidade (back)** | JUnit 5 + AssertJ | `backend/src/test/.../*Test.java` (sem "Api" no nome) | Regras puras, sem banco: validadores da CLT e da empresa, saldo, máquina de estados do pedido e da troca | `ValidadorDeFeriasTest`, `SolicitacaoFeriasTest` |
| **Integração da API** | JUnit 5 + MockMvc + PostgreSQL | `backend/src/test/.../*ApiTest.java` | A rota HTTP de ponta a ponta no back: segurança, JSON, banco, códigos de resposta (200, 403, 409, 422) | `SolicitacaoApiTest`, `AprovacaoApiTest` |
| **Unidade (front)** | Vitest | `frontend/src/app/**/*.regras.spec.ts` | As decisões das telas em funções puras: qual pedido mostrar, o filtro das aprovações, o calendário | `calendario.regras.spec.ts` |
| **Componente (front)** | Vitest + TestBed | `frontend/src/app/**/*.spec.ts` | Uma tela inteira com a API substituída por um dublê: o que aparece, o que cada botão chama, como a tela trata os erros | `solicitar.spec.ts`, `aprovacoes.spec.ts` |
| **E2E** | Playwright (Chromium) | `e2e/specs`, `e2e/pages`, `e2e/fixtures` | O Crona inteiro no Docker, num navegador de verdade, como uma pessoa usaria | `ferias.spec.ts`: Ana pede, Roberto aprova |
| **Fumaça** | curl + jq (no CI) | `.github/workflows/ci.yml` | Depois de subir o Docker: a API está viva, o login funciona e o site abre | — |

### Como rodar cada camada

| Camada | Comando | Pasta |
|---|---|---|
| Back-end (unidade + integração + cobertura) | `./mvnw verify` | `backend/` (precisa do PostgreSQL local) |
| Front-end (unidade + componente) | `npm test` (ou `npm run test:cobertura`) | `frontend/` |
| E2E | `npx playwright test` | `e2e/` (com o Crona no Docker) |

Mais detalhes no [README](../README.md#cobertura-dos-testes) e no [README do E2E](../e2e/README.md).

## 3. O que é coberto hoje

| Funcionalidade | Unidade | API | Componente | E2E |
|---|---|---|---|---|
| Login, sessão e saída | ✅ | ✅ | ✅ (tela de login) | ✅ |
| Permissões por perfil (colaborador, gestor, RH) | ✅ | ✅ | — | ✅ |
| Saldo e períodos aquisitivos | ✅ | ✅ | ✅ (Início) | — |
| Regras CLT, da empresa e do Crona (simulação) | ✅ | ✅ | ✅ (Solicitar) | — |
| Pedir férias | ✅ | ✅ | ✅ (Solicitar) | ✅ |
| Aprovar, recusar e aprovar em lote | ✅ | ✅ | ✅ (Aprovações) | ✅ (aprovar) |
| Confirmação do RH (EMP-06) | ✅ | ✅ | ✅ (Aprovações) | — |
| Troca de férias entre colegas | ✅ | ✅ | ⚠️ só as regras | — |
| Aviso de férias em PDF | ✅ | ✅ | ✅ (Início) | — |
| Notificações (sino) | — | ✅ | ✅ | — |
| Calendário da equipe (gestão) | ✅ | ✅ | ⚠️ só as regras | — |

✅ coberto · ⚠️ parcial · — não coberto nesta camada

## 4. O que fica de fora (por enquanto)

Fora do escopo é uma **decisão registrada**, não um esquecimento:

- **Testes no site público** (crona.onrender.com). Os testes E2E criam e aprovam pedidos, então só rodam em `localhost`. A configuração do Playwright recusa qualquer outro endereço.
- **Desempenho e carga.** O Crona é um projeto de estudo e roda no plano grátis do Render.
- **Testes de segurança aprofundados** (pentest). As permissões por perfil são testadas, mas ataques não.
- **Outros navegadores.** Só o Chromium por enquanto. Firefox e WebKit entram com uma linha no `playwright.config.ts`.
- **Versão mobile**, que também está fora do escopo do produto.
- **Acessibilidade automatizada (axe).** É um próximo passo (seção 8).

## 5. Ambientes

| Ambiente | Para quê | Observação |
|---|---|---|
| Máquina local | Desenvolver e rodar os testes | PostgreSQL 18 local para o back; Docker para o E2E |
| E2E local sem brigar pela porta | Rodar o E2E com outro Crona no ar | `docker compose -p <nome>` com um override `ports: !override ["8089:80"]` e `E2E_URL=http://localhost:8089`. No fim: `docker compose -p <nome> down -v --rmi local` |
| CI (GitHub Actions) | Rodar tudo a cada PR, numa máquina limpa | O banco é recriado do zero a cada execução |
| Produção (Render) | A versão no ar | **Nenhum teste automatizado roda aqui** |

## 6. Regras para todo Pull Request

1. **CI verde para fazer merge.** Os 3 jobs (back, front, Docker + fumaça + E2E) precisam passar.
2. **Cobertura mínima (a "catraca").** Back: 90% das linhas e 80% dos desvios. Front: 60% das linhas e 65% dos desvios. Abaixo disso, o CI fica vermelho. Os mínimos só sobem.
3. **Código novo vem com teste**, na camada mais barata que consegue testá-lo.
4. **Todo bug corrigido vem com um teste de regressão** (seção 7).

## 7. Regra: todo bug vira um teste de regressão

> **Todo bug encontrado ganha um teste automatizado de regressão, que entra no mesmo PR da correção.**

Um **teste de regressão** é o que garante que um bug já corrigido não volta. Sem ele, uma mudança futura pode trazer o problema de volta sem ninguém perceber.

### Quem faz o quê

Como num time de verdade, os papéis são separados:
- **QA:** acha e documenta o bug e escreve o teste de regressão, que nasce falhando.
- **Desenvolvimento:** corrige o código até o teste passar.

Teste e correção entram **no mesmo PR**. O QA cria a branch da correção (`correcao/...`), commita o teste falhando e passa a branch para o desenvolvimento. Assim, o histórico mostra o teste vermelho antes e verde depois.

### Passo a passo

1. **Reproduza o bug num teste antes de corrigir.** Rode o teste e veja ele **falhar**: é a prova de que ele pega o bug. Um teste que passa antes da correção não protege nada.
2. **Escolha a camada mais barata** que reproduz o bug:
   - uma regra errada vai em teste de unidade;
   - uma tela errada vai em teste de componente;
   - um problema que só aparece com tudo junto (tela + API + banco) vai em E2E.
3. **Corrija o código** e veja o teste **passar**.
4. **Dê ao teste um nome que conte o bug**, ou explique num comentário. Se ele falhar daqui a um ano, quem ler sabe o que voltou.
5. **Registre o bug** na tabela abaixo.

### Registro dos bugs

| Nº | Bug | Achado por | Correção | Teste de regressão |
|---|---|---|---|---|
| BUG-01 | A API aceitava o mesmo período de férias pedido várias vezes pela mesma pessoa, e o saldo era descontado de novo a cada vez | E2E | PR #24: regra CRONA-01 | `ValidadorDeFeriasTest`, `SolicitacaoApiTest`, `NegociacaoApiTest` |
| BUG-02 | Em Aprovações, clicar de novo no mesmo pedido apagava a mensagem que o gestor estava escrevendo | E2E | PR #24 | `detalhe-pedido.spec.ts` |
| BUG-03 | Em Solicitar, uma simulação que chegava depois de a pessoa recomeçar a seleção aparecia na tela, com o botão Enviar habilitado | Teste de componente (PR #26) | PR #28: `simular()` invalida a simulação pendente antes de tudo | `solicitar.spec.ts` (bloco "BUG-03", 3 testes) |

### Como relatar um bug

Antes de corrigir, descreva o bug assim:

- **Título:** o que acontece, numa frase.
- **Passos para reproduzir:** com o usuário de exemplo e os dados usados.
- **Resultado esperado** e **resultado obtido**.
- **Gravidade:**
  - **alta:** dado errado gravado ou regra da CLT violada;
  - **média:** uma funcionalidade não funciona;
  - **baixa:** a tela confunde, mas nada errado é gravado.
- **Evidência:** print, trace do Playwright ou o teste que falha.

## 8. Técnicas usadas nos testes

- **Dublês (fakes).** Nos testes de componente, a API é um objeto falso (`vi.fn()`) que devolve respostas prontas. Assim o teste não depende do back-end e consegue simular erros difíceis de provocar de verdade, como um 422 no envio ou uma resposta atrasada.
- **Relógio fixo.** Testes que dependem da data fixam o "hoje" (`vi.setSystemTime` no front; no back, a "Bia" é admitida há 18 meses em relação à data atual). Sem isso, o teste quebraria sozinho com o passar dos meses.
- **Testes do ponto de vista do usuário.** Os testes de componente e E2E acham os elementos pelo texto ou pelo nome acessível ("15 de março, ...", "Aprovar"), e não pelo código interno.
- **Page Objects.** No E2E, cada tela é uma classe em `e2e/pages`. Se a tela muda, só a classe muda, e os cenários continuam iguais.
- **Prova por mutação.** Para conferir que um teste pega defeitos de verdade, estraga-se o código de propósito e confirma-se que o teste falha. Depois o código volta ao original.

## 9. Próximos passos

- Testes de componente das telas que faltam: **Trocas**, **calendário da gestão** e o **menu (Casca)**.
- Testes dos **guards** (`logadoGuard`, `perfilGuard`) e do **interceptor** que coloca o token.
- Mais cenários E2E: **recusar pedido**, **troca de férias** e **RH confirmando**.
- **Acessibilidade** com axe, junto com os testes do Playwright.
