FROM docker:29-cli@sha256:35cfcf893c20f2931f4909151b5407aaaeab711277de7ce8b8f521f095d54c32 AS docker-cli
FROM eclipse-temurin:25-jdk-noble@sha256:2feab631bffce6236d8bb5261a4abe19a8d6f85bad1c01166f74686c983d011f AS tools
# python3 is only for the existing external dev-stack bootstrap, not agent logic.
RUN apt-get update && apt-get install -y --no-install-recommends git gh ca-certificates bash curl openssl python3 util-linux && rm -rf /var/lib/apt/lists/*
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/libexec/docker/cli-plugins/docker-compose
ENV GRADLE_USER_HOME=/cache/gradle
ENTRYPOINT []

FROM tools AS build
WORKDIR /build
COPY gradlew build.gradle settings.gradle gradle.lockfile ./
COPY gradle/ gradle/
COPY src/main/ src/main/
COPY compose.yaml ./
COPY runtime/gretl-*.sh runtime/
ARG AGENT_VERSION=0.1.0-dev
RUN --mount=type=cache,target=/cache/gradle ./gradlew --no-daemon --console=plain -PagentVersion=${AGENT_VERSION} jar

FROM tools AS agent
ARG AGENT_VERSION=0.1.0-dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="datenportal-themenintegrator-agent" \
      org.opencontainers.image.source="https://github.com/edigonzales/datenportal-themenintegrator-agent" \
      org.opencontainers.image.version="${AGENT_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"
ENV DATENPORTAL_AGENT_HOME=/opt/datenportal-agent
COPY --from=build /build/build/libs/datenportal-integrator.jar /opt/datenportal-agent/build/libs/datenportal-integrator.jar
COPY templates/ /opt/datenportal-agent/templates/
COPY validation/ /opt/datenportal-agent/validation/
COPY config/local.example.toml config/rules.json config/settings.schema.json /opt/datenportal-agent/config/
COPY tests/fixtures/ /opt/datenportal-agent/tests/fixtures/
COPY runtime/Dockerfile /opt/datenportal-agent/runtime/Dockerfile
WORKDIR /workspace
ENTRYPOINT ["java", "-jar", "/opt/datenportal-agent/build/libs/datenportal-integrator.jar"]
CMD ["--help"]
