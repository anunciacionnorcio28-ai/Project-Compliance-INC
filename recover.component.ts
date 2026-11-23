import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { CryptoService } from '../core/crypto.service';
import { IdentityService } from '../core/identity.service';
import { errorMessage } from '../core/interceptors';
import { Me, RecoveryCompleteResponse } from '../core/models';

@Component({
  selector: 'app-recover',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="col">
      <div class="page-head">
        <h1>Recover with your Owner Seed</h1>
        <p class="lede">Your 32 seed words prove ownership. Hush then issues a new password and a new authenticator secret.</p>
      </div>

      @if (!result()) {
        <div class="field">
          <label class="f" for="u">Username</label>
          <input id="u" type="text" autocapitalize="none" spellcheck="false" [(ngModel)]="username">
        </div>
        <div class="field">
          <label class="f" for="w">Owner Seed words</label>
          <textarea id="w" rows="4" autocapitalize="none" spellcheck="false" autocomplete="off" [(ngModel)]="words"></textarea>
          <div class="hint">Words stay in your browser. Only a signature over a one-time challenge is sent.</div>
        </div>
        @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
        <button class="btn btn-primary" type="button" (click)="recover()" [disabled]="busy() || !username || !words">
          {{ busy() ? 'Signing challenge…' : 'Recover account' }}
        </button>
        <p class="small muted" style="margin-top:1rem">Without an Owner Seed, a lost password and authenticator can't be recovered. <a routerLink="/login">Back to sign in</a>.</p>
      } @else {
        <div class="ok">Ownership verified. Your old password and authenticator no longer work once you confirm below.</div>
        <h2>New password</h2>
        <div class="secret">{{ result()!.password }}</div>
        <h2 style="margin-top:1.2rem">New authenticator</h2>
        <img class="qr" [src]="result()!.qrCode" alt="QR code containing your new authenticator secret">
        <div class="secret" style="margin-top:.6rem">{{ result()!.totpSecret }}</div>
        <div class="field" style="margin-top:1.2rem">
          <label class="f" for="c">Enter the first code from the new authenticator</label>
          <input id="c" class="code-input" type="text" inputmode="numeric" maxlength="6" [(ngModel)]="code" (keydown.enter)="confirm()">
        </div>
        @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
        <div class="row">
          <button class="btn btn-primary" type="button" (click)="confirm()" [disabled]="busy() || code.length !== 6">Confirm and sign in</button>
          <button class="btn" type="button" (click)="download()">Download identity file</button>
        </div>
        <p class="small muted" style="margin-top:.8rem">Save the new password and secret first. Encrypted posts stay readable only on devices that hold your encryption key.</p>
      }
    </div>
  `,
})
export class RecoverComponent {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private crypto = inject(CryptoService);
  private identity = inject(IdentityService);
  private router = inject(Router);

  username = '';
  words = '';
  code = '';
  busy = signal(false);
  error = signal('');
  result = signal<RecoveryCompleteResponse | null>(null);

  async recover(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const name = this.username.trim().toLowerCase();
      const { challenge } = await firstValueFrom(this.http.post<{ challenge: string }>('/api/auth/recovery/challenge', { username: name }));
      const signature = await this.crypto.signChallenge(this.words, challenge);
      this.result.set(await firstValueFrom(this.http.post<RecoveryCompleteResponse>('/api/auth/recovery/complete', { username: name, challenge, signature })));
    } catch (e) {
      this.error.set(e instanceof Error ? e.message : errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  async confirm(): Promise<void> {
    const r = this.result();
    if (!r || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const me = await firstValueFrom(this.http.post<Me>('/api/auth/recovery/confirm', {
        username: this.username.trim().toLowerCase(), password: r.password, code: this.code.trim(),
      }));
      this.auth.setMe(me);
      this.words = '';
      await this.router.navigateByUrl('/feed');
    } catch (e) {
      this.error.set(errorMessage(e));
      this.code = '';
    } finally {
      this.busy.set(false);
    }
  }

  download(): void {
    const r = this.result();
    if (!r) return;
    const name = this.username.trim().toLowerCase();
    this.identity.download(this.identity.build({
      username: name, password: r.password, totpSecret: r.totpSecret, ownershipMnemonic: this.words.trim().toLowerCase(),
    }), name);
  }
}
