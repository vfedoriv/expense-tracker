package io.github.vfedoriv.expensetracker.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserContext userContext;

    @GetMapping("/me")
    public UserResponse me() {
        AppUser user = userContext.getCurrentUser();
        return new UserResponse(user.id(), user.provider(), user.email(), user.displayName());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    public record UserResponse(Long id, String provider, String email, String displayName) {
    }
}
