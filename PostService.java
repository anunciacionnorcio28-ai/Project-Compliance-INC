package app.privmicro.service;

import app.privmicro.dto.Dtos.*;
import app.privmicro.model.Comment;
import app.privmicro.model.MicroPost;
import app.privmicro.repo.CommentRepository;
import app.privmicro.repo.FollowRepository;
import app.privmicro.repo.MediaRepository;
import app.privmicro.repo.PostRepository;
import app.privmicro.repo.UserRepository;
import app.privmicro.security.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class PostService {
    public static final int MAX_CHARS = 280;
    private static final int PAGE = 20;

    private final PostRepository posts;
    private final CommentRepository comments;
    private final FollowRepository follows;
    private final UserRepository users;
    private final MediaRepository media;
    private final ObjectMapper mapper;

    public PostService(PostRepository posts, CommentRepository comments, FollowRepository follows,
                       UserRepository users, MediaRepository media, ObjectMapper mapper) {
        this.posts = posts;
        this.comments = comments;
        this.follows = follows;
        this.users = users;
        this.media = media;
        this.mapper = mapper;
    }

    // ---------- create / delete ----------

    @Transactional
    public PostDto create(AuthUser me, CreatePostRequest req) {
        MicroPost p = new MicroPost();
        p.setUser(users.getReferenceById(me.id()));
        boolean publicPost = req.visibility().equals("PUBLIC");

        if (req.encrypted()) {
            if (publicPost) throw new ApiException(HttpStatus.BAD_REQUEST, "Encrypted posts are for followers only");
            if (req.mediaId() != null) throw new ApiException(HttpStatus.BAD_REQUEST, "Encrypted posts can't carry images");
            validateEnvelope(req.content());
            p.setContentEncrypted(req.content());
        } else {
            String text = req.content().strip();
            if (text.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Write something first");
            if (text.codePointCount(0, text.length()) > MAX_CHARS)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Posts are limited to " + MAX_CHARS + " characters");
            p.setContentEncrypted(text);
        }
        p.setPublicPost(publicPost);
        p.setE2ee(req.encrypted());

        if (req.ttlMinutes() != null) {
            int ttl = req.ttlMinutes();
            if (ttl < 1 || ttl > 43_200) throw new ApiException(HttpStatus.BAD_REQUEST, "Time-to-live must be between 1 minute and 30 days");
            p.setExpiresAt(Instant.now().plus(ttl, ChronoUnit.MINUTES));
        }
        if (req.mediaId() != null) {
            var m = media.findWithOwner(req.mediaId()).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Image not found"));
            if (m.getOwner().getId() != me.id()) throw new ApiException(HttpStatus.FORBIDDEN, "Not your image");
            if (posts.existsByMediaId(m.getId())) throw new ApiException(HttpStatus.CONFLICT, "Image already used");
            p.setMediaId(m.getId());
        }
        posts.save(p);
        return toDto(p, me.id(), me.username());
    }

    @Transactional
    public void delete(AuthUser me, long id) {
        MicroPost p = posts.findWithUser(id).orElseThrow(() -> notFound());
        if (p.getUser().getId() != me.id()) throw new ApiException(HttpStatus.FORBIDDEN, "You can only delete your own posts");
        String mediaId = p.getMediaId();
        posts.delete(p);
        posts.flush();
        if (mediaId != null) media.deleteById(mediaId);
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<PostDto> feed(AuthUser me, Long before) {
        return posts.feed(me.id(), Instant.now(), cursor(before), PageRequest.ofSize(PAGE)).stream()
                .map(p -> toDto(p, me.id(), null)).toList();
    }

    @Transactional(readOnly = true)
    public List<PostDto> discover(AuthUser me, Long before) {
        return posts.discover(Instant.now(), cursor(before), PageRequest.ofSize(PAGE)).stream()
                .map(p -> toDto(p, me.id(), null)).toList();
    }

    @Transactional(readOnly = true)
    public List<PostDto> byUser(AuthUser me, String username, Long before) {
        var author = users.findByUsername(username.toLowerCase(Locale.ROOT)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        boolean full = author.getId() == me.id() || follows.existsByFollowerIdAndFolloweeId(me.id(), author.getId());
        return posts.byAuthor(author.getId(), full, Instant.now(), cursor(before), PageRequest.ofSize(PAGE)).stream()
                .map(p -> toDto(p, me.id(), null)).toList();
    }

    // ---------- engagement ----------

    /** Incognito reaction: only an aggregated counter is incremented, nobody's like is recorded. */
    @Transactional
    public LikeResponse like(AuthUser me, long id) {
        MicroPost p = viewable(me, id);
        posts.incrementLikes(p.getId());
        return new LikeResponse(posts.likeCount(p.getId()));
    }

    @Transactional
    public CommentDto comment(AuthUser me, long postId, CommentRequest req) {
        MicroPost p = viewable(me, postId);
        Comment c = new Comment();
        c.setPost(p);
        c.setUser(users.getReferenceById(me.id()));
        c.setContent(req.content().strip());
        c.setPublicComment(req.visibleToAll());
        comments.save(c);
        return new CommentDto(c.getId(), me.username(), c.getContent(), c.isPublicComment(), c.getCreatedAt(), true);
    }

    /** Private by default: visible only to the post author and the commenter, unless the commenter opened it up. */
    @Transactional(readOnly = true)
    public List<CommentDto> comments(AuthUser me, long postId) {
        MicroPost p = viewable(me, postId);
        boolean isAuthor = p.getUser().getId() == me.id();
        return comments.findByPost(p.getId()).stream()
                .filter(c -> c.isPublicComment() || isAuthor || c.getUser().getId() == me.id())
                .map(c -> new CommentDto(c.getId(), c.getUser().getUsername(), c.getContent(), c.isPublicComment(),
                        c.getCreatedAt(), c.getUser().getId() == me.id()))
                .toList();
    }

    @Transactional
    public void deleteComment(AuthUser me, long commentId) {
        Comment c = comments.findFull(commentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Comment not found"));
        boolean allowed = c.getUser().getId() == me.id() || c.getPost().getUser().getId() == me.id();
        if (!allowed) throw new ApiException(HttpStatus.FORBIDDEN, "Not allowed");
        comments.delete(c);
    }

    // ---------- visibility ----------

    public boolean canView(MicroPost p, long viewerId) {
        if (p.getExpiresAt() != null && !p.getExpiresAt().isAfter(Instant.now())) return false;
        return p.isPublicPost() || p.getUser().getId() == viewerId
                || follows.existsByFollowerIdAndFolloweeId(viewerId, p.getUser().getId());
    }

    private MicroPost viewable(AuthUser me, long id) {
        MicroPost p = posts.findWithUser(id).orElseThrow(PostService::notFound);
        if (!canView(p, me.id())) throw notFound(); // same answer for "hidden" and "missing"
        return p;
    }

    private PostDto toDto(MicroPost p, long viewerId, String knownAuthor) {
        String author = knownAuthor != null ? knownAuthor : p.getUser().getUsername();
        return new PostDto(p.getId(), author, p.getContentEncrypted(), p.isE2ee(),
                p.isPublicPost() ? "PUBLIC" : "FOLLOWERS", p.getCreatedAt(), p.getExpiresAt(), p.getLikeCount(),
                p.getMediaId(), p.getUser().getId() == viewerId);
    }

    private static long cursor(Long before) {
        return before == null ? Long.MAX_VALUE : before;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Post not found");
    }

    /** E2EE envelope: {"v":1,"iv":"..","ct":"..","keys":{"username":"wrappedKey",...}} - the server never sees plaintext. */
    private void validateEnvelope(String content) {
        try {
            JsonNode n = mapper.readTree(content);
            boolean ok = n.isObject() && n.path("v").asInt() == 1 && n.path("iv").isTextual() && n.path("ct").isTextual()
                    && n.path("keys").isObject() && n.path("keys").size() > 0 && n.path("keys").size() <= 1000;
            if (!ok) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid encrypted post");
        }
    }
}
