package com.company.booking.notification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record BookingSnapshot(
        Long id,
        String roomName,
        int floor,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String organizerName,
        String organizerEmail,
        String organizerPhone,
        List<Person> attendees) {

    public boolean sameSlot(BookingSnapshot o) {
        return roomName.equals(o.roomName) && floor == o.floor && date.equals(o.date)
                && startTime.equals(o.startTime) && endTime.equals(o.endTime);
    }
}
