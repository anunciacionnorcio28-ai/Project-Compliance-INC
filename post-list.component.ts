import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../core/interceptors';
import { Post } from '../core/models';
import { PostCardComponent } from './post-card.component';

/** Paged list of posts from any endpoint. Call prepend() to add a freshly written post. */
@Component({
  selector: 'app-post-list',
  imports: [PostCardComponent],
  template: `
    @for (p of posts(); track p.id) {
      <app-post-card [post]="p" (deleted)="drop($event)" />
    }
    @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
    @if (loaded() && posts().length === 0) { <p class="empty">{{ emptyText() }}</p> }
    @if (more()) {
      <button class="btn" type="button" (click)="load()" [disabled]="busy()">{{ busy() ? 'Loading…' : 'Load older posts' }}</button>
    }
  `,
})
export class PostListComponent {
  private http = inject(HttpClient);
  /** API path such as /api/posts/feed */
  url = input.required<string>();
  emptyText = input('Nothing here yet.');

  posts = signal<Post[]>([]);
  loaded = signal(false);
  more = signal(false);
  busy = signal(false);
  error = signal('');

  constructor() {
    effect(() => {
      this.url();
      untracked(() => { this.posts.set([]); this.loaded.set(false); this.more.set(false); void this.load(); });
    });
  }

  prepend(p: Post): void {
    this.posts.update((l) => [p, ...l]);
    this.loaded.set(true);
  }

  drop(id: number): void {
    this.posts.update((l) => l.filter((p) => p.id !== id));
  }

  async load(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const list = this.posts();
      const before = list.length ? list[list.length - 1].id : undefined;
      const url = this.url() + (before ? `?before=${before}` : '');
      const page = await firstValueFrom(this.http.get<Post[]>(url));
      this.posts.update((l) => [...l, ...page]);
      this.more.set(page.length >= 20);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
      this.loaded.set(true);
    }
  }
}
