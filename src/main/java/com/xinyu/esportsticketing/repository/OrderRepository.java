package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByTicketCategoryId(Integer categoryId);
}