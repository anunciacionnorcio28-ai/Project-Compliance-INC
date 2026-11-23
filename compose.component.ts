import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { CryptoService } from '../core/crypto.service';
import { errorMessage } from '../core/interceptors';
import { Post, UserKey } from '../core/models';

const LIMIT = 280;

@Component({
  selector: 'app-compose',
  imports: [FormsModule],
  template: `
    <section class="compose" aria-label="Write a post">
      <label class="f" for="t">What's on your mind?</label>
      <textarea id="t" [(ngModel)]="text" rows="3" [attr.maxlength]="null"></textarea>
      <div class="row spread" style="margin:.35rem 0 .8rem">
        <span class="counter" [class.over]="count() > limit">{{ count() }} / {{ limit }}</span>
        <label class="small" [style.opacity]="visibility === 'ENCRYPTED' ? .5 : 1">
          Image <input type="file" accept="image/jpeg,image/png" (change)="pick($event)" [disabled]="visibility === 'ENCRYPTED'">
        </label>
      </div>
      <div class="row">
        <div class="field" style="flex:1 1 14rem;margin:0">
          <label class="f" for="vis">Who can read it</label>
          <select id="vis" [(ngModel)]="visibility">
            <option value="PUBLIC">Everyone on Hush</option>
            <option value="FOLLOWERS">Followers only</option>
            <option value="ENCRYPTED">Followers, end-to-end encrypted</option>
          </select>
        </div>
        <div class="field" style="flex:1 1 10rem;margin:0">
          <label class="f" for="ttl">Disappears</label>
          <select id="ttl" [(ngModel)]="ttl">
            <option value="">Never</option>
            <option value="60">After 1 hour</option>
            <option value="1440">After 24 hours</option>
            <option value="10080">After 7 days</option>
          </select>
        </div>
      </div>
      @if (visibility === 'ENCRYPTED') {
        <p class="hint">Encrypted in your browser for the people who follow you right now. The server only stores ciphertext. People who follow you later can't read it.</p>
      }
      @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
      <button class="btn btn-primary" type="button" style="margin-top:.8rem" (click)="submit()" [disabled]="busy() || !text.trim() || count() > limit">
        {{ busy() ? 'Posting…' : 'Post' }}
      </button>
    </section>
  `,
})
export class ComposeComponent {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private crypto = inject(CryptoService);
  posted = output<Post>();

  readonly limit = LIMIT;
  text = '';
  visibility: 'PUBLIC' | 'FOLLOWERS' | 'ENCRYPTED' = 'PUBLIC';
  ttl = '';
  file: File | null = null;
  busy = signal(false);
  error = signal('');

  count = () => [...this.text].length;

  pick(e: Event): void {
    this.file = (e.target as HTMLInputElement).files?.[0] ?? null;
  }

  async submit(): Promise<void> {
    const me = this.auth.me();
    if (!me || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const encrypted = this.visibility === 'ENCRYPTED';
      let content = this.text.trim();
      let mediaId: string | null = null;

      if (encrypted) {
        if (!(await this.crypto.hasPrivateKey(me.username))) {
          throw new Error('This device has no encryption key yet. Import your identity file in Settings first.');
        }
        const followers = await firstValueFrom(this.http.get<UserKey[]>('/api/users/me/followers'));
        const recipients = [...followers.filter((f) => f.username !== me.username), { username: me.username, publicKey: me.publicKey }];
        content = await this.crypto.encryptFor(content, recipients);
      } else if (this.file) {
        const form = new FormData();
        form.append('file', this.file);
        mediaId = (await firstValueFrom(this.http.post<{ id: string }>('/api/media', form))).id;
      }

      const post = await firstValueFrom(this.http.post<Post>('/api/posts', {
        content,
        encrypted,
        visibility: this.visibility === 'PUBLIC' ? 'PUBLIC' : 'FOLLOWERS',
        ttlMinutes: this.ttl ? Number(this.ttl) : null,
        mediaId,
      }));
      this.posted.emit(post);
      this.text = '';
      this.file = null;
      const input = document.querySelector<HTMLInputElement>('app-compose input[type=file]');
      if (input) input.value = '';
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
