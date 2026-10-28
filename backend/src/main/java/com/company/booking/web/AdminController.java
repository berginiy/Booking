package com.company.booking.web;

import com.company.booking.domain.BookingStatus;
import com.company.booking.dto.Dtos.*;
import com.company.booking.service.AuditService;
import com.company.booking.directory.DirectorySyncService;
import com.company.booking.exception.ApiException;
import com.company.booking.notification.NotificationService;
import com.company.booking.service.BookingService;
import com.company.booking.service.EmployeeService;
import com.company.booking.service.RoomService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

/** Административный интерфейс. Доступ — только роль ADMIN (см. SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RoomService roomService;
    private final BookingService bookingService;
    private final AuditService auditService;
    private final EmployeeService employeeService;
    private final DirectorySyncService syncService;
    private final NotificationService notificationService;

    public AdminController(RoomService roomService, BookingService bookingService, AuditService auditService,
                           EmployeeService employeeService, DirectorySyncService syncService,
                           NotificationService notificationService) {
        this.roomService = roomService;
        this.bookingService = bookingService;
        this.auditService = auditService;
        this.employeeService = employeeService;
        this.syncService = syncService;
        this.notificationService = notificationService;
    }

    // ---- Залы ----

    @GetMapping("/rooms")
    public List<RoomResponse> rooms() {
        return roomService.listAll().stream().map(RoomResponse::of).toList();
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@Valid @RequestBody RoomRequest r) {
        return RoomResponse.of(roomService.create(r));
    }

    @PutMapping("/rooms/{id}")
    public RoomResponse updateRoom(@PathVariable Long id, @Valid @RequestBody RoomRequest r) {
        return RoomResponse.of(roomService.update(id, r));
    }

    @PatchMapping("/rooms/{id}/status")
    public RoomResponse setStatus(@PathVariable Long id, @Valid @RequestBody RoomStatusRequest r) {
        return RoomResponse.of(roomService.setActive(id, r.active()));
    }

    @DeleteMapping("/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@PathVariable Long id) {
        roomService.delete(id);
    }

    // ---- Бронирования (все, включая отменённые) ----

    @GetMapping("/bookings")
    public List<BookingResponse> bookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) BookingStatus status) {
        return bookingService.search(date, floor, roomId, status, false).stream().map(BookingResponse::of).toList();
    }

    // ---- Сотрудники ----

    @GetMapping("/employees")
    public List<EmployeeResponse> employees() {
        return employeeService.list().stream().map(EmployeeResponse::of).toList();
    }

    /** Создание одного сотрудника; временный пароль возвращается один раз. */
    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public Credential createEmployee(@Valid @RequestBody EmployeeRequest r) {
        var c = employeeService.create(r);
        return new Credential(c.employee().getFullName(), c.employee().getEmail(), c.tempPassword());
    }

    @PutMapping("/employees/{id}")
    public EmployeeResponse updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequest r) {
        return EmployeeResponse.of(employeeService.update(id, r));
    }

    @PostMapping("/employees/{id}/reset-password")
    public Credential resetPassword(@PathVariable Long id) {
        var c = employeeService.resetPassword(id);
        return new Credential(c.employee().getFullName(), c.employee().getEmail(), c.tempPassword());
    }

    /** Массовый импорт из CSV: ФИО; email; телефон. */
    @PostMapping(value = "/employees/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importEmployees(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw ApiException.badRequest("VALIDATION", "Файл пуст");
        return employeeService.importCsv(file.getBytes());
    }

    @PostMapping("/employees/sync")
    public SyncResult syncEmployees() {
        return syncService.sync();
    }

    // ---- Почта ----

    @GetMapping("/mail")
    public MailStatus mailStatus() {
        return notificationService.status();
    }

    @PostMapping("/mail/test")
    public java.util.Map<String, String> mailTest(@Valid @RequestBody MailTestRequest r) {
        notificationService.sendTest(r.to());
        return java.util.Map.of("message", "Письмо отправлено на " + r.to());
    }

    // ---- Журнал ----

    @GetMapping("/audit")
    public List<AuditResponse> audit() {
        return auditService.latest().stream()
                .map(a -> new AuditResponse(a.getId(), a.getActor(), a.getOccurredAt(), a.getOperation(),
                        a.getEntityType(), a.getEntityId(), a.getResult(), a.getDetails()))
                .toList();
    }
}
