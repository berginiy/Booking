package com.company.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.company.booking.domain.Room;
import com.company.booking.dto.Dtos.BookingRequest;
import com.company.booking.exception.ApiException;
import com.company.booking.repository.RoomRepository;
import com.company.booking.service.BookingService;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false"})
class BookingOverlapTest {

    @Autowired BookingService service;
    @Autowired RoomRepository rooms;

    Long roomId;
    final LocalDate day = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        Room r = new Room();
        r.setName("Тестовый зал");
        r.setFloor(1);
        r.setCapacity(5);
        roomId = rooms.save(r).getId();
        service.create(req(roomId, "10:00", "11:00"));
    }

    BookingRequest req(Long room, String s, String e) {
        return new BookingRequest("Иванов Иван", "+7 900 111-22-33", "ivanov@company.ru",
                room, day, LocalTime.parse(s), LocalTime.parse(e), null);
    }

    @Test
    void rejectsPartialOverlap() {
        ApiException ex = assertThrows(ApiException.class, () -> service.create(req(roomId, "10:30", "11:30")));
        assertEquals("ROOM_BUSY", ex.getCode());
    }

    @Test
    void rejectsInsideAndEnclosing() {
        assertThrows(ApiException.class, () -> service.create(req(roomId, "10:15", "10:45")));
        assertThrows(ApiException.class, () -> service.create(req(roomId, "09:00", "12:00")));
    }

    @Test
    void allowsAdjacentIntervals() {
        service.create(req(roomId, "11:00", "12:00"));
        service.create(req(roomId, "09:00", "10:00"));
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThrows(ApiException.class, () -> service.create(req(roomId, "12:00", "12:00")));
    }
}
