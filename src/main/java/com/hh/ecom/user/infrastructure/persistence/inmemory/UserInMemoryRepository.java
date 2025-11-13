package com.hh.ecom.user.infrastructure.persistence.inmemory;

import com.hh.ecom.user.domain.User;
import com.hh.ecom.user.domain.UserRepository;
import com.hh.ecom.user.infrastructure.persistence.entity.UserEntity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class UserInMemoryRepository implements UserRepository {
    private final Map<Long, UserEntity> users = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public User save(User user) {
        UserEntity entity;
        if (user.getId() == null) {
            Long newId = idGenerator.getAndIncrement();
            entity = UserEntity.builder()
                    .id(newId)
                    .nickname(user.getNickname())
                    .createdAt(user.getCreatedAt())
                    .build();
        } else {
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
