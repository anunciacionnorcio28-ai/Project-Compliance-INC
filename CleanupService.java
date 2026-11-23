package app.privmicro.service;

import app.privmicro.repo.MediaRepository;
import app.privmicro.repo.PostRepository;
import app.privmicro.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Automated cleanup: purges expired (TTL) posts and their images, orphaned uploads and unfinished registrations. */
@Service
public class CleanupService {
    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final PostRepository posts;
    private final MediaRepository media;
    private final UserRepository users;
    private final LoginThrottle throttle;
    private final OwnershipService ownership;

    public CleanupService(PostRepository posts, MediaRepository media, UserRepository users,
                          LoginThrottle throttle, OwnershipService ownership) {
        this.posts = posts;
        this.media = media;
        this.users = users;
        this.throttle = throttle;
        this.ownership = ownership;
    }

    @Scheduled(fixedDelayString = "${app.cleanup-interval-ms:60000}", initialDelay = 10_000)
    @Transactional
    public void run() {
        Instant now = Instant.now();
        int mediaOfExpired = posts.deleteMediaOfExpired(now);
        int expired = posts.deleteExpired(now); // comments cascade via foreign keys
        int orphans = media.deleteOrphansBefore(now.minus(1, ChronoUnit.HOURS));
        int unverified = users.deleteUnverifiedBefore(now.minus(1, ChronoUnit.HOURS));
        throttle.purge();
        ownership.purgeNonces();
        if (expired + mediaOfExpired + orphans + unverified > 0) {
            log.info("cleanup: {} expired posts, {} images, {} orphan uploads, {} unfinished registrations",
                    expired, mediaOfExpired + orphans, orphans, unverified);
        }
    }
}
