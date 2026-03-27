package com.expensetracker.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public User findOrCreateByFakeEmail(String email) {
        return userRepository.findByProviderAndProviderUserId("fake", email)
            .orElseGet(() -> userRepository.save(
                User.builder()
                    .provider("fake")
                    .providerUserId(email)
                    .email(email)
                    .displayName(email.contains("@") ? email.split("@")[0] : email)
                    .build()
            ));
    }

    public User findOrCreateByOAuth2(String provider, String providerUserId, String email, String displayName, String avatarUrl) {
        return userRepository.findByProviderAndProviderUserId(provider, providerUserId)
            .map(existing -> {
                // Update profile on each login
                existing.setEmail(email);
                existing.setDisplayName(displayName != null ? displayName : existing.getDisplayName());
                existing.setAvatarUrl(avatarUrl);
                return userRepository.save(existing);
            })
            .orElseGet(() -> userRepository.save(
                User.builder()
                    .provider(provider)
                    .providerUserId(providerUserId)
                    .email(email)
                    .displayName(displayName != null ? displayName : providerUserId)
                    .avatarUrl(avatarUrl)
                    .build()
            ));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }
}
