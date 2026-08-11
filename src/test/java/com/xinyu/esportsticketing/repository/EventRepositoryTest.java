package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Test
    void shouldFindAllEvents() {
        List<Event> events = eventRepository.findAll();

        assertNotNull(events);
    }
}