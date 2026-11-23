export interface Me { username: string; publicKey: string; ownershipEnabled: boolean; }
export interface RegisterResponse {
  username: string; password: string; totpSecret: string; otpauthUri: string; qrCode: string; ownershipMnemonic: string | null;
}
export interface RecoveryCompleteResponse { password: string; totpSecret: string; otpauthUri: string; qrCode: string; }
export interface MfaResetResponse { totpSecret: string; otpauthUri: string; qrCode: string; }
export interface Profile { username: string; followerCount: number; followingCount: number; followedByMe: boolean; self: boolean; }
export interface UserKey { username: string; publicKey: string; }
export interface UserName { username: string; }
export interface Post {
  id: number; author: string; content: string; encrypted: boolean; visibility: 'PUBLIC' | 'FOLLOWERS';
  createdAt: string; expiresAt: string | null; likeCount: number; mediaId: string | null; mine: boolean;
}
export interface PostComment { id: number; author: string; content: string; visibleToAll: boolean; createdAt: string; mine: boolean; }
export interface StepUpPayload { password: string; code: string; challenge?: string; signature?: string; }
export interface IdentityFile {
  format: 'hush-identity-v1';
  username: string;
  password: string;
  totpSecret: string;
  ownershipMnemonic?: string;
  e2eePublicKey?: string;
  e2eePrivateKeyJwk?: JsonWebKey;
}
