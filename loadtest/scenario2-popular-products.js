/**
 * 시나리오 #2: 인기 상품 조회 부하 테스트
 *
 * 목표:
 * - Redis 캐싱 효과 측정
 * - 읽기 집약적 트래픽 처리 성능 측정
 * - 대량 동시 조회 시 응답 시간 측정
 *
 * 실행 방법:
 * k6 run loadtest/scenario2-popular-products.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// ============================================
// 설정 및 메트릭
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const productQueryTime = new Trend('product_query_time');
const cacheHitRate = new Rate('cache_hit_rate_estimated');
const totalRequests = new Counter('total_requests');

// 부하 테스트 시나리오 설정
export const options = {
  stages: [
    // Phase 1: Ramp-Up (점진적 부하 증가)
    { duration: '15s', target: 50 },     // 0~15초: 50 VU
    { duration: '15s', target: 100 },    // 15~30초: 100 VU

    // Phase 2: Peak Traffic (피크 부하)
    { duration: '30s', target: 300 },    // 30~60초: 300 VU (피크)

    // Phase 3: Steady State (안정화)
    { duration: '15s', target: 100 },    // 60~75초: 100 VU

    // Phase 4: Ramp-Down
    { duration: '15s', target: 0 },      // 75~90초: 0 VU
  ],

  // 성공 기준 임계값
  thresholds: {
    // API 응답 시간
    'http_req_duration': [
      'p(50)<50',    // 50% 요청이 50ms 이내 (캐시 히트)
      'p(95)<200',   // 95% 요청이 200ms 이내
      'p(99)<500',   // 99% 요청이 500ms 이내
    ],

    // 에러율
    'errors': ['rate<0.05'],  // 에러율 5% 이하

    // HTTP 성공률
    'http_req_failed': ['rate<0.05'],

    // 요청 처리율 (초당)
    'http_reqs': ['rate>100'],  // 최소 100 RPS
  },

  // 테스트 태그
  tags: {
    test_scenario: 'popular-products',
    environment: 'loadtest',
  },
};

// ============================================
// Setup: 테스트 전 초기화
// ============================================
export function setup() {
  console.log('========================================');
  console.log('인기 상품 조회 부하 테스트 시작');
  console.log('========================================');
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`최대 동시 사용자: 300 VU`);
  console.log(`총 테스트 시간: 1분 30초`);
  console.log('');

  // 애플리케이션 헬스 체크
  const healthRes = http.get(`${BASE_URL}/actuator/health`);

  if (healthRes.status !== 200) {
    console.error('❌ 애플리케이션이 실행되지 않았습니다!');
    throw new Error('Setup failed: Application not running');
  }

  console.log('✅ 애플리케이션 상태: OK');
  console.log('');
  console.log('테스트 시작...');
  console.log('========================================');

  return { baseUrl: BASE_URL };
}

// ============================================
// 메인 테스트 시나리오
// ============================================
export default function(data) {
  totalRequests.add(1);

  // 인기 상품 (1~100번) 위주로 조회 (파레토 법칙: 20%가 80% 트래픽)
  const productId = getPopularProductId();

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      scenario: 'product-query',
      product_id: productId,
    },
  };

  // 상품 조회 API 호출
  const startTime = new Date();
  const res = http.get(`${data.baseUrl}/products/${productId}`, params);
  const endTime = new Date();
  const duration = endTime - startTime;

  // 메트릭 기록
  productQueryTime.add(duration);

  // 응답 검증
  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'has product data': (r) => {
      try {
        const product = JSON.parse(r.body);
        return product && product.id === productId;
      } catch (e) {
        return false;
      }
    },
  });

  // 캐시 히트 추정 (응답 시간 기준)
  // 50ms 이하 = 캐시 히트 추정
  if (duration < 50) {
    cacheHitRate.add(1);
  } else {
    cacheHitRate.add(0);
  }

  if (!success) {
    errorRate.add(1);
    console.error(`❌ Error: ${res.status} - Product ${productId}`);
  } else {
    errorRate.add(0);
  }

  // Think time (사용자가 상품 정보를 읽는 시간)
  sleep(randomIntBetween(1, 3));
}

// ============================================
// Teardown: 테스트 후 검증
// ============================================
export function teardown(data) {
  console.log('');
  console.log('========================================');
  console.log('테스트 완료');
  console.log('========================================');
  console.log('');
  console.log('💡 성능 분석 팁:');
  console.log('');
  console.log('1. 캐시 히트율 확인:');
  console.log('   - cache_hit_rate_estimated 메트릭 확인');
  console.log('   - 90% 이상이면 캐시가 효과적으로 동작');
  console.log('');
  console.log('2. 응답 시간 분포:');
  console.log('   - P50 < 50ms: 캐시 히트');
  console.log('   - P95 < 200ms: 일부 캐시 미스 포함');
  console.log('   - P99 < 500ms: DB 조회 포함');
  console.log('');
  console.log('3. Redis 상태 확인:');
  console.log('   docker exec -it ecom-redis redis-cli INFO stats');
  console.log('   docker exec -it ecom-redis redis-cli DBSIZE');
  console.log('');
  console.log('========================================');
}

// ============================================
// 헬퍼 함수
// ============================================

/**
 * 인기 상품 ID 생성 (파레토 법칙 적용)
 * - 80%의 요청이 상위 20% 상품에 집중
 */
function getPopularProductId() {
  const random = Math.random();

  if (random < 0.8) {
    // 80% 확률: 인기 상품 (1~20번)
    return randomIntBetween(1, 20);
  } else {
    // 20% 확률: 일반 상품 (21~100번)
    return randomIntBetween(21, 100);
  }
}

// ============================================
// 핸들 요약 (Custom Summary)
// ============================================
export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary-popular-products.json': JSON.stringify(data),
  };
}

function textSummary(data, options) {
  // k6 기본 summary 사용
  return '';
}
