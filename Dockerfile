# syntax=docker/dockerfile:1

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

# 唯一的測試 FinalApplicationTests 是 @SpringBootTest，需要真的 MySQL，
# build 階段沒有資料庫 → 必須 skip。測試改用 compose 起來之後再跑。
# BuildKit cache mount 讓 ~/.m2 跨次建置保留，第二次 build 不用重抓依賴。
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests clean package

# repackage 會產出 Final-0.0.1-SNAPSHOT.jar（fat jar）與 .jar.original，
# *.jar 只會命中前者。改名以免把版本號寫死在下一階段。
RUN cp target/*.jar app.jar

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build --chown=spring:spring /build/app.jar app.jar

USER spring

# Java 17 的 file.encoding 跟隨系統 locale，容器內預設是 ASCII，
# 會影響中英雙語內容的處理 → 明確鎖定 UTF-8。
ENV LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
