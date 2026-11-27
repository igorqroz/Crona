# Plano de testes do Crona

Este documento diz **o que** é testado no Crona, **onde** (em qual camada e com qual ferramenta), **o que fica de fora** e **quais regras** valem para todo PR. Ele é o mapa da qualidade do projeto: quem chega ao repositório lê aqui como os testes estão organizados antes de abrir o código.

Situação em 24/11/2026: **257 testes no back-end, 168 no front-end e 11 E2E**, todos rodando no CI a cada Pull Request.

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
              /\        E2E (Playwright)             11    o sistema inteiro, no navegador
             /  \       Componente (Vitest)          114   uma tela, com a API falsa
            /    \      Integração da API (JUnit)    ~110  HTTP + banco de verdade
           /      \     Unidade (JUnit e Vitest)     ~200  regras puras, em milissegundos
          ----------                                       (~147 no back + 54 no front)
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
| Permissões por perfil (colaborador, gestor, RH) | ✅ | ✅ | ✅ (menu) | ✅ |
| Saldo e períodos aquisitivos | ✅ | ✅ | ✅ (Início) | — |
| Regras CLT, da empresa e do Crona (simulação) | ✅ | ✅ | ✅ (Solicitar) | — |
| Pedir férias | ✅ | ✅ | ✅ (Solicitar) | ✅ |
| Aprovar, recusar e aprovar em lote | ✅ | ✅ | ✅ (Aprovações) | ✅ (aprovar) |
| Confirmação do RH (EMP-06) | ✅ | ✅ | ✅ (Aprovações) | — |
| Cancelamento de férias aprovadas (RH) | ✅ | ✅ | ✅ (Aprovações) | ✅ |
| Troca de férias entre colegas | ✅ | ✅ | ✅ (Trocas e Aprovações) | — |
| Aviso de férias em PDF | ✅ | ✅ | ✅ (Início) | — |
| Notificações (sino) | — | ✅ | ✅ | — |
| Calendário da equipe (gestão) | ✅ | ✅ | ✅ | — |
| Cadastros > Departamentos (RH): lista com pessoas e limite, criar, renomear, excluir | ✅ (regras da tela) | ✅ | ✅ (tela, foco e menu) | — |

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
2. **Cobertura mínima (a "catraca").** Back: 90% das linhas e 80% dos desvios. Front: 80% das linhas e 78% dos desvios. Abaixo disso, o CI fica vermelho. Os mínimos só sobem.
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
| BUG-04 | Quando a lista recarrega (depois de uma ação em OUTRO cartão), o cartão de troca apaga o que estava sendo digitado. Acontece em Aprovações, na mensagem do gestor, e em Trocas, onde a contraproposta aberta fecha. É a mesma causa do BUG-02: o cartão reage a qualquer objeto novo, e não à mudança de troca | Teste de componente (PR #30) | PR #31: os dois cartões só limpam o formulário quando a troca muda (um `computed` com o id, ou com id, status, vez e rodadas) | `troca-para-aprovar.spec.ts` e `trocas.spec.ts` (blocos "BUG-04", 3 testes, um deles guarda) |
| BUG-05 | As férias do RH ficam presas. O pedido do Rafael (ADMIN, sem gestor, único ADMIN) aparece só na fila dele, mas ele não pode aprovar o próprio pedido (422), e o gestor da equipe não o vê. Ninguém consegue aprovar | Teste manual do Igor, confirmado por teste de API | Decisão do Igor: aprova o gestor cadastrado da pessoa (sem gestor, outro ADMIN); o próprio pedido sai das filas de Aprovações e de confirmação do RH. PR #33: o `AprovacaoService` tira o pedido de quem está logado das duas filas (`pendentesQueEuDecido` e `filaDoRh`), e `SolicitacaoFerias.confirmarNoRh` recusa o próprio pedido com 422, como o `aprovarPeloGestor` já fazia. Nos dados de exemplo (`R__dados_desenvolvimento.sql`), o Roberto passa a ser o gestor do Rafael, o único ADMIN; antes, o pedido do Rafael não ia para a fila de ninguém | `AprovacaoApiTest` (bloco "BUG-05", 6 testes, dois deles guardas; o último confere os dados de exemplo) |

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

- Testes dos **guards** (`logadoGuard`, `perfilGuard`) e do **interceptor** que coloca o token.
- Mais cenários E2E: **recusar pedido**, **troca de férias** e **RH confirmando**.
- **E2E repetível sem recriar o banco.** O `ferias.spec.ts` aprova 15 dias da Ana a cada execução, e o saldo dela acaba depois de 2 ou 3 execuções. Com o cancelamento pelo RH, o teste pode devolver os dias no fim.
- **Testes de API menos dependentes do banco.** O `NotificacaoApiTest` conta as notificações do Roberto e quebra se o banco local tiver sobras de um teste manual. Ele pode contar só as que o próprio teste criou.
- **Concorrência no cancelamento.** Dois RHs cancelando as mesmas férias ao mesmo tempo podem gerar notificação em dobro, porque não há trava no pedido. É um risco de baixa probabilidade, ainda não confirmado por teste.
- **Acessibilidade** com axe, junto com os testes do Playwright.
