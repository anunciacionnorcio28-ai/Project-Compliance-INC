package app.privmicro.repo;

import app.privmicro.model.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    long countByFolloweeId(Long followeeId);

    long countByFollowerId(Long followerId);

    @Query("select f from Follow f join fetch f.follower where f.followee.id = :userId order by f.id")
    List<Follow> findFollowersOf(@Param("userId") Long userId);

    @Query("select f from Follow f join fetch f.followee where f.follower.id = :userId order by f.id")
    List<Follow> findFollowingOf(@Param("userId") Long userId);

    @Modifying
    @Query("delete from Follow f where f.follower.id = :follower and f.followee.id = :followee")
    int deleteLink(@Param("follower") Long follower, @Param("followee") Long followee);
}
