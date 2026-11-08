# Regras de negócio do Crona

Este documento é a fonte da verdade das regras. Toda regra aqui vira **um teste automatizado** na Fase 4. Cada regra tem um código (ex.: `CLT-03`) para ser citada no código e nos testes.

> As regras da CLT foram resumidas para guiar o sistema e não substituem a orientação do RH ou do jurídico da empresa.

## 1. Conceitos

| Termo | Significado |
|---|---|
| **Período aquisitivo (PA)** | Cada 12 meses de trabalho, contados da admissão. Ao completar um PA, o colaborador ganha direito a férias. |
| **Período concessivo** | Os 12 meses seguintes ao fim do PA. As férias daquele PA precisam ser tiradas dentro dele. |
| **Saldo** | Dias de direito menos os dias já aprovados ou pendentes daquele PA. |
| **Abono pecuniário** | "Vender" 1/3 das férias: o colaborador trabalha esses dias e recebe por eles. |
| **Negociação** | Proposta de troca de período entre dois colaboradores, que depois vai para o gestor. |

## 2. Regras da CLT

| Código | Regra | Base legal |
|---|---|---|
| CLT-01 | A cada 12 meses de trabalho o colaborador adquire direito a **30 dias** de férias. | Art. 130 |
| CLT-02 | Faltas injustificadas no PA reduzem o direito: até 5 → 30 dias; 6 a 14 → 24; 15 a 23 → 18; 24 a 32 → 12; mais de 32 → perde o direito. | Art. 130 |
| CLT-03 | As férias devem ser tiradas no **período concessivo** (12 meses após o fim do PA). Fora dele, a empresa paga **em dobro**. | Art. 134 e 137 |
| CLT-04 | As férias podem ser divididas em **até 3 períodos**: um com **no mínimo 14 dias** e os outros com **no mínimo 5 dias** cada. | Art. 134, §1º |
| CLT-05 | As férias **não podem começar nos 2 dias antes de um feriado ou do descanso semanal** (ex.: não começar na quinta ou sexta para quem folga no sábado e domingo). | Art. 134, §3º |
| CLT-06 | O colaborador pode converter **1/3 dos dias** em abono pecuniário, pedindo até **15 dias antes do fim do PA**. | Art. 143 |
| CLT-07 | A empresa deve **comunicar as férias com pelo menos 30 dias** de antecedência. | Art. 135 |
| CLT-08 | O pagamento das férias deve ser feito **até 2 dias antes do início**. | Art. 145 |
| CLT-09 | Quem decide a data é a empresa, o que justifica a **aprovação do gestor**. | Art. 136 |

## 3. Regras da empresa (configuráveis)

Essas regras variam de empresa para empresa. Cada uma pode ser **ligada, desligada e ajustada** na tela de Configurações. A lista abaixo é o ponto de partida, e outras regras podem entrar depois.

| Código | Regra | Padrão no protótipo |
|---|---|---|
| EMP-01 | **Limite por departamento:** um percentual mínimo do departamento precisa continuar trabalhando. Se a nova solicitação passar do limite, ela é **bloqueada**. | 30% trabalhando |
| EMP-02 | **Aviso de sobreposição:** se há colegas de férias no período, mas dentro do limite, o sistema **avisa** e permite enviar. | — |
| EMP-03 | **Antecedência mínima** para solicitar, em dias. Nunca menor que os 30 dias da CLT-07. | 30 dias |
| EMP-04 | **Limite da empresa toda (financeiro):** não deixar muita gente da empresa sair ao mesmo tempo, por causa do impacto no caixa (CLT-08). | **Não existe no protótipo** |
| EMP-05 | **Períodos bloqueados:** datas em que ninguém (ou um departamento) pode sair, como fechamento de mês ou alta temporada. | **Não existe no protótipo** |

## 4. Fluxos

### Solicitação de férias
1. O colaborador escolhe o PA, a quantidade de dias e a data de início.
2. O sistema calcula a data de fim e valida as regras (CLT-03 a CLT-07, EMP-01 a EMP-05, as que estiverem ligadas).
3. Se bloqueada, o sistema mostra os colegas no período e sugere uma negociação.
4. Se válida, vai para o gestor como **pendente**.
5. O gestor **aprova** ou **recusa**.

### Negociação de troca
1. O colaborador A propõe ao colaborador B: "eu fico com o período X e você com o período Y".
2. B **aceita**, **recusa** ou faz uma **contraproposta**.
3. Se A e B concordam, a negociação vai para o gestor.
4. Com a aprovação do gestor, as férias dos dois são atualizadas.

Status: `AGUARDANDO_COLEGA` ⇄ `AGUARDANDO_SOLICITANTE` (enquanto houver contrapropostas) → `AGUARDANDO_GESTOR` → `APROVADA` | `RECUSADA` | `CANCELADA`. Detalhes em [modelo-de-dominio.md](modelo-de-dominio.md#37-negociação-com-rodadas).

## 5. Perfis

| Perfil | Pode |
|---|---|
| Colaborador | Ver painel e saldos, solicitar férias, negociar trocas, ver histórico e recibo |
| Gestor | Tudo do colaborador, mais aprovar solicitações e negociações da **própria equipe** |
| Administrador | Cadastrar colaboradores e departamentos, configurar regras, ver conflitos de toda a empresa |

## 6. Dúvidas em aberto

Para responder com quem conhece o processo na empresa antes da Fase 4:

- [ ] **EMP-04:** o limite financeiro é por **número de pessoas** de férias ao mesmo tempo ou por **valor total** a pagar no mês?
- [ ] O Financeiro precisa de algum papel no sistema (ex.: ver a previsão de pagamentos do mês)?
- [ ] Quantas vezes uma negociação pode ir e voltar com contraproposta?
- [ ] O gestor pode aprovar uma solicitação que passa do limite do departamento (exceção)?
- [ ] Faltas injustificadas (CLT-02) vão ser lançadas no Crona ou vêm de outro sistema?

## 7. O que corrigir em relação ao protótipo

O protótipo serviu para validar a ideia. Na versão real:

- **Senhas e dados** ficam no servidor, com senha criptografada. No protótipo tudo está no navegador.
- **Fracionamento:** o protótipo oferece 30/20/15/10 dias, mas não confere a regra dos 14 + 5 dias (CLT-04) nem o limite de 3 períodos.
- **Abono:** escolher abono hoje não muda os dias de descanso nem o saldo (CLT-06).
- **Período concessivo:** o protótipo não avisa nem impede férias fora do prazo (CLT-03).
- **Início antes de feriado ou fim de semana** não é verificado (CLT-05).
- **Aprovação de negociação:** o protótipo altera a primeira solicitação que encontra do colaborador, que pode não ser a envolvida na troca.
