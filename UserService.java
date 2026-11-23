package app.privmicro.service;

import app.privmicro.dto.Dtos.*;
import app.privmicro.model.Follow;
import app.privmicro.model.User;
import app.privmicro.repo.FollowRepository;
import app.privmicro.repo.UserRepository;
import app.privmicro.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UserService {
    private final UserRepository users;
    private final FollowRepository follows;

    public UserService(UserRepository users, FollowRepository follows) {
        this.users = users;
        this.follows = follows;
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(AuthUser me, String username) {
        User u = find(username);
        return new ProfileResponse(u.getUsername(), follows.countByFolloweeId(u.getId()), follows.countByFollowerId(u.getId()),
                follows.existsByFollowerIdAndFolloweeId(me.id(), u.getId()), u.getId() == me.id());
    }

    @Transactional
    public void follow(AuthUser me, String username) {
        User target = find(username);
        if (target.getId() == me.id()) throw new ApiException(HttpStatus.BAD_REQUEST, "You can't follow yourself");
        if (follows.existsByFollowerIdAndFolloweeId(me.id(), target.getId())) return;
        Follow f = new Follow();
        f.setFollower(users.getReferenceById(me.id()));
        f.setFollowee(target);
        follows.save(f);
    }

    @Transactional
    public void unfollow(AuthUser me, String username) {
        User target = find(username);
        follows.deleteLink(me.id(), target.getId());
    }

    /** Followers with their public keys: the author needs these to wrap the post key for end-to-end encryption. */
    @Transactional(readOnly = true)
    public List<UserKey> myFollowers(AuthUser me) {
        return follows.findFollowersOf(me.id()).stream()
                .map(f -> new UserKey(f.getFollower().getUsername(), f.getFollower().getPublicKey())).toList();
    }

    @Transactional(readOnly = true)
    public List<UserName> myFollowing(AuthUser me) {
        return follows.findFollowingOf(me.id()).stream().map(f -> new UserName(f.getFollowee().getUsername())).toList();
    }

    @Transactional(readOnly = true)
    public List<UserName> search(String q) {
        String prefix = q == null ? "" : q.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (prefix.length() < 2) return List.of();
        return users.findTop10ByUsernameStartingWithAndMfaVerifiedTrueOrderByUsername(prefix).stream()
                .map(u -> new UserName(u.getUsername())).toList();
    }

    private User find(String username) {
        return users.findByUsername(username.toLowerCase(Locale.ROOT)).filter(User::isMfaVerified)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
