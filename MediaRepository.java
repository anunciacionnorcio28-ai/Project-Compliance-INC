package app.privmicro.repo;

import app.privmicro.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, String> {
    @Query("select m from Media m join fetch m.owner where m.id = :id")
    Optional<Media> findWithOwner(@Param("id") String id);

    @Modifying
    @Query(value = "DELETE FROM media WHERE created_at < :cutoff "
            + "AND id NOT IN (SELECT media_id FROM micro_posts WHERE media_id IS NOT NULL)", nativeQuery = true)
    int deleteOrphansBefore(@Param("cutoff") Instant cutoff);
}
