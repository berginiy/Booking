package com.company.booking.security;

import com.company.booking.repository.EmployeeRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Не является @Component намеренно — подключается только в цепочке Spring Security.
 * Для сотрудников статус (активен / должен сменить пароль) берётся из БД на каждый запрос,
 * поэтому отключение сотрудника и сброс пароля действуют немедленно.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final EmployeeRepository employees;

    public JwtAuthFilter(JwtService jwtService, EmployeeRepository employees) {
        this.jwtService = jwtService;
        this.employees = employees;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtService.parse(header.substring(7));
            if (claims != null) authenticate(claims);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        String role = claims.get("role", String.class);
        if ("ADMIN".equals(role)) {
            set(new AuthUser(null, null, claims.getSubject(), true), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        } else if ("USER".equals(role)) {
            try {
                Long id = Long.valueOf(claims.getSubject());
                employees.findById(id).filter(e -> e.isActive()).ifPresent(e -> set(
                        new AuthUser(e.getId(), e.getEmail(), e.getFullName(), false),
                        List.of(new SimpleGrantedAuthority(e.isMustChangePassword() ? "ROLE_PWD_CHANGE" : "ROLE_USER"))));
            } catch (NumberFormatException ignored) {
                // неверный subject — остаёмся анонимом
            }
        }
    }

    private void set(AuthUser user, Collection<? extends GrantedAuthority> authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }
}
