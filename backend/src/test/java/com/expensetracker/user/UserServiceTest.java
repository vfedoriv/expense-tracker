package com.expensetracker.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findOrCreateByFakeEmail_whenUserExists_returnsExistingUser() {
        User existing = User.builder()
            .id(1L)
            .provider("fake")
            .providerUserId("test@example.com")
            .email("test@example.com")
            .displayName("test")
            .build();
        when(userRepository.findByProviderAndProviderUserId("fake", "test@example.com"))
            .thenReturn(Optional.of(existing));

        User result = userService.findOrCreateByFakeEmail("test@example.com");

        assertThat(result).isEqualTo(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void findOrCreateByFakeEmail_whenUserNotExists_createsNewUser() {
        String email = "new@example.com";
        User saved = User.builder()
            .id(2L)
            .provider("fake")
            .providerUserId(email)
            .email(email)
            .displayName("new")
            .build();
        when(userRepository.findByProviderAndProviderUserId("fake", email))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(saved);

        User result = userService.findOrCreateByFakeEmail(email);

        assertThat(result).isEqualTo(saved);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void findOrCreateByFakeEmail_setsDisplayNameFromEmailPrefix() {
        String email = "johndoe@example.com";
        when(userRepository.findByProviderAndProviderUserId("fake", email))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.findOrCreateByFakeEmail(email);

        verify(userRepository).save(argThat(user -> "johndoe".equals(user.getDisplayName())));
    }
}
