# Imagem Docker da API do Crona (Fase 9).
#
# "Multi-stage build": a etapa 1 compila (precisa do Maven e baixa as dependências), a etapa 2
# só RODA. Na imagem final vai apenas o .jar, sem o código-fonte nem o cache do Maven.

# ------------------------------------------------------------------ etapa 1: compilar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Primeiro só o pom.xml: as dependências ficam numa camada própria, que o Docker reaproveita
# (cache) enquanto o pom não mudar. Assim, mudar uma linha de Java não baixa tudo de novo.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
# Os testes rodam no CI (Fase 9b), contra um PostgreSQL de verdade; aqui só empacota.
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

# ------------------------------------------------------------------ etapa 2: rodar
# Só o JRE (o necessário para RODAR Java, sem o compilador): imagem menor.
# O modelo do aviso de férias (.jrxml) é compilado pelo JDT, que vem junto no .jar (ver pom.xml).
FROM eclipse-temurin:21-jre

# Não roda como root: se alguém explorar uma falha da aplicação, fica sem poder de administrador.
RUN useradd --system --create-home crona
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER crona

EXPOSE 8080
# MaxRAMPercentage: usa até 75% da memória do contêiner (e não da máquina inteira).
# headless: sem tela; o JasperReports usa as fontes do Java sem precisar de interface gráfica.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
