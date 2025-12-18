# Kafka 기본 개념 정리

## 1. Kafka란?

Kafka는 LinkedIn에서 개발한 **분산 스트리밍 플랫폼**이다.  
대용량의 실시간 데이터를 안정적으로 처리하기 위해 설계되었으며, **Publish–Subscribe 모델**을 기반으로 동작한다.

전통적인 메시징 시스템과 달리 메시지를 **디스크에 영속적으로 저장**하고, **수평 확장이 가능한 구조**를 가진다.

다시 말해, Kafka는 메시지를 전달하는 시스템이 아니라 **이벤트를 안전하게 저장하고, 여러 Consumer가 각자 필요할 때 읽어가는 로그 플랫폼**이다.

---

## 2. 아키텍처 구성 요소

### Broker

Kafka 서버의 인스턴스를 의미한다.  
각 Broker는 고유한 ID를 가지며, Topic의 Partition 데이터를 저장하고 관리한다.

여러 Broker가 모여 **Kafka 클러스터**를 구성하며, 클러스터 단위로 **장애 허용성**과 **확장성**을 제공한다.

---

### Topic

메시지를 분류하는 **논리적 채널**이다.  
Producer는 특정 Topic에 메시지를 발행하고, Consumer는 관심 있는 Topic을 구독한다.

Topic 이름은 의미 있는 이름으로 지정하는 것이 관리에 유리하다.

예시:
- `order-events`
- `user-activities`
- `payment-transactions`

---

### Partition

Topic의 **물리적 분할 단위**이다.  
하나의 Topic은 여러 Partition으로 나뉘며, 각 Partition은 독립적으로 읽고 쓸 수 있다.

- Partition 수는 **병렬 처리 성능**을 결정
- Consumer의 **최대 동시 처리 수**와 동일
- **동일 Partition 내 메시지는 순서가 보장**

---

### Replication & ISR

Kafka는 데이터 유실 방지를 위해 **Replication** 메커니즘을 제공한다.

- **Replication Factor**: 각 Partition의 복제본 개수 (일반적으로 3)
- 각 Partition은 **1개의 Leader**와 **여러 Follower**로 구성
- **Leader만 읽기/쓰기를 담당**

**ISR (In-Sync Replicas)** 는 Leader와 동기화 상태를 유지하는 복제본 집합이다.  
Leader 장애 시 ISR 내에서 새로운 Leader가 선출된다.

`min.insync.replicas` 설정으로 최소 ISR 개수를 보장할 수 있다.

---

### Producer

메시지를 발행하는 주체다.

- **Partition Key**를 지정하여 저장될 Partition을 결정
- 동일 Key → 동일 Partition → **순서 보장**
- Key가 없으면 **Round-robin 방식**으로 분배

---

### Consumer & Consumer Group

메시지를 소비하는 주체다.

- **Consumer Group**은 여러 Consumer가 협력하여 메시지를 처리하는 논리적 단위
- 각 Partition은 **Consumer Group 내 하나의 Consumer에만 할당**
- 중복 처리 방지 + 부하 분산

Consumer는 **Offset**을 통해 읽은 위치를 관리한다.

- Offset 정보는 내부 Topic인 `__consumer_offsets`에 저장
- Consumer 재시작 시 마지막 Offset부터 처리 가능

---

## 3. 메시지 전달 보장 수준

### At Most Once (최대 1회)

- 메시지가 최대 1번 전달
- **유실 가능**, 중복 없음
- 설정 예:
    - Producer: `acks=0`
    - Consumer: Auto Commit

사용 사례: 로그 수집 등 일부 유실이 허용되는 경우

---

### At Least Once (최소 1회)

- 메시지가 최소 1번 전달
- **중복 가능**, 유실 없음
- 설정 예:
    - Producer: `acks=1` 또는 `acks=all`
    - Consumer: Manual Commit

실무에서 가장 많이 사용  
→ Consumer 측에서 **멱등성 보장 필수**

---

### Exactly Once (정확히 1회)

- 메시지가 정확히 1번 전달
- 유실과 중복 모두 없음
- **Transactional Producer + Idempotent Consumer** 필요
- 성능 오버헤드 존재

사용 사례: 금융 거래 등 정확성이 중요한 경우

---

## 4. 주요 설정

### Producer 설정

- `acks`: 메시지 전송 확인 수준  
  (`0`: 확인 안 함, `1`: Leader만, `all`: 모든 ISR)
- `retries`: 전송 실패 시 재시도 횟수
- `batch.size`: 배치로 보낼 메시지 크기
- `linger.ms`: 배치를 보내기 전 대기 시간
- `enable.idempotence`: 멱등성 활성화 (중복 제거)

---

### Consumer 설정

- `group.id`: Consumer Group 식별자
- `enable.auto.commit`: Offset 자동 커밋 여부
- `auto.offset.reset`: Offset이 없을 때 시작 위치  
  (`earliest`: 처음부터, `latest`: 최신부터)
- `max.poll.records`: 한 번에 가져올 최대 레코드 수
- `session.timeout.ms`: Consumer 세션 타임아웃

---

### Topic 설정

- `num.partitions`: Partition 개수 (병렬 처리 수준 결정)
- `replication.factor`: 복제본 수 (일반적으로 3)
- `retention.ms`: 메시지 보관 기간
- `min.insync.replicas`: 최소 ISR 개수 (보통 2)

---

## 5. Offset 관리

Offset은 Consumer가 각 Partition에서 읽은 위치를 나타내는 숫자다.  
Kafka는 `__consumer_offsets` 내부 Topic에 Consumer Group별 Offset을 저장한다.

- **Auto Commit**
    - 설정 간단
    - 중복 처리 또는 유실 가능성 존재
- **Manual Commit**
    - 처리 완료 후 명시적 커밋
    - 정확성 ↑, 구현 복잡도 ↑

`auto.offset.reset`:
- `earliest`: 처음부터 읽기
- `latest`: 최신부터 읽기

---

## 6. Partition과 순서 보장

Kafka는 **동일 Partition 내에서만 순서를 보장**한다.  
Topic 전체 단위로는 순서가 보장되지 않는다.

순서가 중요한 경우:
- 동일한 **Partition Key** 사용
- 예: 사용자 이벤트 → `userId`를 Key로 사용


운영 중 Partition 수 변경 시  
Key-Partition 매핑이 변경되어 순서가 깨질 수 있으므로  
**초기 설계가 중요**

---

## 7. 장애 처리

### Broker 장애

- ISR 내 Follower 중 하나가 새로운 Leader로 선출
- `replication.factor=3`, `min.insync.replicas=2`라면  
  Broker 1대 장애에도 서비스 지속 가능

---

### Consumer 장애

- **Rebalancing** 발생
- 다른 Consumer가 장애 Consumer의 Partition을 인계
- Rebalancing 동안 메시지 처리 일시 중단

---

### 메시지 유실 방지 전략

- `acks=all`
- `min.insync.replicas=2`

→ Leader + 최소 1 Follower 저장 후 성공 응답

---

### Consumer Lag

Producer가 발행한 메시지와  
Consumer가 처리한 메시지의 차이

- Lag 증가 지속 → 성능 문제 또는 장애
- **모니터링 필수 지표**

---

## 8. 정리

Kafka는 **Partition, Replication, ISR**을 통해  
고가용성과 확장성을 제공하는 분산 메시징 시스템이다.

- 실무에서는 **At Least Once**가 가장 일반적
- 안정적인 처리를 위해 **Offset & Consumer Group 관리가 핵심**
- Partition 설계 시 **순서 보장 vs 병렬 처리** 트레이드오프 고려
- 장애 대응을 위한 적절한 Replication 설정이 중요