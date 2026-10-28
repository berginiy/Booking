package com.company.booking.security;

import java.security.Principal;

/** Текущий вошедший пользователь: сотрудник (employeeId != null) либо администратор. */
public record AuthUser(Long employeeId, String email, String fullName, boolean admin) implements Principal {
    @Override
    public String getName() {
        return admin ? fullName : email;
    }
}
