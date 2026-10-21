# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY deploy/maven-settings.xml /tmp/public-maven-settings.xml
COPY vendor/ruoyi/ vendor/ruoyi/
# common is a local SNAPSHOT: install the retained vendor reactor before the application.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -s /tmp/public-maven-settings.xml -gs /tmp/public-maven-settings.xml \
      -f vendor/ruoyi/pom.xml -DskipTests install
COPY backend/ backend/
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -s /tmp/public-maven-settings.xml -gs /tmp/public-maven-settings.xml \
      -f backend/pom.xml -DskipTests package && \
    cp backend/target/construction-erp-*.jar /tmp/application.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app && \
    mkdir -p /app/data/attachments && chown -R app:app /app/data
COPY --from=build /tmp/application.jar /app/application.jar
COPY vendor/ruoyi/LICENSE /app/licenses/RuoYi-Vue-Pro-MIT-LICENSE.txt
COPY THIRD_PARTY_NOTICES.md /app/THIRD_PARTY_NOTICES.md
COPY docs/ /app/docs/
USER app
ENV SERVER_PORT=18080 ATTACHMENT_DIR=/app/data/attachments
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
