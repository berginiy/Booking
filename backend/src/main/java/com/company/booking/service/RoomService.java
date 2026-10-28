package com.company.booking.service;

import com.company.booking.config.AppProperties;
import com.company.booking.domain.Room;
import com.company.booking.dto.Dtos.RoomRequest;
import com.company.booking.exception.ApiException;
import com.company.booking.repository.BookingRepository;
import com.company.booking.repository.RoomRepository;
import com.company.booking.security.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final AuditService audit;
    private final AppProperties props;

    public RoomService(RoomRepository rooms, BookingRepository bookings, AuditService audit, AppProperties props) {
        this.rooms = rooms;
        this.bookings = bookings;
        this.audit = audit;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<Room> listActive(Integer floor) {
        return floor == null ? rooms.findByActiveTrueOrderByFloorAscNameAsc()
                : rooms.findByActiveTrueAndFloorOrderByNameAsc(floor);
    }

    @Transactional(readOnly = true)
    public List<Room> listAll() {
        return rooms.findAllByOrderByFloorAscNameAsc();
    }

    @Transactional
    public Room create(RoomRequest r) {
        Room room = new Room();
        apply(room, r);
        room.setActive(r.active() == null || r.active());
        Room saved = rooms.save(room);
        audit.log(CurrentUser.adminName(), "ROOM_CREATE", "ROOM", saved.getId(), "SUCCESS", saved.getName());
        return saved;
    }

    @Transactional
    public Room update(Long id, RoomRequest r) {
        Room room = get(id);
        apply(room, r);
        if (r.active() != null) room.setActive(r.active());
        audit.log(CurrentUser.adminName(), "ROOM_UPDATE", "ROOM", id, "SUCCESS", room.getName());
        return room;
    }

    @Transactional
    public Room setActive(Long id, boolean active) {
        Room room = get(id);
        room.setActive(active);
        audit.log(CurrentUser.adminName(), active ? "ROOM_ENABLE" : "ROOM_DISABLE", "ROOM", id, "SUCCESS", room.getName());
        return room;
    }

    @Transactional
    public void delete(Long id) {
        Room room = get(id);
        if (bookings.existsByRoomId(id)) {
            audit.log(CurrentUser.adminName(), "ROOM_DELETE", "ROOM", id, "FAILED", "У зала есть бронирования");
            throw ApiException.conflict("ROOM_HAS_BOOKINGS",
                    "Нельзя удалить зал с бронированиями — деактивируйте его");
        }
        rooms.delete(room);
        audit.log(CurrentUser.adminName(), "ROOM_DELETE", "ROOM", id, "SUCCESS", room.getName());
    }

    public Room get(Long id) {
        return rooms.findById(id).orElseThrow(() -> ApiException.notFound("Зал не найден"));
    }

    private void apply(Room room, RoomRequest r) {
        if (r.floor() < 1 || r.floor() > props.floors()) {
            throw ApiException.badRequest("VALIDATION", "Этаж должен быть от 1 до " + props.floors());
        }
        room.setName(r.name().trim());
        room.setFloor(r.floor());
        room.setCapacity(r.capacity());
        room.setDescription(r.description());
        List<String> eq = new ArrayList<>();
        if (r.equipment() != null) {
            r.equipment().stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(eq::add);
        }
        room.getEquipment().clear();
        room.getEquipment().addAll(eq);
    }
}
