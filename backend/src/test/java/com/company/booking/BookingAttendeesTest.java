package com.company.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.company.booking.domain.Booking;
import com.company.booking.domain.Room;
import com.company.booking.dto.Dtos.BookingRequest;
import com.company.booking.dto.Dtos.EmployeeRequest;
import com.company.booking.exception.ApiException;
import com.company.booking.repository.RoomRepository;
import com.company.booking.service.BookingService;
import com.company.booking.service.EmployeeService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendeesdb;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false"})
class BookingAttendeesTest {

    @Autowired BookingService bookings;
    @Autowired RoomRepository rooms;
    @Autowired EmployeeService employees;

    Long roomId;

    @BeforeEach
    void setUp() {
        Room r = new Room();
        r.setName("Малый зал");
        r.setFloor(2);
        r.setCapacity(3);
        roomId = rooms.save(r).getId();
    }

    Long newEmployee(String name) {
        String email = UUID.randomUUID() + "@test.ru";
        return employees.create(new EmployeeRequest(name, email, null, null)).employee().getId();
    }

    BookingRequest req(List<Long> attendeeIds) {
        return new BookingRequest("Организатор Олег", null, "org@company.ru", roomId,
                LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0), attendeeIds);
    }

    @Test
    void savesAttendeesAndCollapsesDuplicates() {
        Long a = newEmployee("Первый Участник");
        Long b = newEmployee("Второй Участник");
        Booking saved = bookings.create(req(List.of(a, b, a)));
        assertEquals(2, bookings.get(saved.getId()).getAttendees().size());
    }

    @Test
    void rejectsMoreThanRoomCapacity() {
        List<Long> three = List.of(newEmployee("Раз Один"), newEmployee("Два Два"), newEmployee("Три Три"));
        ApiException ex = assertThrows(ApiException.class, () -> bookings.create(req(three)));
        assertEquals("CAPACITY", ex.getCode());
    }

    @Test
    void rejectsUnknownAttendee() {
        assertThrows(ApiException.class, () -> bookings.create(req(List.of(999999L))));
    }
}
