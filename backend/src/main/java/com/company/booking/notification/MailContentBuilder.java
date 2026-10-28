package com.company.booking.notification;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MailContentBuilder {

    public record Content(String subject, String text, String html) {}

    private record Texts(String subjectPrefix, String title, String intro, String accent) {}

    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter ICS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private MailContentBuilder() {}

    public static Content build(BookingNotification.Kind kind, BookingSnapshot b, String siteUrl) {
        Texts t = switch (kind) {
            case INVITED -> new Texts("Приглашение на встречу", "Вас пригласили на встречу",
                    b.organizerName() + " приглашает вас на встречу.", "#3b5bdb");
            case UPDATED -> new Texts("Встреча изменена", "Параметры встречи изменены",
                    "Актуальные данные встречи указаны ниже.", "#b45309");
            case REMOVED -> new Texts("Вы исключены из встречи", "Вы больше не участвуете во встрече",
                    "Организатор убрал вас из списка участников.", "#475569");
            case CANCELLED -> new Texts("Встреча отменена", "Встреча отменена",
                    "Бронирование зала отменено.", "#c92a3a");
        };

        String place = b.roomName() + ", " + b.floor() + " этаж";
        String subject = t.subjectPrefix() + ": " + place + ", " + SHORT_DATE.format(b.date()) + " " + b.startTime();
        String organizer = b.organizerName() + " (" + b.organizerEmail()
                + (b.organizerPhone() == null || b.organizerPhone().isBlank() ? "" : ", " + b.organizerPhone()) + ")";
        String attendees = b.attendees().isEmpty() ? "—"
                : b.attendees().stream().map(Person::fullName).collect(Collectors.joining(", "));
        List<String[]> rows = List.of(
                new String[]{"Зал", place},
                new String[]{"Дата", LONG_DATE.format(b.date())},
                new String[]{"Время", b.startTime() + "–" + b.endTime()},
                new String[]{"Организатор", organizer},
                new String[]{"Участники", attendees});
        String link = siteUrl.replaceAll("/+$", "") + "/my";

        StringBuilder text = new StringBuilder(t.title()).append("\n\n").append(t.intro()).append("\n\n");
        rows.forEach(r -> text.append(r[0]).append(": ").append(r[1]).append('\n'));
        text.append("\nМои бронирования: ").append(link).append('\n');

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:'Segoe UI',Arial,sans-serif;max-width:560px;margin:0 auto;color:#0f172a\">")
                .append("<div style=\"border-top:4px solid ").append(t.accent()).append(";padding:20px 0 4px\">")
                .append("<h2 style=\"margin:0 0 8px;font-size:20px\">").append(esc(t.title())).append("</h2>")
                .append("<p style=\"margin:0 0 16px;color:#475569\">").append(esc(t.intro())).append("</p></div>")
                .append("<table style=\"width:100%;border-collapse:collapse\">");
        for (String[] r : rows) {
            html.append("<tr><td style=\"padding:8px 12px 8px 0;color:#8592a6;vertical-align:top;width:120px\">")
                    .append(esc(r[0])).append("</td><td style=\"padding:8px 0;font-weight:600\">")
                    .append(esc(r[1])).append("</td></tr>");
        }
        html.append("</table><p style=\"margin:20px 0\"><a href=\"").append(esc(link))
                .append("\" style=\"background:").append(t.accent())
                .append(";color:#ffffff;padding:10px 18px;border-radius:8px;text-decoration:none\">Мои бронирования</a></p>")
                .append("<p style=\"color:#8592a6;font-size:12px\">Это автоматическое сообщение системы бронирования залов.</p></div>");

        return new Content(subject, text.toString(), html.toString());
    }

    public static byte[] ics(BookingSnapshot b, String domain) {
        ZoneId zone = ZoneId.systemDefault();
        String start = ZonedDateTime.of(b.date(), b.startTime(), zone).withZoneSameInstant(ZoneOffset.UTC).format(ICS_TIME);
        String end = ZonedDateTime.of(b.date(), b.endTime(), zone).withZoneSameInstant(ZoneOffset.UTC).format(ICS_TIME);
        String stamp = ZonedDateTime.now(ZoneOffset.UTC).format(ICS_TIME);
        String place = b.roomName() + ", " + b.floor() + " этаж";
        String names = b.attendees().isEmpty() ? "—"
                : b.attendees().stream().map(Person::fullName).collect(Collectors.joining(", "));
        String description = "Организатор: " + b.organizerName() + "\nУчастники: " + names;

        List<String> lines = new ArrayList<>(List.of(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Conference Booking//RU",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:booking-" + b.id() + "@" + domain,
                "DTSTAMP:" + stamp,
                "SEQUENCE:" + (int) Instant.now().getEpochSecond(),
                "DTSTART:" + start,
                "DTEND:" + end,
                "SUMMARY:" + icsText("Встреча: " + place),
                "LOCATION:" + icsText(place),
                "DESCRIPTION:" + icsText(description),
                "ORGANIZER;CN=\"" + b.organizerName().replace("\"", "") + "\":mailto:" + b.organizerEmail(),
                "STATUS:CONFIRMED",
                "END:VEVENT",
                "END:VCALENDAR"));
        return lines.stream().map(MailContentBuilder::fold)
                .collect(Collectors.joining("\r\n", "", "\r\n"))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String icsText(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    private static String fold(String line) {
        if (line.length() <= 35) return line;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            int end = Math.min(line.length(), i + (i == 0 ? 35 : 34));
            if (i > 0) sb.append("\r\n ");
            sb.append(line, i, end);
            i = end;
        }
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
