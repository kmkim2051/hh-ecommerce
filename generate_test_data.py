"""
테스트 데이터 생성 스크립트
- Product: n
- ProductView: 각 상품당 m (총 n * m 개)
- Order: 1000건
- OrderItem: 주문당 2~4개 (총 2,000~4,000개)
"""

import random
from datetime import datetime, timedelta
from decimal import Decimal

# 설정
NUM_PRODUCTS = 1000
VIEWS_PER_PRODUCT = 100
NUM_ORDERS = 10000
MIN_ITEMS_PER_ORDER = 5
MAX_ITEMS_PER_ORDER = 5

# 시간 범위 설정 (최근 30일)
now = datetime.now()
start_date = now - timedelta(days=30)

def generate_timestamp(start, end):
    """start와 end 사이의 랜덤 타임스탬프 생성"""
    time_delta = end - start
    random_seconds = random.randint(0, int(time_delta.total_seconds()))
    return start + timedelta(seconds=random_seconds)

def format_datetime(dt):
    """MySQL DATETIME 형식으로 포맷"""
    return dt.strftime('%Y-%m-%d %H:%M:%S')

def escape_string(s):
    """SQL 문자열 이스케이프"""
    return s.replace("'", "''")

# SQL 파일 생성
output_file = 'test_data.sql'

with open(output_file, 'w', encoding='utf-8') as f:
    f.write("-- 테스트 데이터 생성 스크립트\n")
    f.write("-- 생성 시간: " + format_datetime(now) + "\n\n")

    f.write("SET FOREIGN_KEY_CHECKS = 0;\n\n")

    # 기존 데이터 삭제
    f.write("-- 기존 데이터 삭제\n")
    f.write("TRUNCATE TABLE product_views;\n")
    f.write("TRUNCATE TABLE order_items;\n")
    f.write("TRUNCATE TABLE orders;\n")
    f.write("TRUNCATE TABLE products;\n\n")

    # 1. Products 생성 (100개)
    f.write("-- Products 생성 ({}개)\n".format(NUM_PRODUCTS))
    product_categories = ['전자제품', '의류', '식품', '도서', '생활용품', '스포츠', '완구', '가구', '화장품', '주방용품']

    for i in range(1, NUM_PRODUCTS + 1):
        category = random.choice(product_categories)
        name = f"{category} 상품 {i:03d}"
        description = f"{name}에 대한 상세 설명입니다. 고품질의 제품으로 고객 만족도가 높습니다."
        price = Decimal(random.randint(1000, 100000) / 100) * 100  # 1,000 ~ 100,000원 (백원 단위)
        stock = random.randint(50, 500)
        view_count = 0  # 초기값
        created_at = format_datetime(generate_timestamp(start_date - timedelta(days=365), start_date))
        updated_at = created_at

        f.write(f"INSERT INTO products (name, description, price, stock_quantity, view_count, is_active, created_at, updated_at) ")
        f.write(f"VALUES ('{escape_string(name)}', '{escape_string(description)}', {price}, {stock}, {view_count}, true, '{created_at}', '{updated_at}');\n")

    f.write("\n")

    # 2. Product Views 생성 (각 상품당 100개, 총 10,000개)
    f.write("-- Product Views 생성 (%d개)\n" % (NUM_PRODUCTS * VIEWS_PER_PRODUCT))

    # Bulk insert를 위한 배치 크기 설정
    BATCH_SIZE = 1000
    values_batch = []

    for product_id in range(1, NUM_PRODUCTS + 1):
        for _ in range(VIEWS_PER_PRODUCT):
            viewed_at = format_datetime(generate_timestamp(start_date, now))
            values_batch.append(f"({product_id}, '{viewed_at}')")

            # 배치 크기에 도달하면 INSERT 실행
            if len(values_batch) >= BATCH_SIZE:
                f.write("INSERT INTO product_views (product_id, viewed_at) VALUES\n")
                f.write(",\n".join(values_batch))
                f.write(";\n\n")
                values_batch = []

    # 남은 데이터 처리
    if values_batch:
        f.write("INSERT INTO product_views (product_id, viewed_at) VALUES\n")
        f.write(",\n".join(values_batch))
        f.write(";\n\n")

    f.write("\n")

    # 3. Orders 생성 (1000건)
    f.write("-- Orders 생성 (%d건)\n" % NUM_ORDERS)

    order_statuses = ['COMPLETED', 'COMPLETED', 'COMPLETED', 'PAID', 'PENDING']  # COMPLETED 비중 높임

    for order_idx in range(1, NUM_ORDERS + 1):
        user_id = random.randint(1, 100)  # 100명의 사용자
        order_number = f"ORD-{now.strftime('%Y%m%d')}-{order_idx:06d}"
        status = random.choice(order_statuses)
        created_at = format_datetime(generate_timestamp(start_date, now))
        updated_at = created_at

        # 주문 아이템 개수 결정 (2~4개)
        num_items = random.randint(MIN_ITEMS_PER_ORDER, MAX_ITEMS_PER_ORDER)

        # 주문에 포함될 상품들 선택 (중복 없이)
        selected_products = random.sample(range(1, NUM_PRODUCTS + 1), num_items)

        # 총액 계산 (나중에 order_items에서 사용)
        total_amount = Decimal(0)
        items_data = []

        for product_id in selected_products:
            quantity = random.randint(1, 5)
            price = Decimal(random.randint(1000, 100000) / 100) * 100
            total_amount += price * quantity
            items_data.append({
                'product_id': product_id,
                'quantity': quantity,
                'price': price
            })

        discount_amount = Decimal(0)
        final_amount = total_amount - discount_amount

        # Order INSERT
        f.write(f"INSERT INTO orders (user_id, order_number, total_amount, discount_amount, final_amount, status, created_at, updated_at, version) ")
        f.write(f"VALUES ({user_id}, '{order_number}', {total_amount}, {discount_amount}, {final_amount}, '{status}', '{created_at}', '{updated_at}', 0);\n")

        # Order ID는 AUTO_INCREMENT로 order_idx와 같다고 가정
        order_id = order_idx

        # OrderItems INSERT
        for item in items_data:
            product_name = f"상품 {item['product_id']:03d}"
            item_status = 'NORMAL'

            f.write(f"INSERT INTO order_items (order_id, product_id, product_name, price, quantity, status, created_at, updated_at) ")
            f.write(f"VALUES ({order_id}, {item['product_id']}, '{product_name}', {item['price']}, {item['quantity']}, '{item_status}', '{created_at}', '{updated_at}');\n")

        f.write("\n")

    f.write("SET FOREIGN_KEY_CHECKS = 1;\n\n")
    f.write("-- 데이터 생성 완료\n")
    f.write(f"-- Products: {NUM_PRODUCTS}개\n")
    f.write(f"-- Product Views: {NUM_PRODUCTS * VIEWS_PER_PRODUCT}개\n")
    f.write(f"-- Orders: {NUM_ORDERS}개\n")
    f.write(f"-- Order Items: 약 {NUM_ORDERS * (MIN_ITEMS_PER_ORDER + MAX_ITEMS_PER_ORDER) // 2}개\n")

print(f"✅ SQL 파일 생성 완료: {output_file}")
print(f"\n생성된 데이터:")
print(f"  - Products: {NUM_PRODUCTS}개")
print(f"  - Product Views: {NUM_PRODUCTS * VIEWS_PER_PRODUCT}개")
print(f"  - Orders: {NUM_ORDERS}개")
print(f"  - Order Items: 약 {NUM_ORDERS * (MIN_ITEMS_PER_ORDER + MAX_ITEMS_PER_ORDER) // 2}개")
print(f"\nDB에 적용하려면:")
print(f"  mysql -u [username] -p [database] < {output_file}")
print(f"  또는")
print(f"  source {output_file};  (MySQL 콘솔에서)")
