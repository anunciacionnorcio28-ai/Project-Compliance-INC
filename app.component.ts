import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="bar">
      <div class="bar-in">
        <a class="wordmark" routerLink="/">Hush</a>
        <nav class="nav" aria-label="Main">
          @if (auth.me(); as me) {
            <a routerLink="/feed" routerLinkActive="active">Feed</a>
            <a routerLink="/discover" routerLinkActive="active">Discover</a>
            <a [routerLink]="['/u', me.username]" routerLinkActive="active">&#64;{{ me.username }}</a>
            <a routerLink="/settings" routerLinkActive="active">Settings</a>
            <a href="#" (click)="logout($event)">Sign out</a>
          } @else {
            <a routerLink="/login" routerLinkActive="active">Sign in</a>
            <a routerLink="/register" routerLinkActive="active">Create account</a>
            <a routerLink="/security" routerLinkActive="active">Security guide</a>
          }
        </nav>
      </div>
    </header>
    <main><router-outlet /></main>
  `,
})
export class AppComponent {
  auth = inject(AuthService);
  private router = inject(Router);

  async logout(e: Event): Promise<void> {
    e.preventDefault();
    await this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
