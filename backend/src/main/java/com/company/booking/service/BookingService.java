package com.company.booking.service;

import com.company.booking.config.AppProperties;
import com.company.booking.domain.Booking;
import com.company.booking.domain.BookingStatus;
import com.company.booking.domain.Employee;
import com.company.booking.domain.Room;
import com.company.booking.dto.Dtos.BookingRequest;
import com.company.booking.exception.ApiException;
import com.company.booking.notification.BookingNotification;
import com.company.booking.notification.BookingSnapshot;
import com.company.booking.notification.Person;
import com.company.booking.repository.BookingRepository;
import com.company.booking.repository.EmployeeRepository;
import com.company.booking.repository.RoomRepository;
import com.company.booking.security.AuthUser;
import com.company.booking.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AppProperties props;
    private final ApplicationEventPublisher events;

    public BookingService(BookingRepository bookings, RoomRepository rooms, EmployeeRepository employees,
                          AuditService audit, AppProperties props, ApplicationEventPublisher events) {
        this.bookings = bookings;
        this.rooms = rooms;
        this.employees = employees;
        this.audit = audit;
        this.props = props;
        this.events = events;
    }

    // ---------------------------------------------------------------- чтение

    @Transactional(readOnly = true)
    public List<Booking> search(LocalDate date, Integer floor, Long roomId, BookingStatus status, boolean upcomingOnly) {
        Specification<Booking> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (date != null) p.add(cb.equal(root.get("date"), date));
            else if (upcomingOnly) p.add(cb.greaterThanOrEqualTo(root.get("date"), LocalDate.now()));
            if (floor != null) p.add(cb.equal(root.get("room").get("floor"), floor));
            if (roomId != null) p.add(cb.equal(root.get("room").get("id"), roomId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return bookings.findAll(spec, Sort.by("date", "startTime"));
    }

    /** Брони, где текущий сотрудник — организатор или приглашённый участник; новые сверху. */
    @Transactional(readOnly = true)
    public List<Booking> mine() {
        AuthUser u = CurrentUser.get();
        if (u == null || u.employeeId() == null) return List.of();
        return bookings.findMine(u.employeeId());
    }

    @Transactional(readOnly = true)
    public Booking get(Long id) {
        return bookings.findById(id).orElseThrow(() -> ApiException.notFound("Бронирование не найдено"));
    }

    // ---------------------------------------------------------------- создание

    @Transactional
    public Booking create(BookingRequest in) {
        AuthUser u = CurrentUser.get();
        Employee emp = null;
        if (u != null && !u.admin()) {
            emp = employees.findById(u.employeeId()).orElseThrow(() -> ApiException.forbidden("Сотрудник не найден"));
        }
        BookingRequest r = emp != null ? withIdentity(in, emp) : in;
        String actor = actor(r.email());
        try {
            validate(r, !CurrentUser.isAdmin());
            Room room = lockRoom(r.roomId(), true);
            Set<Employee> attendees = resolveAttendees(r.attendeeIds(), emp != null ? emp.getId() : null, List.of());
            checkCapacity(room, attendees);
            ensureFree(room.getId(), r, -1L);

            Booking b = new Booking();
            fill(b, r, room);
            b.setEmployee(emp);
            b.setAttendees(attendees);
            b.setStatus(BookingStatus.ACTIVE);
            b = bookings.save(b);
            audit.log(actor, "BOOKING_CREATE", "BOOKING", b.getId(), "SUCCESS", describe(b));
            emit(BookingNotification.Kind.INVITED, snapshot(b), attendees);
            return b;
        } catch (ApiException e) {
            audit.log(actor, "BOOKING_CREATE", "BOOKING", null, "FAILED", e.getMessage());
            throw e;
        }
    }

    // ---------------------------------------------------------------- изменение

    @Transactional
    public Booking update(Long id, BookingRequest in) {
        Booking b = get(id);
        boolean admin = CurrentUser.isAdmin();
        String actor = actor(b.getEmail());
        try {
            authorize(b);
            ensureActive(b);
            if (!admin && isPast(b)) {
                throw ApiException.badRequest("BOOKING_PAST", "Нельзя изменить завершённое бронирование");
            }
            BookingRequest r = !admin && b.getEmployee() != null ? withIdentity(in, b.getEmployee()) : in;
            validate(r, !admin);

            BookingSnapshot before = snapshot(b);
            Set<Long> beforeIds = ids(b.getAttendees());
            List<Employee> beforeList = new ArrayList<>(b.getAttendees());

            boolean sameRoom = r.roomId().equals(b.getRoom().getId());
            Room room = lockRoom(r.roomId(), !sameRoom);

            Long organizerId = b.getEmployee() != null ? b.getEmployee().getId() : null;
            Set<Employee> attendees = r.attendeeIds() == null
                    ? new LinkedHashSet<>(b.getAttendees())
                    : resolveAttendees(r.attendeeIds(), organizerId, b.getAttendees());
            checkCapacity(room, attendees);
            ensureFree(room.getId(), r, b.getId());

            fill(b, r, room);
            if (r.attendeeIds() != null) {
                b.getAttendees().clear();
                b.getAttendees().addAll(attendees);
            }
            audit.log(actor, "BOOKING_UPDATE", "BOOKING", b.getId(), "SUCCESS", describe(b));

            BookingSnapshot after = snapshot(b);
            boolean slotChanged = !before.sameSlot(after);
            Set<Long> afterIds = ids(b.getAttendees());
            List<Employee> added = b.getAttendees().stream().filter(e -> !beforeIds.contains(e.getId())).toList();
            List<Employee> removed = beforeList.stream().filter(e -> !afterIds.contains(e.getId())).toList();
            List<Employee> kept = b.getAttendees().stream().filter(e -> beforeIds.contains(e.getId())).toList();
            emit(BookingNotification.Kind.INVITED, after, added);
            emit(BookingNotification.Kind.REMOVED, before, removed);
            if (slotChanged) emit(BookingNotification.Kind.UPDATED, after, kept);
            if (admin && b.getEmployee() != null && (slotChanged || !added.isEmpty() || !removed.isEmpty())) {
                emit(BookingNotification.Kind.UPDATED, after, List.of(b.getEmployee()));
            }
            return b;
        } catch (ApiException e) {
            audit.log(actor, "BOOKING_UPDATE", "BOOKING", id, "FAILED", e.getMessage());
            throw e;
        }
    }

    // ---------------------------------------------------------------- отмена

    @Transactional
    public void cancel(Long id) {
        Booking b = get(id);
        String actor = actor(b.getEmail());
        try {
            authorize(b);
            ensureActive(b);
            if (!CurrentUser.isAdmin() && isPast(b)) {
                throw ApiException.badRequest("BOOKING_PAST", "Нельзя отменить завершённое бронирование");
            }
            b.setStatus(BookingStatus.CANCELLED);
            audit.log(actor, "BOOKING_CANCEL", "BOOKING", id, "SUCCESS", describe(b));

            BookingSnapshot snap = snapshot(b);
            emit(BookingNotification.Kind.CANCELLED, snap, b.getAttendees());
            if (CurrentUser.isAdmin() && b.getEmployee() != null) {
                emit(BookingNotification.Kind.CANCELLED, snap, List.of(b.getEmployee()));
            }
        } catch (ApiException e) {
            audit.log(actor, "BOOKING_CANCEL", "BOOKING", id, "FAILED", e.getMessage());
            throw e;
        }
    }

    // ---------------------------------------------------------------- уведомления

    private BookingSnapshot snapshot(Booking b) {
        List<Person> attendees = b.getAttendees().stream()
                .sorted(Comparator.comparing(Employee::getFullName))
                .map(e -> new Person(e.getFullName(), e.getEmail()))
                .toList();
        return new BookingSnapshot(b.getId(), b.getRoom().getName(), b.getRoom().getFloor(), b.getDate(),
                b.getStartTime(), b.getEndTime(), b.getFullName(), b.getEmail(), b.getPhone(), attendees);
    }

    /** Письмо уйдёт только после успешного коммита транзакции; отключённым сотрудникам не отправляется. */
    private void emit(BookingNotification.Kind kind, BookingSnapshot snapshot, Collection<Employee> to) {
        List<Person> recipients = to.stream()
                .filter(Employee::isActive)
                .filter(e -> e.getEmail() != null && !e.getEmail().isBlank())
                .map(e -> new Person(e.getFullName(), e.getEmail()))
                .toList();
        if (!recipients.isEmpty()) events.publishEvent(new BookingNotification(kind, snapshot, recipients));
    }

    private static Set<Long> ids(Collection<Employee> employees) {
        return employees.stream().map(Employee::getId).collect(Collectors.toSet());
    }

    // ---------------------------------------------------------------- helpers

    private String actor(String email) {
        AuthUser u = CurrentUser.get();
        if (u == null) return "user:" + email.toLowerCase(Locale.ROOT);
        return u.admin() ? u.fullName() : "employee:" + u.email();
    }

    private BookingRequest withIdentity(BookingRequest r, Employee e) {
        return new BookingRequest(e.getFullName(), e.getPhone(), e.getEmail(),
                r.roomId(), r.date(), r.startTime(), r.endTime(), r.attendeeIds());
    }

    /**
     * Участники: организатор в список не попадает, дубли схлопываются, отключённых сотрудников
     * добавить нельзя (кроме тех, кто уже был в этой брони).
     */
    private Set<Employee> resolveAttendees(List<Long> ids, Long organizerId, Collection<Employee> current) {
        Set<Employee> result = new LinkedHashSet<>();
        if (ids == null || ids.isEmpty()) return result;

        Set<Long> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (organizerId != null) unique.remove(organizerId);
        if (unique.isEmpty()) return result;

        List<Employee> found = employees.findAllById(unique);
        if (found.size() != unique.size()) {
            throw ApiException.badRequest("VALIDATION", "Один из выбранных участников не найден");
        }
        Set<Long> already = ids(current);
        for (Employee e : found) {
            if (!e.isActive() && !already.contains(e.getId())) {
                throw ApiException.badRequest("VALIDATION", "Сотрудник отключён и не может быть участником: " + e.getFullName());
            }
            result.add(e);
        }
        return result;
    }

    private void checkCapacity(Room room, Set<Employee> attendees) {
        int people = attendees.size() + 1;
        if (people > room.getCapacity()) {
            throw ApiException.badRequest("CAPACITY", "Зал «" + room.getName() + "» вмещает " + room.getCapacity()
                    + " чел., а участников вместе с организатором " + people);
        }
    }

    private void authorize(Booking b) {
        if (CurrentUser.isAdmin()) return;
        AuthUser u = CurrentUser.get();
        boolean owner = u != null && u.employeeId() != null && b.getEmployee() != null
                && u.employeeId().equals(b.getEmployee().getId());
        if (!owner) throw ApiException.forbidden("Нет прав на изменение этого бронирования");
    }

    private void ensureActive(Booking b) {
        if (b.getStatus() != BookingStatus.ACTIVE) {
            throw ApiException.conflict("BOOKING_CANCELLED", "Бронирование уже отменено");
        }
    }

    private boolean isPast(Booking b) {
        return LocalDateTime.of(b.getDate(), b.getEndTime()).isBefore(LocalDateTime.now());
    }

    private Room lockRoom(Long roomId, boolean requireActive) {
        Room room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> ApiException.notFound("Зал не найден"));
        if (requireActive && !room.isActive()) {
            throw ApiException.badRequest("ROOM_INACTIVE", "Зал недоступен для бронирования");
        }
        return room;
    }

    private void ensureFree(Long roomId, BookingRequest r, Long excludeId) {
        if (bookings.existsOverlap(roomId, r.date(), r.startTime(), r.endTime(), excludeId)) {
            throw ApiException.conflict("ROOM_BUSY", "Зал занят в выбранное время");
        }
    }

    private void validate(BookingRequest r, boolean checkPast) {
        if (!r.endTime().isAfter(r.startTime())) {
            throw ApiException.badRequest("VALIDATION", "Время окончания должно быть позже времени начала");
        }
        if (r.startTime().isBefore(props.workStart()) || r.endTime().isAfter(props.workEnd())) {
            throw ApiException.badRequest("VALIDATION",
                    "Бронирование возможно с " + props.workDayStart() + " до " + props.workDayEnd());
        }
        if (checkPast && LocalDateTime.of(r.date(), r.startTime()).isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("VALIDATION", "Нельзя бронировать на прошедшее время");
        }
        String domain = props.corporateEmailDomain();
        if (domain != null && !domain.isBlank()
                && !r.email().toLowerCase(Locale.ROOT).endsWith("@" + domain.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("VALIDATION", "Укажите корпоративную почту (@" + domain + ")");
        }
    }

    private void fill(Booking b, BookingRequest r, Room room) {
        b.setFullName(r.fullName().trim());
        b.setPhone(r.phone() == null || r.phone().isBlank() ? null : r.phone().trim());
        b.setEmail(r.email().trim());
        b.setRoom(room);
        b.setDate(r.date());
        b.setStartTime(r.startTime());
        b.setEndTime(r.endTime());
    }

    private String describe(Booking b) {
        return "%s, этаж %d, %s %s–%s, %s, участников: %d".formatted(b.getRoom().getName(), b.getRoom().getFloor(),
                b.getDate(), b.getStartTime(), b.getEndTime(), b.getFullName(), b.getAttendees().size());
    }
}
