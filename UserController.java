package app.privmicro.web;

import app.privmicro.dto.Dtos.*;
import app.privmicro.security.AuthUser;
import app.privmicro.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/search")
    public List<UserName> search(@RequestParam("q") String q) {
        return users.search(q);
    }

    @GetMapping("/me/followers")
    public List<UserKey> followers(@AuthenticationPrincipal AuthUser me) {
        return users.myFollowers(me);
    }

    @GetMapping("/me/following")
    public List<UserName> following(@AuthenticationPrincipal AuthUser me) {
        return users.myFollowing(me);
    }

    @GetMapping("/{username}")
    public ProfileResponse profile(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
        return users.profile(me, username);
    }

    @PostMapping("/{username}/follow")
    public ResponseEntity<Void> follow(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
        users.follow(me, username);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{username}/follow")
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
        users.unfollow(me, username);
        return ResponseEntity.noContent().build();
    }
}
