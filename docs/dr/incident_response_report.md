# 장애 대응 보고서 (Incident Response Report)

### 개요

**장애 ID**: HHPLUS-2025-12-001

**심각도**: Critical

**작성일**: 2025-12-26

**작성자**: Server Dev-1 Team

**검토자**: CTO, 개발팀 리더

---

## 목차

1. [장애 개요](#1-장애-개요)
2. [디테일한 타임라인](#2-디테일한-타임라인)
3. [장애 감지 및 분류](#3-장애-감지-및-분류)
4. [긴급 대응 조치](#4-긴급-대응-조치)
5. [근본 원인 분석](#5-근본-원인-분석)
6. [복구 및 검증](#6-복구-및-검증)
7. [비즈니스 영향 분석](#7-비즈니스-영향-분석)
8. [재발 방지 대책](#8-재발-방지-대책)
9. [회고 및 교훈](#9-회고-및-교훈)

---

## 1. 장애 개요

### 1.1 장애 요약

**장애명**: 선착순 쿠폰 발급 시스템 전면 장애
**발생 일시**: 2025-12-26 (수) 14:00:00 KST
**복구 완료**: 2025-12-26 (수) 16:45:00 KST
**총 장애 시간**: 2시간 45분
**영향 범위**: 쿠폰 발급 기능 100% 중단

### 1.2 장애 증상

```
사용자 증상:
- 쿠폰 발급 API 호출 시 "이미 발급된 쿠폰입니다" 에러 메시지
- 실제로는 쿠폰을 받지 못했으나 재시도 불가
- 고객센터 문의 폭주 (약 1,200건)

시스템 증상:
- Kafka Consumer Lag 급증 (0 → 45,000 메시지)
- MySQL 쿠폰 재고 변동 없음 (available_quantity = 5000 고정)
- Redis Set 크기 폭발적 증가 (45,000개 userId 저장)
- Application 로그에 Consumer 처리 기록 없음
```

### 1.3 비즈니스 영향

| 항목 | 영향 |
|------|------|
| **영향 고객 수** | 약 45,000명 |
| **서비스 가용성** | 쿠폰 발급: 0% (완전 중단)<br>기타 서비스: 100% (정상) |
| **직접적 손실** | 쿠폰 발급 실패로 인한 프로모션 효과 상실 |
| **간접적 손실** | 브랜드 신뢰도 하락, 고객 이탈 위험 |
| **예상 매출 손실** | 약 225,000,000원 (45,000명 × 평균 주문 5,000원) |

---

## 2. 디테일한 타임라인

### 2.1 장애 발생 전 (D-Day)

#### **13:30** - 이벤트 준비
```
상황: "크리스마스 특별 쿠폰 이벤트" 오픈 예정
- 발급 쿠폰: 5,000개 (선착순)
- 할인 금액: 5,000원
- 예상 참여자: ~50,000명
```

#### **13:45** - 사전 점검
```bash
# 개발팀 사전 점검 로그
✓ Redis 캐시 워밍 완료
  SET coupon:issue:async:stock:1 "5000"

✓ MySQL 쿠폰 데이터 확인
  SELECT * FROM coupon WHERE id=1;
  → available_quantity: 5000 ✓

✓ Kafka Topic 생성 확인
  kafka-topics --list
  → coupon-issue ✓

✓ Application 헬스 체크
  curl /actuator/health
  → {"status":"UP"} ✓
```

#### **13:55** - 최종 준비
```
마케팅팀: SNS 공지 발행
개발팀: 모니터링 대시보드 오픈 (Grafana)
인프라팀: Auto-scaling 정책 활성화 (2→10 pods)
```

---

### 2.2 장애 발생 및 감지 (14:00 - 14:15)

#### **14:00:00** - 이벤트 시작
```
상황: SNS 링크 공개 동시에 대규모 트래픽 유입

초기 메트릭:
- 동시 접속자: 0 → 15,000명 (30초 내)
- RPS: 0 → 850 (초당 850 요청)
- 응답 시간 P95: 3ms (정상)
```

#### **14:00:30** - 첫 번째 이상 징후
```
모니터링 알람:
[WARNING] Kafka Consumer Lag 증가
- Topic: coupon-issue
- Group: coupon-issue-group
- Lag: 0 → 500 messages (30초만에)

개발자 대응:
"아직 초기라 메시지가 쌓이는 것 같습니다. 조금 더 지켜봅시다."
→ 조치 없음 (1차 실수)
```

#### **14:02:00** - 고객 문의 시작
```
고객센터 상담원 A:
"고객님, 쿠폰 발급이 정상적으로 접수되었습니다.
곧 쿠폰함에서 확인하실 수 있습니다."

실제 상황:
- Redis: userId 저장 완료
- Kafka: 메시지 큐잉됨
- MySQL: 쿠폰 발급 안됨 (Consumer 미처리)
```

#### **14:05:00** - 재시도 차단 시작
```
고객 행동 패턴:
1. 쿠폰 발급 버튼 클릭 → "이미 발급된 쿠폰입니다" (409)
2. 쿠폰함 확인 → 쿠폰 없음
3. 재시도 → 동일 에러 메시지
4. 고객센터 문의 or 이탈

고객센터 문의 증가:
- 14:00: 0건
- 14:05: 50건
- 14:10: 200건
- 14:15: 500건 ← 폭발적 증가
```

#### **14:10:00** - 모니터링 임계값 초과
```
🚨 [CRITICAL] Kafka Consumer Lag Critical
- Current Lag: 8,500 messages
- Threshold: 1,000 messages
- Alert sent to: #dev-alerts (Slack)

개발팀 상황:
- 개발자 1: 회의 중
- 개발자 2: 점심 식사
- 개발자 3: 코드 리뷰 중
→ Slack 알람 확인했으나 긴급성 인지 못함 (2차 실수)
```

#### **14:15:00** - 문제 인지
```
고객센터 팀장 → CTO 직접 연락:
"쿠폰 이벤트 문의가 폭주하고 있습니다.
발급은 됐다고 나오는데 실제로는 안 들어옵니다!"

CTO → 개발팀 긴급 소집
→ 전사 비상 대응 모드 전환
```

---

### 2.3 긴급 분석 및 대응 (14:15 - 15:30)

#### **14:15:00** - 긴급 회의 소집
```
참석자:
- CTO
- 백엔드 개발팀 (3명)
- 인프라팀 (2명)
- 고객센터 팀장

회의 내용:
1. 현상 파악: 쿠폰 발급 안됨, 재시도 불가
2. 우선순위: 고객 불만 해소 > 근본 원인 파악
3. 임시 조치 결정: 수동 쿠폰 발급 검토
```

#### **14:20:00** - 로그 분석 시작
```bash
# 개발자 A - Application 로그 확인
$ docker logs ecom-app --tail 1000 | grep -i "coupon.*consumer"

결과: Consumer 처리 로그 전혀 없음 🚨

# 개발자 B - Kafka Topic 메시지 확인
$ kafka-console-consumer \
  --topic coupon-issue \
  --from-beginning \
  --max-messages 10

결과: 메시지 정상 적재됨 (샘플 10개 확인) ✓

# 개발자 C - Consumer Group 상태 확인
$ kafka-consumer-groups \
  --group coupon-issue-group \
  --describe

결과:
TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
coupon-issue    0          0               3500            3500
coupon-issue    1          0               3200            3200
coupon-issue    2          0               2800            2800

→ LAG = LOG-END-OFFSET 🚨
→ Consumer가 메시지를 전혀 읽지 않음!
```

#### **14:25:00** - 원인 가설 수립
```
가설 1: Consumer 프로세스가 죽었다
→ 검증: ps aux | grep kafka
→ 결과: 프로세스 살아있음 ✗

가설 2: Consumer가 메시지를 읽지만 처리 실패
→ 검증: 로그에 처리 기록 전혀 없음
→ 결과: 읽지조차 않음 ✗

가설 3: Consumer Group 설정 오류
→ 검증: application.yml 확인 필요
→ 결과: 이것부터 확인하자! ✓
```

#### **14:30:00** - 설정 파일 분석
```yaml
# application.yml 확인
spring:
  kafka:
    consumer:
      group-id: coupon-issue-group  # ✓ 정상
      auto-offset-reset: earliest   # ✓ 정상
      enable-auto-commit: false     # ✓ 정상

# @KafkaListener 어노테이션 확인
@KafkaListener(
    topics = "coupon-issue",
    groupId = "coupon-issue-group",
    concurrency = "3",
    containerFactory = "couponKafkaListenerContainerFactory"  # ← 의심!
)
```

#### **14:35:00** - ContainerFactory 확인
```java
// KafkaConfig.java 확인
@Bean
public ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequestEvent>
    couponKafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, CouponIssueRequestEvent> factory
        = new ConcurrentKafkaListenerContainerFactory<>();

    // ❌ 여기가 문제!
    factory.setConsumerFactory(consumerFactory());  // 기본 factory 사용
    factory.setConcurrency(3);
    factory.setCommonErrorHandler(/* ... */);

    return factory;
}

// consumerFactory() 메서드 확인
@Bean
public ConsumerFactory<String, CouponIssueRequestEvent> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");  // ❌ 잘못된 주소!
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "coupon-issue-group");
    // ... 기타 설정
    return new DefaultKafkaConsumerFactory<>(props);
}
```

#### **14:40:00** - 근본 원인 발견 🎯
```
근본 원인:
Consumer가 Kafka Broker에 연결하지 못함

상세:
- application.yml: bootstrap-servers: kafka:9092  ✓ (Docker 네트워크)
- 하지만 consumerFactory()에서 하드코딩: localhost:9092  ❌

결과:
- Consumer가 localhost:9092 접속 시도
- Docker 컨테이너 내부에서 localhost = 자기 자신
- Kafka Broker 없음 → 연결 실패
- 조용히 실패 (에러 로깅 부족)
```

#### **14:45:00** - 임시 조치 결정
```
의사결정:
옵션 1: 코드 수정 → 재배포 (30분 소요)
옵션 2: 수동 쿠폰 발급 스크립트 (10분 소요)
옵션 3: 이벤트 중단 공지 → 내일 재개최

선택: 옵션 1 + 옵션 3 병행
- 근본 원인 수정 (코드 배포)
- 고객 공지 (이벤트 일시 중단 → 익일 재오픈)
- 이미 시도한 고객 보상책 마련
```

#### **14:50:00** - 긴급 패치 개발
```java
// 수정 전
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

// 수정 후
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
    kafkaProperties.getBootstrapServers());  // application.yml에서 읽기
```

#### **15:00:00** - 고객 공지 발행
```
[공지] 크리스마스 쿠폰 이벤트 일시 중단 안내

안녕하세요, E-Commerce팀입니다.

현재 쿠폰 발급 시스템 오류로 인해 일시적으로
이벤트를 중단하게 되었습니다.

• 중단 시간: 14:00 ~ 복구 완료 시까지
• 재개 예정: 12/27 (목) 14:00
• 보상: 이미 참여하신 분들께 별도 쿠폰 지급 예정

불편을 드려 죄송합니다.
```

#### **15:10:00** - 코드 리뷰 및 테스트
```bash
# Local 환경 테스트
$ docker-compose up -d
$ ./gradlew test --tests CouponIssueKafkaConsumerTest
→ PASSED ✓

# 통합 테스트
$ curl -X POST /coupons/1/issue -H "userId: 99999"
→ {"status": "QUEUED"} ✓

# Consumer 로그 확인
$ docker logs ecom-app | grep "쿠폰 발급 성공"
→ 로그 출력 확인 ✓

# MySQL 확인
$ SELECT available_quantity FROM coupon WHERE id=1;
→ 4999 (1개 감소 확인) ✓
```

#### **15:20:00** - Production 배포 승인
```
CTO: "테스트 결과 확인했습니다. 배포 진행하세요."

배포 계획:
1. Blue-Green 배포 (무중단)
2. Canary Release (10% → 50% → 100%)
3. 배포 후 10분간 집중 모니터링
```

#### **15:30:00** - Production 배포 완료
```bash
# Kubernetes 배포
$ kubectl apply -f k8s/deployment.yaml
deployment.apps/ecom-app configured

$ kubectl rollout status deployment/ecom-app
deployment "ecom-app" successfully rolled out

# 헬스 체크
$ curl https://api.ecommerce.com/actuator/health
{"status":"UP"}
```

---

### 2.4 복구 및 검증 (15:30 - 16:30)

#### **15:35:00** - Consumer 동작 확인
```bash
# Kafka Consumer Lag 확인
$ kafka-consumer-groups --describe --group coupon-issue-group

TOPIC         PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
coupon-issue  0          1200            3500            2300  ← 처리 중!
coupon-issue  1          1100            3200            2100  ← 처리 중!
coupon-issue  2          980             2800            1820  ← 처리 중!

→ Lag 감소 추세 확인 ✓
→ 약 6분 후 완전 처리 예상
```

#### **15:40:00** - 실시간 모니터링
```
Grafana 대시보드:
┌─────────────────────────────────────┐
│ Kafka Consumer Lag                  │
│                                     │
│ 9500 ┤                              │
│      │╲                             │
│      │ ╲                            │
│      │  ╲                           │
│      │   ╲___                       │
│    0 ┤       ╲____                  │
│      └──────────────────────────→   │
│      15:35   15:40   15:45   15:50  │
└─────────────────────────────────────┘

Consumer 처리 속도: ~100 msg/sec
예상 완료 시간: 15:46
```

#### **15:42:00** - 테스트 쿠폰 발급
```bash
# 개발팀 테스트 (Internal)
for i in {1..10}; do
  curl -X POST /coupons/1/issue -H "userId: test$i"
done

결과: 10/10 성공 ✓

# 고객 대상 소프트 런칭 (5분간)
→ SNS 비공개 그룹에만 공지
→ 50명 참여 → 50명 성공 ✓
```

#### **15:46:00** - Kafka Lag 완전 해소
```
TOPIC         PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
coupon-issue  0          3500            3500            0  ✓
coupon-issue  1          3200            3200            0  ✓
coupon-issue  2          2800            2800            0  ✓

Total Messages Processed: 9,500개
Average Processing Time: 11분
```

#### **15:50:00** - 데이터 정합성 검증
```sql
-- Redis vs MySQL 비교
Redis SET 크기:
> SCARD "coupon:issue:async:participants:1"
9500

MySQL 발급 기록:
> SELECT COUNT(*) FROM coupon_user WHERE coupon_id=1;
9500

쿠폰 재고:
> SELECT total_quantity - available_quantity AS issued
  FROM coupon WHERE id=1;
→ 9500

✓ 모든 데이터 정합성 일치 확인
```

#### **16:00:00** - 부하 테스트 (Stress Test)
```bash
# k6 부하 테스트 (300 VU, 1분)
$ k6 run --vus 300 --duration 1m coupon-stress-test.js

결과:
✓ http_req_duration: avg=3.2ms, p95=8.5ms
✓ http_req_failed: 0.2% (6/3000)
✓ kafka_consumer_lag: max=45, recovered in 10s

평가: 정상 복구 확인 ✓
```

#### **16:15:00** - 보상 쿠폰 발급 스크립트 실행
```sql
-- 이벤트 참여했으나 발급 실패한 고객 추출
SELECT DISTINCT user_id
FROM event_participation_log
WHERE event_id = 'xmas-2025'
  AND participated_at BETWEEN '2025-12-26 14:00:00' AND '2025-12-26 14:15:00'
  AND user_id NOT IN (
    SELECT user_id FROM coupon_user WHERE coupon_id = 1
  );

→ 35,500명 추출

-- 보상 쿠폰 생성
INSERT INTO coupon (name, discount_amount, total_quantity, ...)
VALUES ('크리스마스 이벤트 보상 쿠폰', 5000, 35500, ...);

-- 일괄 발급
INSERT INTO coupon_user (user_id, coupon_id, ...)
SELECT user_id, 2, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY)
FROM event_participation_log
WHERE ...;

→ 35,500건 발급 완료
```

#### **16:30:00** - 최종 공지 발행
```
[공지] 쿠폰 이벤트 복구 완료 및 보상 안내

시스템 복구가 완료되었습니다.

• 복구 완료: 16:30
• 조치 사항: 시스템 오류 수정 완료
• 보상 내용:
  - 14:00~14:15 참여 고객: 보상 쿠폰 5,000원 지급
  - 이미 쿠폰 받으신 분: 추가 500원 쿠폰 지급

• 이벤트 재개: 12/27 (목) 14:00
  - 잔여 수량: 5,000개 → 10,000개로 증량

불편을 드려 죄송합니다.
```

---

### 2.5 모니터링 및 안정화 (16:30 - 17:00)

#### **16:35:00** - 알람 설정 강화
```yaml
# Prometheus Alert Rules 추가
groups:
  - name: kafka_consumer_alerts
    rules:
      - alert: KafkaConsumerLagHigh
        expr: kafka_consumer_lag > 100
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Kafka Consumer Lag 증가"

      - alert: KafkaConsumerLagCritical
        expr: kafka_consumer_lag > 1000
        for: 30s
        labels:
          severity: critical
        annotations:
          summary: "Kafka Consumer 처리 지연 심각"
          description: "즉시 확인 필요!"
```

#### **16:45:00** - 장애 종료 선언
```
CTO 승인:
"모든 지표 정상, 고객 보상 완료.
장애 대응 종료를 선언합니다."

Post-Incident Meeting 예정:
- 일시: 12/27 (목) 10:00
- 참석: 전체 개발팀, 인프라팀
- 안건: 근본 원인 분석, 재발 방지 대책
```

---

## 3. 장애 감지 및 분류

### 3.1 감지 경로 분석

#### 1차 감지: Monitoring Alert (14:00:30)
```
✗ 실패
이유:
- Slack 알람 발생했으나 개발자 확인 안 함
- "지켜보자" 판단 → 조치 지연
- 알람 피로도 (False Positive 누적)

개선 필요:
- 알람 임계값 재조정
- Critical 알람은 전화/SMS 병행
- On-call 담당자 명확화
```

#### 2차 감지: 고객 문의 (14:02:00)
```
✗ 실패
이유:
- 고객센터 ↔ 개발팀 커뮤니케이션 부재
- 초기에는 "정상" 안내 → 상황 악화
- 개발팀에 전달까지 13분 소요

개선 필요:
- 고객센터 ↔ 개발팀 직통 채널
- 실시간 이슈 트래킹 대시보드
```

#### 3차 감지: 관리자 직접 확인 (14:15:00)
```
✓ 성공
경로:
- 고객센터 팀장 → CTO 직접 연락
- CTO → 개발팀 긴급 소집
- 15분 내 전사 대응 체계 가동

효과:
- 신속한 의사결정
- 리소스 집중 투입
```

### 3.2 장애 분류

**심각도**: SEV-1 (Critical)

**분류 기준**:
```
SEV-1 조건 (하나라도 해당 시):
✓ 주요 기능 완전 중단
✓ 다수의 고객 영향 (1,000명+)
✓ 매출 직접 영향
✓ 브랜드 이미지 훼손 위험

→ 모든 조건 충족 → SEV-1 확정
```

**영향 범위**:
- 기능: 쿠폰 발급 (100% 중단)
- 사용자: 약 45,000명
- 서비스: 쿠폰 발급 API만 영향 (격리 성공)

---

## 4. 긴급 대응 조치

### 4.1 대응 조직 구성

```
Incident Commander (지휘관): CTO
└─ Tech Lead: 백엔드 팀장
   ├─ 개발팀 (3명): 코드 분석 및 수정
   ├─ 인프라팀 (2명): 모니터링 및 배포
   └─ QA팀 (1명): 긴급 테스트

Communication Lead: 마케팅 팀장
└─ 고객센터 (5명): 고객 응대
└─ PR팀 (2명): 대외 공지

총 투입 인원: 14명
```

### 4.2 긴급 조치 우선순위

#### P0 - 즉시 실행
```
✓ 고객 공지 (이벤트 중단)
✓ 근본 원인 파악
✓ 긴급 패치 개발
```

#### P1 - 30분 이내
```
✓ 코드 배포
✓ 동작 검증
✓ 보상 계획 수립
```

#### P2 - 1시간 이내
```
✓ 밀린 메시지 처리
✓ 데이터 정합성 검증
✓ 보상 쿠폰 발급
```

### 4.3 커뮤니케이션

#### 내부 커뮤니케이션
```
채널: Slack #incident-response
주기: 10분마다 상황 업데이트

예시:
[14:20] CTO: 장애 확인. 전체 개발자 소집.
[14:30] 개발팀: 원인 파악 중. Consumer 설정 의심.
[14:40] 개발팀: 근본 원인 발견. 패치 개발 시작.
[15:00] 마케팅: 고객 공지 발행 완료.
[15:30] 인프라: 배포 완료. 모니터링 중.
[16:30] CTO: 장애 복구 완료. 보상 진행.
```

#### 외부 커뮤니케이션
```
채널: 홈페이지 공지, 이메일, SNS
타이밍:
- 장애 인지 후 15분 이내 1차 공지
- 복구 완료 후 즉시 2차 공지
- 보상 완료 후 3차 공지

원칙:
- 투명한 정보 공유
- 구체적인 보상 제시
- 재발 방지 약속
```

---

## 5. 근본 원인 분석

### 5.1 Five Whys Analysis

```
Q1: 왜 쿠폰이 발급되지 않았는가?
A1: Kafka Consumer가 메시지를 처리하지 않았기 때문

Q2: 왜 Consumer가 메시지를 처리하지 않았는가?
A2: Consumer가 Kafka Broker에 연결되지 않았기 때문

Q3: 왜 Kafka Broker에 연결되지 않았는가?
A3: Consumer가 잘못된 주소(localhost:9092)로 접속 시도했기 때문

Q4: 왜 잘못된 주소로 접속했는가?
A4: consumerFactory()에서 bootstrap-servers를 하드코딩했기 때문

Q5: 왜 하드코딩했는가?
A5:
- 개발 중 테스트 목적으로 임시 작성
- 코드 리뷰 미실시
- 통합 테스트 부재
- Production 배포 전 검증 프로세스 부족
```

### 5.2 근본 원인 (Root Cause)

#### 직접적 원인
```java
// KafkaConfig.java
@Bean
public ConsumerFactory<String, CouponIssueRequestEvent> consumerFactory() {
    Map<String, Object> props = new HashMap<>();

    // ❌ 잘못된 설정
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

    // ✅ 올바른 설정
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaProperties.getBootstrapServers());  // "kafka:9092"

    return new DefaultKafkaConsumerFactory<>(props);
}
```

#### 근본적 원인 (프로세스 결함)

1. **코드 리뷰 부재**
   - 하드코딩된 설정값 검출 실패
   - "localhost" 사용 금지 규칙 미적용

2. **통합 테스트 부족**
   - Kafka Consumer 동작 테스트 없음
   - Docker 환경 테스트 미실시

3. **모니터링 사각지대**
   - Consumer 연결 상태 모니터링 없음
   - 에러 로깅 불충분

4. **배포 검증 프로세스 미흡**
   - Production 배포 전 Smoke Test 없음
   - Canary Release 미적용

### 5.3 기여 요인 (Contributing Factors)

```
기술적 요인:
✗ Consumer 연결 실패 시 Silent Failure
✗ 에러 로깅 레벨 부적절 (DEBUG → ERROR로 변경 필요)
✗ Retry 메커니즘 부재

프로세스 요인:
✗ 배포 체크리스트 미준수
✗ Production 환경 테스트 생략
✗ 코드 리뷰 형식적 진행

문화적 요인:
✗ "빨리 배포" 압박
✗ "테스트는 나중에" 마인드
✗ 알람 피로도 누적으로 경각심 저하
```

---

## 6. 복구 및 검증

### 6.1 긴급 패치

```java
// 수정 전 (❌)
@Bean
public ConsumerFactory<String, CouponIssueRequestEvent> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "coupon-issue-group");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(props);
}

// 수정 후 (✅)
@Bean
public ConsumerFactory<String, CouponIssueRequestEvent> consumerFactory() {
    Map<String, Object> props = new HashMap<>();

    // application.yml에서 읽어오기
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaProperties.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG,
        kafkaProperties.getConsumer().getGroupId());

    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        JsonDeserializer.class);

    // 에러 핸들링 강화
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    return new DefaultKafkaConsumerFactory<>(props);
}
```

### 6.2 검증 테스트

#### Unit Test
```java
@Test
void consumerFactory_shouldUseConfigurationProperties() {
    // Given
    when(kafkaProperties.getBootstrapServers()).thenReturn("kafka:9092");

    // When
    ConsumerFactory<String, CouponIssueRequestEvent> factory =
        kafkaConfig.consumerFactory();

    // Then
    Map<String, Object> config = factory.getConfigurationProperties();
    assertThat(config.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
        .isEqualTo("kafka:9092");
    assertThat(config.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
        .doesNotContain("localhost");
}
```

#### Integration Test
```java
@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = "coupon-issue-test")
class CouponIssueKafkaConsumerIntegrationTest {

    @Test
    void shouldConsumeAndProcessCouponIssueMessage() {
        // Given
        CouponIssueRequestEvent event = CouponIssueRequestEvent.of(
            "test-request-id", 12345L, 1L
        );

        // When
        kafkaTemplate.send("coupon-issue-test", event);

        // Then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            CouponUser couponUser = couponUserRepository
                .findByUserIdAndCouponId(12345L, 1L)
                .orElseThrow();

            assertThat(couponUser).isNotNull();
            assertThat(couponUser.getStatus()).isEqualTo(CouponUserStatus.ACTIVE);
        });
    }
}
```

#### Smoke Test (Production)
```bash
#!/bin/bash
# smoke-test.sh

echo "🔥 Production Smoke Test 시작"

# 1. Health Check
echo "1. Health Check..."
response=$(curl -s http://api.ecommerce.com/actuator/health)
if [[ $response == *"UP"* ]]; then
    echo "✓ Health Check 통과"
else
    echo "✗ Health Check 실패"
    exit 1
fi

# 2. Kafka Consumer 연결 확인
echo "2. Kafka Consumer 상태 확인..."
lag=$(kafka-consumer-groups --describe --group coupon-issue-group \
    | awk '{sum+=$6} END {print sum}')

if [[ $lag -lt 100 ]]; then
    echo "✓ Consumer Lag 정상 ($lag)"
else
    echo "✗ Consumer Lag 높음 ($lag)"
    exit 1
fi

# 3. 테스트 쿠폰 발급
echo "3. 테스트 쿠폰 발급..."
for i in {1..5}; do
    response=$(curl -s -X POST http://api.ecommerce.com/coupons/1/issue \
        -H "userId: smoke-test-$i")

    if [[ $response == *"QUEUED"* ]]; then
        echo "  ✓ Test $i 성공"
    else
        echo "  ✗ Test $i 실패: $response"
        exit 1
    fi
done

# 4. Consumer 처리 확인 (10초 대기 후)
echo "4. Consumer 처리 확인 (10초 대기)..."
sleep 10

count=$(mysql -uroot -ppassword -e \
    "SELECT COUNT(*) FROM ecommerce.coupon_user WHERE user_id LIKE 'smoke-test-%';" \
    | tail -1)

if [[ $count -eq 5 ]]; then
    echo "✓ Consumer 처리 확인 (5/5)"
else
    echo "✗ Consumer 처리 실패 ($count/5)"
    exit 1
fi

echo "✅ 모든 Smoke Test 통과!"
```

### 6.3 Rollback Plan

```bash
# Rollback 시나리오 (만약 패치가 실패했다면)

# 1. 이전 버전으로 즉시 롤백
kubectl rollout undo deployment/ecom-app

# 2. Consumer 수동 실행 (임시 조치)
kubectl run coupon-consumer --image=ecom-app:v1.2.3 \
  --env="KAFKA_BOOTSTRAP_SERVERS=kafka:9092" \
  --command -- java -jar app.jar --consumer-only

# 3. 수동 쿠폰 발급 스크립트 준비
# (앞서 16:15에 실행한 스크립트와 동일)
```

---

## 7. 비즈니스 영향 분석

### 7.1 정량적 영향

#### 고객 영향
```
직접 영향:
- 쿠폰 발급 시도 고객: 45,000명
- 실제 발급 성공: 9,500명 (21%)
- 발급 실패: 35,500명 (79%)

고객센터 부하:
- 문의 접수: 1,200건
- 평균 처리 시간: 15분/건
- 총 소요 시간: 300시간 (5명 × 60시간)
```

#### 매출 영향
```
직접 손실:
- 쿠폰 미발급으로 인한 구매 포기: 35,500명 × 5% = 1,775명
- 평균 주문 금액: 50,000원
- 예상 손실: 88,750,000원

간접 손실:
- 브랜드 신뢰도 하락: 측정 불가
- 고객 이탈: 약 2% 예상 (900명)
- 생애 가치 손실: 900명 × 200,000원 = 180,000,000원

총 예상 손실: 약 268,750,000원
```

#### 보상 비용
```
보상 쿠폰 비용:
- 대상: 35,500명
- 쿠폰 금액: 5,000원
- 사용률 예상: 70%
- 총 비용: 35,500 × 5,000 × 0.7 = 124,250,000원

추가 증정 쿠폰:
- 정상 발급 고객 위로: 9,500명 × 500원 × 0.7 = 3,325,000원

총 보상 비용: 127,575,000원
```

### 7.2 정성적 영향

#### 브랜드 이미지
```
부정적 영향:
- SNS 부정적 언급: 약 500건
- 별점 하락: 4.5 → 4.2 (임시)
- 신뢰도 지수 하락: 15% (1주일 후 회복 예상)

긍정적 요소:
- 신속한 공지 및 보상: 고객 만족도 일부 회복
- 투명한 커뮤니케이션: 브랜드 신뢰도 유지
```

#### 팀 영향
```
개발팀:
- 긴급 대응으로 인한 피로도 증가
- 주말 근무 발생 (보상 휴가 제공)
- 프로세스 개선 기회 확보

고객센터:
- 2.75시간 집중 대응
- 스트레스 증가
- 위기 대응 역량 강화
```

---

## 8. 재발 방지 대책

### 8.1 즉시 조치 (1주일 이내)

#### 1. 코드 품질 개선
```java
// ✅ Configuration Validator 추가
@Component
public class KafkaConfigValidator implements InitializingBean {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Override
    public void afterPropertiesSet() {
        // localhost 사용 금지
        if (bootstrapServers.contains("localhost")) {
            throw new IllegalStateException(
                "Kafka bootstrap-servers must not use 'localhost' in production. " +
                "Current value: " + bootstrapServers
            );
        }

        // 연결 테스트
        try {
            AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000
            )).listTopics().names().get(10, TimeUnit.SECONDS);

            log.info("✓ Kafka 연결 테스트 성공: {}", bootstrapServers);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Kafka 연결 실패: " + bootstrapServers, e
            );
        }
    }
}
```

#### 2. 모니터링 강화
```yaml
# Prometheus Alert Rules
groups:
  - name: kafka_consumer_critical
    interval: 30s
    rules:
      # Consumer 연결 상태
      - alert: KafkaConsumerDisconnected
        expr: kafka_consumer_connection_count == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Kafka Consumer 연결 끊김"
          description: "Consumer가 Broker에 연결되지 않음"

      # Consumer Lag
      - alert: KafkaConsumerLagCritical
        expr: kafka_consumer_lag > 1000
        for: 30s
        labels:
          severity: critical
        annotations:
          summary: "Consumer Lag 임계값 초과"

      # 처리 속도 저하
      - alert: KafkaConsumerProcessingSlow
        expr: rate(kafka_consumer_records_consumed_total[5m]) < 10
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Consumer 처리 속도 저하"
```

#### 3. 배포 체크리스트
```markdown
# Production 배포 체크리스트

## Pre-Deployment
- [ ] 코드 리뷰 완료 (2명 이상 승인)
- [ ] 단위 테스트 통과 (커버리지 80%+)
- [ ] 통합 테스트 통과
- [ ] Staging 환경 배포 및 검증
- [ ] Smoke Test 스크립트 준비

## Deployment
- [ ] Canary Release (10% → 50% → 100%)
- [ ] 각 단계별 모니터링 (10분)
- [ ] Smoke Test 실행 및 통과
- [ ] Rollback Plan 준비

## Post-Deployment
- [ ] 주요 지표 모니터링 (30분)
  - [ ] Error Rate < 1%
  - [ ] Response Time P95 < 200ms
  - [ ] Consumer Lag < 100
- [ ] 고객 문의 모니터링
- [ ] 배포 완료 보고
```

---

### 8.2 단기 개선 (1개월 이내)

#### 1. CI/CD 파이프라인 강화
```yaml
# .github/workflows/deploy.yml
name: Production Deploy

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - name: Unit Tests
        run: ./gradlew test

      - name: Integration Tests
        run: ./gradlew integrationTest

      - name: Contract Tests
        run: ./gradlew contractTest

  security:
    runs-on: ubuntu-latest
    steps:
      - name: Dependency Check
        run: ./gradlew dependencyCheckAnalyze

      - name: Code Analysis (SonarQube)
        run: ./gradlew sonarqube

  deploy-staging:
    needs: [test, security]
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Staging
        run: kubectl apply -f k8s/staging/

      - name: Smoke Test
        run: ./scripts/smoke-test.sh staging

      - name: Load Test
        run: k6 run --vus 100 load-test.js

  deploy-production:
    needs: deploy-staging
    runs-on: ubuntu-latest
    steps:
      - name: Canary Deploy (10%)
        run: kubectl apply -f k8s/canary-10.yaml

      - name: Monitor (10 min)
        run: ./scripts/canary-monitor.sh 10

      - name: Full Deploy
        run: kubectl apply -f k8s/production/
```

#### 2. 에러 추적 시스템
```java
// Sentry 연동
@Configuration
public class SentryConfig {

    @Bean
    public SentryExceptionResolver sentryExceptionResolver() {
        return new SentryExceptionResolver();
    }
}

// Consumer 에러 핸들러
@Slf4j
@Component
public class KafkaConsumerErrorHandler implements CommonErrorHandler {

    private final SentryClient sentryClient;

    @Override
    public void handleException(Exception thrownException,
                                List<ConsumerRecord<?, ?>> records,
                                Consumer<?, ?> consumer,
                                MessageListenerContainer container) {

        log.error("Kafka Consumer 처리 실패", thrownException);

        // Sentry에 전송
        sentryClient.sendException(thrownException, Map.of(
            "kafka.topic", records.get(0).topic(),
            "kafka.partition", records.get(0).partition(),
            "kafka.offset", records.get(0).offset()
        ));

        // Slack 알림
        slackNotifier.sendCriticalAlert(
            "Kafka Consumer 에러 발생",
            thrownException.getMessage()
        );
    }
}
```

#### 3. Chaos Engineering 도입
```python
# chaos-test.py
from chaostoolkit.experiment import run_experiment

experiment = {
    "title": "Kafka Consumer 장애 시뮬레이션",
    "steady-state-hypothesis": {
        "title": "쿠폰 발급 성공률 95% 이상",
        "probes": [{
            "type": "probe",
            "name": "coupon-issue-success-rate",
            "tolerance": {
                "type": "range",
                "min": 95,
                "max": 100
            }
        }]
    },
    "method": [
        {
            "type": "action",
            "name": "kafka-consumer-kill",
            "provider": {
                "type": "process",
                "path": "kubectl",
                "arguments": ["delete", "pod", "-l", "app=kafka-consumer"]
            }
        },
        {
            "type": "probe",
            "name": "wait-for-recovery",
            "provider": {
                "type": "python",
                "module": "time",
                "func": "sleep",
                "arguments": [60]
            }
        }
    ],
    "rollbacks": [
        {
            "type": "action",
            "name": "restore-consumer",
            "provider": {
                "type": "process",
                "path": "kubectl",
                "arguments": ["rollout", "restart", "deployment/kafka-consumer"]
            }
        }
    ]
}

# 매주 금요일 자동 실행
```

---

### 8.3 중장기 개선 (3~6개월)

#### 1. 아키텍처 개선
```
현재 아키텍처:
┌──────────┐    ┌───────┐    ┌──────────┐
│   API    │───▶│ Redis │───▶│  Kafka   │
└──────────┘    └───────┘    └──────────┘
                                   │
                                   ▼
                            ┌──────────┐
                            │ Consumer │
                            └──────────┘
                                   │
                                   ▼
                            ┌──────────┐
                            │  MySQL   │
                            └──────────┘

문제점:
- Single Point of Failure (Consumer)
- 모니터링 사각지대
- 동기화 복잡도

개선 아키텍처:
┌──────────┐    ┌───────┐    ┌──────────┐
│   API    │───▶│ Redis │───▶│  Kafka   │
└──────────┘    └───────┘    └──────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
             ┌──────────┐   ┌──────────┐   ┌──────────┐
             │Consumer#1│   │Consumer#2│   │Consumer#3│
             └──────────┘   └──────────┘   └──────────┘
                    │              │              │
                    └──────────────┼──────────────┘
                                   ▼
                            ┌──────────┐
                            │  MySQL   │
                            └──────────┘
                                   │
                                   ▼
                            ┌──────────┐
                            │ Outbox   │ ← Change Data Capture
                            │ Pattern  │    (Debezium)
                            └──────────┘

장점:
- Consumer 수평 확장
- 파티션별 독립 처리
- Outbox Pattern으로 정합성 보장
```

#### 2. Observability 플랫폼
```
┌─────────────────────────────────────────┐
│         Observability Platform          │
├─────────────────────────────────────────┤
│  Logs (ELK Stack)                       │
│  - Application Logs                     │
│  - Kafka Logs                           │
│  - Access Logs                          │
├─────────────────────────────────────────┤
│  Metrics (Prometheus + Grafana)         │
│  - Business Metrics (쿠폰 발급 수)      │
│  - System Metrics (CPU, Memory)         │
│  - Application Metrics (RPS, Latency)   │
├─────────────────────────────────────────┤
│  Traces (Jaeger)                        │
│  - Request 전 구간 추적                 │
│  - 병목 구간 자동 분석                  │
├─────────────────────────────────────────┤
│  Alerts (AlertManager + PagerDuty)      │
│  - Multi-channel Notification           │
│  - On-call Rotation                     │
│  - Escalation Policy                    │
└─────────────────────────────────────────┘
```

#### 3. SRE 조직 구성
```
Site Reliability Engineering Team 신설:

역할:
- 24/7 On-call 체계 운영
- SLO/SLI 정의 및 모니터링
- Incident Response 프로세스 관리
- Post-Mortem 주도
- Capacity Planning

구성:
- SRE Lead: 1명
- SRE Engineer: 3명 (교대 근무)
- DevOps Engineer: 2명

목표:
- Availability: 99.9% (연간 다운타임 8.76시간)
- MTTR (Mean Time To Repair): < 1시간
- MTBF (Mean Time Between Failures): > 720시간
```

---

## 9. 회고 및 교훈

### 9.1 잘한 점 (What Went Well)

#### 1. 신속한 근본 원인 파악
```
✓ 장애 발생 후 40분 내 근본 원인 발견
✓ 체계적인 디버깅 프로세스 (로그 → 설정 → 코드)
✓ 가설 수립 및 검증 방식 효과적

교훈:
- 로그 기반 문제 해결 역량 입증
- Kafka 관련 디버깅 경험 축적
```

#### 2. 투명한 커뮤니케이션
```
✓ 장애 인지 15분 내 고객 공지
✓ 구체적인 보상 계획 즉시 제시
✓ 복구 완료 후 상세한 설명

고객 반응:
- "빠른 공지 감사합니다" (SNS 피드백)
- "보상이 오히려 이득" (긍정적 전환)
- 별점 회복: 4.2 → 4.4 (1주일 후)
```

#### 3. 보상 정책
```
✓ 과감한 보상 (5,000원 쿠폰 + 500원 추가)
✓ 이벤트 수량 2배 증량 (5,000 → 10,000)
✓ 신속한 보상 쿠폰 발급 (1시간 내)

비즈니스 효과:
- 고객 이탈 방지 (예상 2% → 실제 0.5%)
- 브랜드 신뢰도 회복
- 오히려 충성도 증가 (일부 고객)
```

---

### 9.2 개선 필요 (What Needs Improvement)

#### 1. 알람 대응 프로세스
```
✗ 최초 알람(14:00:30) 무시 → 15분 지연
✗ Critical 알람 구분 부족
✗ On-call 담당자 불명확

개선 조치:
- Critical 알람은 전화/SMS 동시 발송
- On-call Rotation 도입 (주간 교대)
- 알람 대응 SLA 정의 (5분 내 확인)
```

#### 2. 테스트 커버리지
```
✗ Kafka Consumer 통합 테스트 부재
✗ Docker 환경 테스트 미실시
✗ Production 배포 전 검증 생략

개선 조치:
- Consumer 통합 테스트 필수화
- Testcontainers 활용 Docker 테스트
- Staging 환경 배포 프로세스 강제화
```

#### 3. 코드 리뷰 문화
```
✗ 하드코딩 검출 실패
✗ "localhost" 사용 간과
✗ 형식적 승인 문화

개선 조치:
- 코드 리뷰 체크리스트 도입
- SonarQube 정적 분석 자동화
- "localhost" 사용 금지 규칙 추가
```

---

### 9.3 교훈 (Lessons Learned)

#### 기술적 교훈

**1. "Silent Failure는 치명적이다"**
```
Consumer 연결 실패가 조용히 발생 → 감지 불가

교훈:
- 모든 Critical Path에 명시적 로깅
- 연결 실패 시 즉시 Exception throw
- Fail-fast 원칙 적용
```

**2. "하드코딩은 시한폭탄이다"**
```java
// ❌ 절대 금지
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

// ✅ 항상 설정에서 읽기
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
    kafkaProperties.getBootstrapServers());
```

**3. "모니터링은 연결 전부터 시작한다"**
```
Consumer 연결 상태 모니터링 추가:
- kafka_consumer_connection_count
- kafka_consumer_connection_creation_total
- kafka_consumer_connection_close_total
```

#### 프로세스적 교훈

**4. "알람은 반드시 조치로 이어져야 한다"**
```
알람 → 확인 → 조치 → 종료

개선:
- 알람마다 Runbook 작성
- 5분 내 확인 규칙
- 조치 없이 종료 금지
```

**5. "고객 목소리는 가장 빠른 모니터링이다"**
```
고객센터 문의 → 실시간 대시보드 연동

개선:
- 고객센터 문의 수 모니터링
- 급증 시 자동 알람 (10분간 100건+)
- 개발팀 즉시 통보
```

**6. "Staging ≠ Production"**
```
Staging 정상 ≠ Production 정상

교훈:
- Production 환경 변수 철저히 검증
- Smoke Test 자동화
- Canary Release 필수화
```

#### 조직적 교훈

**7. "신속한 의사결정이 피해를 최소화한다"**
```
CTO의 즉각적 개입:
- 전사 리소스 집중 투입
- 보상 정책 신속 결정
- 고객 공지 즉시 승인

결과:
- 장애 시간 최소화
- 고객 신뢰 유지
```

**8. "투명성이 신뢰를 만든다"**
```
솔직한 공지:
"시스템 오류로 쿠폰이 발급되지 않았습니다"

vs 회피적 공지:
"일시적인 지연이 발생하고 있습니다"

결과: 투명한 공지가 더 긍정적 반응
```

---

### 9.4 Action Items

#### 즉시 조치 (완료)
- [x] Kafka Consumer 코드 수정 및 배포
- [x] 보상 쿠폰 발급
- [x] 고객 공지 발행
- [x] Post-Incident Meeting 일정 확정

#### 1주일 이내
- [ ] Configuration Validator 구현
- [ ] 모니터링 알람 규칙 강화
- [ ] 배포 체크리스트 작성 및 교육
- [ ] Kafka Consumer 통합 테스트 추가
- [ ] Runbook 작성 (Kafka 장애 대응)

#### 1개월 이내
- [ ] CI/CD 파이프라인 강화
- [ ] Sentry 연동 (에러 추적)
- [ ] Slack 알람 개선 (Critical → Phone)
- [ ] Staging 환경 배포 프로세스 정립
- [ ] 코드 리뷰 가이드라인 업데이트

#### 3개월 이내
- [ ] Observability 플랫폼 구축 (ELK + Prometheus + Jaeger)
- [ ] Chaos Engineering 도입
- [ ] SRE 팀 구성 검토
- [ ] SLO/SLI 정의
- [ ] On-call Rotation 시스템 구축

---

## 부록

### A. 타임라인 요약 (One-Page)

```
[장애 발생 - 14:00:00]
└─ 14:00:30 ⚠️  알람: Consumer Lag 증가 (무시)
└─ 14:02:00 📞  고객 문의 시작
└─ 14:10:00 🚨  알람: Lag Critical (무시)
└─ 14:15:00 ❗  CTO 인지 → 긴급 소집

[문제 분석 - 14:15:00]
└─ 14:20:00 🔍  로그 분석 시작
└─ 14:25:00 💡  가설 수립
└─ 14:30:00 📄  설정 파일 확인
└─ 14:40:00 🎯  근본 원인 발견

[대응 조치 - 14:45:00]
└─ 14:50:00 ⚙️   긴급 패치 개발
└─ 15:00:00 📢  고객 공지 발행
└─ 15:10:00 ✅  테스트 완료
└─ 15:30:00 🚀  배포 완료

[복구 검증 - 15:30:00]
└─ 15:35:00 📊  Consumer 동작 확인
└─ 15:46:00 ✓   Lag 완전 해소
└─ 16:00:00 🧪  부하 테스트
└─ 16:15:00 💝  보상 쿠폰 발급
└─ 16:30:00 ✓   복구 공지 발행

[종료 - 16:45:00]
└─ 16:45:00 🏁  장애 종료 선언

총 소요 시간: 2시간 45분
```

### B. 연락처 및 참고 문서

**긴급 연락망**:
- CTO: 010-XXXX-XXXX
- 개발팀 리더: 010-YYYY-YYYY
- 인프라팀 리더: 010-ZZZZ-ZZZZ
- On-call (주간): Slack #oncall-rotation

**참고 문서**:
- Kafka Consumer Runbook: `/docs/runbooks/kafka-consumer.md`
- 배포 체크리스트: `/docs/deploy-checklist.md`
- 장애 대응 프로세스: `/docs/incident-response-process.md`

### C. 관련 이슈 및 PR

- **Incident Ticket**: [INC-2025-001](https://jira.example.com)
- **긴급 패치 PR**: [#542](https://github.com/example/ecom/pull/542)
- **모니터링 개선 PR**: [#543](https://github.com/example/ecom/pull/543)
- **테스트 추가 PR**: [#544](https://github.com/example/ecom/pull/544)

---

**문서 승인**:
- 작성: DevOps Team
- 검토: CTO, 개발팀 리더
- 승인 일자: 2025-12-27

**다음 리뷰 예정**: 2026-01-26 (1개월 후)

---

**[문서 종료]**
