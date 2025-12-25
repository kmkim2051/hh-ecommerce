/**
 * 시나리오 #1: 선착순 쿠폰 발급 부하 테스트
 *
 * 목표:
 * - Kafka 파티션 키 기반 동시성 제어 검증
 * - Redis 빠른 검증 효과 측정
 * - Consumer 처리율 및 병목 지점 파악
 *
 * 실행 방법:
 * k6 run loadtest/scenario1-coupon-issue.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// ============================================
// 설정 및 메트릭
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1';  // 테스트 대상 쿠폰 ID

// 커스텀 메트릭
const errorRate = new Rate('errors');
const queuedRate = new Rate('queued_responses');
const soldOutRate = new Rate('sold_out_responses');
const duplicateRate = new Rate('duplicate_responses');
const apiResponseTime = new Trend('api_response_time');
const successCount = new Counter('success_count');
const failCount = new Counter('fail_count');

// 부하 테스트 시나리오 설정
export const options = {
  stages: [
    // Phase 1: Ramp-Up (점진적 부하 증가)
    { duration: '15s', target: 50 },     // 0~15초: 50 VU
    { duration: '15s', target: 100 },    // 15~30초: 100 VU

    // Phase 2: Peak Traffic (선착순 이벤트 시작)
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
      'p(50)<100',   // 50% 요청이 100ms 이내
      'p(95)<300',   // 95% 요청이 300ms 이내
      'p(99)<500',   // 99% 요청이 500ms 이내
    ],

    // 에러율
    'errors': ['rate<0.10'],  // 에러율 10% 이하

    // HTTP 성공률
    'http_req_failed': ['rate<0.10'],

    // 요청 처리율 (초당)
    'http_reqs': ['rate>50'],  // 최소 50 RPS
  },

  // 테스트 태그
  tags: {
    test_scenario: 'coupon-issue',
    environment: 'loadtest',
  },
};

// ============================================
// 헬퍼 함수
// ============================================

/**
 * 랜덤 사용자 ID 생성
 * VU 번호 기반으로 고유한 사용자 생성
 */
function getRandomUserId() {
  // VU 번호를 기반으로 사용자 ID 생성 (1 ~ 10000)
  // __VU는 k6의 Virtual User 번호 (1부터 시작)
  const baseUserId = (__VU % 10000) + 1;
  // 약간의 랜덤성 추가 (같은 VU가 여러 번 시도할 수 있도록)
  return baseUserId + randomIntBetween(0, 10) * 10000;
}

/**
 * HTTP 요청 공통 헤더
 */
function getHeaders() {
  return {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };
}

// ============================================
// 메인 테스트 시나리오
// ============================================

export default function () {
  const userId = getRandomUserId();

  group('Coupon Issue API', () => {
    // 쿠폰 발급 요청
    const issueUrl = `${BASE_URL}/coupons/${COUPON_ID}/issue`;

    const startTime = new Date().getTime();
    const issueRes = http.post(issueUrl, null, {
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'userId': userId.toString(),
      },
      tags: { name: 'CouponIssue' },
    });
    const duration = new Date().getTime() - startTime;

    // 응답 시간 기록
    apiResponseTime.add(duration);

    // 응답 검증
    const issueSuccess = check(issueRes, {
      'status is 200 (QUEUED)': (r) => r.status === 200,
      'status is 409 (ALREADY_ISSUED)': (r) => r.status === 409,
      'status is 410 (SOLD_OUT)': (r) => r.status === 410,
      'response time < 100ms': (r) => r.timings.duration < 100,
      'response has body': (r) => r.body && r.body.length > 0,
    });

    // 응답별 카운팅
    if (issueRes.status === 200) {
      queuedRate.add(1);
      successCount.add(1);
    } else if (issueRes.status === 409) {
      duplicateRate.add(1);
      failCount.add(1);
    } else if (issueRes.status === 410) {
      soldOutRate.add(1);
      failCount.add(1);
    } else {
      errorRate.add(1);
      failCount.add(1);
    }

    // 응답 파싱
    let responseBody = {};
    try {
      responseBody = JSON.parse(issueRes.body);
    } catch (e) {
      console.error(`Failed to parse response: ${issueRes.body}`);
      errorRate.add(1);
    }

    // 응답 데이터 검증
    if (issueRes.status === 200) {
      check(responseBody, {
        'has status field': (r) => r.status !== undefined,
        'status is QUEUED': (r) => r.status === 'QUEUED',
        'has userId': (r) => r.userId === userId,
        'has couponId': (r) => r.couponId === parseInt(COUPON_ID),
      });
    }

    // 로그 출력 (샘플링: 1% 확률)
    if (randomIntBetween(1, 100) <= 1) {
      console.log(`[VU ${__VU}] User ${userId}: Status ${issueRes.status}, Duration ${duration}ms`);
    }
  });

  // 요청 간 대기 시간 (Think Time)
  // 사용자 행동 시뮬레이션: 0.5~2초 대기
  sleep(randomIntBetween(0.5, 2));
}

// ============================================
// 셋업 및 티어다운
// ============================================

/**
 * 테스트 시작 전 초기화 (1회 실행)
 */
export function setup() {
  console.log('='.repeat(80));
  console.log('🚀 쿠폰 발급 부하 테스트 시작');
  console.log('='.repeat(80));
  console.log(`📍 Target URL: ${BASE_URL}`);
  console.log(`🎫 Coupon ID: ${COUPON_ID}`);
  console.log(`👥 Max VUs: 5000`);
  console.log(`⏱️  Duration: 150s`);
  console.log('='.repeat(80));

  // // 쿠폰 정보 조회 (사전 확인)
  // const checkUrl = `${BASE_URL}/api/coupons/${COUPON_ID}`;
  // const checkRes = http.get(checkUrl, {
  //   headers: getHeaders(),
  // });
  //
  // if (checkRes.status === 200) {
  //   const coupon = JSON.parse(checkRes.body);
  //   console.log(`✅ 쿠폰 확인 완료: ${coupon.name} (재고: ${coupon.quantity})`);
  //   return { coupon };
  // } else {
  //   console.error(`❌ 쿠폰 조회 실패: Status ${checkRes.status}`);
  //   return { coupon: null };
  // }
}

/**
 * 테스트 종료 후 정리 (1회 실행)
 */
export function teardown(data) {
  console.log('='.repeat(80));
  console.log('🏁 쿠폰 발급 부하 테스트 종료');
  console.log('='.repeat(80));

  // 최종 쿠폰 재고 확인
  const checkUrl = `${BASE_URL}/api/coupons/${COUPON_ID}`;
  const checkRes = http.get(checkUrl, {
    headers: getHeaders(),
  });

  if (checkRes.status === 200) {
    const coupon = JSON.parse(checkRes.body);
    console.log(`📊 최종 재고: ${coupon.quantity}`);

    if (data.coupon) {
      const issued = data.coupon.quantity - coupon.quantity;
      console.log(`✅ 발급된 쿠폰: ${issued}개`);
    }
  }

  console.log('='.repeat(80));
  console.log('💡 Tip: Grafana 대시보드에서 상세 지표를 확인하세요');
  console.log('='.repeat(80));
}

/**
 * 테스트 결과 핸들러
 */
export function handleSummary(data) {
  return {
    'summary.json': JSON.stringify(data, null, 2),
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

// 텍스트 요약 생성
function textSummary(data, opts) {
  const indent = opts.indent || '';
  const enableColors = opts.enableColors !== false;

  let summary = `
${indent}=================================================================
${indent}📊 쿠폰 발급 부하 테스트 결과
${indent}=================================================================

${indent}📈 성능 지표:
${indent}  • 총 요청 수: ${data.metrics.http_reqs.values.count}
${indent}  • 성공 요청: ${data.metrics.success_count ? data.metrics.success_count.values.count : 0}
${indent}  • 실패 요청: ${data.metrics.fail_count ? data.metrics.fail_count.values.count : 0}
${indent}  • 테스트 시간: ${(data.state.testRunDurationMs / 1000).toFixed(2)}s

${indent}⏱️  응답 시간:
${indent}  • P50: ${(data.metrics.http_req_duration?.values?.['p(50)'] || 0).toFixed(2)}ms
${indent}  • P95: ${(data.metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
${indent}  • P99: ${(data.metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms
${indent}  • Max: ${(data.metrics.http_req_duration?.values?.max || 0).toFixed(2)}ms

${indent}📊 응답 분류:
${indent}  • QUEUED (200): ${((data.metrics.queued_responses?.values.rate || 0) * 100).toFixed(2)}%
${indent}  • SOLD_OUT (410): ${((data.metrics.sold_out_responses?.values.rate || 0) * 100).toFixed(2)}%
${indent}  • DUPLICATE (409): ${((data.metrics.duplicate_responses?.values.rate || 0) * 100).toFixed(2)}%
${indent}  • ERROR: ${((data.metrics.errors?.values.rate || 0) * 100).toFixed(2)}%

${indent}✅ 임계값 통과 여부:
`;

  // 임계값 검증
  for (const [name, thresholds] of Object.entries(data.thresholds || {})) {
    for (const threshold of thresholds.thresholds) {
      const passed = threshold.ok ? '✅' : '❌';
      summary += `${indent}  ${passed} ${name}: ${threshold.value}\n`;
    }
  }

  summary += `
${indent}=================================================================
`;

  return summary;
}
