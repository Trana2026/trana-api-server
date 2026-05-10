# ============================================
# Build stage
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Gradle wrapper + 빌드 설정
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# Docker 환경에서는 본인 PC의 JDK 경로 무시 (시스템 JDK 21 사용)
RUN sed -i '/org.gradle.java.home/d' gradle.properties

# 의존성 사전 다운로드 (캐시 레이어)
RUN ./gradlew --no-daemon dependencies

# 소스 복사 + 빌드
COPY src src
RUN ./gradlew --no-daemon bootJar

# ============================================
# Runtime stage
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 보안 — non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 빌드된 jar 복사
COPY --from=build /app/build/libs/*.jar app.jar

# Railway가 PORT 환경변수 동적 주입
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
