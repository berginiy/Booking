package com.company.booking.dto;

import com.company.booking.domain.Booking;
import com.company.booking.domain.Employee;
import com.company.booking.domain.Room;
import com.company.booking.security.AuthUser;
import com.company.booking.security.CurrentUser;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    private static final String PHONE_RE = "^\\+?[0-9][0-9()\\-\\s]{6,19}$";

    // ---------- Запросы ----------

    public record LoginRequest(
            @NotBlank(message = "Укажите email или логин") String username,
            @NotBlank(message = "Укажите пароль") String password) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Укажите текущий пароль") String currentPassword,
            @NotBlank(message = "Укажите новый пароль")
            @Size(min = 8, max = 72, message = "Пароль должен быть от 8 до 72 символов") String newPassword) {}

    public record EmployeeRequest(
            @NotBlank(message = "Укажите ФИО") @Size(max = 150, message = "Не более 150 символов") String fullName,
            @NotBlank(message = "Укажите email") @Email(message = "Некорректный email") @Size(max = 150) String email,
            @Pattern(regexp = PHONE_RE, message = "Некорректный номер телефона") String phone,
            Boolean active) {}

    public record RoomRequest(
            @NotBlank(message = "Укажите название зала") @Size(max = 100, message = "Не более 100 символов") String name,
            @NotNull(message = "Укажите этаж") Integer floor,
            @NotNull(message = "Укажите вместимость") @Min(value = 1, message = "Вместимость должна быть не менее 1") Integer capacity,
            @Size(max = 1000, message = "Не более 1000 символов") String description,
            List<@Size(max = 100, message = "Не более 100 символов") String> equipment,
            Boolean active) {}

    public record RoomStatusRequest(@NotNull Boolean active) {}

    public record BookingRequest(
            @NotBlank(message = "Укажите ФИО") @Size(max = 150, message = "Не более 150 символов") String fullName,
            @Pattern(regexp = PHONE_RE, message = "Некорректный номер телефона") String phone,
            @NotBlank(message = "Укажите email") @Email(message = "Некорректный email") @Size(max = 150) String email,
            @NotNull(message = "Выберите зал") Long roomId,
            @NotNull(message = "Укажите дату") LocalDate date,
            @NotNull(message = "Укажите время начала") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @NotNull(message = "Укажите время окончания") @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            @Size(max = 100, message = "Не более 100 участников") List<Long> attendeeIds) {}

    // ---------- Ответы ----------

    public record RoomResponse(Long id, String name, int floor, int capacity, String description,
                               List<String> equipment, boolean active) {
        public static RoomResponse of(Room r) {
            return new RoomResponse(r.getId(), r.getName(), r.getFloor(), r.getCapacity(), r.getDescription(),
                    List.copyOf(r.getEquipment()), r.isActive());
        }
    }

    public record PersonResponse(Long id, String fullName, String email) {
        public static PersonResponse of(Employee e) {
            return new PersonResponse(e.getId(), e.getFullName(), e.getEmail());
        }
    }

    public record BookingResponse(
            Long id, String fullName, String phone, String email,
            Long roomId, String roomName, int floor,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            String status, Instant createdAt, List<PersonResponse> attendees, boolean organizer) {
        public static BookingResponse of(Booking b) {
            AuthUser u = CurrentUser.get();
            boolean organizer = u != null && (u.admin()
                    || (u.employeeId() != null && b.getEmployee() != null && u.employeeId().equals(b.getEmployee().getId())));
            List<PersonResponse> attendees = b.getAttendees().stream()
                    .sorted(Comparator.comparing(Employee::getFullName))
                    .map(PersonResponse::of)
                    .toList();
            return new BookingResponse(b.getId(), b.getFullName(), b.getPhone(), b.getEmail(),
                    b.getRoom().getId(), b.getRoom().getName(), b.getRoom().getFloor(),
                    b.getDate(), b.getStartTime(), b.getEndTime(), b.getStatus().name(), b.getCreatedAt(),
                    attendees, organizer);
        }
    }

    /** Профиль вошедшего пользователя. role: ADMIN | USER. */
    public record MeResponse(String role, String fullName, String email, String phone, boolean mustChangePassword) {
        public static MeResponse of(Employee e) {
            return new MeResponse("USER", e.getFullName(), e.getEmail(), e.getPhone(), e.isMustChangePassword());
        }
    }

    public record LoginResponse(String token, MeResponse user) {}

    public record EmployeeResponse(Long id, String fullName, String email, String phone, boolean active,
                                   boolean mustChangePassword, Instant createdAt) {
        public static EmployeeResponse of(Employee e) {
            return new EmployeeResponse(e.getId(), e.getFullName(), e.getEmail(), e.getPhone(), e.isActive(),
                    e.isMustChangePassword(), e.getCreatedAt());
        }
    }

    /** Временный пароль показывается администратору один раз. */
    public record Credential(String fullName, String email, String tempPassword) {}

    public record ImportError(int line, String message) {}

    public record ImportResult(int created, int updated, int skipped, List<ImportError> errors, List<Credential> credentials) {}

    public record SyncResult(int created, int updated, int disabled, int skipped,
                             List<ImportError> errors, List<Credential> credentials) {}

    public record ConfigResponse(List<Integer> floors, String workDayStart, String workDayEnd, String corporateEmailDomain,
                                 boolean mailEnabled) {}

    public record MailStatus(boolean enabled, String from, String host, int port, String siteUrl) {}

    public record MailTestRequest(@NotBlank(message = "Укажите адрес") @Email(message = "Некорректный email") String to) {}

    public record AuditResponse(Long id, String actor, Instant occurredAt, String operation,
                                String entityType, Long entityId, String result, String details) {}

    public record ErrorResponse(String code, String message, java.util.Map<String, String> fieldErrors) {}
}
