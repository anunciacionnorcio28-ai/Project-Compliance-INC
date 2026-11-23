import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { PostListComponent } from '../shared/post-list.component';
import { UserName } from '../core/models';

@Component({
  selector: 'app-discover',
  imports: [FormsModule, RouterLink, PostListComponent],
  template: `
    <div class="col">
      <div class="page-head"><h1>Discover</h1><p class="lede">Public posts, and a way to find people by username.</p></div>
      <div class="field">
        <label class="f" for="q">Find a person</label>
        <input id="q" type="search" autocapitalize="none" spellcheck="false" placeholder="Start typing a username" [(ngModel)]="q" (ngModelChange)="search()">
      </div>
      @if (results().length) {
        <ul class="pill-list" aria-label="Matching users">
          @for (u of results(); track u.username) { <li><a [routerLink]="['/u', u.username]">&#64;{{ u.username }}</a></li> }
        </ul>
      } @else if (q.length >= 2 && searched()) { <p class="muted small">No one with that username.</p> }
      <hr>
      <app-post-list url="/api/posts/discover" emptyText="No public posts yet. Be the first." />
    </div>
  `,
})
export class DiscoverComponent {
  private http = inject(HttpClient);
  q = '';
  results = signal<UserName[]>([]);
  searched = signal(false);
  private timer?: ReturnType<typeof setTimeout>;

  search(): void {
    clearTimeout(this.timer);
    if (this.q.trim().length < 2) { this.results.set([]); this.searched.set(false); return; }
    this.timer = setTimeout(async () => {
      try {
        this.results.set(await firstValueFrom(this.http.get<UserName[]>('/api/users/search', { params: { q: this.q.trim() } })));
      } catch { this.results.set([]); }
      this.searched.set(true);
    }, 250);
  }
}
