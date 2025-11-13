package com.hh.ecom.user.infrastructure.persistence.jpa;

import com.hh.ecom.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
}
