package com.pricetracker.service.impl;

import com.pricetracker.entity.User;
import com.pricetracker.repository.UserRepository;
import com.pricetracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int MAX_DISPLAY_NAME = 100;

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Nobody is logged in");
        }

        UUID keycloakId = UUID.fromString(jwt.getSubject());
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String displayName = displayName(jwt);

        User user = userRepository.findByKeycloakId(keycloakId).orElse(null);
        if (user != null && Objects.equals(user.getUsername(), username)
                && Objects.equals(user.getEmail(), email) && Objects.equals(user.getDisplayName(), displayName)) {
            return user;
        }
        // New user, or the name or e-mail was changed in Keycloak
        userRepository.upsert(keycloakId, username, email, displayName);
        return userRepository.findByKeycloakId(keycloakId).orElseThrow();
    }

    private String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.length() > MAX_DISPLAY_NAME ? name.substring(0, MAX_DISPLAY_NAME) : name;
    }
}
