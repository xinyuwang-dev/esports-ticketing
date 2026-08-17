package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {
}