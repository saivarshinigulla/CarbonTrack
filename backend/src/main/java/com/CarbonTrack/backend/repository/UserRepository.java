
package com.CarbonTrack.backend.repository;

import com.CarbonTrack.backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findAllByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select user
            from User user
            where lower(user.email) = lower(:email)
            """)
    Optional<User> findByEmailIgnoreCaseForUpdate(
            @Param("email") String email);
}
