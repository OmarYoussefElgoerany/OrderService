package com.screen.orders.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {

  @EntityGraph(attributePaths = {"items", "user"})
  Optional<Order> findWithDetailsById(Integer id);

  @EntityGraph(attributePaths = {"items", "user"})
  Optional<Order> findWithDetailsByOrderNumber(String orderNumber);

  boolean existsByOrderNumber(String orderNumber);
}
