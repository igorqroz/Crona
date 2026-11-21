# Crona — Gestão de Férias

O Crona é um sistema web para **planejar, solicitar, negociar e aprovar férias** dentro das regras da CLT e das regras de cada empresa.

> 🚧 **Em desenvolvimento.** O projeto está na Fase 0 (fundação). Veja o [roadmap](#roadmap).

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
| Back-end | Java 21 + Spring Boot 3 | API REST com as regras de negócio |
| Banco | PostgreSQL + Flyway | Dados relacionais, com o esquema versionado como código |
| Persistência | Spring Data JPA / Hibernate | Mapeamento entre objetos Java e tabelas |
| Segurança | Spring Security + JWT | Login e permissões por perfil |
| Testes | JUnit 5 + Mockito | Cada regra de negócio vira um teste |
| Front-end | Angular + TypeScript | Interface web |
| Relatórios | JasperReports | Recibo de férias em PDF |

## Roadmap

O projeto é construído em fases, e cada fase entrega algo que funciona.

- [x] **Fase 0 — Fundação:** repositório, documentação e regras de negócio
- [x] **Fase 1 — Modelo de domínio:** entidades, relacionamentos e banco PostgreSQL com Flyway
- [x] **Fase 2 — Primeira API:** projeto Spring Boot, cadastro de departamentos e colaboradores, primeiros testes
- [x] **Fase 3 — Autenticação:** login com Spring Security + JWT e permissões por perfil
- [x] **Fase 4 — Regras de férias:** períodos aquisitivos, saldo, divisão das férias e regras da empresa (com TDD)
- [ ] **Fase 5 — Solicitação e aprovação:** fluxo completo do pedido até a aprovação do gestor
- [ ] **Fase 6 — Negociação:** proposta de troca entre colegas, contraproposta e aprovação
- [ ] **Fase 7 — Front-end Angular:** telas do [redesign](docs/redesign.md), versão para computador (celular fica para depois)
- [ ] **Fase 8 — Extras:** recibo em PDF, notificações e calendário do departamento
- [ ] **Fase 9 — Entrega:** Docker, CI com GitHub Actions e deploy

## Como rodar

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

Para rodar os testes automatizados: `./mvnw test` (dentro de `backend/`).

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
| `GET` | `/colaboradores/{id}/periodos-aquisitivos` | Os períodos de um colaborador | `COLABORADOR_VER` |
| `PUT` | `/colaboradores/{id}/periodos-aquisitivos/{numero}/faltas` | Lança faltas injustificadas (CLT-02) | `COLABORADOR_GERENCIAR` |

As regras de férias ficam em classes de domínio puras, testadas com TDD: [`CalendarioAquisitivo`](backend/src/main/java/br/com/crona/ferias/CalendarioAquisitivo.java), [`SaldoDoPeriodo`](backend/src/main/java/br/com/crona/ferias/SaldoDoPeriodo.java), [`ValidadorDeFerias`](backend/src/main/java/br/com/crona/ferias/validacao/ValidadorDeFerias.java) (CLT) e [`ValidadorDaEquipe`](backend/src/main/java/br/com/crona/ferias/validacao/ValidadorDaEquipe.java) (regras da empresa). Cada verificação cita o código da regra em [regras-de-negocio.md](docs/regras-de-negocio.md).

**Permissões de cada perfil** (definidas em [`Permissao.java`](backend/src/main/java/br/com/crona/seguranca/Permissao.java)):

| Perfil | Permissões |
|---|---|
| Colaborador | `DEPARTAMENTO_VER` |
| Gestor | `DEPARTAMENTO_VER`, `COLABORADOR_VER` |
| Administrador | Todas |

Os erros seguem o padrão ProblemDetail (RFC 9457): `400` dado inválido, `401` sem login ou token inválido, `403` sem permissão, `404` não encontrado, `409` conflito, `422` regra de negócio.

Em produção, a chave que assina os tokens vem da variável de ambiente `JWT_SEGREDO` (mínimo de 32 caracteres). Sem ela, a API não sobe.

## Protótipo

Antes do código, a ideia foi validada com um protótipo navegável, feito em HTML e JavaScript, sem back-end. Para ver, abra [`docs/prototipo/index.html`](docs/prototipo/index.html) no navegador e entre com `adm@teste.com` / `1234`.

## Sobre o projeto

O Crona nasceu no 1º semestre da faculdade, a partir de uma sugestão de projeto de gestão de férias e da experiência real de marcar férias de forma manual. Ele é desenvolvido como projeto de estudo, com o objetivo de virar um produto usado de verdade.

Desenvolvido por **Igor Queiroz**.
