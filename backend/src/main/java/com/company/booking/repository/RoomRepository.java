package com.company.booking.repository;

import com.company.booking.domain.Room;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByOrderByFloorAscNameAsc();

    List<Room> findByActiveTrueOrderByFloorAscNameAsc();

    List<Room> findByActiveTrueAndFloorOrderByNameAsc(int floor);

    /** Блокировка строки зала на время транзакции — сериализует конкурентные бронирования одного зала. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
