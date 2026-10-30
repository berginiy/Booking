package com.company.booking.web;

import com.company.booking.config.AppProperties;
import com.company.booking.domain.Employee;
import com.company.booking.dto.Dtos.*;
import com.company.booking.exception.ApiException;
import com.company.booking.security.AuthUser;
import com.company.booking.security.CurrentUser;
import com.company.booking.security.JwtService;
import com.company.booking.service.AuditService;
import com.company.booking.service.EmployeeService;
import com.company.booking.service.LoginAttemptService;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppProperties props;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final EmployeeService employees;
    private final LoginAttemptService attempts;
    private final String adminPasswordHash;

    public AuthController(AppProperties props, PasswordEncoder encoder, JwtService jwt, AuditService audit,
                          EmployeeService employees, LoginAttemptService attempts) {
        this.props = props;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.employees = employees;
        this.attempts = attempts;
        this.adminPasswordHash = encoder.encode(props.admin().password());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) {
        String username = r.username().trim();
        String key = username.toLowerCase(Locale.ROOT);
        if (attempts.isBlocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Слишком много неудачных попыток входа. Повторите через 15 минут.");
        }

        LoginResponse response = username.contains("@") ? employeeLogin(username, r.password()) : adminLogin(username, r.password());
        if (response == null) {
            attempts.failed(key);
            audit.log(username.contains("@") ? "employee:" + key : "anonymous", "LOGIN", "AUTH", null, "FAILED", "Логин: " + username);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Неверный логин или пароль");
        }
        attempts.succeeded(key);
        return response;
    }

    private LoginResponse employeeLogin(String email, String password) {
        Employee e = employees.authenticate(email, password);
        if (e == null) return null;
        audit.log("employee:" + e.getEmail(), "LOGIN", "AUTH", e.getId(), "SUCCESS", null);
        return new LoginResponse(jwt.generate(String.valueOf(e.getId()), "USER"), MeResponse.of(e));
    }

    private LoginResponse adminLogin(String username, String password) {
        boolean ok = props.admin().username().equals(username) && encoder.matches(password, adminPasswordHash);
        if (!ok) return null;
        audit.log(username, "ADMIN_LOGIN", "AUTH", null, "SUCCESS", null);
        return new LoginResponse(jwt.generate(username, "ADMIN"), new MeResponse("ADMIN", username, null, null, false));
    }

    @GetMapping("/me")
    public MeResponse me() {
        AuthUser u = CurrentUser.get();
        if (u == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется вход");
        if (u.admin()) return new MeResponse("ADMIN", u.fullName(), null, null, false);
        return MeResponse.of(employees.get(u.employeeId()));
    }

    @PostMapping("/change-password")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest r) {
        AuthUser u = CurrentUser.get();
        if (u == null || u.employeeId() == null) {
            throw ApiException.badRequest("VALIDATION", "Смена пароля доступна только сотрудникам");
        }
        Employee e = employees.changePassword(u.employeeId(), r.currentPassword(), r.newPassword());
        return new LoginResponse(jwt.generate(String.valueOf(e.getId()), "USER"), MeResponse.of(e));
    }
}
