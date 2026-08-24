package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.TicketCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.List;

public interface TicketCategoryRepository
        extends JpaRepository<TicketCategory, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tc FROM TicketCategory tc WHERE tc.id = :id")
    Optional<TicketCategory> findByIdForUpdate(@Param("id") Integer id);

    List<TicketCategory> findByEventId(Integer eventId);
}