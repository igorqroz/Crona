# Crona — front-end

Interface web do Crona em **Angular 22**. As telas seguem o [redesign](../docs/redesign.md).

## Rodar

```bash
npm install   # só na primeira vez
npm start     # http://localhost:4200 (a API precisa estar rodando na porta 8080)
npm test      # testes com Vitest
```

## Organização

| Pasta | O que tem |
|---|---|
| `src/app/nucleo/` | O que a aplicação inteira usa: sessão (login), interceptor que coloca o token, guard das rotas, tipos da API e ajudantes de data |
| `src/app/compartilhado/` | Componentes reutilizáveis: ícone, logo, avatar |
| `src/app/layout/` | A "casca" das telas logadas: menu lateral + área da página |
| `src/app/ferias/` | Serviço que chama a API de férias |
| `src/app/paginas/` | Uma pasta por tela do colaborador (login, início, solicitar, trocas). As regras de cada tela ficam em funções puras (`*.regras.ts`), testadas sem navegador |
| `src/app/gestao/` | As telas do gestor e do RH (aprovações), protegidas pelo `perfilGuard` |
| `src/styles.css` | Os tokens visuais do redesign (cores, raios, fontes) e as peças comuns (botão, cartão, selo) |

Em desenvolvimento, as chamadas para `/api/...` são repassadas para `http://localhost:8080` pelo [`proxy.conf.json`](proxy.conf.json).

## Problemas comuns

- **Erro `NG0203 ... takeUntilDestroyed() can only be used within an injection context`** (ou outro erro estranho de injeção) logo depois de adicionar um import novo: o cache do Vite ficou com duas cópias do Angular. Pare o `npm start`, apague a pasta `.angular/cache` e rode de novo.
