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

### Regras do próprio Crona

Não vêm da CLT nem mudam de empresa para empresa: existem para o sistema não aceitar pedidos sem sentido.

| Código | Regra |
|---|---|
| CRONA-01 | **Sem férias sobrepostas:** as datas de um pedido não podem coincidir com outras férias da mesma pessoa que estejam em análise (aguardando o gestor ou o RH) ou aprovadas, de qualquer período aquisitivo. Vale também para as datas novas de uma troca. Férias que só encostam (uma termina na véspera do início da outra) podem. Pedidos recusados ou cancelados não contam. |

## 3. Regras da empresa (configuráveis)

Essas regras variam de empresa para empresa. Cada uma pode ser **ligada, desligada e ajustada** na tela de Configurações. A lista abaixo é o ponto de partida, e outras regras podem entrar depois.

| Código | Regra | Padrão no protótipo |
|---|---|---|
| EMP-01 | **Limite por departamento:** um percentual mínimo do departamento precisa continuar trabalhando. O percentual é **o mesmo para todos os departamentos**, e a tela mostra o limite já convertido em pessoas (ex.: 10 pessoas e 70% trabalhando → no máximo 3 fora por dia). Se a nova solicitação passar do limite, ela **pode ser enviada como ponto de atenção**: o colaborador vê o aviso (e uma data alternativa), e o gestor decide se recusa ou se **aprova mesmo assim**. | 30% trabalhando |
| EMP-02 | **Aviso de sobreposição:** se há colegas de férias no período, mas dentro do limite, o sistema **avisa** e permite enviar. | — |
| EMP-03 | **Antecedência mínima** para solicitar, em dias. Nunca menor que os 30 dias da CLT-07. | 30 dias |
| EMP-04 | **Limite da empresa toda (financeiro):** não deixar muita gente da empresa sair ao mesmo tempo, por causa do impacto no caixa (CLT-08). | **Não existe no protótipo** |
| EMP-05 | **Períodos restritos:** datas sensíveis para a empresa toda ou para um departamento, como fechamento do mês ou alta temporada. Ao cadastrar, a empresa escolhe o tipo: **alerta** (o pedido pode ser enviado e o gestor vê o aviso) ou **bloqueio** (o pedido nem pode ser enviado). | **Não existe no protótipo** |
| EMP-06 | **Confirmação do RH:** depois da aprovação do gestor, o pedido ainda passa pelo RH antes de ser aprovado de vez e gerar o aviso de férias. Desligada, a aprovação do gestor já é a final. | **Não existe no protótipo** |

### Impedimento ou ponto de atenção

Ao conferir um pedido, cada regra quebrada gera um de dois resultados:
- **Impedimento:** o pedido não pode ser enviado.
- **Ponto de atenção:** o pedido é enviado e o gestor vê o aviso, podendo "aprovar mesmo assim".

| Regra | Tipo | Por quê |
|---|---|---|
| Saldo insuficiente no período aquisitivo | Impedimento | Não existe dia para tirar |
| CRONA-01 Datas que coincidem com outras férias da pessoa | Impedimento | Ninguém tira férias duas vezes no mesmo dia |
| CLT-03 Fim das férias depois do prazo de concessão | Impedimento | Gera pagamento em dobro |
| CLT-03 Período que **já venceu** | Ponto de atenção | As férias continuam devidas (pagas em dobro); bloquear deixaria os dias presos para sempre |
| CLT-04 Divisão inválida (menos de 5 dias, mais de 3 partes, sem uma parte de 14+) | Impedimento | Proibido pela CLT |
| CLT-05 Início nos 2 dias antes de folga ou feriado | Impedimento | Proibido pela CLT |
| CLT-06 Abono acima de 1/3 do direito | Impedimento | Proibido pela CLT |
| CLT-06 Abono pedido a menos de 15 dias do fim do período aquisitivo | Ponto de atenção | A empresa pode aceitar mesmo fora do prazo |
| CLT-07 / EMP-03 Antecedência menor que a mínima | Ponto de atenção | O gestor ou o RH podem aceitar uma exceção |
| EMP-01 Acima do limite do departamento | Ponto de atenção | Decidido no redesign |
| EMP-02 Colegas de férias no período, dentro do limite | Informativo | Só mostra quem estará fora |
| EMP-04 Acima do limite da empresa toda | Ponto de atenção | Igual à EMP-01 |
| EMP-05 Período restrito | Conforme o tipo cadastrado (alerta ou bloqueio) | A empresa escolhe |

### Faltas (CLT-02)

O RH lança no Crona as faltas injustificadas de cada período aquisitivo, e o sistema recalcula os dias de direito. Uma integração com a folha de pagamento pode substituir isso no futuro.

## 4. Fluxos

### Solicitação de férias
1. O colaborador escolhe o primeiro e o último dia no calendário, que mostra quantos colegas já estarão fora em cada dia.
2. O sistema confere as regras (CRONA-01, CLT-03 a CLT-07, EMP-01 a EMP-06, as que estiverem ligadas) e separa o resultado em dois tipos:
   - **Impedimento:** o pedido não pode ser enviado (ex.: passa do saldo ou cai num período restrito do tipo bloqueio).
   - **Ponto de atenção:** o pedido pode ser enviado, e o gestor verá o aviso (ex.: passa do limite do departamento).
3. Quando há conflito com colegas, o sistema sugere uma data alternativa e a opção de propor uma troca.
4. Enviado, o pedido vai para o gestor (`AGUARDANDO_GESTOR`). O colaborador pode deixar um recado.
5. O gestor **aprova**, **aprova mesmo assim** (quando há ponto de atenção) ou **recusa**. Ao recusar, pode escrever o motivo ou sugerir outra data.
6. Se a EMP-06 estiver ligada, o pedido aprovado vai para o RH (`AGUARDANDO_RH`), que confirma.
7. Aprovado de vez (`APROVADA`), o sistema gera o aviso de férias.

### Negociação de troca
1. O colaborador A propõe ao colaborador B: "eu fico com o período X e você com o período Y".
2. B **aceita**, **recusa** ou faz uma **contraproposta**.
3. Se A e B concordam, a negociação vai para o gestor.
4. Com a aprovação do gestor, as férias dos dois são atualizadas.

Status: `AGUARDANDO_COLEGA` ⇄ `AGUARDANDO_SOLICITANTE` (enquanto houver contrapropostas) → `AGUARDANDO_GESTOR` → `APROVADA` | `RECUSADA` | `CANCELADA`. Detalhes em [modelo-de-dominio.md](modelo-de-dominio.md#37-negociação-com-rodadas).

Regras da troca (decididas na Fase 6):

| Regra | Decisão |
|---|---|
| O que pode ser trocado | Só férias do colega já **aprovadas** e que ainda não começaram. Um pedido em análise pode mudar ou ser recusado no meio da conversa. |
| Quantidade de dias | **Cada um mantém os seus dias**: é uma troca de datas, não de saldo. Quem ainda não tem férias marcadas informa quantos dias quer, e o pedido dele nasce na aprovação da troca. |
| Com quem | Só entre colegas com o **mesmo gestor**, que é quem aprova (ou o ADMIN). Quem participa da troca não pode aprová-la. |
| Rodadas | **Sem limite.** Responde quem está na vez (ninguém aceita a própria proposta), e qualquer um pode recusar e encerrar. Quem propôs pode cancelar até o gestor decidir. |
| Regras de férias | As datas novas passam pelas mesmas verificações do pedido, para os dois lados, no cenário **depois** da troca. Impedimento bloqueia a proposta; ponto de atenção vai para o gestor, que pode "aprovar mesmo assim" com motivo. |
| Uma por vez | As mesmas férias não podem estar em duas negociações em aberto ao mesmo tempo. |
| Depois de aprovada | As férias trocadas continuam aprovadas, só mudam as datas (não passam de novo pelo RH, porque os dias não mudam). O pedido novo, de quem não tinha férias marcadas, segue a EMP-06 como qualquer pedido. |

### Cancelamento de férias aprovadas

Para corrigir um erro humano (ex.: férias aprovadas por engano). Antes de aprovar, quem desiste é o próprio colaborador, que cancela o pedido em análise. Depois de aprovado, vale a tabela abaixo.

| Regra | Decisão |
|---|---|
| Quem cancela | **Só o RH** (perfil ADMIN), na aba "Aprovadas" da tela Aprovações. Ninguém cancela as próprias férias por aqui: as do RH precisam de outra pessoa do RH. |
| Até quando | **Até a véspera do início.** Se faltarem 3 dias ou menos, a tela avisa que o pagamento pode já ter saído (CLT-08), mas não bloqueia. |
| Motivo | **Obrigatório.** Fica registrado no pedido junto com quem cancelou e quando, e vai na notificação. |
| Troca em aberto | **Bloqueia.** Se as férias estão numa negociação em aberto, é preciso encerrar a troca antes. |
| Depois de cancelar | O pedido fica `CANCELADA` (o histórico não é apagado), os dias voltam para o saldo, o aviso de férias deixa de valer e o colaborador e o gestor são avisados. |

## 5. Perfis

| Perfil | Pode |
|---|---|
| Colaborador | Ver painel e saldos, solicitar férias, negociar trocas, ver histórico e recibo |
| Gestor | Tudo do colaborador, mais aprovar solicitações e negociações da **própria equipe** |
| Administrador | Cadastrar colaboradores e departamentos, configurar regras, ver conflitos de toda a empresa, cancelar férias aprovadas por engano |

## 6. Dúvidas em aberto

Para responder com quem conhece o processo na empresa antes da Fase 4:

- [ ] **EMP-04:** o limite financeiro é por **número de pessoas** de férias ao mesmo tempo ou por **valor total** a pagar no mês?
- [ ] O Financeiro precisa de algum papel no sistema (ex.: ver a previsão de pagamentos do mês)?
- [x] Quantas vezes uma negociação pode ir e voltar com contraproposta? **Sem limite** (decidido na Fase 6): qualquer um dos dois pode recusar e encerrar. Ver "Regras da troca" na seção 4.
- [x] Para cada regra, ela é **impedimento** ou **ponto de atenção**? Decidido na Fase 4: ver a tabela na seção 3.
- [x] O gestor pode aprovar uma solicitação que passa do limite do departamento? **Sim** (decidido no redesign): o pedido chega com ponto de atenção e o gestor pode "aprovar mesmo assim". Ver EMP-01.
- [x] Faltas injustificadas (CLT-02) vão ser lançadas no Crona ou vêm de outro sistema? **O RH lança no Crona** (ver seção 3).
- [x] Criar a **EMP-07** (não permitir cancelar férias aprovadas a menos de N dias do início)? **Não, por enquanto**: o RH pode cancelar até a véspera, e a tela só avisa quando faltam 3 dias ou menos. Ver "Cancelamento de férias aprovadas" na seção 4.
- [ ] Feriados móveis e estaduais/municipais para a CLT-05: como cadastrar? Ver [PAD-11](padroes-de-referencia.md#pad-11--feriados-recorrentes-e-móveis).

## 7. O que corrigir em relação ao protótipo

O protótipo serviu para validar a ideia. Na versão real:

- **Senhas e dados** ficam no servidor, com senha criptografada. No protótipo tudo está no navegador.
- **Fracionamento:** o protótipo oferece 30/20/15/10 dias, mas não confere a regra dos 14 + 5 dias (CLT-04) nem o limite de 3 períodos.
- **Abono:** escolher abono hoje não muda os dias de descanso nem o saldo (CLT-06).
- **Período concessivo:** o protótipo não avisa nem impede férias fora do prazo (CLT-03).
- **Início antes de feriado ou fim de semana** não é verificado (CLT-05).
- **Aprovação de negociação:** o protótipo altera a primeira solicitação que encontra do colaborador, que pode não ser a envolvida na troca.
