package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Integer> {

}