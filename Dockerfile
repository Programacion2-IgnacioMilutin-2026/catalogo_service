FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system catalogo && useradd --system --gid catalogo --no-create-home catalogo
COPY --from=build /workspace/app.jar app.jar
USER catalogo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
