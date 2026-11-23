package app.privmicro.repo;

import app.privmicro.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByIdAndMfaVerifiedTrue(Long id);

    List<User> findTop10ByUsernameStartingWithAndMfaVerifiedTrueOrderByUsername(String prefix);

    @Modifying
    @Query("delete from User u where u.mfaVerified = false and u.createdAt < :cutoff")
    int deleteUnverifiedBefore(@Param("cutoff") Instant cutoff);
}
