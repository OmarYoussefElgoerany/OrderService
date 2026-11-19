package com.screen.orders.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  boolean existsByRole(Role role);

  long countByRole(Role role);

}
