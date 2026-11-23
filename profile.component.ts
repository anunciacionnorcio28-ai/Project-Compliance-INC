import { Component, effect, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../core/interceptors';
import { Profile } from '../core/models';
import { PostListComponent } from '../shared/post-list.component';

@Component({
  selector: 'app-profile',
  imports: [PostListComponent],
  template: `
    <div class="col">
      @if (profile(); as p) {
        <div class="page-head">
          <h1>&#64;{{ p.username }}</h1>
          <p class="muted">{{ p.followerCount }} {{ p.followerCount === 1 ? 'follower' : 'followers' }} · following {{ p.followingCount }}</p>
          @if (!p.self) {
            <button class="btn" [class.btn-primary]="!p.followedByMe" type="button" (click)="toggle()" [disabled]="busy()">
              {{ p.followedByMe ? 'Unfollow' : 'Follow' }}
            </button>
          }
        </div>
        @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
        @if (!p.self && !p.followedByMe) { <p class="note small">Follow &#64;{{ p.username }} to see their followers-only and encrypted posts.</p> }
        <app-post-list [url]="'/api/posts/user/' + p.username" [emptyText]="'No posts you can see yet.'" />
      } @else if (missing()) {
        <div class="page-head"><h1>User not found</h1><p class="lede">Check the username and try again.</p></div>
      }
    </div>
  `,
})
export class ProfileComponent {
  private http = inject(HttpClient);
  username = input.required<string>();
  profile = signal<Profile | null>(null);
  missing = signal(false);
  busy = signal(false);
  error = signal('');

  constructor() {
    effect(() => { void this.load(this.username()); });
  }

  private async load(name: string): Promise<void> {
    this.profile.set(null);
    this.missing.set(false);
    try {
      this.profile.set(await firstValueFrom(this.http.get<Profile>(`/api/users/${encodeURIComponent(name)}`)));
    } catch {
      this.missing.set(true);
    }
  }

  async toggle(): Promise<void> {
    const p = this.profile();
    if (!p || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const url = `/api/users/${encodeURIComponent(p.username)}/follow`;
      await firstValueFrom(p.followedByMe ? this.http.delete<void>(url) : this.http.post<void>(url, {}));
      await this.load(p.username);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
