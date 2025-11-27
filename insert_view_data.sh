#!/bin/bash

# Redis Docker 컨테이너 이름
REDIS_CONTAINER="my-redis"

# 상품 ID 범위 (1~1000개 상품)
PRODUCT_COUNT=1000

# Redis 키
KEY_1D="product:view:recent1d"
KEY_3D="product:view:recent3d"
KEY_7D="product:view:recent7d"

echo "Inserting view count data into Redis..."

# 1일 데이터 삽입 (평균 조회수: 100~5000)
echo "Inserting 1-day view data..."
for ((i=1; i<=$PRODUCT_COUNT; i++)); do
    VIEW_COUNT=$((10000 + RANDOM % 10000))
    docker exec $REDIS_CONTAINER redis-cli ZADD $KEY_1D $VIEW_COUNT "$i" > /dev/null
done

# 3일 데이터 삽입 (1일의 약 3배)
echo "Inserting 3-day view data..."
for ((i=1; i<=$PRODUCT_COUNT; i++)); do
    VIEW_COUNT=$((30000 + RANDOM % 30000))
    docker exec $REDIS_CONTAINER redis-cli ZADD $KEY_3D $VIEW_COUNT "$i" > /dev/null
done

# 7일 데이터 삽입 (1일의 약 7배)
echo "Inserting 7-day view data..."
for ((i=1; i<=$PRODUCT_COUNT; i++)); do
    VIEW_COUNT=$((70000 + RANDOM % 70000))
    docker exec $REDIS_CONTAINER redis-cli ZADD $KEY_7D $VIEW_COUNT "$i" > /dev/null
done

# 데이터 확인
echo ""
echo "Data insertion completed!"
echo "-----------------------------------"
echo "1-day data count: $(docker exec $REDIS_CONTAINER redis-cli ZCARD $KEY_1D)"
echo "3-day data count: $(docker exec $REDIS_CONTAINER redis-cli ZCARD $KEY_3D)"
echo "7-day data count: $(docker exec $REDIS_CONTAINER redis-cli ZCARD $KEY_7D)"
echo ""
echo "Top 5 products (1-day):"
docker exec $REDIS_CONTAINER redis-cli ZREVRANGE $KEY_1D 0 4 WITHSCORES
