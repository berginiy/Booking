package com.company.booking.repository;

import com.company.booking.domain.Booking;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    /**
     * Пересечение интервалов [start, end): существующая.start < новая.end И существующая.end > новая.start.
     * Покрывает все 4 случая из ТЗ; бронь «встык» (11:00 конец / 11:00 начало) пересечением не считается.
     */
    @Query("""
            select count(b) > 0 from Booking b
            where b.room.id = :roomId
              and b.date = :date
              and b.status = com.company.booking.domain.BookingStatus.ACTIVE
              and b.startTime < :end
              and b.endTime > :start
              and b.id <> :excludeId
            """)
    boolean existsOverlap(@Param("roomId") Long roomId,
                          @Param("date") LocalDate date,
                          @Param("start") LocalTime start,
                          @Param("end") LocalTime end,
                          @Param("excludeId") Long excludeId);

    boolean existsByRoomId(Long roomId);

    @Query("""
            select distinct b from Booking b
            left join b.attendees a
            where b.employee.id = :employeeId or a.id = :employeeId
            order by b.date desc, b.startTime desc
            """)
    List<Booking> findMine(@Param("employeeId") Long employeeId);
}
