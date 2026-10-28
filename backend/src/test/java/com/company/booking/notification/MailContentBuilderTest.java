package com.company.booking.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailContentBuilderTest {

    private BookingSnapshot snapshot(String organizerName) {
        return new BookingSnapshot(7L, "Байкал", 3, LocalDate.of(2026, 10, 26), LocalTime.of(10, 0), LocalTime.of(11, 30),
                organizerName, "org@company.test", "+7 900 111-22-33",
                List.of(new Person("Петрова Анна", "petrova@company.test")));
    }

    @Test
    void invitationContainsKeyFacts() {
        MailContentBuilder.Content c = MailContentBuilder.build(BookingNotification.Kind.INVITED, snapshot("Иванов Иван"), "http://site/");
        assertTrue(c.subject().startsWith("Приглашение на встречу: Байкал, 3 этаж, 26.10.2026 10:00"));
        assertTrue(c.text().contains("Иванов Иван приглашает вас"));
        assertTrue(c.text().contains("10:00–11:30"));
        assertTrue(c.html().contains("http://site/my"));
    }

    @Test
    void htmlEscapesUserInput() {
        MailContentBuilder.Content c = MailContentBuilder.build(BookingNotification.Kind.CANCELLED,
                snapshot("<script>alert(1)</script>"), "http://site");
        assertFalse(c.html().contains("<script>"));
        assertTrue(c.html().contains("&lt;script&gt;"));
    }

    @Test
    void calendarAttachmentIsWellFormed() {
        String ics = new String(MailContentBuilder.ics(snapshot("Иванов Иван"), "company.test"), StandardCharsets.UTF_8);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("UID:booking-7@company.test"));
        assertTrue(ics.contains("DTSTART:") && ics.contains("DTEND:"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
    }
}
