package app.privmicro.repo;

import app.privmicro.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("select c from Comment c join fetch c.user where c.post.id = :postId order by c.id")
    List<Comment> findByPost(@Param("postId") Long postId);

    @Query("select c from Comment c join fetch c.user join fetch c.post p join fetch p.user where c.id = :id")
    Optional<Comment> findFull(@Param("id") Long id);
}
