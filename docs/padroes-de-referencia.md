# Padrões de referência

Este documento reúne padrões que funcionam bem em sistemas de gestão que já estão em produção, como ERPs com dezenas de cadastros e relatórios, e mostra **como cada um entra no Crona e em qual fase**. Também lista armadilhas comuns nesses sistemas, para o Crona não repetir.

Cada padrão tem um código (ex.: `PAD-03`) para ser citado nas issues, nos commits e nos outros documentos. As fases são as do [roadmap](../README.md#roadmap) e as regras citadas estão em [regras-de-negocio.md](regras-de-negocio.md).

## 1. Resumo

| Código | Padrão | Fase |
|---|---|---|
| PAD-01 | Registro de eventos (auditoria + movimentações) | 2–3 |
| PAD-02 | Permissões por operação, não por perfil | 3 |
| PAD-03 | Notificações por tipo, geradas por rotina | 8 |
| PAD-04 | Tela de regras com seções, busca e explicação | 4 e 7 |
| PAD-05 | Bloqueio por prazo e confirmação em ação sensível | 4–5 |
| PAD-06 | Motivos padronizados | 5 |
| PAD-07 | Central de documentos com data de leitura | 8 |
| PAD-08 | Importação de colaboradores por planilha | 2 ou 9 |
| PAD-09 | Relatórios com filtros e exportação | 8 |
| PAD-10 | Contexto visível e telas de cadastro iguais | 7 |
| PAD-11 | Feriados recorrentes e móveis | 4 |

## 2. Padrões

### PAD-01 — Registro de eventos
**Como costuma ser:** toda inclusão, alteração e exclusão grava data, hora, usuário e tipo da ação. Um relatório de auditoria filtra por período, usuário e tipo de ação. Além dos tipos genéricos (incluído, modificado, excluído), existem tipos do próprio negócio.

**No Crona:**
- Uma tabela `evento` alimentada por um *listener* do JPA (`@EntityListeners`) ou pelos próprios serviços, nos pontos do fluxo.
- A mesma tabela serve para duas coisas: **auditoria** (o RH vê quem fez o quê) e **movimentações** do Painel do redesign ("Ana pediu 17/03 a 31/03 há 2 horas").
- Tipos genéricos (`INCLUIDO`, `ALTERADO`, `EXCLUIDO`) e tipos do domínio (`SOLICITACAO_ENVIADA`, `SOLICITACAO_APROVADA`, `TROCA_PROPOSTA`...).
- **Guardar o antes e o depois** dos campos alterados (ex.: em uma coluna `jsonb`). Uma linha de auditoria que só diz "Alterado colaborador 12" não ajuda ninguém a descobrir o que mudou.
- Começar cedo: colocar auditoria num sistema que já existe é muito mais trabalhoso do que nascer com ela.

```text
evento: id, empresa_id, usuario_id, tipo, entidade, entidade_id, dados_antes, dados_depois, criado_em
```

### PAD-02 — Permissões por operação, não por perfil
**Como costuma ser:** o perfil de acesso é um **conjunto de operações**, organizadas em grupo → subgrupo → ação (acessar, incluir, alterar). O administrador marca o que cada perfil pode fazer.

**No Crona:**
- Por enquanto os 3 perfis continuam fixos (Colaborador, Gestor, Administrador), sem tela de configuração.
- Mas o código confere **permissões**, não perfis:

```java
@PreAuthorize("hasAuthority('SOLICITACAO_APROVAR')")   // em vez de hasRole('GESTOR')
```

- A relação perfil → permissões fica num único lugar (um `enum` ou uma tabela). Se um dia os perfis ficarem configuráveis, nenhum *controller* precisa mudar.
- Evitar a tela de centenas de *checkboxes* em lista paginada: se os perfis ficarem configuráveis, agrupar por tela, com "marcar o grupo inteiro".

### PAD-03 — Notificações por tipo, geradas por rotina
**Como costuma ser:** uma rotina diária procura situações que pedem atenção (ex.: itens perto do vencimento) e cria notificações com tipo, data e um link "clique para ver". Um sino com contador mostra as não lidas, e há "marcar todas como lidas".

**No Crona:**
- Um `@Scheduled` diário gera `FERIAS_A_VENCER` (CLT-03), com 90, 60 e 30 dias de antecedência.
- Eventos do fluxo geram as outras: `SOLICITACAO_PENDENTE` (para o gestor), `TROCA_RECEBIDA` (para o colega), `SOLICITACAO_DECIDIDA` (para o colaborador).
- Cada notificação tem um **link para a tela exata** (ex.: `/aprovacoes/42`), que funciona mesmo depois do login (ver PAD-10).
- **Evitar duplicadas:** uma restrição única em `(destinatario_id, tipo, referencia_id, data)`. Rodar a rotina duas vezes no mesmo dia não pode gerar o mesmo aviso duas vezes.

### PAD-04 — Tela de regras com seções, busca e explicação
**Como costuma ser:** os parâmetros do sistema ficam numa única tela, dividida em seções com atalhos na lateral e um campo "filtrar campos". Cada parâmetro é escrito como uma frase ("Bloquear alterações após (dias)"), e algumas seções têm um texto explicando o efeito.

**No Crona (tela de Configurações, EMP-01 a EMP-06):**
- Seções: **Limites** (EMP-01, EMP-04), **Prazos** (EMP-03), **Aprovação** (EMP-06), **Períodos restritos** (EMP-05).
- Cada regra tem: interruptor liga/desliga, valor, uma frase dizendo o que acontece ("Com 10 pessoas e 70% trabalhando, no máximo 3 ficam fora por dia") e o código da regra.
- As regras da CLT aparecem na mesma tela, **travadas**, para o RH ver que elas existem e que não podem ser mudadas.

### PAD-05 — Bloqueio por prazo e confirmação em ação sensível
**Como costuma ser:** depois de N dias, um registro não pode mais ser alterado. Ações sensíveis, como cancelar uma venda ou reimprimir um comprovante, pedem uma confirmação extra (senha ou segundo fator).

**No Crona (candidatas a novas regras configuráveis):**
- **EMP-07 (proposta):** não permitir cancelar férias aprovadas a menos de N dias do início, porque o pagamento sai até 2 dias antes (CLT-08).
- **"Aprovar mesmo assim"** (EMP-01) exige um motivo (ver PAD-06).
- Solicitação `APROVADA` com aviso gerado não pode mais ser editada, só cancelada, e o cancelamento gera um evento (PAD-01).

### PAD-06 — Motivos padronizados
**Como costuma ser:** um cadastro de justificativas com os motivos mais comuns, escolhidos numa lista em vez de digitados.

**No Crona:**
- Na recusa e no "aprovar mesmo assim", o gestor escolhe um motivo da lista e pode completar com texto (`mensagem_decisao`, que já existe).
- Motivos iniciais: "Período de alta demanda", "Equipe já no limite", "Sobreposição com colega-chave", "Outro".
- Com motivos padronizados dá para fazer relatório depois ("por que mais recusamos férias?").

### PAD-07 — Central de documentos com data de leitura
**Como costuma ser:** os arquivos gerados ficam numa central com *gerado em*, *baixado em*, *disponível até* e quem gerou.

**No Crona:**
- O aviso e o recibo de férias ficam no histórico do colaborador, com **data em que foi gerado** e **data em que o colaborador abriu**.
- A data de abertura serve como registro de ciência do aviso, que precisa sair com pelo menos 30 dias de antecedência (CLT-07). Isso tem valor para o RH em caso de questionamento.
- O documento não expira para o colaborador: ele pode precisar do recibo anos depois.

### PAD-08 — Importação de colaboradores por planilha
**Como costuma ser:** na implantação de um cliente, os cadastros iniciais entram por arquivo, separados por tipo, e não um a um.

**No Crona:**
- Importar colaboradores por CSV: nome, e-mail, departamento, gestor, cargo, **data de admissão**.
- Os períodos aquisitivos são gerados automaticamente a partir da admissão.
- Antes de gravar, mostrar uma **prévia** com os erros por linha (e-mail repetido, departamento inexistente, gestor não encontrado) e só gravar quando tudo estiver certo.
- Sem isso, nenhuma empresa com 200 colaboradores começa a usar o sistema.

### PAD-09 — Relatórios com filtros e exportação
**Como costuma ser:** filtros num painel à esquerda e o relatório ao lado, com exportação para PDF e Excel e botão de imprimir. O cabeçalho mostra a empresa, o título, o período filtrado e a versão do sistema, e o rodapé mostra "Página X de Y".

**No Crona (com JasperReports):**
- Primeiros relatórios: **férias a vencer**, **férias do período** (quem sai quando) e **solicitações por status**.
- O mesmo cabeçalho e rodapé em todos os relatórios, num modelo base do Jasper.

### PAD-10 — Contexto visível e telas de cadastro iguais
**Como costuma ser:**
- Um rodapé fixo mostra **empresa, usuário e versão do sistema**.
- Ao entrar, um cartão mostra o **último acesso**, o que ajuda a perceber um acesso indevido.
- Todos os cadastros seguem o mesmo molde: lista com busca e paginação, ícones de editar e excluir, rotas `/x`, `/x/novo` e `/x/:id`, botões Voltar e Salvar sempre no mesmo lugar.
- O login guarda a página pedida (`returnUrl`) e volta para ela depois de entrar.

**No Crona:**
- A **versão no rodapé** ajuda muito a reportar bugs: quem testa sabe exatamente em que versão o problema apareceu.
- Componentes genéricos `crona-lista` e `crona-formulario`, para as telas de cadastro do RH (colaboradores, departamentos, feriados, períodos restritos) saírem todas iguais.
- O `returnUrl` faz o link de uma notificação ou de um e-mail abrir direto na aprovação, mesmo que a pessoa precise entrar antes.

### PAD-11 — Feriados recorrentes e móveis
**Como costuma ser:** o feriado é cadastrado só com dia e mês e se repete todo ano. É simples, mas não comporta feriados que mudam de data (Carnaval, Sexta-feira Santa, Corpus Christi) nem feriados estaduais e municipais.

**No Crona:** o modelo atual (`data` completa e `empresa_id` nulo para nacional) já é melhor. Para a CLT-05 funcionar de verdade, falta:
- Um campo `recorrente` (boolean): `true` para feriados de data fixa (25/12), `false` para os móveis.
- Uma forma de carregar os feriados móveis de cada ano: calcular a partir da Páscoa ou importar de uma fonte pública, por uma rotina anual.
- Decidir se feriados estaduais e municipais entram por empresa ou por localidade.

## 3. Armadilhas a evitar

| # | Armadilha | Como o Crona evita | Fase |
|---|---|---|---|
| A-01 | A API devolve a entidade inteira, **inclusive o hash da senha** | As respostas usam DTOs (`record`) montados à mão. `senha_hash` nunca sai do back-end | 2–3 |
| A-02 | A empresa vem do navegador (`?idEmpresa=2`), e a API confia nela | O `empresa_id` vem **do token**, nunca de um parâmetro. Um teste garante que um usuário não lê dados de outra empresa | 3 |
| A-03 | O navegador guarda dados demais (cadastro completo do usuário e de todas as empresas, CPFs) | O front guarda só o token e o mínimo para a tela. O resto vem da API quando precisa | 3 e 7 |
| A-04 | Recarregar a página (F5) ou abrir um link direto encerra a sessão | Com o token ainda válido, recarregar mantém o usuário dentro. Isso vira um teste E2E | 3 e 7 |
| A-05 | A mesma notificação aparece duas vezes | Restrição única no banco (ver PAD-03) | 8 |
| A-06 | O formulário já abre mostrando erro de validação | Mostrar o erro só depois que o campo for tocado ou no envio | 7 |
| A-07 | Framework parado numa versão sem suporte | Atualizar o Angular e o Spring Boot a cada versão estável, e não deixar acumular | Sempre |
| A-08 | O ambiente de testes fica cheio de dados criados pelos testes automáticos | Testes E2E criam e **apagam** os próprios dados, ou rodam num banco recriado a cada execução | 9 |
