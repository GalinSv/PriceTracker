package com.pricetracker.repository;

import com.pricetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKeycloakId(UUID keycloakId);

    // Two requests of a new user can come at the same time; only one row is created
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO users (keycloak_id, username, email, display_name, created_at)
            VALUES (:keycloakId, :username, :email, :displayName, now())
            ON CONFLICT (keycloak_id) DO UPDATE
            SET username = EXCLUDED.username, email = EXCLUDED.email, display_name = EXCLUDED.display_name
            """)
    void upsert(@Param("keycloakId") UUID keycloakId, @Param("username") String username,
                @Param("email") String email, @Param("displayName") String displayName);
}
