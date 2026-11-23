package app.privmicro.web;

import app.privmicro.dto.Dtos.*;
import app.privmicro.security.AuthUser;
import app.privmicro.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @PostMapping
    public PostDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreatePostRequest req) {
        return posts.create(me, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        posts.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/feed")
    public List<PostDto> feed(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long before) {
        return posts.feed(me, before);
    }

    @GetMapping("/discover")
    public List<PostDto> discover(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long before) {
        return posts.discover(me, before);
    }

    @GetMapping("/user/{username}")
    public List<PostDto> byUser(@AuthenticationPrincipal AuthUser me, @PathVariable String username,
                                @RequestParam(required = false) Long before) {
        return posts.byUser(me, username, before);
    }

    @PostMapping("/{id}/like")
    public LikeResponse like(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return posts.like(me, id);
    }

    @GetMapping("/{id}/comments")
    public List<CommentDto> comments(@AuthenticationPrincipal AuthUser me, @PathVariable long id) {
        return posts.comments(me, id);
    }

    @PostMapping("/{id}/comments")
    public CommentDto comment(@AuthenticationPrincipal AuthUser me, @PathVariable long id,
                              @Valid @RequestBody CommentRequest req) {
        return posts.comment(me, id, req);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal AuthUser me, @PathVariable long commentId) {
        posts.deleteComment(me, commentId);
        return ResponseEntity.noContent().build();
    }
}
