-- ============================================
-- E-Commerce 부하 테스트 데이터 준비 스크립트
-- ============================================
--
-- 목적: 부하 테스트를 위한 대량 테스트 데이터 생성
--
-- 사용 방법:
-- 1. MySQL 접속
--    mysql -h localhost -P 3306 -u root -p ecom_db
--
-- 2. 스크립트 실행
--    source loadtest/setup-test-data.sql;
--
-- 3. 또는 Docker 컨테이너에서 실행
--    docker exec -i ecom-mysql mysql -uroot -ppassword ecommerce < loadtest/setup-test-data.sql
--
-- ============================================
--
-- 📊 데이터 생성 개요
-- ============================================
--
-- 기본 생성량 (Default Quantities):
--   ┌─────────────────┬──────────┬─────────────────────────────┐
--   │ 도메인 테이블    │ 기본 개수 │ 조정 위치                    │
--   ├─────────────────┼──────────┼─────────────────────────────┤
--   │ users           │ 1,000명  │ Line 63: LIMIT 1000         │
--   │ products        │ 1,000개  │ Line 101: LIMIT 1000        │
--   │ points          │ 1,000개  │ Line 118: 사용자 수에 자동 맞춤 │
--   │ coupons         │ 4개      │ Line 126-135: INSERT VALUES │
--   │ orders (샘플)    │ 100개    │ Line 167: LIMIT 100         │
--   │ order_items     │ 200개    │ Line 186: LIMIT 200         │
--   └─────────────────┴──────────┴─────────────────────────────┘
--
-- 데이터 양 조정 방법:
--
-- 1. 사용자 수 조정 (Line 63):
--    LIMIT 1000  ←  원하는 사용자 수로 변경 (예: 500, 5000, 10000)
--
-- 2. 상품 수 조정 (Line 101):
--    LIMIT 1000  ←  원하는 상품 수로 변경 (예: 500, 5000)
--
-- 3. 포인트 계좌:
--    사용자 수에 자동으로 맞춰집니다 (각 사용자당 1개 생성)
--    초기 잔액 조정: Line 114의 "100000" 값 변경
--
-- 4. 쿠폰 수 및 수량 조정 (Line 126-135):
--    - 쿠폰 종류를 추가/삭제하려면 VALUES 절에 행 추가/제거
--    - 각 쿠폰의 수량 조정: 'quantity' 컬럼 값 변경
--      예: ('부하테스트_선착순1000', 'FIXED', 5000, 10000, ...)
--                                                  ^^^^^ 이 값 조정
--
-- 5. 샘플 주문 수 조정 (Line 167):
--    LIMIT 100  ←  원하는 주문 수로 변경
--
-- 6. 주문 아이템 수 조정 (Line 186):
--    LIMIT 200  ←  원하는 아이템 수로 변경
--    (일반적으로 주문 수 * 1~3배 정도 설정)
--
-- 테스트 규모별 권장 설정:
--
--   소규모 테스트 (개발/디버깅):
--     - users: 100, products: 100, orders: 10, order_items: 20
--
--   중규모 테스트 (기본/권장):
--     - users: 1000, products: 1000, orders: 100, order_items: 200
--
--   대규모 테스트 (성능 검증):
--     - users: 10000, products: 5000, orders: 1000, order_items: 2000
--
--   주의사항:
--     - 대규모 데이터 생성 시 실행 시간이 오래 걸릴 수 있습니다
--     - 10,000개 이상 생성 시 LIMIT 값을 늘리기 전에 cross-join 테이블 개수 조정 필요
--       (예: t4 테이블 추가로 10 x 10 x 10 x 10 = 10,000)
--
-- ============================================

-- 변수 초기화
SET @row := 0;

-- ============================================
-- 1. 기존 테스트 데이터 정리 (선택사항)
-- ============================================

# TRUNCATE TABLE coupon_user;
# TRUNCATE TABLE order_items;
# TRUNCATE TABLE orders;
# TRUNCATE TABLE cart_items;
# TRUNCATE TABLE points;
# TRUNCATE TABLE point_transactions;
# TRUNCATE TABLE coupon;
# TRUNCATE TABLE products;
# TRUNCATE TABLE product_views;
# TRUNCATE TABLE users;
DROP TABLE IF EXISTS coupon_user;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS point_transactions;
DROP TABLE IF EXISTS points;
DROP TABLE IF EXISTS coupon;
DROP TABLE IF EXISTS product_views;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;
-- ============================================
-- 2. 사용자 생성 (1,000명)
-- ============================================


create table ecommerce.cart_items
(
    id         bigint auto_increment
        primary key,
    created_at datetime(6) not null,
    product_id bigint      not null,
    quantity   int         not null,
    user_id    bigint      not null,
    version    bigint      null
);
create table ecommerce.coupon
(
    id                 bigint auto_increment
        primary key,
    available_quantity int                                                not null,
    created_at         datetime(6)                                        not null,
    discount_amount    decimal(10, 2)                                     not null,
    end_date           datetime(6)                                        null,
    is_active          bit                                                null default 1,
    name               varchar(255)                                       not null,
    start_date         datetime(6)                                        not null,
    status             enum ('ACTIVE', 'DISABLED', 'EXPIRED', 'SOLD_OUT') not null,
    total_quantity     int                                                not null,
    updated_at         datetime(6)                                        null,
    version            bigint                                             null
);
create table ecommerce.coupon_user
(
    id          bigint auto_increment primary key,
    coupon_id   bigint      not null,
    expire_date datetime(6) null,
    is_used     bit         not null,
    issued_at   datetime(6) not null,
    order_id    bigint      null,
    used_at     datetime(6) null,
    user_id     bigint      not null,
    version     bigint      null,
    constraint UKkkn2jxhpgkf8kce5ipsvak6vi
        unique (user_id, coupon_id)
);

create table ecommerce.order_items
(
    id           bigint auto_increment
        primary key,
    created_at   datetime(6)                 not null,
    order_id     bigint                      not null,
    price        decimal(10, 2)              not null,
    product_id   bigint                      not null,
    product_name varchar(255)                not null,
    quantity     int                         not null,
    status       enum ('CANCELED', 'NORMAL') not null,
    updated_at   datetime(6)                 null
);

create index idx_order_items_join_group_covering
    on ecommerce.order_items (order_id, product_id, quantity);

create table ecommerce.orders
(
    id              bigint auto_increment
        primary key,
    coupon_user_id  bigint                                            null,
    created_at      datetime(6)                                       not null,
    discount_amount decimal(10, 2)                                    null,
    final_amount    decimal(10, 2)                                    not null,
    order_number    varchar(50)                                       not null,
    status          enum ('CANCELED', 'COMPLETED', 'PAID', 'PENDING') not null,
    total_amount    decimal(10, 2)                                    not null,
    updated_at      datetime(6)                                       null,
    user_id         bigint                                            not null,
    version         bigint                                            null,
    constraint UKnthkiu7pgmnqnu86i2jyoe2v7
        unique (order_number)
);

create index idx_orders_status_id
    on ecommerce.orders (status, id);

create table ecommerce.point_transactions
(
    id            bigint auto_increment
        primary key,
    amount        decimal(19, 2)                   not null,
    balance_after decimal(19, 2)                   not null,
    created_at    datetime(6)                      not null,
    order_id      bigint                           null,
    point_id      bigint                           not null,
    type          enum ('CHARGE', 'REFUND', 'USE') not null
);

create table ecommerce.points
(
    id         bigint auto_increment
        primary key,
    balance    decimal(19, 2) not null,
    updated_at datetime(6)    not null,
    user_id    bigint         not null,
    version    bigint         null,
    created_at datetime(6)    not null,
    constraint UKswg8y3uo5dm5psbnesgeu1my
        unique (user_id)
);

create table ecommerce.product_views
(
    id         bigint auto_increment
        primary key,
    product_id bigint      not null,
    viewed_at  datetime(6) not null
);

create index idx_product_id
    on ecommerce.product_views (product_id);

create index idx_product_viewed
    on ecommerce.product_views (product_id, viewed_at);

create index idx_viewed_at
    on ecommerce.product_views (viewed_at);
create table ecommerce.products
(
    id             bigint auto_increment
        primary key,
    created_at     datetime(6)    not null,
    deleted_at     datetime(6)    null,
    description    text           null,
    is_active      bit            not null,
    name           varchar(255)   not null,
    price          decimal(10, 2) not null,
    stock_quantity int            not null,
    updated_at     datetime(6)    not null,
    version        bigint         null,
    view_count     int            not null
);



create table ecommerce.users
(
    id         bigint auto_increment
        primary key,
    created_at datetime(6)  not null,
    nickname   varchar(255) not null
);

-- ============================================
INSERT INTO users (id, nickname, created_at)
SELECT
  CONCAT(n) AS id,
  CONCAT('loadtest', n) AS nickname,
  NOW() AS created_at
FROM (
  SELECT @row := @row + 1 AS n
  FROM (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9
  ) t1,
  (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9
  ) t2,
  (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9
  ) t3,
  (SELECT @row := 0) r
  LIMIT 1000
) nums
ON DUPLICATE KEY UPDATE nickname = nickname;  -- 중복 시 무시

SELECT CONCAT('✅ 사용자 생성 완료: ', COUNT(*), '명') AS status FROM users;

-- ============================================
-- 3. 상품 생성 (1,000개)
-- ============================================

SET @row := 0;

INSERT INTO products (name, price, is_active, view_count, stock_quantity, description, created_at, updated_at)
SELECT
  CONCAT('부하테스트상품_', n) AS name,
  (10000 + (n * 100)) AS price,
  true,
  0,
  1000 AS stock_quantity,
  CONCAT('부하 테스트용 상품입니다. 상품 번호: ', n) AS description,
  NOW() AS created_at,
  NOW() AS updated_at
FROM (
  SELECT @row := @row + 1 AS n
  FROM (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9
  ) t1,
  (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9
  ) t2,
  (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9
  ) t3,
  (SELECT @row := 0) r
  LIMIT 1000
) nums
ON DUPLICATE KEY UPDATE name = name;

SELECT CONCAT('✅ 상품 생성 완료: ', COUNT(*), '개') AS status FROM products;

-- ============================================
-- 4. 포인트 계좌 생성 (사용자당 100,000 포인트)
-- ============================================

INSERT INTO points (id, user_id, balance, created_at, updated_at)
SELECT
  id,
  id AS user_id,
  100000 AS balance,
  NOW() AS created_at,
  NOW() AS updated_at
FROM users
ON DUPLICATE KEY UPDATE balance = 100000;

SELECT CONCAT('✅ 포인트 계좌 생성 완료: ', COUNT(*), '개') AS status FROM points;

-- ============================================
-- 5. 쿠폰 생성 (부하 테스트용)
-- ============================================

INSERT INTO coupon (id, name, discount_amount, available_quantity, total_quantity, start_date, end_date, status, is_active, created_at, updated_at)
VALUES
  -- 시나리오 #1: 선착순 쿠폰 발급 테스트용
  (1, '부하테스트_선착순1000', 5000, 1000, 1000, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'ACTIVE', 1, NOW(), NOW()),
  (2, '부하테스트_선착순500', 10000, 500, 500, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'ACTIVE', 1, NOW(), NOW()),
  (3, '부하테스트_선착순5000', 3000, 5000, 5000, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'ACTIVE', 1, NOW(), NOW()),

  -- 시나리오 #2: 주문 생성 테스트용 (대량)
  (4, '주문테스트_할인쿠폰', 2000, 10000, 10000, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'ACTIVE', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = name;

SELECT CONCAT('✅ 쿠폰 생성 완료: ', COUNT(*), '개') AS status FROM coupon;

-- ============================================
-- 6. 판매 데이터 생성 (시나리오 #3: 판매 랭킹 테스트용)
-- ============================================

-- 주문 생성 (샘플 100개)
SET @row := 0;

INSERT INTO orders (id, user_id, order_number, status, discount_amount, total_amount, final_amount, created_at, updated_at)
SELECT
    n + 1,
  ((n % 100) + 1) AS user_id,
  CONCAT('LOAD-', LPAD(n, 8, '0')) AS order_number,
  'COMPLETED' AS status,
    (20000 + (n * 10)) AS discount_amount,
  (20000 + (n * 100)) AS total_amount,
    (20000 + (n * 100)) AS final_amount,
  DATE_SUB(NOW(), INTERVAL (n % 30) DAY) AS created_at,
  DATE_SUB(NOW(), INTERVAL (n % 30) DAY) AS updated_at
FROM (
  SELECT @row := @row + 1 AS n
  FROM (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9
  ) t1,
  (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL
    SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL
    SELECT 8 UNION ALL SELECT 9
  ) t2,
  (SELECT @row := 0) r
  LIMIT 100
) nums
ON DUPLICATE KEY UPDATE order_number = order_number;

SELECT CONCAT('✅ 주문 생성 완료: ', COUNT(*), '개') AS status FROM orders;

-- 주문 아이템 생성 (각 주문당 1~3개 상품)
SET @row := 0;

INSERT INTO order_items (id, order_id, product_id, quantity, price, created_at, updated_at, status, product_name)
SELECT
    @row as id,
  o.id AS order_id,
  (((@row := @row + 1) % 100) + 1) AS product_id,  -- 상위 100개 상품에 집중
  (1 + (@row % 3)) AS quantity,  -- 1~3개
  (10000 + ((@row % 100) * 100)) AS price,
  o.created_at AS created_at,
  o.updated_at AS updated_at,
  'NORMAL' as status,
  'abc' as product_name
FROM orders o
CROSS JOIN (SELECT @row := 0) r
LIMIT 200  -- 주문 100개 * 평균 2개 = 200개
ON DUPLICATE KEY UPDATE quantity = quantity;

SELECT CONCAT('✅ 주문 아이템 생성 완료: ', COUNT(*), '개') AS status FROM order_items;

-- ============================================
-- 7. 데이터 검증
-- ============================================

SELECT '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=';
SELECT '📊 테스트 데이터 생성 결과' AS '';
SELECT '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=';

SELECT
  '사용자' AS 항목,
  COUNT(*) AS 개수,
  '1,000명' AS 목표
FROM users
UNION ALL
SELECT
  '상품',
  COUNT(*),
  '1,000개'
FROM products
UNION ALL
SELECT
  '포인트 계좌',
  COUNT(*),
  '1,000개'
FROM points
UNION ALL
SELECT
  '쿠폰',
  COUNT(*),
  '4개'
FROM coupon
UNION ALL
SELECT
  '샘플 주문',
  COUNT(*),
  '100개'
FROM orders
UNION ALL
SELECT
  '주문 아이템',
  COUNT(*),
  '200개'
FROM order_items;

SELECT '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=';
SELECT '✅ 테스트 데이터 준비 완료!' AS '';
SELECT '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=', '=' AS '=';

-- ============================================
-- 8. 인덱스 확인 (성능 테스트용)
-- ============================================

SELECT '📋 인덱스 현황' AS '';

SHOW INDEX FROM order_items WHERE Key_name LIKE 'idx_%';
SHOW INDEX FROM orders WHERE Key_name LIKE 'idx_%';
SHOW INDEX FROM products WHERE Key_name LIKE 'idx_%';

-- ============================================
-- 완료 메시지
-- ============================================

SELECT '🚀 부하 테스트를 시작하세요!' AS '';
SELECT '   k6 run loadtest/scenario1-coupon-issue.js' AS '';
