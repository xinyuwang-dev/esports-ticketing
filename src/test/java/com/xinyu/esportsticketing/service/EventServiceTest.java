package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Event;
import com.xinyu.esportsticketing.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void shouldReturnAllEvents() {

        Event event1 = new Event();
        event1.setTitle("League of Legends World Championship");

        Event event2 = new Event();
        event2.setTitle("Valorant Champions");

        List<Event> expectedEvents = List.of(event1, event2);

        when(eventRepository.findAll()).thenReturn(expectedEvents);

        List<Event> actualEvents = eventService.getAllEvents();

        assertEquals(expectedEvents, actualEvents);

        verify(eventRepository).findAll();
    }
}
