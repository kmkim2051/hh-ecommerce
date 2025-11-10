package com.hh.ecom.user.domain;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    boolean existsById(Long id);
    void deleteAll(); // for testing
}
