package com.company.booking.config;

import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        int floors,
        String workDayStart,
        String workDayEnd,
        String corporateEmailDomain,
        boolean seedDemoData,
        String corsOrigin,
        Admin admin,
        Jwt jwt,
        Graph graph,
        Mail mail) {

    public record Admin(String username, String password) {}

    public record Jwt(String secret, long expirationMinutes) {}

    public record Mail(boolean enabled, String from, String fromName, String siteUrl) {}

    public record Graph(
            boolean enabled,
            String baseUrl,
            String authority,
            String tenantId,
            String clientId,
            String clientSecret,
            String scope,
            int pageSize) {}

    public LocalTime workStart() { return LocalTime.parse(workDayStart); }

    public LocalTime workEnd() { return LocalTime.parse(workDayEnd); }
}
