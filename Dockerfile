# syntax=docker/dockerfile:1.7
# ↑ BuildKit cache mount 사용 위해 필수 선언

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

# 의존성 사전 다운로드 — BuildKit cache mount 로 ~/.gradle/caches 재사용
# 두 번째 빌드부터 dependency 재다운로드 X (수십 MB → 0)
RUN --mount=type=cache,id=s/e728073e-cf90-477a-821c-9857f70d7b0c-/root/.gradle/caches,target=/root/.gradle/caches \
  ./gradlew --no-daemon dependencies

# 소스 복사 + 빌드
COPY src src

# bootJar 도 gradle caches + Kotlin compiler daemon 캐시 재사용
# 증분 컴파일 유지되어 소스 변경분만 재컴파일
RUN --mount=type=cache,id=s/e728073e-cf90-477a-821c-9857f70d7b0c-/root/.gradle/caches,target=/root/.gradle/caches \
  ./gradlew --no-daemon bootJar

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
