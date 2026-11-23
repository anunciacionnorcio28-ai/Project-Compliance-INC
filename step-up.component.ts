import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { CryptoService } from '../core/crypto.service';
import { errorMessage } from '../core/interceptors';
import { StepUpPayload } from '../core/models';

/**
 * Re-authentication for high-privilege actions: password + current authenticator code, plus (when an Owner Seed is
 * active) a signature over a fresh server challenge, computed here from the seed words.
 */
@Component({
  selector: 'app-step-up',
  imports: [FormsModule],
  template: `
    <div class="field">
      <label class="f" [for]="id + 'p'">Password</label>
      <input [id]="id + 'p'" type="password" autocomplete="current-password" [(ngModel)]="password">
    </div>
    <div class="field">
      <label class="f" [for]="id + 'c'">Authenticator code</label>
      <input [id]="id + 'c'" class="code-input" type="text" inputmode="numeric" maxlength="6" autocomplete="off" [(ngModel)]="code">
      <div class="hint">If you signed in less than 30 seconds ago, wait for the next code.</div>
    </div>
    @if (needsSeed()) {
      <div class="field">
        <label class="f" [for]="id + 'w'">Owner Seed words</label>
        <textarea [id]="id + 'w'" rows="3" autocapitalize="none" spellcheck="false" autocomplete="off" [(ngModel)]="words"></textarea>
        <div class="hint">Your seed is active, so this action must be signed with it. The words never leave this browser.</div>
      </div>
    }
    @if (localError()) { <div class="error" role="alert">{{ localError() }}</div> }
    @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
    <button class="btn" [class.btn-danger]="danger()" [class.btn-primary]="!danger()" type="button" (click)="go()"
            [disabled]="busy() || working() || !password || code.length !== 6 || (needsSeed() && !words.trim())">
      {{ working() ? 'Signing…' : label() }}
    </button>
  `,
})
export class StepUpComponent {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private crypto = inject(CryptoService);

  /** challenge action: mfa-reset | ownership-disable | delete-account | ownership-enable */
  action = input.required<string>();
  label = input('Confirm');
  danger = input(false);
  busy = input(false);
  error = input('');
  confirmed = output<StepUpPayload>();

  readonly id = 'su' + Math.random().toString(36).slice(2, 7);
  password = '';
  code = '';
  words = '';
  working = signal(false);
  localError = signal('');
  needsSeed = computed(() => this.action() !== 'ownership-enable' && !!this.auth.me()?.ownershipEnabled);

  async go(): Promise<void> {
    this.localError.set('');
    this.working.set(true);
    try {
      const payload: StepUpPayload = { password: this.password, code: this.code.trim() };
      if (this.needsSeed()) {
        const { challenge } = await firstValueFrom(this.http.post<{ challenge: string }>('/api/account/challenge', { action: this.action() }));
        payload.challenge = challenge;
        payload.signature = await this.crypto.signChallenge(this.words, challenge);
      }
      this.confirmed.emit(payload);
    } catch (e) {
      this.localError.set(e instanceof Error ? e.message : errorMessage(e));
    } finally {
      this.working.set(false);
    }
  }

  reset(): void {
    this.password = '';
    this.code = '';
    this.words = '';
  }
}
