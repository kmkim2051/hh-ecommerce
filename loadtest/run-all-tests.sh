#!/bin/bash

# ============================================
# E-Commerce 부하 테스트 자동 실행 스크립트
# ============================================
#
# 사용 방법:
# 1. 실행 권한 부여
#    chmod +x loadtest/run-all-tests.sh
#
# 2. 실행
#    ./loadtest/run-all-tests.sh
#
# ============================================

set -e  # 에러 발생 시 즉시 종료

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 설정
BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULTS_DIR="loadtest/results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# ============================================
# 함수 정의
# ============================================

print_header() {
  echo -e "${BLUE}============================================${NC}"
  echo -e "${BLUE}$1${NC}"
  echo -e "${BLUE}============================================${NC}"
}

print_success() {
  echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
  echo -e "${RED}❌ $1${NC}"
}

print_warning() {
  echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
  echo -e "${BLUE}ℹ️  $1${NC}"
}

check_prerequisites() {
  print_header "사전 확인"

  # k6 설치 확인
  if ! command -v k6 &> /dev/null; then
    print_error "k6가 설치되지 않았습니다"
    echo "설치 방법: brew install k6 (macOS)"
    exit 1
  fi
  print_success "k6 설치 확인: $(k6 version | head -n 1)"

  # 애플리케이션 상태 확인
  if ! curl -s -f "$BASE_URL/actuator/health" > /dev/null; then
    print_error "애플리케이션이 실행되지 않았습니다: $BASE_URL"
    echo "다음 명령으로 애플리케이션을 시작하세요:"
    echo "  ./gradlew bootRun --args='--spring.profiles.active=loadtest'"
    exit 1
  fi
  print_success "애플리케이션 상태: OK"

  # 결과 디렉토리 생성
  mkdir -p "$RESULTS_DIR"
  print_success "결과 디렉토리: $RESULTS_DIR"
}

wait_for_system() {
  local seconds=$1
  print_info "시스템 안정화 대기 중... ($seconds초)"
  for i in $(seq "$seconds" -1 1); do
    echo -ne "\r  남은 시간: ${i}초  "
    sleep 1
  done
  echo -e "\r${GREEN}  시스템 준비 완료!       ${NC}"
}

run_scenario() {
  local scenario_file=$1
  local scenario_name=$2
  local coupon_id=${3:-1}

  print_header "시나리오: $scenario_name"

  local output_file="$RESULTS_DIR/${scenario_name}-${TIMESTAMP}.json"

  # k6 실행
  k6 run \
    -e BASE_URL="$BASE_URL" \
    -e COUPON_ID="$coupon_id" \
    --summary-export="$output_file" \
    --out influxdb=http://localhost:8086/k6 \
    "loadtest/$scenario_file"

  local exit_code=$?

  if [ $exit_code -eq 0 ]; then
    print_success "$scenario_name 완료"
    print_info "결과 파일: $output_file"
  else
    print_error "$scenario_name 실패 (Exit Code: $exit_code)"
    return 1
  fi
}

generate_summary() {
  print_header "테스트 결과 요약"

  echo "테스트 시작: $(date -r "$RESULTS_DIR/${TIMESTAMP}".* +"%Y-%m-%d %H:%M:%S" 2>/dev/null || echo "N/A")"
  echo "테스트 종료: $(date +"%Y-%m-%d %H:%M:%S")"
  echo ""
  echo "생성된 결과 파일:"
  ls -lh "$RESULTS_DIR"/*"${TIMESTAMP}"* 2>/dev/null || echo "  (없음)"
  echo ""
  print_info "Grafana 대시보드: http://localhost:3000"
  print_info "Kafka UI: http://localhost:8080"
  print_info "Redis Commander: http://localhost:8081"
}

# ============================================
# 메인 실행
# ============================================

main() {
  print_header "🚀 E-Commerce 부하 테스트 자동 실행"

  # 사전 확인
  check_prerequisites

  # 시작 시간 기록
  start_time=$(date +%s)

  # ========================================
  # 시나리오 #1: 쿠폰 발급 (최대 300 VU)
  # ========================================
  print_info "시나리오 #1: 선착순 쿠폰 발급 테스트"
  print_info "  - 최대 부하: 300 VU"
  print_info "  - 예상 시간: 3분"
  echo ""

  if run_scenario "scenario1-coupon-issue.js" "scenario1-coupon" 1; then
    wait_for_system 30
  else
    print_warning "시나리오 1 실패, 계속 진행합니다"
    wait_for_system 10
  fi

  # ========================================
  # 시나리오 #2: 인기 상품 조회 (최대 300 VU)
  # ========================================
  print_info "시나리오 #2: 인기 상품 조회 테스트"
  print_info "  - 최대 부하: 300 VU"
  print_info "  - 예상 시간: 3분"
  echo ""

  if run_scenario "scenario2-popular-products.js" "scenario2-popular"; then
    print_success "모든 시나리오 완료!"
  else
    print_warning "시나리오 2 실패"
  fi

  # 종료 시간 기록
  end_time=$(date +%s)
  duration=$((end_time - start_time))

  print_header "📊 최종 결과"
  echo "총 실행 시나리오: 2개"
  echo "총 소요 시간: $((duration / 60))분 $((duration % 60))초"
  echo ""

  # 결과 요약
  generate_summary

  print_success "부하 테스트 완료!"
}

# 스크립트 실행
main

# ============================================
# 완료
# ============================================
