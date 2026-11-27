# Changelog

O que mudou em cada versão do Crona. O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e os números seguem o [Versionamento Semântico](https://semver.org/lang/pt-BR/) (MAIOR.MENOR.CORREÇÃO):

- **MAIOR** (2.0.0): uma mudança que quebra o que já funcionava (ex.: um endpoint da API que muda de formato).
- **MENOR** (1.3.0): algo novo, sem quebrar nada (uma tela, um filtro, uma regra).
- **CORREÇÃO** (1.2.1): só conserto de bug.

A versão fica em dois lugares, que mudam **juntos**: a `version` do `frontend/package.json` e a `<version>` do
`backend/pom.xml`. O CI confere que os dois são iguais. Ela aparece no pé do menu lateral ("Crona v1.2.0") e em
`GET /actuator/info`. Cada versão lançada vira uma tag `vX.Y.Z` e uma Release no GitHub.

## [1.2.0] - 2026-11-27

### Novo
- **Cadastros > Departamentos** (só o RH): criar, renomear e excluir, com quantas pessoas cada um tem e o limite de
  gente fora por dia (PR #37).
- **Cadastros > Colaboradores** (só o RH): busca, filtros, paginação e um painel para cadastrar, editar, desativar e
  reativar. A API ganhou os filtros `perfil` e `gestorId` (PR #38).
- Acessibilidade: foco visível, bordas de campo com mais contraste, abas e filtros com estado para leitor de tela
  (PR #34).
- Peças visuais únicas (selos, avisos, campos, estado vazio) e tokens de cor, forma e texto (PR #35).
- A versão do sistema no pé do menu e em `/actuator/info`.

### Mudou
- O Início não repete mais o botão "Solicitar férias"; o detalhe do pedido diz de qual departamento é o "Fora no dia"
  (PR #32).

### Corrigido
- BUG-04: o cartão de troca apagava o que estava sendo digitado quando a lista recarregava (PR #31).
- BUG-05: as férias de quem é do RH ficavam presas, sem ninguém para aprovar (PR #33).
- BUG-06: o RH conseguia desativar o próprio acesso ou tirar de si o perfil de ADMIN (PR #38).
- BUG-07: em Colaboradores, a lista podia mostrar o resultado de um filtro antigo (PR #38).

## [1.1.0] - 2026-11-26

### Novo
- O RH cancela férias já aprovadas, até a véspera, com motivo (PR #29).
- Contas de demonstração clicáveis na tela de login (PR #21).
- Testes E2E com Playwright e cobertura mínima no CI (PRs #22, #23, #25, #26).

### Corrigido
- CRONA-01: não dava para impedir férias que coincidem com outras da mesma pessoa (PR #24).
- BUG-02: o detalhe do pedido apagava a mensagem ao recarregar (PR #24).
- BUG-03: a simulação atrasada podia mostrar o resultado de outras datas (PR #28).

## [1.0.0] - 2026-11-22

Primeira versão no ar (crona.onrender.com): login, saldo e períodos aquisitivos, pedido de férias com as regras da
CLT e da empresa, troca de período entre colegas, aprovações do gestor e do RH, aviso de férias em PDF, notificações,
calendário da equipe, Docker e CI (até o PR #20).

[1.2.0]: https://github.com/igorqroz/Crona/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/igorqroz/Crona/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/igorqroz/Crona/releases/tag/v1.0.0
