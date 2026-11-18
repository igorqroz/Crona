# Redesign do Crona

O redesign foi feito no Claude Design e está em [`prototipo/redesign.html`](prototipo/redesign.html). Abra no navegador para ver as telas lado a lado. Ele substitui o visual do [protótipo antigo](prototipo/index.html) e será a referência da **Fase 7 (front-end Angular)**.

## 1. Telas

**Escopo atual: só a versão para computador (telas 1 a 4).** As telas de celular ficam guardadas no arquivo como referência, mas estão fora do escopo por enquanto.

| # | Tela | Para quem | O que tem de principal |
|---|---|---|---|
| 1 | Painel | Gestor | Cartões "Precisa de você", linha do tempo de quem está fora em 6 semanas, pico de ausências por departamento, movimentações |
| 2 | Aprovações | Gestor | Lista com filtro (com/sem conflito), "aprovar os sem conflito de uma vez", detalhe com gráfico de pessoas fora por dia, sugestão de data alternativa, checagem da CLT |
| 3 | Início | Colaborador | Saldo, andamento do pedido (gestor → RH → aviso), proposta de troca recebida, quem da equipe está fora |
| 4 | Solicitar férias | Colaborador | Calendário de 2 meses com barrinha de colegas fora por dia, resumo, abono, checagem das regras antes de enviar |
| 5–8 | Versão celular | Colaborador e gestor | **Fora do escopo por enquanto** |

## 2. É viável?

**Sim.** O redesign é **HTML e CSS comuns**: flexbox, grid, ícones em SVG e as fontes Inter e Manrope do Google Fonts. Não usa nenhuma biblioteca visual. O Angular gera o mesmo HTML, então o resultado pode ficar idêntico.

| No redesign | Como fica no Crona |
|---|---|
| Estilos escritos direto em cada elemento | **Variáveis CSS** (tokens) e um arquivo de estilo por componente |
| Peças que se repetem (botão, cartão, selo, avatar) | **Componentes Angular** reutilizáveis |
| Ícones SVG copiados em cada lugar | Um componente `<crona-icone nome="calendario">` |
| Calendário de seleção de datas | Componente próprio com **signals** do Angular. A lógica do design já existe e dá para portar quase linha a linha |
| Linha do tempo "quem está fora" | **CSS grid** com uma coluna por dia, sem biblioteca de gráfico |
| Checagem das regras ("começa numa segunda", "cabe no saldo") | O **back-end calcula** com as mesmas regras da Fase 4. O front só mostra |
| "Sugestão: 24/03 a 07/04" | Endpoint no Spring Boot que procura o próximo período livre |
| Aviso de férias em PDF | **JasperReports** |

## 3. Tokens visuais

Estas variáveis vão para o CSS global do Angular. Assim, mudar uma cor muda o sistema inteiro.

```css
:root {
  /* Marca */
  --cor-primaria: #1B6FD4;
  --cor-primaria-escura: #14569F;
  --cor-primaria-clara: #EAF2FD;

  /* Texto */
  --cor-texto: #0D1B3E;
  --cor-texto-secundario: #3D4A66;
  --cor-texto-suave: #5B6680;

  /* Superfícies */
  --cor-fundo: #F4F6FA;
  --cor-superficie: #FFFFFF;
  --cor-borda: #E3E8F0;
  --cor-borda-forte: #CDD5E1;

  /* Estados */
  --cor-alerta: #E8792B;      --cor-alerta-texto: #A84B07;  --cor-alerta-fundo: #FFF1E5;
  --cor-sucesso-texto: #067A57; --cor-sucesso-fundo: #E6F5EE;

  /* Forma */
  --raio-pequeno: 8px;
  --raio: 12px;
  --raio-cartao: 16px;
  --raio-pilula: 999px;
  --altura-toque: 44px;       /* todo botão tem pelo menos 44px: bom para o dedo */

  /* Fontes */
  --fonte-titulo: 'Manrope', 'Segoe UI', system-ui, sans-serif;
  --fonte-texto: 'Inter', 'Segoe UI', system-ui, sans-serif;
}
```

## 4. O que o redesign mudou nas regras

Decisões tomadas ao analisar o redesign (já aplicadas em [regras-de-negocio.md](regras-de-negocio.md), [modelo-de-dominio.md](modelo-de-dominio.md) e no script V1):

| Assunto | Decisão |
|---|---|
| Limite por departamento | Continua sendo **um percentual da empresa** (EMP-01). A tela mostra esse percentual já convertido em pessoas ("limite 3") |
| Pedido acima do limite | Deixa de ser bloqueado: vira **ponto de atenção**, e o gestor pode **aprovar mesmo assim** |
| Fechamento do mês e datas sensíveis | Viraram **períodos restritos** (EMP-05). A empresa escolhe se cada período só **alerta** ou **bloqueia** |
| Confirmação do RH | Nova regra **EMP-06**, configurável. O pedido passa a ter os status `AGUARDANDO_GESTOR` → `AGUARDANDO_RH` → `APROVADA` |
| Recado do colaborador e sugestão do gestor | Novos campos `recado_colaborador` e `mensagem_decisao` na solicitação |

## 5. O que ainda falta desenhar

O redesign cobre as telas principais. Estas aparecem no menu, mas ainda não foram desenhadas:

- Login
- Trocas de período (lista e proposta)
- Histórico e avisos
- Conflitos e calendário da equipe (visão completa)
- Colaboradores, regras e departamentos (cadastros do RH)

Elas podem ser desenhadas antes da Fase 7 ou montadas com os mesmos componentes e tokens.

## 6. O que fica para depois

- **Movimentações e notificações** ("Ana pediu 17/03 a 31/03 há 2 horas"): precisam de uma tabela de eventos. Fase 8.
- **"Aviso de férias PDF assinado":** gerar o PDF é simples. Já uma assinatura digital com validade jurídica é um projeto à parte.
- **"Desfazer" depois de aprovar ou recusar:** dá para fazer com alguns segundos de espera antes de gravar de vez.
