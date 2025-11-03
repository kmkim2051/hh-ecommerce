# E-commerce Mock API Server

JSON Server를 활용한 E-commerce Mock API 서버입니다.

## 설치 방법

```bash
cd mock-server
npm install
```

## 실행 방법

### 기본 실행
```bash
npm start
```

### 개발 모드 (500ms 지연)
```bash
npm run dev
```

서버는 기본적으로 `http://localhost:3000`에서 실행됩니다.

## API 엔드포인트

### Products (상품)
- `GET /products` - 상품 목록 조회
- `GET /products/:id` - 상품 상세 조회
- `POST /products` - 상품 추가
- `PUT /products/:id` - 상품 수정
- `DELETE /products/:id` - 상품 삭제

### Cart Items (장바구니)
- `GET /cartItems` - 장바구니 목록 조회
- `GET /cartItems/:id` - 장바구니 아이템 조회
- `POST /cartItems` - 장바구니에 상품 추가
- `PUT /cartItems/:id` - 장바구니 수량 수정
- `DELETE /cartItems/:id` - 장바구니 상품 삭제

### Orders (주문)
- `GET /orders` - 주문 목록 조회
- `GET /orders/:id` - 주문 상세 조회
- `POST /orders` - 주문 생성
- `PUT /orders/:id` - 주문 수정

### Coupons (쿠폰)
- `GET /coupons` - 쿠폰 목록 조회
- `GET /coupons/:id` - 쿠폰 상세 조회
- `POST /coupons` - 쿠폰 추가

### User Coupons (사용자 쿠폰)
- `GET /userCoupons` - 사용자 쿠폰 목록 조회
- `GET /userCoupons/:id` - 사용자 쿠폰 상세 조회
- `POST /userCoupons` - 쿠폰 발급

### Points (포인트)
- `GET /points` - 포인트 조회
- `PUT /points/:id` - 포인트 업데이트

### Point Transactions (포인트 거래 내역)
- `GET /pointTransactions` - 포인트 거래 내역 조회
- `POST /pointTransactions` - 포인트 거래 내역 추가

## 필터링 및 검색

JSON Server는 다양한 쿼리 파라미터를 지원합니다:

### 필터링
```bash
# userId가 1인 장바구니 조회
GET /cartItems?userId=1

# status가 PAID인 주문 조회
GET /orders?status=PAID

# 사용하지 않은 쿠폰 조회
GET /userCoupons?isUsed=false
```

### 페이징
```bash
# 페이지 2, 페이지당 10개
GET /products?_page=2&_limit=10
```

### 정렬
```bash
# 생성일시 기준 내림차순 정렬
GET /orders?_sort=createdAt&_order=desc
```

### 전체 텍스트 검색
```bash
# name 필드에서 "마우스" 검색
GET /products?q=마우스
```

## 사용 예시

### 상품 목록 조회
```bash
curl http://localhost:3000/products
```

### 장바구니에 상품 추가
```bash
curl -X POST http://localhost:3000/cartItems \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productId": 1,
    "productName": "노트북",
    "price": 1500000,
    "quantity": 1
  }'
```

### 주문 생성
```bash
curl -X POST http://localhost:3000/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "orderNumber": "ORDER-1234567890",
    "totalAmount": 1570000,
    "discountAmount": 0,
    "status": "PAID",
    "items": [
      {
        "productId": 1,
        "productName": "노트북",
        "price": 1500000,
        "quantity": 1
      }
    ]
  }'
```

## 주의사항

- 이 Mock API 서버는 **개발 및 테스트 용도**로만 사용하세요
- 복잡한 비즈니스 로직(재고 차감, 쿠폰 수량 체크, 트랜잭션 등)은 구현되지 않았습니다
- 단순 CRUD 작업만 지원됩니다
- 실제 서비스에서는 Spring Boot 애플리케이션의 API를 사용하세요

## Spring Boot API 엔드포인트

실제 비즈니스 로직이 구현된 Spring Boot API는 다음과 같습니다:

### Orders
- `POST /orders` - 주문 생성 (재고 차감, 포인트 차감, 쿠폰 사용 처리 포함)
- `GET /orders` - 주문 목록 조회
- `GET /orders/:id` - 주문 상세 조회
- `POST /orders/:id/cancel` - 주문 취소 (재고 복원, 포인트 환불, 쿠폰 복원)

### Products
- `GET /products` - 상품 목록 조회
- `GET /products/:id` - 상품 상세 조회
- `GET /products/:id/stock` - 실시간 재고 조회

### Cart
- `GET /cart-items` - 장바구니 조회
- `POST /cart-items` - 장바구니에 상품 추가 (재고 검증)
- `PATCH /cart-items/:id` - 장바구니 수량 변경 (재고 검증)
- `DELETE /cart-items/:id` - 장바구니 상품 삭제

### Coupons
- `GET /coupons` - 발급 가능한 쿠폰 목록 조회
- `POST /coupons/:couponId/issue` - 쿠폰 발급 (선착순, 동시성 제어)
- `GET /coupons/my` - 내 쿠폰 목록 조회

### Points
- `GET /points/balance` - 포인트 잔액 조회
- `POST /points/charge` - 포인트 충전
- `GET /points/transactions` - 포인트 거래 내역 조회

## 참고

- JSON Server 공식 문서: https://github.com/typicode/json-server
