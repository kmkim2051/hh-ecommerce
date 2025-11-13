-- E-commerce Database Schema for TestContainers
-- 성능 최적화 인덱스 포함

-- 1. Users 테이블
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

-- 2. Products 테이블
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    stock_quantity INT NOT NULL,
    view_count INT NOT NULL DEFAULT 0,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- 3. Cart Items 테이블
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- 4. Orders 테이블
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    final_amount DECIMAL(10,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- 5. Order Items 테이블
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- 6. Coupons 테이블
CREATE TABLE IF NOT EXISTS coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    discount_type VARCHAR(50) NOT NULL,
    discount_value DECIMAL(10,2) NOT NULL,
    min_order_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_discount_amount DECIMAL(10,2),
    total_quantity INT NOT NULL,
    issued_quantity INT NOT NULL DEFAULT 0,
    start_date DATETIME(6) NOT NULL,
    end_date DATETIME(6) NOT NULL,
    is_active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- 7. Coupon Users 테이블
CREATE TABLE IF NOT EXISTS coupon_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    is_used BIT(1) NOT NULL DEFAULT b'0',
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);

-- 8. Points 테이블
CREATE TABLE IF NOT EXISTS points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- 9. Point Transactions 테이블
CREATE TABLE IF NOT EXISTS point_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    balance_after BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY (point_id) REFERENCES points(id)
);

-- ==========================================
-- 성능 최적화 인덱스
-- ==========================================

-- Orders 테이블: status 필터링 최적화
CREATE INDEX idx_orders_status_id ON orders(status, id);

-- Order Items 테이블: Covering Index (JOIN + GROUP BY + SUM 최적화)
-- ProductService.getProductsBySalesCount() 쿼리 최적화
CREATE INDEX idx_order_items_join_group_covering ON order_items(order_id, product_id, quantity);

-- Cart Items 테이블: 사용자별 조회 최적화
CREATE INDEX idx_cart_items_user_id ON cart_items(user_id);

-- Coupon Users 테이블: 사용자별 쿠폰 조회 최적화
CREATE INDEX idx_coupon_users_user_id ON coupon_users(user_id);
CREATE INDEX idx_coupon_users_coupon_id ON coupon_users(coupon_id);

-- Point Transactions 테이블: 포인트별 거래 내역 조회 최적화
CREATE INDEX idx_point_transactions_point_id ON point_transactions(point_id);
