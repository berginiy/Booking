package com.company.booking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    /** null — если запрос анонимный. */
    public static AuthUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthUser u ? u : null;
    }

    public static boolean isAdmin() {
        AuthUser u = get();
        return u != null && u.admin();
    }

    /** Логин администратора либо null. */
    public static String adminName() {
        AuthUser u = get();
        return u != null && u.admin() ? u.fullName() : null;
    }
}
