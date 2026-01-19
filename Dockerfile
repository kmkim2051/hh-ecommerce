# ============================================
# E-Commerce Spring Boot Application Dockerfile
# ============================================
#
# 목적: 부하 테스트를 위한 Spring Boot 애플리케이션 컨테이너화
#
# 빌드 방법:
#   docker build -t ecom-app:latest .
#
# 실행 방법:
#   docker-compose up -d
#
# ============================================

# Stage 1: Build
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Gradle 캐시 최적화를 위한 의존성 파일 먼저 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 의존성 다운로드 (캐시 활용)
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사
COPY src ./src

# 애플리케이션 빌드 (테스트 스킵)
RUN gradle bootJar --no-daemon -x test

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 애플리케이션 포트
EXPOSE 8080

# JVM 메모리 설정 (컨테이너 2GB 기준)
ENV JAVA_OPTS="-Xms1024m -Xmx1536m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs"

# 애플리케이션 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ============================================
# Health Check
# ============================================
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
