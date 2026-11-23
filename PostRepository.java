package app.privmicro.repo;

import app.privmicro.model.MicroPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<MicroPost, Long> {

    @Query("select p from MicroPost p join fetch p.user where p.id = :id")
    Optional<MicroPost> findWithUser(@Param("id") Long id);

    @Query("select p from MicroPost p join fetch p.user where p.mediaId = :mediaId")
    Optional<MicroPost> findByMediaIdWithUser(@Param("mediaId") String mediaId);

    boolean existsByMediaId(String mediaId);

    @Query("""
            select p from MicroPost p join fetch p.user u
            where (p.expiresAt is null or p.expiresAt > :now) and p.id < :before
              and (u.id = :uid or u.id in (select f.followee.id from Follow f where f.follower.id = :uid))
            order by p.id desc""")
    List<MicroPost> feed(@Param("uid") Long uid, @Param("now") Instant now, @Param("before") Long before, Pageable page);

    @Query("""
            select p from MicroPost p join fetch p.user u
            where (p.expiresAt is null or p.expiresAt > :now) and p.id < :before and p.publicPost = true
            order by p.id desc""")
    List<MicroPost> discover(@Param("now") Instant now, @Param("before") Long before, Pageable page);

    @Query("""
            select p from MicroPost p join fetch p.user u
            where u.id = :authorId and (p.expiresAt is null or p.expiresAt > :now) and p.id < :before
              and (:full = true or p.publicPost = true)
            order by p.id desc""")
    List<MicroPost> byAuthor(@Param("authorId") Long authorId, @Param("full") boolean full,
                             @Param("now") Instant now, @Param("before") Long before, Pageable page);

    /** Atomic aggregated counter: no per-user like record is ever stored. */
    @Modifying
    @Query("update MicroPost p set p.likeCount = p.likeCount + 1 where p.id = :id")
    int incrementLikes(@Param("id") Long id);

    @Query("select p.likeCount from MicroPost p where p.id = :id")
    int likeCount(@Param("id") Long id);

    @Modifying
    @Query(value = "DELETE m FROM media m JOIN micro_posts p ON p.media_id = m.id "
            + "WHERE p.expires_at IS NOT NULL AND p.expires_at <= :now", nativeQuery = true)
    int deleteMediaOfExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from MicroPost p where p.expiresAt is not null and p.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
