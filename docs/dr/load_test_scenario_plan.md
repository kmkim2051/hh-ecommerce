# E-Commerce 시스템 부하 테스트 시나리오 계획서

**작성일:** 2025-12-25
**작성자:** 김경민 (with Claude Code)
**문서 버전:** 1.0

---

## 목차
1. [부하 테스트 요구사항 정리](#1-부하-테스트-요구사항-정리)
2. [프로젝트 아키텍처 분석](#2-프로젝트-아키텍처-분석)
3. [부하 테스트 대상 기능 선정](#3-부하-테스트-대상-기능-선정)
4. [부하 테스트 시나리오 설계](#4-부하-테스트-시나리오-설계)
5. [인프라 구성 방안](#5-인프라-구성-방안)
6. [부하 테스트 도구 선정](#6-부하-테스트-도구-선정)
7. [실행 계획 및 타임라인](#7-실행-계획-및-타임라인)

---

## 1. 부하 테스트 요구사항 정리

### 1.1 과제 요구사항 (@docs/dr/requirements.txt 기반)

#### 과제 #1: 부하 테스트 스크립트 작성 및 진행
- **목표**: 예상 트래픽과 병목 구간을 기준으로 부하 테스트 시나리오를 작성하고, 적합한 부하 테스트 도구를 통해 서버의 성능을 진단
- **산출물**:
  - 부하 테스트 대상 선정 및 목적, 시나리오 등의 계획 문서
  - 적합한 테스트 스크립트 작성 및 수행

#### 과제 #2: 부하 테스트로 인한 문제 개선 및 보고서 작성
- **목표**: 테스트를 진행하며 획득한 다양한 성능 지표를 분석 및 시스템 내의 병목을 탐색 및 개선
- **산출물**:
  - 성능 지표 분석 (TPS, 응답시간, 병목 지점)
  - 가상의 장애 대응 문서 작성 (장애 감지 → 분류 → 대응 → 회고)

---

## 2. 프로젝트 아키텍처 분석

### 2.1 현재 시스템 구조

**기술 스택**:
- **Backend**: Spring Boot 3.5.7, Java 17
- **Database**: MySQL 8.0
- **Cache/Queue**: Redis
- **Message Queue**: Kafka (Confluent 7.5.0 + Zookeeper)
- **Build Tool**: Gradle

**주요 도메인**:
```
[User Service Layer]
- 회원 관리
- 포인트 관리 (충전/사용/환불)

[Product Service Layer]
- 상품 조회/검색
- 장바구니 관리
- 판매 랭킹 (Redis Sorted Set)
- 조회수 집계 (Redis Write-Behind)

[Order Service Layer]
- 주문 생성/조회
- 결제 처리
- 주문 완료 이벤트 발행 (Kafka)

[Coupon Service Layer]
- 쿠폰 발급 (Kafka 기반 비동기 처리)
- 쿠폰 사용
```

### 2.2 예상 병목 지점

**1. Kafka Consumer 처리율**
- 쿠폰 발급 Consumer: 파티션당 초당 처리량 제한
- DB 트랜잭션 처리 시간이 병목

**2. Redis 메모리 및 네트워크**
- 조회수 집계: 메모리 버퍼링으로 완화
- 판매 랭킹: ZUNIONSTORE 성능 (최근 N일 조회)

**3. DB Lock Contention**
- 재고 차감: 비관적 락으로 인한 대기 시간
- 주문 생성: 복잡한 트랜잭션 (Order, OrderItem, Product, Point, Coupon)

**4. 네트워크 레이턴시**
- Kafka 메시지 발행/소비 지연
- Redis 네트워크 RTT

---

## 3. 부하 테스트 대상 기능 선정

### 3.1 선정 기준

**우선순위 평가 기준**:
1. **트래픽 집중도**: 이벤트성 대량 트래픽 발생 가능성
2. **복잡도**: 다중 컴포넌트 연계 (DB, Redis, Kafka)
3. **비즈니스 영향도**: 장애 시 사용자 경험 및 금전적 손실
4. **최적화 검증**: 현재 적용된 성능 최적화 효과 측정

### 3.2 선정된 3가지 핵심 기능

#### 기능 #1: 선착순 쿠폰 발급 시스템 (Kafka 기반 비동기 처리)

**선정 이유**:
- ✅ **높은 트래픽 집중도**: 특정 시간 대량 동시 접속 (선착순 이벤트)
- ✅ **복잡한 아키텍처**: Redis → Kafka → Consumer → DB 전체 파이프라인
- ✅ **성능 병목 검증**: 비동기 처리 파이프라인의 처리율 한계 측정
- ✅ **이벤트성 트래픽**: 순간적인 트래픽 폭증 상황 시뮬레이션

**테스트 목표**:
- Kafka 파티션별 처리 성능 및 처리량 한계 측정
- Redis 검증 단계의 응답 시간 및 처리율 측정
- Consumer 처리율 병목 지점 파악 및 스케일링 효과 검증
- End-to-End 처리 시간 (API 요청 → DB 저장) 측정

**핵심 검증 지표**:
- **TPS (Transactions Per Second)**: 초당 처리 요청 수
- **API 응답 시간**: P50, P95, P99 분포
- **Kafka Consumer Lag**: 메시지 처리 지연 시간
- **Consumer 처리율**: 파티션당 초당 처리량

---

#### 기능 #2: 상품 조회 및 판매 랭킹 (읽기 집약적)

**선정 이유**:
- ✅ **높은 읽기 트래픽**: 메인 페이지, 카테고리 페이지 접근
- ✅ **Redis 캐싱 성능**: Sorted Set (랭킹), Write-Behind (조회수) 처리량 측정
- ✅ **DB 쿼리 최적화**: Covering Index 성능 효과 측정
- ✅ **대용량 읽기 부하**: RPS 증가에 따른 응답 시간 변화 측정

**테스트 목표**:
- Redis 캐시 응답 시간 및 처리량 한계 측정
- 판매 랭킹 조회 성능 측정 (ZUNIONSTORE 연산 시간)
- 조회수 집계 Write-Behind 패턴 성능 효과
- 캐시 히트/미스 상황별 응답 시간 비교

**핵심 검증 지표**:
- **상품 조회 응답 시간**: P50, P95, P99 (캐시 히트 vs 미스)
- **RPS (Requests Per Second)**: 초당 처리 가능 요청 수
- **판매 랭킹 조회 시간**: ZUNIONSTORE 연산 성능
- **Redis 처리량**: 초당 처리 가능한 명령 수

---

### 3.3 선정 기능 요약표

| 순위     | 기능 | 트래픽 특성 | 주요 성능 지표 | 예상 병목 |
|--------|------|------------|--------------|-----------|
| **#1** | 쿠폰 발급 | 이벤트성 폭증 | TPS, API 응답시간, Consumer Lag | Consumer 처리율, Kafka 파티션 |
| **#2** | 상품 조회 | 대량 읽기 | RPS, 응답시간, Redis 처리량 | Redis 메모리, 네트워크 RTT |

---

## 4. 부하 테스트 시나리오 설계

### 4.1 시나리오 #1: 선착순 쿠폰 발급 이벤트

#### 4.1.1 시나리오 설명
```
[상황] 특정 시간 선착순 1,000개 쿠폰 발급 이벤트
[사용자] 동시 접속 5,000명
[목표] 순간 트래픽 폭증 상황에서 API 응답 시간 및 Kafka 파이프라인 처리율 측정
```

#### 4.1.2 테스트 단계

**Phase 1: 준비 단계**
- 쿠폰 생성 (수량: 1,000개)
- Redis 재고 초기화
- Kafka 토픽 초기화

**Phase 2: Ramp-Up (점진적 부하 증가)**
- 0~10초: 100 VU (Virtual Users)
- 10~20초: 500 VU
- 20~30초: 1,000 VU
- 30~60초: 5,000 VU (피크)

**Phase 3: 피크 트래픽 (선착순 이벤트 시작)**
- 60~90초: 5,000 VU 유지
- 모든 사용자가 동시에 쿠폰 발급 요청

**Phase 4: Steady State (안정화)**
- 90~120초: 1,000 VU
- 발급 완료 후 조회 트래픽

**Phase 5: Ramp-Down**
- 120~150초: VU 점진적 감소

#### 4.1.3 테스트 케이스

**TC1-1: Baseline 성능 측정**
- 100 VU → 500 VU → 1,000 VU → 5,000 VU 단계별 부하
- VU 증가에 따른 TPS 및 응답 시간 변화 측정
- 각 단계별 P50, P95, P99 응답 시간 분포 확인

**TC1-2: Before/After 성능 비교**
- Before: Redis 검증 제거 (DB 직접 조회)
- After: Redis 검증 유지 (현재 구조)
- TPS, 응답 시간, DB CPU 사용률 비교

**TC1-3: Consumer 스케일링 효과**
- Consumer 1개 → 3개 → 6개
- 각 구성별 Consumer Lag, 전체 처리 시간 측정
- 스케일링 효율성 분석 (선형 확장 여부)

**TC1-4: 피크 부하 지속 테스트**
- 5,000 VU 부하를 5분간 지속
- 시스템 안정성 및 성능 저하 여부 확인
- CPU, Memory, DB Connection Pool 사용률 모니터링

#### 4.1.4 성공 기준

| 지표 | 목표 | 측정 방법 |
|------|------|-----------|
| **API 응답 시간 (P95)** | < 100ms | k6 summary, Grafana |
| **API 응답 시간 (P99)** | < 200ms | k6 summary, Grafana |
| **TPS** | > 1,000 TPS | k6 summary |
| **Consumer Lag** | < 100ms | Kafka Manager, JMX metrics |
| **에러율** | < 5% | k6 summary (타임아웃, 5xx 에러) |
| **Redis 응답 시간** | < 10ms | Redis SLOWLOG, 애플리케이션 로그 |

---

### 4.2 시나리오 #3: 상품 조회 및 판매 랭킹

#### 4.2.1 시나리오 설명
```
[상황] 메인 페이지 및 카테고리 페이지 대량 접속
[사용자] 1,000 RPS → 2,000 RPS → 5,000 RPS (읽기 집약적)
[목표] Redis 캐시 응답 시간 및 대용량 읽기 처리량 한계 측정
```

#### 4.2.2 테스트 단계

**Phase 1: 캐시 워밍 (Cache Warming)**
- 상품 1,000개 생성
- 판매 데이터 100만 건 생성
- Redis 캐시 사전 로드

**Phase 2: 읽기 부하 (Constant Load)**
- 0~600초: 1,000 RPS (Requests Per Second)
- 상품 조회 (70%) + 판매 랭킹 조회 (30%)

**Phase 3: 피크 부하 (Peak Load)**
- 600~900초: 5,000 RPS
- Redis 캐시 히트율 측정

**Phase 4: 캐시 무효화 테스트**
- 900초: Redis Flush
- 캐시 미스 시 성능 저하 측정
- 캐시 재구축 시간

#### 4.2.3 테스트 케이스

**TC3-1: Baseline 성능 측정 (캐시 히트)**
- 1,000 RPS → 2,000 RPS → 5,000 RPS 단계별 부하
- RPS 증가에 따른 응답 시간 변화 측정
- Redis 캐시 히트 시 P50, P95, P99 응답 시간

**TC3-2: 판매 랭킹 조회 성능**
- 전체 기간 Top 10 (단일 Sorted Set 조회)
- 최근 3일 Top 10 (ZUNIONSTORE 연산)
- 최근 7일 Top 10 (ZUNIONSTORE 연산)
- 각 쿼리별 응답 시간 측정

**TC3-3: 캐시 미스 성능 (DB 조회)**
- Redis FLUSHALL 후 부하 테스트
- 캐시 미스 시 DB 직접 조회 응답 시간
- Covering Index 효과 측정

**TC3-4: Before/After 성능 비교 (Redis)**
- Before: Redis 캐시 제거 (모든 요청 DB 조회)
- After: Redis 캐시 사용 (현재 구조)
- RPS, 응답 시간, DB CPU 사용률 비교

**TC3-5: Before/After 성능 비교 (Index)**
- Before: Covering Index 제거
- After: Covering Index 적용
- 판매량 조회 쿼리 실행 시간 비교 (EXPLAIN ANALYZE)

#### 4.2.4 성공 기준

| 지표 | 목표 | 측정 방법 |
|------|------|-----------|
| **상품 조회 응답 시간 (P95, 캐시 히트)** | < 50ms | k6 summary, Grafana |
| **상품 조회 응답 시간 (P99, 캐시 히트)** | < 100ms | k6 summary, Grafana |
| **판매 랭킹 조회 (P95)** | < 100ms | k6 summary |
| **RPS** | > 3,000 RPS | k6 summary |
| **ZUNIONSTORE 응답 시간** | < 50ms | Redis SLOWLOG, 애플리케이션 로그 |
| **DB 쿼리 시간 (캐시 미스, Covering Index)** | < 200ms | MySQL slow query log |

---

## 5. 인프라 구성 방안

### 5.1 테스트 환경 구성: 전체 Docker Compose 기반 (선정)

#### 구성 방식

**전체 컴포넌트 Docker 컨테이너화**:
```yaml
# docker-compose.yml
services:
  app:
    build: .
    image: ecom-app:latest
    # 서버 최소 스펙 설정
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G

  mysql:
    image: mysql:8.0
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G

  redis:
    image: redis:7.2
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
```

#### 선정 이유

**환경 이식성**:
- ✅ Local, AWS, 별도 테스트 서버 등 여러 환경에서 동일하게 실행
- ✅ 환경별 설정 차이 최소화 (docker-compose.yml 공유)
- ✅ 개발 환경과 운영 환경 패리티 향상

**리소스 관리**:
- ✅ 서버 최소 스펙으로 명확한 제약 설정
- ✅ 컴포넌트별 CPU, Memory 제한 명시적 설정
- ✅ 리소스 병목 재현 용이

**배포 및 관리**:
- ✅ 단일 명령으로 전체 인프라 구성 (`docker-compose up`)
- ✅ 버전 관리 용이 (Dockerfile, docker-compose.yml)
- ✅ CI/CD 파이프라인 통합 가능

**성능 테스트 관점**:
- ✅ 프로덕션 환경 유사성 향상 (컨테이너 오버헤드 포함)
- ✅ 네트워크 레이턴시 포함한 실제 성능 측정
- ✅ 리소스 제한 하에서의 실제 처리량 확인

---

### 5.2 리소스 할당 전략

#### 총 리소스 스펙 (서버 최소 스펙 기준)
```
총 CPU: 5.0 cores
총 Memory: 5 GB

컴포넌트별 할당:
- Spring Boot: 2.0 CPU, 2GB Memory
- MySQL: 1.0 CPU, 1GB Memory
- Kafka: 1.0 CPU, 1GB Memory
- Redis: 0.5 CPU, 512MB Memory
- Zookeeper: 0.5 CPU, 512MB Memory
```

#### 리소스 조정 가이드
```bash
# 병목 발견 시 우선순위별 조정:

# 1순위: Spring Boot (애플리케이션 레이어)
#   - CPU 사용률 > 80% → cpus: 2.0 → 4.0
#   - Memory 사용률 > 80% → memory: 2G → 4G

# 2순위: MySQL (데이터베이스 레이어)
#   - Slow Query 증가 → cpus: 1.0 → 2.0
#   - Buffer Pool 부족 → memory: 1G → 2G

# 3순위: Kafka (메시징 레이어)
#   - Consumer Lag 증가 → cpus: 1.0 → 2.0
#   - 파티션 수 증가 시 → memory: 1G → 2G
```

---

### 5.3 인프라 세부 설정

#### 5.3.1 Spring Boot 애플리케이션 설정
```yaml
# docker-compose.yml (app service)
app:
  build:
    context: .
    dockerfile: Dockerfile
  image: ecom-app:latest
  container_name: ecom-app
  ports:
    - "8080:8080"
  environment:
    - SPRING_PROFILES_ACTIVE=loadtest
    - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/ecom_db
    - SPRING_DATA_REDIS_HOST=redis
    - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
  depends_on:
    - mysql
    - redis
    - kafka
  deploy:
    resources:
      limits:
        cpus: '2.0'
        memory: 2G
      reservations:
        cpus: '1.0'
        memory: 1G
```

#### 5.3.2 MySQL 설정
```yaml
# docker-compose.yml (mysql service)
mysql:
  image: mysql:8.0
  container_name: ecom-mysql
  ports:
    - "3306:3306"
  environment:
    MYSQL_ROOT_PASSWORD: password
    MYSQL_DATABASE: ecom_db
  volumes:
    - mysql-data:/var/lib/mysql
    - ./mysql/my.cnf:/etc/mysql/conf.d/my.cnf
  deploy:
    resources:
      limits:
        cpus: '1.0'
        memory: 1G
      reservations:
        cpus: '0.5'
        memory: 512M
```

```ini
# mysql/my.cnf
[mysqld]
# 메모리 설정 (1GB 컨테이너 기준)
innodb_buffer_pool_size = 512M
innodb_log_file_size = 64M
max_connections = 100

# 쿼리 로깅
slow_query_log = 1
long_query_time = 0.1
log_queries_not_using_indexes = 1

# 성능 모니터링
performance_schema = ON
```

---

#### 5.3.3 Redis 설정
```yaml
# docker-compose.yml (redis service)
redis:
  image: redis:7.2-alpine
  container_name: ecom-redis
  ports:
    - "6379:6379"
  command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
  deploy:
    resources:
      limits:
        cpus: '0.5'
        memory: 512M
      reservations:
        cpus: '0.25'
        memory: 256M
```

---

#### 5.3.4 Kafka 설정
```yaml
# docker-compose.yml (kafka service)
kafka:
  image: confluentinc/cp-kafka:7.5.0
  container_name: ecom-kafka
  ports:
    - "9092:9092"
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_NUM_PARTITIONS: 3
    KAFKA_LOG_RETENTION_HOURS: 1
  depends_on:
    - zookeeper
  deploy:
    resources:
      limits:
        cpus: '1.0'
        memory: 1G
      reservations:
        cpus: '0.5'
        memory: 512M
```

```yaml
# docker-compose.yml (zookeeper service)
zookeeper:
  image: confluentinc/cp-zookeeper:7.5.0
  container_name: ecom-zookeeper
  ports:
    - "2181:2181"
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_TICK_TIME: 2000
  deploy:
    resources:
      limits:
        cpus: '0.5'
        memory: 512M
      reservations:
        cpus: '0.25'
        memory: 256M
```

---

#### 5.3.5 Spring Boot 애플리케이션 프로파일 설정
```yaml
# application-loadtest.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 초기값
      minimum-idle: 10
      connection-timeout: 10000

  kafka:
    listener:
      concurrency: 3  # Consumer 수
    producer:
      batch-size: 16384
      linger-ms: 10

  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10

logging:
  level:
    com.hh.ecom: DEBUG
    org.springframework.kafka: INFO
```

**조정 전략**:
- DB Connection Pool 부족 시 maximum-pool-size 증가
- Kafka Producer 처리량 부족 시 batch-size 증가

---

## 6. 부하 테스트 도구 선정

### 6.1 도구 비교 분석

| 도구 | 장점 | 단점 | 적합성 |
|------|------|------|--------|
| **k6** | 스크립트 간결(JS), Grafana 연동, Cloud 지원 | Java 에코시스템 외부 | ✅ **선택** |
| **Gatling** | Scala DSL, 상세 리포트, Java 친화적 | 학습 곡선 높음 | ⚠️ 차선책 |
| **JMeter** | GUI 편리, 플러그인 많음 | 리소스 소모 큼, 스크립트 복잡 | ❌ 비추천 |
| **Locust** | Python 기반, 분산 테스트 | 성능 제한 | ❌ 대량 부하 부적합 |

### 6.2 선택: k6

**선정 이유**:
1. **간결한 스크립트**: JavaScript 기반, 빠른 작성 및 수정
2. **Grafana 연동**: 실시간 모니터링 및 대시보드
3. **성능**: Go 언어 기반, 높은 처리량
4. **Cloud 지원**: k6 Cloud로 확장 가능
5. **멘토 조언 반영**: Native 실행, 리소스 효율적

---

## 7. 실행 계획 및 타임라인

### 7.1 준비 단계 (1~2일)

**Day 1: 환경 구성**
- [ ] k6 설치 및 기본 스크립트 작성
- [ ] Docker Compose 리소스 제한 설정
- [ ] MySQL, Redis, Kafka 초기 설정
- [ ] Spring Boot application-loadtest.yml 작성

**Day 2: 데이터 준비 및 검증**
- [ ] 테스트 데이터 생성 스크립트 작성
  - 사용자 1,000명
  - 상품 1,000개
  - 주문 10만 건 (판매 데이터)
- [ ] 기능별 Smoke Test (단일 요청 성공 확인)
- [ ] 모니터링 도구 설정 (Grafana, Prometheus)

---

### 7.2 시나리오 실행 단계 (3~5일)

**Day 3: 시나리오 #1 - 쿠폰 발급**
- **오전**:
  - Baseline 측정 (100 VU, 1분)
  - Before/After 비교 (Redis 검증 제거)
- **오후**:
  - 피크 테스트 (5,000 VU, 2분)
  - Consumer 스케일링 테스트 (1 → 3 → 6)
  - 데이터 정합성 검증

**Day 4: 시나리오 #2 - 주문 생성**
- **오전**:
  - 정상 부하 테스트 (100 TPS, 5분)
  - Lock 대기 시간 측정
- **오후**:
  - 피크 부하 (500 TPS, 5분)
  - Spike Test (1,000 TPS, 30초)
  - Outbox 패턴 검증

**Day 5: 시나리오 #3 - 상품 조회**
- **오전**:
  - 캐시 히트율 측정 (1,000 RPS, 10분)
  - ZUNIONSTORE 성능 측정
- **오후**:
  - 피크 부하 (5,000 RPS, 5분)
  - 캐시 무효화 테스트
  - Before/After 비교 (Covering Index)

---

### 7.3 분석 및 보고서 작성 (2일)

**Day 6: 성능 지표 분석**
- TPS, 응답 시간 (P50, P95, P99) 정리
- 병목 지점 파악 (Slow Query Log, Redis Slow Log, Kafka Lag)
- 리소스 사용률 분석 (CPU, Memory, Disk I/O)
- Before/After 비교 결과 정리

**Day 7: 장애 대응 문서 작성**
- 가상 장애 시나리오 선정
  - 예: Kafka Consumer Lag 급증으로 인한 쿠폰 발급 지연
- 타임라인 작성 (디테일 중요)
- 장애 감지 → 분류 → 대응 → 회고
- Scale Up/Out 결정 근거 및 수치

---

## 8. 예상 결과 및 개선 방향

### 8.1 예상 성능 지표

#### 시나리오 #1: 쿠폰 발급
| 지표 | 예상 결과 | 근거 |
|------|-----------|------|
| **API 응답 시간 (P95)** | 50~100ms | Redis 검증 O(1) + Kafka 발행 비동기 |
| **API 응답 시간 (P99)** | 100~200ms | 고부하 시 일부 요청 지연 |
| **TPS** | 1,500~2,000 | Redis 처리율, Kafka 파티션 3개 기준 |
| **Consumer Lag** | 50~100ms | Consumer 3개 병렬 처리 기준 |
| **에러율** | < 5% | 타임아웃 및 시스템 오류 |

#### 시나리오 #2: 상품 조회
| 지표 | 예상 결과 | 근거 |
|------|-----------|------|
| **응답 시간 (P95, 캐시 히트)** | 20~50ms | Redis 캐시 히트, 네트워크 RTT |
| **응답 시간 (P99, 캐시 히트)** | 50~100ms | 고부하 시 Redis 처리 지연 |
| **RPS** | 3,000~5,000 | Redis 처리량 한계 |
| **ZUNIONSTORE 응답 시간** | 30~50ms | Sorted Set 연산 (7일치 데이터) |
| **DB 쿼리 (캐시 미스)** | 100~200ms | Covering Index 적용 시 |

---

### 8.2 예상 병목 지점 및 개선 방안

#### 병목 #1: Kafka Consumer 처리율
**증상**: Consumer Lag > 500ms, 쿠폰 발급 지연

**개선 방안**:
1. Consumer 수 증가 (3 → 6 → 9)
2. 파티션 수 증가 (3 → 6)
3. DB 배치 처리 (현재 단건 → 10건 배치 INSERT)

---

#### 병목 #2: DB Lock Contention (재고 차감)
**증상**: Lock 대기 시간 > 200ms, 주문 응답 시간 증가

**개선 방안**:
1. **비관적 락 유지** (정합성 우선)
2. **재고 예약 시스템 도입** (Try-Confirm-Cancel 패턴)
3. **Redis 재고 관리** (DB는 정산용, Redis는 실시간용)

---

#### 병목 #3: MySQL Slow Query
**증상**: 판매 랭킹 조회 > 500ms (캐시 미스 시)

**개선 방안**:
1. **Covering Index 유지** (이미 적용)
2. **집계 테이블 생성** (Materialized View)
   ```sql
   CREATE TABLE product_sales_summary (
       product_id BIGINT PRIMARY KEY,
       sales_count BIGINT,
       INDEX idx_sales_count (sales_count DESC)
   );
   ```
3. **배치 작업으로 갱신** (1시간 주기)

---

#### 병목 #4: Redis 메모리 부족
**증상**: Eviction 발생, 캐시 히트율 < 80%

**개선 방안**:
1. **maxmemory 증가** (512M → 1G → 2G)
2. **TTL 재조정** (판매 랭킹 7일 → 3일)
3. **Redis Cluster 도입** (메모리 분산)

---

## 9. 결론 및 넥스트 스텝

### 9.1 핵심 요약

**선정된 핵심 기능**:
1. ✅ 선착순 쿠폰 발급 (Kafka 기반 비동기 처리)
2✅ 상품 조회 및 판매 랭킹 (읽기 집약적)

**부하 테스트 도구**: k6 (Native 실행)

**인프라 구성**: Spring Boot (Native) + Docker Compose (MySQL, Redis, Kafka)

**실행 계획**: 7일 (준비 2일 + 실행 3일 + 분석 2일)

---

### 9.2 기대 효과

**성능 검증 및 최적화**:
- 시스템 처리량 한계 파악 (TPS, RPS)
- 응답 시간 분포 및 병목 지점 식별 (P50, P95, P99)
- Redis 캐싱 성능 효과 정량화
- Kafka 파티션별 처리 성능 측정
- Covering Index 쿼리 최적화 효과 검증

**스케일링 전략 수립**:
- 부하 증가에 따른 성능 저하 패턴 분석
- 수평/수직 확장 효과 측정 (Consumer 수, DB Connection Pool)
- 리소스 병목 지점 파악 (CPU, Memory, Network, Disk I/O)
- 적정 리소스 설정값 도출

**운영 가이드라인 확립**:
- 성능 임계값 설정 (알람 기준)
- 장애 대응 프로세스 정립 (트래픽 급증, Consumer Lag)
- 성능 모니터링 지표 정의 (Grafana 대시보드)
- Before/After 비교를 통한 최적화 효과 입증

---
