# E-Commerce 부하 테스트 실행 가이드

**작성일:** 2025-12-25
**작성자:** 김경민 (with Claude Code)

---

## 목차
1. [사전 준비](#1-사전-준비)
2. [k6 설치](#2-k6-설치)
3. [테스트 환경 구성](#3-테스트-환경-구성)
4. [부하 테스트 실행](#4-부하-테스트-실행)
5. [결과 분석](#5-결과-분석)
6. [문제 해결](#6-문제-해결)

---

## 1. 사전 준비

### 1.1 필수 요구사항

**소프트웨어**:
- Docker & Docker Compose
- Java 17+
- Gradle
- k6 (부하 테스트 도구)
- 최소 8GB RAM, 4 Core CPU (권장: 16GB RAM, 8 Core CPU)

**확인 명령어**:
```bash
# Docker 버전 확인
docker --version
docker-compose --version

# Java 버전 확인
java -version

# Gradle 버전 확인
./gradlew --version
```

### 1.2 프로젝트 디렉토리 구조 확인

```
ecom/
├── docker-compose.yml              # 기본 인프라 (MySQL, Redis, Kafka)
├── docker-compose-monitoring.yml   # 모니터링 스택 (Grafana, InfluxDB)
├── loadtest/                       # 부하 테스트 스크립트
│   ├── scenario1-coupon-issue.js
│   ├── scenario2-order-creation.js
│   ├── scenario3-product-query.js
│   └── LOADTEST_GUIDE.md (이 파일)
└── grafana/                        # Grafana 설정
    ├── provisioning/
    │   ├── datasources/
    │   │   └── influxdb.yml
    │   └── dashboards/
    │       └── dashboard.yml
    └── dashboards/
```

---

## 2. k6 설치

### 2.1 macOS (Homebrew)

```bash
brew install k6
```

### 2.2 Linux (Debian/Ubuntu)

```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### 2.3 설치 확인

```bash
k6 version
```

**예상 출력**:
```
k6 v0.48.0 (2023-11-29T10:37:34+0000/v0.48.0-0-gf0e8e03, go1.21.4, darwin/arm64)
```

---

## 3. 테스트 환경 구성

### 3.1 인프라 시작 (Docker Compose)

#### Step 1: 기본 인프라 시작

```bash
# 프로젝트 루트 디렉토리로 이동
cd /Users/km/Desktop/hanghae/workspace/ecom

# Docker Compose 시작 (MySQL, Redis, Kafka, Zookeeper)
docker-compose up -d

# 컨테이너 상태 확인
docker-compose ps
```

**예상 출력**:
```
NAME                COMMAND                  SERVICE             STATUS              PORTS
ecom-kafka          "/etc/confluent/dock…"   kafka               running             0.0.0.0:9092->9092/tcp
ecom-kafka-init     "/bin/sh -c '\n  # K…"   kafka-init          exited (0)
ecom-zookeeper      "/etc/confluent/dock…"   zookeeper           running             0.0.0.0:2181->2181/tcp
```

**참고**: Redis와 MySQL은 Spring Boot 애플리케이션에서 자동으로 연결됩니다.

#### Step 2: 모니터링 스택 시작 (선택사항)

```bash
# 모니터링 스택 시작 (Grafana, InfluxDB)
docker-compose -f docker-compose-monitoring.yml up -d

# 모니터링 컨테이너 상태 확인
docker-compose -f docker-compose-monitoring.yml ps
```

**예상 출력**:
```
NAME                     COMMAND                  SERVICE             STATUS              PORTS
ecom-grafana             "/run.sh"                grafana             running             0.0.0.0:3000->3000/tcp
ecom-influxdb            "/entrypoint.sh infl…"   influxdb            running             0.0.0.0:8086->8086/tcp
ecom-kafka-ui            "java -jar kafka-ui-…"   kafka-ui            running             0.0.0.0:8080->8080/tcp (선택)
ecom-redis-commander     "/usr/src/app/docker…"   redis-commander     running             0.0.0.0:8081->8081/tcp (선택)
```

**모니터링 도구 접속**:
- **Grafana**: http://localhost:3000 (admin/admin)
- **InfluxDB**: http://localhost:8086
- **Kafka UI**: http://localhost:8080 (선택사항)
- **Redis Commander**: http://localhost:8081 (선택사항)

#### Step 3: Spring Boot 애플리케이션 시작

```bash
# 애플리케이션 빌드 (처음 한 번만)
./gradlew clean build -x test

# 부하 테스트용 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=loadtest'
```

**또는 JAR 파일로 실행**:
```bash
# JAR 빌드
./gradlew bootJar

# JAR 실행
java -jar build/libs/ecom-*.jar --spring.profiles.active=loadtest
```

**애플리케이션 준비 확인**:
```bash
# Health Check
curl http://localhost:8080/actuator/health

# 상품 목록 조회 (샘플 데이터 있는 경우)
curl http://localhost:8080/api/products
```

---

### 3.2 테스트 데이터 준비

#### Option A: SQL 스크립트로 직접 삽입 (권장)

```bash
# MySQL 컨테이너에 접속 (docker-compose로 MySQL 실행 중인 경우)
docker exec -it ecom-mysql mysql -uroot -ppassword ecom_db

# 또는 로컬 MySQL 접속
mysql -h localhost -P 3306 -u root -p ecom_db
```

**테스트 데이터 삽입 예시** (MySQL 쉘에서 실행):
```sql
-- 사용자 1,000명 생성
INSERT INTO users (name, email, created_at)
SELECT
  CONCAT('User_', n) AS name,
  CONCAT('user', n, '@test.com') AS email,
  NOW()
FROM (
  SELECT @row := @row + 1 AS n
  FROM (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t1,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t2,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t3,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t4,
       (SELECT @row := 0) r
  LIMIT 1000
) nums;

-- 상품 1,000개 생성
INSERT INTO products (name, price, stock, description, created_at)
SELECT
  CONCAT('Product_', n) AS name,
  (n * 1000) AS price,
  1000 AS stock,
  CONCAT('Description for Product ', n) AS description,
  NOW()
FROM (
  SELECT @row := @row + 1 AS n
  FROM (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t1,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t2,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t3,
       (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t4,
       (SELECT @row := 0) r
  LIMIT 1000
) nums;

-- 포인트 충전 (사용자당 100,000 포인트)
INSERT INTO points (user_id, balance, created_at)
SELECT id, 100000, NOW() FROM users;

-- 쿠폰 생성 (선착순 1,000개)
INSERT INTO coupons (name, discount_amount, quantity, start_date, end_date, created_at)
VALUES
  ('선착순 쿠폰 1,000개', 5000, 1000, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW()),
  ('선착순 쿠폰 500개', 10000, 500, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW());

-- 데이터 확인
SELECT COUNT(*) AS user_count FROM users;
SELECT COUNT(*) AS product_count FROM products;
SELECT COUNT(*) AS coupon_count FROM coupons;
```

#### Option B: REST API로 데이터 생성 (프로그래밍 방식)

```bash
# 사용자 생성 스크립트 (bash)
for i in {1..100}; do
  curl -X POST http://localhost:8080/api/users \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"User_$i\",\"email\":\"user$i@test.com\"}"
done

# 상품 생성 스크립트
for i in {1..100}; do
  curl -X POST http://localhost:8080/api/products \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Product_$i\",\"price\":$((i*1000)),\"stock\":1000,\"description\":\"Test product $i\"}"
done
```

---

## 4. 부하 테스트 실행

### 4.1 시나리오 #1: 선착순 쿠폰 발급

#### 기본 실행

```bash
# loadtest 디렉토리로 이동
cd loadtest

# 기본 실행 (로컬 8080 포트)
k6 run scenario1-coupon-issue.js
```

#### 환경 변수 설정

```bash
# 커스텀 URL 및 쿠폰 ID 지정
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e COUPON_ID=1 \
  scenario1-coupon-issue.js
```

#### Grafana 연동 실행 (권장)

```bash
# InfluxDB로 메트릭 전송
k6 run \
  --out influxdb=http://localhost:8086/k6 \
  scenario1-coupon-issue.js
```

#### 결과 저장

```bash
# JSON 결과 저장
k6 run \
  --summary-export=results/scenario1-summary.json \
  scenario1-coupon-issue.js

# CSV 결과 저장 (상세 로그)
k6 run \
  --out csv=results/scenario1-metrics.csv \
  scenario1-coupon-issue.js
```

---


### 4.2 시나리오 #2: 상품 조회 및 판매 랭킹

```bash
# 기본 실행
k6 run scenario3-product-query.js

# Grafana 연동 + 결과 저장
k6 run \
  --out influxdb=http://localhost:8086/k6 \
  --summary-export=results/scenario3-summary.json \
  scenario3-product-query.js
```

---

### 4.4 모든 시나리오 순차 실행

```bash
# 결과 디렉토리 생성
mkdir -p results

# 시나리오 1: 쿠폰 발급
echo "🚀 시나리오 1: 쿠폰 발급 테스트 시작..."
k6 run \
  --out influxdb=http://localhost:8086/k6 \
  --summary-export=results/scenario1-$(date +%Y%m%d-%H%M%S).json \
  scenario1-coupon-issue.js

# 대기 (시스템 안정화)
echo "⏳ 시스템 안정화 대기 (30초)..."
sleep 30

# 시나리오 2: 주문 생성
echo "🚀 시나리오 2: 주문 생성 테스트 시작..."
k6 run \
  --out influxdb=http://localhost:8086/k6 \
  --summary-export=results/scenario2-$(date +%Y%m%d-%H%M%S).json \
  scenario2-order-creation.js

# 대기
echo "⏳ 시스템 안정화 대기 (30초)..."
sleep 30

# 시나리오 3: 상품 조회
echo "🚀 시나리오 3: 상품 조회 테스트 시작..."
k6 run \
  --out influxdb=http://localhost:8086/k6 \
  --summary-export=results/scenario3-$(date +%Y%m%d-%H%M%S).json \
  scenario3-product-query.js

echo "✅ 모든 시나리오 완료!"
```

---

## 5. 결과 분석

### 5.1 Grafana 대시보드 확인

1. **Grafana 접속**: http://localhost:3000
2. **로그인**: admin / admin (첫 로그인 시 비밀번호 변경 권장)
3. **대시보드 접속**:
   - 좌측 메뉴 → Dashboards → Browse
   - "Load Testing" 폴더 → k6 Load Testing Results

**주요 패널**:
- **Virtual Users**: 동시 접속자 수 (VU)
- **Request Rate**: 초당 요청 수 (RPS)
- **Response Time**: 응답 시간 (P50, P95, P99)
- **Error Rate**: 에러율 (%)
- **HTTP Status Codes**: 상태 코드 분포

### 5.2 콘솔 출력 해석

```
=================================================================
📊 쿠폰 발급 부하 테스트 결과
=================================================================

📈 성능 지표:
  • 총 요청 수: 15,234
  • 성공 요청: 1,000
  • 실패 요청: 14,234
  • 평균 RPS: 1,523.40
  • 테스트 시간: 150.00s

⏱️  응답 시간:
  • P50: 45.23ms
  • P95: 87.56ms
  • P99: 123.45ms
  • Max: 234.56ms

📊 응답 분류:
  • QUEUED (200): 6.56%       ← 쿠폰 발급 대기
  • SOLD_OUT (410): 87.12%    ← 재고 소진
  • DUPLICATE (409): 6.21%    ← 중복 발급 시도
  • ERROR: 0.11%              ← 시스템 에러

✅ 임계값 통과 여부:
  ✅ http_req_duration: p(95)<100
  ✅ http_req_duration: p(99)<200
  ❌ errors: rate<0.05         ← 에러율 5% 초과 (조사 필요)
```

**해석**:
- **성공 요청 1,000개**: 정확히 쿠폰 수량만큼 발급 (정합성 OK)
- **P95 응답 시간 87.56ms**: 95% 사용자가 100ms 이내 응답 (목표 달성)
- **에러율 0.11%**: 매우 낮은 시스템 에러 (양호)

### 5.3 데이터 정합성 검증

#### 쿠폰 발급 정합성

```bash
# MySQL에서 발급된 쿠폰 수 확인
mysql -h localhost -P 3306 -u root -p -D ecom_db -e "
SELECT
  coupon_id,
  COUNT(*) AS issued_count
FROM coupon_users
GROUP BY coupon_id;
"
```

**예상 결과**:
```
+-----------+--------------+
| coupon_id | issued_count |
+-----------+--------------+
|         1 |         1000 |  ← 정확히 1,000개 발급 (OK)
+-----------+--------------+
```

#### 재고 정합성

```bash
# 상품 재고 확인
mysql -h localhost -P 3306 -u root -p -D ecom_db -e "
SELECT
  id,
  name,
  stock,
  (SELECT SUM(quantity) FROM order_items WHERE product_id = products.id) AS sold
FROM products
WHERE id <= 10
ORDER BY id;
"
```

**예상 결과**:
```
+----+------------+-------+------+
| id | name       | stock | sold |
+----+------------+-------+------+
|  1 | Product_1  |   850 |  150 |  ← 초기 1,000 - 150 = 850 (OK)
|  2 | Product_2  |   920 |   80 |
+----+------------+-------+------+
```

### 5.4 시스템 리소스 모니터링

#### Docker 컨테이너 리소스

```bash
# 컨테이너별 리소스 사용률
docker stats --no-stream
```

**예상 출력**:
```
CONTAINER ID   NAME                CPU %     MEM USAGE / LIMIT     MEM %     NET I/O
abc123def456   ecom-kafka          5.23%     512MiB / 2GiB         25.60%    1.2MB / 3.4MB
def456ghi789   ecom-mysql          12.34%    1.5GiB / 4GiB         37.50%    5.6MB / 8.9MB
ghi789jkl012   ecom-redis          2.45%     256MiB / 512MiB       50.00%    2.3MB / 1.2MB
```

**주의사항**:
- **CPU > 80%**: 리소스 부족, 컨테이너 재시작 또는 리소스 증가 필요
- **MEM > 90%**: 메모리 부족, OOM Killer 위험

#### MySQL Slow Query Log

```bash
# Slow Query 확인 (MySQL 컨테이너 내부)
docker exec -it ecom-mysql mysql -uroot -ppassword -e "
SELECT
  query_time,
  lock_time,
  rows_examined,
  sql_text
FROM mysql.slow_log
ORDER BY query_time DESC
LIMIT 10;
"
```

#### Kafka Consumer Lag

```bash
# Kafka UI에서 확인: http://localhost:8080
# 또는 CLI로 확인
docker exec -it ecom-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group coupon-issue-group
```

**예상 출력**:
```
GROUP           TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
coupon-issue-group  coupon-issue    0          5234            5234            0    ← Lag = 0 (OK)
coupon-issue-group  coupon-issue    1          4987            4987            0
coupon-issue-group  coupon-issue    2          5123            5123            0
```

**주의사항**:
- **Lag > 100**: Consumer 처리 지연, concurrency 증가 필요
- **Lag > 1000**: 심각한 지연, Consumer 추가 또는 파티션 증가 검토

---

## 6. 문제 해결

### 6.1 자주 발생하는 문제

#### 문제 1: "Connection refused" 에러

**증상**:
```
WARN[0001] Request Failed error="Post \"http://localhost:8080/api/coupons/1/issue\":
dial tcp 127.0.0.1:8080: connect: connection refused"
```

**원인**: Spring Boot 애플리케이션이 실행되지 않음

**해결**:
```bash
# 애플리케이션 상태 확인
curl http://localhost:8080/actuator/health

# 실행되지 않은 경우 시작
./gradlew bootRun --args='--spring.profiles.active=loadtest'
```

---

#### 문제 2: Kafka Consumer Lag 급증

**증상**: Grafana에서 Consumer Lag > 1000

**원인**: Consumer 처리 속도 < Producer 발행 속도

**해결**:
```yaml
# application-loadtest.yml 수정
spring:
  kafka:
    listener:
      concurrency: 6  # 3 → 6으로 증가 (파티션 수 이하)
```

```bash
# 애플리케이션 재시작
./gradlew bootRun --args='--spring.profiles.active=loadtest'
```

---

#### 문제 3: MySQL Connection Pool Exhausted

**증상**:
```
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

**원인**: DB Connection Pool 부족

**해결**:
```yaml
# application-loadtest.yml 수정
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 20 → 50으로 증가
      minimum-idle: 20       # 10 → 20으로 증가
```

---

#### 문제 4: Redis OOM (Out of Memory)

**증상**: Redis 응답 느려짐 또는 에러 발생

**원인**: Redis 메모리 부족 (maxmemory 초과)

**해결**:
```bash
# Redis 메모리 사용률 확인
docker exec -it ecom-redis redis-cli INFO memory | grep used_memory_human

# maxmemory 증가 (redis.conf 수정 또는 CLI)
docker exec -it ecom-redis redis-cli CONFIG SET maxmemory 2gb
docker exec -it ecom-redis redis-cli CONFIG REWRITE
```

---

#### 문제 5: k6 "too many open files" 에러

**증상**:
```
WARN[0030] Request Failed error="dial tcp: lookup localhost: too many open files"
```

**원인**: 운영체제의 파일 디스크립터 제한

**해결 (macOS/Linux)**:
```bash
# 현재 제한 확인
ulimit -n

# 제한 증가 (임시, 현재 세션만)
ulimit -n 65535

# 영구 변경 (macOS)
# /etc/sysctl.conf에 추가
kern.maxfiles=65536
kern.maxfilesperproc=65536

# 시스템 재시작 후 적용
sudo sysctl -p
```

---

### 6.2 성능 튜닝 가이드

#### CPU 사용률 높을 때 (> 80%)

**조치 1**: 애플리케이션 스레드 풀 조정
```yaml
# application-loadtest.yml
server:
  tomcat:
    threads:
      max: 400        # 기본 200 → 400
      min-spare: 50   # 기본 10 → 50
```

**조치 2**: Docker 리소스 증가
```yaml
# docker-compose.yml
services:
  mysql:
    deploy:
      resources:
        limits:
          cpus: '4'      # CPU 코어 수 증가
          memory: 4G     # 메모리 증가
```

---

#### 응답 시간 느릴 때 (P95 > 500ms)

**체크리스트**:
1. ✅ DB 인덱스 확인 (Covering Index 적용 여부)
2. ✅ Redis 캐시 히트율 확인 (> 95% 목표)
3. ✅ Kafka Consumer Lag 확인 (< 100 목표)
4. ✅ MySQL Slow Query Log 확인
5. ✅ Connection Pool 사용률 확인

---

### 6.3 긴급 대응 절차

#### 시나리오: 부하 테스트 중 서비스 다운

**Step 1: 즉시 부하 테스트 중단**
```bash
# k6 프로세스 종료
Ctrl + C

# 또는 프로세스 강제 종료
pkill -f k6
```

**Step 2: 시스템 상태 확인**
```bash
# Docker 컨테이너 상태
docker-compose ps

# 애플리케이션 로그 확인
docker-compose logs -f --tail=100

# 리소스 사용률
docker stats --no-stream
```

**Step 3: 장애 격리 및 복구**
```bash
# MySQL 재시작 (필요 시)
docker-compose restart mysql

# Kafka 재시작 (필요 시)
docker-compose restart kafka zookeeper

# 전체 재시작 (최후 수단)
docker-compose down
docker-compose up -d
```

**Step 4: 데이터 정합성 검증**
```bash
# 쿠폰 발급 수 확인
mysql -h localhost -P 3306 -u root -p -D ecom_db -e "
SELECT COUNT(*) FROM coupon_users;
"

# 재고 확인
mysql -h localhost -P 3306 -u root -p -D ecom_db -e "
SELECT SUM(stock) FROM products;
"
```

---

## 7. Before/After 비교 테스트

### 7.1 인덱스 제거 후 성능 비교

#### Before: 인덱스 적용 전

```sql
-- MySQL에서 인덱스 제거
DROP INDEX idx_order_items_join_group_covering ON order_items;
DROP INDEX idx_orders_status_id ON orders;
```

```bash
# 부하 테스트 실행
k6 run \
  --summary-export=results/before-index.json \
  scenario2-order-creation.js
```

#### After: 인덱스 적용 후

```sql
-- 인덱스 재생성
CREATE INDEX idx_order_items_join_group_covering
ON order_items(order_id, product_id, quantity);

CREATE INDEX idx_orders_status_id
ON orders(status, id);
```

```bash
# 부하 테스트 실행
k6 run \
  --summary-export=results/after-index.json \
  scenario2-order-creation.js
```

#### 결과 비교

```bash
# JSON 비교 (수동)
cat results/before-index.json | jq '.metrics.http_req_duration.values'
cat results/after-index.json | jq '.metrics.http_req_duration.values'
```

---

## 8. 추가 리소스

### 8.1 k6 공식 문서
- https://k6.io/docs/

### 8.2 Grafana k6 대시보드
- https://grafana.com/grafana/dashboards/2587

### 8.3 성능 최적화 가이드
- MySQL: https://dev.mysql.com/doc/refman/8.0/en/optimization.html
- Redis: https://redis.io/docs/management/optimization/
- Kafka: https://kafka.apache.org/documentation/#performance

---

**문서 종료**
