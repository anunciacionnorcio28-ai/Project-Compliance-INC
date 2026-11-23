import { Component, signal, viewChild } from '@angular/core';
import { ComposeComponent } from './compose.component';
import { PostListComponent } from '../shared/post-list.component';
import { SecurityBannerComponent } from '../shared/security-guide.component';

@Component({
  selector: 'app-feed',
  imports: [ComposeComponent, PostListComponent, SecurityBannerComponent],
  template: `
    <div class="col">
      @if (showGuide()) { <app-security-banner (dismissed)="dismissGuide()" /> }
      <app-compose (posted)="list()?.prepend($event)" />
      <div class="page-head"><h2>Your feed</h2></div>
      <app-post-list #l url="/api/posts/feed" emptyText="Your feed is empty. Follow people from Discover to see their posts here." />
    </div>
  `,
})
export class FeedComponent {
  list = viewChild<PostListComponent>('l');
  showGuide = signal(sessionStorage.getItem('hush.showGuide') === '1');

  dismissGuide(): void {
    sessionStorage.removeItem('hush.showGuide');
    this.showGuide.set(false);
  }
}
