# Imagem Docker do front do Crona (Fase 9).
#
# Etapa 1: o Node compila o Angular (ng build) e gera arquivos estáticos (HTML, JS, CSS).
# Etapa 2: o nginx serve esses arquivos e repassa /api/... para a API. O Node não vai para
# a imagem final: depois de compilado, o Angular é só um site estático.

# ------------------------------------------------------------------ etapa 1: compilar
FROM node:24-alpine AS build
WORKDIR /app

# npm ci instala exatamente o que está no package-lock.json (reprodutível, igual em todo lugar).
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# "production" = build normal. O docker-compose.yml passa "production,demo": a versão de
# demonstração mostra as contas de exemplo na tela de login.
ARG CONFIGURACAO=production
RUN npx ng build --configuration $CONFIGURACAO

# ------------------------------------------------------------------ etapa 2: servir
FROM nginx:1.27-alpine

# O nginx oficial troca ${API_URL} no modelo pelo valor da variável de ambiente ao subir.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/crona/browser /usr/share/nginx/html

ENV API_URL=http://api:8080
EXPOSE 80
