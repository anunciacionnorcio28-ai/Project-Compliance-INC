import { Injectable, inject } from '@angular/core';
import { KeystoreService } from './keystore.service';
import { UserKey } from './models';
import { WORDLIST } from './wordlist';

export interface Envelope { v: 1; iv: string; ct: string; keys: Record<string, string>; }

const RSA = { name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' } as const;
// PKCS#8 wrapper for a raw 32-byte Ed25519 seed
const ED25519_PKCS8_PREFIX = new Uint8Array([0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20]);

export function toB64(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s);
}

export function fromB64(b64: string): Uint8Array<ArrayBuffer> {
  const s = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(s.length));
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

/** All cryptography runs here in the browser with the Web Crypto API. */
@Injectable({ providedIn: 'root' })
export class CryptoService {
  private keystore = inject(KeystoreService);

  // ---------- end-to-end encryption (RSA-OAEP key wrapping + AES-GCM content) ----------

  async generateIdentityKeys(): Promise<{ publicKey: string; privateJwk: JsonWebKey }> {
    const pair = await crypto.subtle.generateKey(RSA, true, ['encrypt', 'decrypt']);
    const publicKey = toB64(await crypto.subtle.exportKey('spki', pair.publicKey));
    const privateJwk = await crypto.subtle.exportKey('jwk', pair.privateKey);
    return { publicKey, privateJwk };
  }

  /** Re-imports the private key as non-extractable and stores it for this user. */
  async storePrivateKey(username: string, jwk: JsonWebKey): Promise<void> {
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['decrypt']);
    await this.keystore.save(username, key);
  }

  hasPrivateKey(username: string): Promise<boolean> {
    return this.keystore.load(username).then((k) => !!k).catch(() => false);
  }

  async encryptFor(plaintext: string, recipients: UserKey[]): Promise<string> {
    const aes = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt']);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, aes, new TextEncoder().encode(plaintext));
    const raw = await crypto.subtle.exportKey('raw', aes);
    const keys: Record<string, string> = {};
    for (const r of recipients) {
      const pub = await crypto.subtle.importKey('spki', fromB64(r.publicKey), { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['encrypt']);
      keys[r.username] = toB64(await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, pub, raw));
    }
    const env: Envelope = { v: 1, iv: toB64(iv), ct: toB64(ct), keys };
    return JSON.stringify(env);
  }

  /** Returns the text, or null when this user isn't a recipient or has no private key on this device. */
  async decryptFor(username: string, envelopeJson: string): Promise<string | null> {
    try {
      const env = JSON.parse(envelopeJson) as Envelope;
      const wrapped = env.keys[username];
      const priv = await this.keystore.load(username);
      if (!wrapped || !priv) return null;
      const raw = await crypto.subtle.decrypt({ name: 'RSA-OAEP' }, priv, fromB64(wrapped));
      const aes = await crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['decrypt']);
      const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: fromB64(env.iv) }, aes, fromB64(env.ct));
      return new TextDecoder().decode(pt);
    } catch {
      return null;
    }
  }

  // ---------- Owner Seed: mnemonic <-> Ed25519 signing for challenge-response ----------

  normalizeMnemonic(input: string): string[] {
    return input.toLowerCase().split(/[\s,]+/).filter(Boolean);
  }

  mnemonicToSeed(input: string): Uint8Array<ArrayBuffer> {
    const words = this.normalizeMnemonic(input);
    if (words.length !== 32) throw new Error('An Owner Seed has 32 words.');
    const seed = new Uint8Array(new ArrayBuffer(32));
    words.forEach((w, i) => {
      const idx = WORDLIST.indexOf(w);
      if (idx < 0) throw new Error(`"${w}" is not an Owner Seed word.`);
      seed[i] = idx;
    });
    return seed;
  }

  async signChallenge(mnemonic: string, challenge: string): Promise<string> {
    const seed = this.mnemonicToSeed(mnemonic);
    const pkcs8 = new Uint8Array(ED25519_PKCS8_PREFIX.length + seed.length);
    pkcs8.set(ED25519_PKCS8_PREFIX, 0);
    pkcs8.set(seed, ED25519_PKCS8_PREFIX.length);
    try {
      const key = await crypto.subtle.importKey('pkcs8', pkcs8, 'Ed25519', false, ['sign']);
      const sig = await crypto.subtle.sign('Ed25519', key, new TextEncoder().encode(challenge));
      return toB64(sig);
    } catch {
      throw new Error('This browser can\'t sign with Ed25519. Update your browser and try again.');
    } finally {
      seed.fill(0);
      pkcs8.fill(0);
    }
  }
}
