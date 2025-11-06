package com.hh.ecom.user.infrastructure.persistence;

import com.hh.ecom.user.domain.User;
import com.hh.ecom.user.domain.UserRepository;
import com.hh.ecom.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class UserInMemoryRepository implements UserRepository {
    private final Map<Long, UserEntity> users = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public User save(User user) {
        UserEntity entity;

        if (user.getId() == null) {
            // 새로운 사용자 생성
            Long newId = idGenerator.getAndIncrement();
            entity = UserEntity.builder()
                    .id(newId)
                    .nickname(user.getNickname())
                    .createdAt(user.getCreatedAt())
                    .build();
        } else {
            // 기존 사용자 업데이트 (현재는 사용 안 함)
            entity = UserEntity.from(user);
        }

        users.put(entity.getId(), entity);
        return entity.toDomain();
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(users.get(id))
                .map(UserEntity::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return users.containsKey(id);
    }

    @Override
    public void deleteAll() {
        users.clear();
        idGenerator.set(1);
    }
}
