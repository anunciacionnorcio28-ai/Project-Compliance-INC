import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/interceptors';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="col">
      <div class="page-head">
        <h1>Sign in</h1>
        <p class="lede">You need your generated password and a fresh code from your authenticator app.</p>
      </div>
      <div class="field">
        <label class="f" for="u">Username</label>
        <input id="u" type="text" autocomplete="username" autocapitalize="none" spellcheck="false" [(ngModel)]="username">
      </div>
      <div class="field">
        <label class="f" for="p">Password</label>
        <input id="p" type="password" autocomplete="current-password" [(ngModel)]="password">
      </div>
      <div class="field">
        <label class="f" for="c">Authenticator code</label>
        <input id="c" class="code-input" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6"
               [(ngModel)]="code" (keydown.enter)="submit()">
      </div>
      @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
      <div class="row">
        <button class="btn btn-primary" type="button" (click)="submit()" [disabled]="busy() || !username || !password || code.length !== 6">
          {{ busy() ? 'Signing in…' : 'Sign in' }}
        </button>
      </div>
      <hr>
      <p class="small muted">New here? <a routerLink="/register">Create an account</a>. Lost your authenticator and password but saved an Owner Seed?
        <a routerLink="/recover">Recover your account</a>.</p>
    </div>
  `,
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  username = '';
  password = '';
  code = '';
  busy = signal(false);
  error = signal('');

  async submit(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.login(this.username.trim(), this.password, this.code.trim());
      this.password = '';
      await this.router.navigateByUrl('/feed');
    } catch (e) {
      this.error.set(errorMessage(e));
      this.code = '';
    } finally {
      this.busy.set(false);
    }
  }
}
