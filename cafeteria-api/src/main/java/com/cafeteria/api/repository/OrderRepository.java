
package com.cafeteria.api.repository;

import com.cafeteria.api.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("SELECT DISTINCT o FROM Order o")
    List<Order> findAllWithItems();
}
