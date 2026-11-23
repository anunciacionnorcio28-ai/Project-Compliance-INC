package app.privmicro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class Dtos {
    private Dtos() {}

    // ---- auth ----
    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9_]{3,24}$", message = "3-24 characters: a-z, 0-9, underscore") String username,
            @NotBlank @Size(max = 1000) String publicKey,
            boolean enableOwnership) {}

    public record RegisterResponse(String username, String password, String totpSecret, String otpauthUri,
                                   String qrCode, String ownershipMnemonic) {}

    public record CredentialsRequest(
            @NotBlank String username,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code") String code) {}

    public record MeResponse(String username, String publicKey, boolean ownershipEnabled) {}

    public record RecoveryChallengeRequest(@NotBlank String username) {}

    public record ChallengeResponse(String challenge) {}

    public record RecoveryCompleteRequest(@NotBlank String username, @NotBlank String challenge, @NotBlank String signature) {}

    public record RecoveryCompleteResponse(String password, String totpSecret, String otpauthUri, String qrCode) {}

    // ---- account ----
    public record ChallengeRequest(@NotBlank String action) {}

    public record StepUpRequest(
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code") String code,
            String challenge,
            String signature) {}

    public record MfaResetResponse(String totpSecret, String otpauthUri, String qrCode) {}

    public record CodeRequest(@NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code") String code) {}

    public record OwnershipResponse(String mnemonic) {}

    // ---- users ----
    public record ProfileResponse(String username, long followerCount, long followingCount, boolean followedByMe, boolean self) {}

    public record UserKey(String username, String publicKey) {}

    public record UserName(String username) {}

    // ---- posts ----
    public record CreatePostRequest(
            @NotBlank @Size(max = 500000) String content,
            boolean encrypted,
            @NotBlank @Pattern(regexp = "PUBLIC|FOLLOWERS") String visibility,
            Integer ttlMinutes,
            String mediaId) {}

    public record PostDto(long id, String author, String content, boolean encrypted, String visibility,
                          Instant createdAt, Instant expiresAt, int likeCount, String mediaId, boolean mine) {}

    public record LikeResponse(int likeCount) {}

    public record CommentRequest(@NotBlank @Size(max = 280) String content, boolean visibleToAll) {}

    public record CommentDto(long id, String author, String content, boolean visibleToAll, Instant createdAt, boolean mine) {}

    public record MediaResponse(String id) {}
}
