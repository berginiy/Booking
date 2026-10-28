package com.company.booking.notification;

import java.util.List;

public record BookingNotification(Kind kind, BookingSnapshot booking, List<Person> recipients) {

    public enum Kind { INVITED, UPDATED, REMOVED, CANCELLED }
}
