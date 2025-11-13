package com.hh.ecom.user.infrastructure.persistence.jpa;

import com.hh.ecom.user.domain.User;
import com.hh.ecom.user.domain.UserRepository;
import com.hh.ecom.user.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Primary
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        UserEntity entity = UserEntity.from(user);
        UserEntity savedEntity = userJpaRepository.save(entity);
        return savedEntity.toDomain();
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id)
                .map(UserEntity::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return userJpaRepository.existsById(id);
    }

    @Override
    public void deleteAll() {
        userJpaRepository.deleteAll();
    }
}
