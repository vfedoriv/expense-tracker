package io.github.vfedoriv.expensetracker.auth;

public record AppUser(Long id, String provider, String providerId, String email, String displayName) {
}
