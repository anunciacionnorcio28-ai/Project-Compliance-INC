import { Component, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { CryptoService } from '../core/crypto.service';
import { errorMessage } from '../core/interceptors';
import { Post, PostComment } from '../core/models';

@Component({
  selector: 'app-post-card',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <article class="post" [class.followers]="post().visibility === 'FOLLOWERS' && !post().encrypted" [class.sealed]="post().encrypted">
      <div class="post-head">
        <a class="who" [routerLink]="['/u', post().author]">&#64;{{ post().author }}</a>
        <span class="muted">{{ post().createdAt | date: 'MMM d, HH:mm' }}</span>
        @if (post().encrypted) { <span class="badge seal">End-to-end encrypted</span> }
        @else if (post().visibility === 'FOLLOWERS') { <span class="badge ink">Followers</span> }
        @if (post().expiresAt) { <span class="badge amber">{{ remainingText() }}</span> }
      </div>

      @if (post().encrypted) {
        @if (text() === undefined) { <p class="post-body locked">Decrypting…</p> }
        @else if (text() === null) { <p class="post-body locked">This post is sealed. It was encrypted for other recipients, or this device doesn't hold your encryption key (import your identity file in Settings).</p> }
        @else { <p class="post-body">{{ text() }}</p> }
      } @else {
        <p class="post-body">{{ post().content }}</p>
      }
      @if (post().mediaId) {
        <img class="attached" [src]="'/api/media/' + post().mediaId" alt="Image attached to this post" loading="lazy">
      }

      <div class="post-actions">
        <button class="btn btn-quiet btn-small" type="button" (click)="like()" [disabled]="liked()" [attr.aria-label]="'Like, ' + likes() + ' so far'">
          {{ liked() ? 'Liked' : 'Like' }} · {{ likes() }}
        </button>
        <button class="btn btn-quiet btn-small" type="button" (click)="toggleComments()" [attr.aria-expanded]="open()">Comments</button>
        @if (post().mine) { <button class="btn btn-quiet btn-small" type="button" (click)="remove()">Delete</button> }
      </div>

      @if (open()) {
        <div class="comments">
          @if (comments().length === 0) { <p class="muted small">No comments you can see yet.</p> }
          @for (c of comments(); track c.id) {
            <div class="comment">
              <strong>&#64;{{ c.author }}</strong>
              <span class="muted small">{{ c.visibleToAll ? 'visible to everyone who can see this post' : 'private' }}</span>
              <div>{{ c.content }}</div>
              @if (c.mine || post().mine) { <button class="btn btn-quiet btn-small" type="button" (click)="removeComment(c)">Delete</button> }
            </div>
          }
          <div class="field" style="margin-top:.6rem">
            <label class="f" [for]="'cm' + post().id">Add a comment</label>
            <input [id]="'cm' + post().id" type="text" maxlength="280" [(ngModel)]="draft" (keydown.enter)="send()">
          </div>
          <label class="small"><input type="checkbox" [(ngModel)]="visibleToAll"> Show to everyone who can see this post</label>
          <div class="hint">By default only {{ post().mine ? 'you and the commenter' : '@' + post().author + ' and you' }} can read it.</div>
          @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
          <button class="btn btn-small" type="button" style="margin-top:.5rem" (click)="send()" [disabled]="!draft.trim()">Post comment</button>
        </div>
      }

      @if (post().expiresAt) { <div class="fuse" aria-hidden="true"><i [style.width.%]="fuse()"></i></div> }
    </article>
  `,
})
export class PostCardComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private crypto = inject(CryptoService);

  post = input.required<Post>();
  deleted = output<number>();

  text = signal<string | null | undefined>(undefined);
  likes = signal(0);
  liked = signal(false);
  open = signal(false);
  comments = signal<PostComment[]>([]);
  error = signal('');
  now = signal(Date.now());
  draft = '';
  visibleToAll = false;
  private timer?: ReturnType<typeof setInterval>;

  async ngOnInit(): Promise<void> {
    const p = this.post();
    this.likes.set(p.likeCount);
    // Likes are aggregate counts on the server, so the "already liked" memory lives only in this browser.
    this.liked.set(this.likedIds().has(p.id));
    if (p.encrypted) {
      const me = this.auth.me();
      this.text.set(me ? await this.crypto.decryptFor(me.username, p.content) : null);
    }
    if (p.expiresAt) this.timer = setInterval(() => this.now.set(Date.now()), 15_000);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  fuse(): number {
    const p = this.post();
    if (!p.expiresAt) return 0;
    const start = Date.parse(p.createdAt);
    const end = Date.parse(p.expiresAt);
    return Math.max(0, Math.min(100, ((end - this.now()) / (end - start)) * 100));
  }

  remainingText(): string {
    const ms = Date.parse(this.post().expiresAt!) - this.now();
    if (ms <= 0) return 'expiring';
    const m = Math.ceil(ms / 60_000);
    if (m < 60) return `vanishes in ${m} min`;
    const h = Math.round(m / 60);
    if (h < 48) return `vanishes in ${h} h`;
    return `vanishes in ${Math.round(h / 24)} d`;
  }

  private likedIds(): Set<number> {
    try { return new Set<number>(JSON.parse(localStorage.getItem('hush.liked') ?? '[]')); } catch { return new Set(); }
  }

  async like(): Promise<void> {
    if (this.liked()) return;
    try {
      const res = await firstValueFrom(this.http.post<{ likeCount: number }>(`/api/posts/${this.post().id}/like`, {}));
      this.likes.set(res.likeCount);
      this.liked.set(true);
      const ids = this.likedIds();
      ids.add(this.post().id);
      localStorage.setItem('hush.liked', JSON.stringify([...ids].slice(-500)));
    } catch (e) { this.error.set(errorMessage(e)); }
  }

  async toggleComments(): Promise<void> {
    this.open.set(!this.open());
    if (this.open()) await this.loadComments();
  }

  private async loadComments(): Promise<void> {
    try {
      this.comments.set(await firstValueFrom(this.http.get<PostComment[]>(`/api/posts/${this.post().id}/comments`)));
    } catch (e) { this.error.set(errorMessage(e)); }
  }

  async send(): Promise<void> {
    const content = this.draft.trim();
    if (!content) return;
    this.error.set('');
    try {
      const c = await firstValueFrom(this.http.post<PostComment>(`/api/posts/${this.post().id}/comments`, { content, visibleToAll: this.visibleToAll }));
      this.comments.update((list) => [...list, c]);
      this.draft = '';
    } catch (e) { this.error.set(errorMessage(e)); }
  }

  async removeComment(c: PostComment): Promise<void> {
    try {
      await firstValueFrom(this.http.delete<void>(`/api/posts/comments/${c.id}`));
      this.comments.update((list) => list.filter((x) => x.id !== c.id));
    } catch (e) { this.error.set(errorMessage(e)); }
  }

  async remove(): Promise<void> {
    if (!confirm('Delete this post for good?')) return;
    try {
      await firstValueFrom(this.http.delete<void>(`/api/posts/${this.post().id}`));
      this.deleted.emit(this.post().id);
    } catch (e) { this.error.set(errorMessage(e)); }
  }
}
