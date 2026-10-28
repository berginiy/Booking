package com.company.booking.directory;

import com.company.booking.config.AppProperties;
import com.company.booking.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GraphDirectoryClient {

    private final AppProperties.Graph cfg;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public GraphDirectoryClient(AppProperties props, ObjectMapper mapper) {
        this.cfg = props.graph();
        this.mapper = mapper;
    }

    public boolean isEnabled() {
        return cfg != null && cfg.enabled();
    }

    public List<DirectoryUser> fetchUsers() {
        if (!isEnabled()) {
            throw ApiException.badRequest("DIRECTORY_DISABLED", "Синхронизация с каталогом выключена: app.graph.enabled=false");
        }
        List<DirectoryUser> users = new ArrayList<>();
        String url = cfg.baseUrl()
                + "/users?$select=id,displayName,mail,userPrincipalName,mobilePhone,businessPhones,accountEnabled&$top="
                + cfg.pageSize();
        for (int guard = 0; url != null && guard < 1000; guard++) {
            JsonNode page = getJson(url);
            for (JsonNode u : page.path("value")) users.add(toUser(u));
            url = page.hasNonNull("@odata.nextLink") ? page.get("@odata.nextLink").asText() : null;
        }
        return users;
    }

    private DirectoryUser toUser(JsonNode u) {
        String mail = text(u, "mail");
        if (mail.isBlank()) mail = text(u, "userPrincipalName");
        String phone = text(u, "mobilePhone");
        JsonNode business = u.path("businessPhones");
        if (phone.isBlank() && business.isArray() && business.size() > 0) {
            phone = business.get(0).asText("").trim();
        }
        boolean enabled = u.path("accountEnabled").asBoolean(true);
        return new DirectoryUser(text(u, "id"), text(u, "displayName"), mail, phone, enabled);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private JsonNode getJson(String url) {
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + accessToken())
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() == 401 && attempt == 0) {
                    resetToken();
                    continue;
                }
                if (res.statusCode() / 100 != 2) {
                    throw directoryError("Каталог вернул ошибку " + res.statusCode());
                }
                return mapper.readTree(res.body());
            }
            throw directoryError("Не удалось авторизоваться в каталоге");
        } catch (IOException e) {
            throw directoryError("Каталог недоступен: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw directoryError("Запрос к каталогу прерван");
        }
    }

    private synchronized String accessToken() throws IOException, InterruptedException {
        if (token != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) return token;
        String body = "grant_type=client_credentials"
                + "&client_id=" + enc(cfg.clientId())
                + "&client_secret=" + enc(cfg.clientSecret())
                + "&scope=" + enc(cfg.scope());
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.authority() + "/" + cfg.tenantId() + "/oauth2/v2.0/token"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw directoryError("Не удалось получить токен каталога (код " + res.statusCode()
                    + "). Проверьте tenant-id, client-id и client-secret");
        }
        JsonNode json = mapper.readTree(res.body());
        String value = json.path("access_token").asText("");
        if (value.isBlank()) throw directoryError("Каталог не вернул токен доступа");
        token = value;
        tokenExpiresAt = Instant.now().plusSeconds(json.path("expires_in").asLong(3600));
        return token;
    }

    private synchronized void resetToken() {
        token = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static ApiException directoryError(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "DIRECTORY_ERROR", message);
    }
}
