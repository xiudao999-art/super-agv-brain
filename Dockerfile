# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-8 AS builder

WORKDIR /workspace
COPY . .

# 仅构建可执行应用及其依赖模块；跳过测试执行，但保留测试源码编译检查。
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl scheduling-app -am -DskipTests package

FROM eclipse-temurin:8u492-b09-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 kunling \
    && useradd --uid 10001 --gid kunling --no-create-home --shell /usr/sbin/nologin kunling

WORKDIR /app

COPY --from=builder --chown=kunling:kunling \
    /workspace/scheduling-app/target/kunling-scheduling.jar /app/kunling-scheduling.jar

RUN mkdir -p /app/data/files && chown -R kunling:kunling /app/data

USER kunling

EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8081/v3/api-docs >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/kunling-scheduling.jar"]
