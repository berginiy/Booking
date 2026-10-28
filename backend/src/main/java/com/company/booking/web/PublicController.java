package com.company.booking.web;

import com.company.booking.config.AppProperties;
import com.company.booking.domain.BookingStatus;
import com.company.booking.dto.Dtos.*;
import com.company.booking.service.BookingService;
import com.company.booking.service.EmployeeService;
import com.company.booking.service.RoomService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Эндпоинты для сотрудников. Требуют входа (кроме /config) — см. SecurityConfig. */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final RoomService roomService;
    private final BookingService bookingService;
    private final EmployeeService employeeService;
    private final AppProperties props;

    public PublicController(RoomService roomService, BookingService bookingService, EmployeeService employeeService,
                            AppProperties props) {
        this.roomService = roomService;
        this.bookingService = bookingService;
        this.employeeService = employeeService;
        this.props = props;
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        return new ConfigResponse(IntStream.rangeClosed(1, props.floors()).boxed().toList(),
                props.workDayStart(), props.workDayEnd(), props.corporateEmailDomain(),
                props.mail() != null && props.mail().enabled());
    }

    /** Только активные залы — неактивные пользователю не предлагаются. */
    @GetMapping("/rooms")
    public List<RoomResponse> rooms(@RequestParam(required = false) Integer floor) {
        return roomService.listActive(floor).stream().map(RoomResponse::of).toList();
    }

    /** Активные брони. Без даты — все предстоящие. */
    @GetMapping("/bookings")
    public List<BookingResponse> bookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) Long roomId) {
        return bookingService.search(date, floor, roomId, BookingStatus.ACTIVE, true)
                .stream().map(BookingResponse::of).toList();
    }

    /** Активные сотрудники — для выбора участников брони. */
    @GetMapping("/employees")
    public List<PersonResponse> employees() {
        return employeeService.listActive().stream().map(PersonResponse::of).toList();
    }

    /** Брони текущего сотрудника, включая отменённые и прошедшие. */
    @GetMapping("/bookings/mine")
    public List<BookingResponse> mine() {
        return bookingService.mine().stream().map(BookingResponse::of).toList();
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse booking(@PathVariable Long id) {
        return BookingResponse.of(bookingService.get(id));
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody BookingRequest r) {
        return BookingResponse.of(bookingService.create(r));
    }

    /** Изменение: владелец брони или администратор. */
    @PutMapping("/bookings/{id}")
    public BookingResponse update(@PathVariable Long id, @Valid @RequestBody BookingRequest r) {
        return BookingResponse.of(bookingService.update(id, r));
    }

    /** Отмена (мягкая): статус → CANCELLED. Владелец брони или администратор. */
    @DeleteMapping("/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        bookingService.cancel(id);
    }
}
