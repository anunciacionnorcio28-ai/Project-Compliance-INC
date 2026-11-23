import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { CryptoService } from '../core/crypto.service';
import { IdentityService } from '../core/identity.service';
import { KeystoreService } from '../core/keystore.service';
import { errorMessage } from '../core/interceptors';
import { MfaResetResponse, StepUpPayload } from '../core/models';
import { StepUpComponent } from '../shared/step-up.component';

@Component({
  selector: 'app-settings',
  imports: [FormsModule, RouterLink, StepUpComponent],
  template: `
    @if (auth.me(); as me) {
    <div class="col">
      <div class="page-head">
        <h1>Settings</h1>
        <p class="lede">Signed in as <strong>&#64;{{ me.username }}</strong>. <a routerLink="/security">Security guide</a></p>
      </div>
      @if (notice()) { <div class="ok" role="status">{{ notice() }}</div> }

      <section class="section">
        <h2>Encryption key on this device</h2>
        @if (hasKey() === null) { <p class="muted">Checking…</p> }
        @else if (hasKey()) { <p>This device holds your encryption key, so you can read and write end-to-end encrypted posts.</p> }
        @else {
          <div class="note">This device has no encryption key. Import your Secure Identity File to read encrypted posts addressed to you.</div>
        }
        <div class="field">
          <label class="f" for="idf">Import Secure Identity File</label>
          <input id="idf" type="file" accept="application/json,.json" (change)="importIdentity($event)">
          <div class="hint">The file is read in your browser. Only the encryption key is imported and it's stored where page scripts can't read it back.</div>
        </div>
        @if (importError()) { <div class="error" role="alert">{{ importError() }}</div> }
      </section>

      <section class="section">
        <h2>Reset authenticator</h2>
        @if (!mfa()) {
          <p>Switch to a new authenticator secret, for example after changing phones. Your current one keeps working until you confirm the new one.</p>
          <app-step-up action="mfa-reset" label="Start reset" [busy]="busy()" [error]="mfaError()" (confirmed)="startMfa($event)" />
        } @else {
          <img class="qr" [src]="mfa()!.qrCode" alt="QR code containing your new authenticator secret">
          <div class="secret" style="margin:.6rem 0">{{ mfa()!.totpSecret }}</div>
          <div class="field">
            <label class="f" for="mc">Enter the first code from the new authenticator</label>
            <input id="mc" class="code-input" type="text" inputmode="numeric" maxlength="6" [(ngModel)]="mfaCode" (keydown.enter)="confirmMfa()">
          </div>
          @if (mfaError()) { <div class="error" role="alert">{{ mfaError() }}</div> }
          <button class="btn btn-primary" type="button" (click)="confirmMfa()" [disabled]="busy() || mfaCode.length !== 6">Confirm new authenticator</button>
        }
      </section>

      <section class="section">
        <h2>Owner Seed</h2>
        @if (seedWords()) {
          <p>Write these 32 words down in order and keep them offline. They are shown once.</p>
          <ol class="words">@for (w of seedWords()!.split(' '); track $index) { <li>{{ w }}</li> }</ol>
          <div class="field"><label><input type="checkbox" [(ngModel)]="seedSaved"> I stored my Owner Seed safely</label></div>
          <button class="btn btn-primary" type="button" (click)="seedWords.set(null)" [disabled]="!seedSaved">Done</button>
        } @else if (!me.ownershipEnabled) {
          <p>An optional 32-word master key. With it, resetting your authenticator, deleting your account and recovering a lost password all need a signature
            from your seed. Without it, those actions use your password and authenticator alone.</p>
          <app-step-up action="ownership-enable" label="Create Owner Seed" [busy]="busy()" [error]="seedError()" (confirmed)="enableSeed($event)" />
        } @else {
          <p>Your Owner Seed is active. Turning it off means those actions fall back to your password and authenticator, and Owner Seed recovery stops working.</p>
          <app-step-up action="ownership-disable" label="Turn off Owner Seed" [busy]="busy()" [error]="seedError()" (confirmed)="disableSeed($event)" />
        }
      </section>

      <section class="danger-zone">
        <h2>Delete account</h2>
        <p>This permanently deletes your account, posts, comments, images and follows. It can't be undone and nobody can restore it.</p>
        <div class="field">
          <label class="f" for="dn">Type your username to confirm</label>
          <input id="dn" type="text" autocapitalize="none" spellcheck="false" [(ngModel)]="deleteName">
        </div>
        @if (deleteName === me.username) {
          <app-step-up action="delete-account" label="Delete my account permanently" [danger]="true" [busy]="busy()" [error]="deleteError()" (confirmed)="deleteAccount($event)" />
        }
      </section>
    </div>
    }
  `,
})
export class SettingsComponent implements OnInit {
  auth = inject(AuthService);
  private http = inject(HttpClient);
  private crypto = inject(CryptoService);
  private identity = inject(IdentityService);
  private keystore = inject(KeystoreService);
  private router = inject(Router);

  hasKey = signal<boolean | null>(null);
  notice = signal('');
  busy = signal(false);
  importError = signal('');
  mfa = signal<MfaResetResponse | null>(null);
  mfaError = signal('');
  mfaCode = '';
  seedWords = signal<string | null>(null);
  seedSaved = false;
  seedError = signal('');
  deleteName = '';
  deleteError = signal('');

  async ngOnInit(): Promise<void> {
    const me = this.auth.me();
    if (me) this.hasKey.set(await this.crypto.hasPrivateKey(me.username));
  }

  async importIdentity(e: Event): Promise<void> {
    const me = this.auth.me();
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!me || !file) return;
    this.importError.set('');
    try {
      const data = this.identity.parse(await file.text());
      if (data.username !== me.username) throw new Error(`This file belongs to @${data.username}, not @${me.username}.`);
      if (!data.e2eePrivateKeyJwk) throw new Error('This file has no encryption key.');
      if (data.e2eePublicKey && data.e2eePublicKey !== me.publicKey) throw new Error('This encryption key does not match your account.');
      await this.crypto.storePrivateKey(me.username, data.e2eePrivateKeyJwk);
      this.hasKey.set(true);
      this.notice.set('Encryption key imported on this device.');
    } catch (err) {
      this.importError.set(err instanceof Error ? err.message : 'Could not read that file.');
    } finally {
      (e.target as HTMLInputElement).value = '';
    }
  }

  private async run<T>(fn: () => Promise<T>, onError: (m: string) => void): Promise<T | undefined> {
    this.busy.set(true);
    onError('');
    this.notice.set('');
    try {
      return await fn();
    } catch (err) {
      onError(errorMessage(err));
      return undefined;
    } finally {
      this.busy.set(false);
    }
  }

  async startMfa(p: StepUpPayload): Promise<void> {
    const res = await this.run(() => firstValueFrom(this.http.post<MfaResetResponse>('/api/account/mfa/reset', p)), (m) => this.mfaError.set(m));
    if (res) this.mfa.set(res);
  }

  async confirmMfa(): Promise<void> {
    const done = await this.run(() => firstValueFrom(this.http.post<void>('/api/account/mfa/confirm', { code: this.mfaCode.trim() })), (m) => this.mfaError.set(m));
    if (done !== undefined) {
      this.mfa.set(null);
      this.mfaCode = '';
      this.notice.set('New authenticator is active. Update your password manager with the new secret.');
    } else {
      this.mfaCode = '';
    }
  }

  async enableSeed(p: StepUpPayload): Promise<void> {
    const res = await this.run(() => firstValueFrom(this.http.post<{ mnemonic: string }>('/api/account/ownership/enable', p)), (m) => this.seedError.set(m));
    const me = this.auth.me();
    if (res && me) {
      this.seedSaved = false;
      this.seedWords.set(res.mnemonic);
      this.auth.setMe({ ...me, ownershipEnabled: true });
    }
  }

  async disableSeed(p: StepUpPayload): Promise<void> {
    const done = await this.run(() => firstValueFrom(this.http.post<void>('/api/account/ownership/disable', p)), (m) => this.seedError.set(m));
    const me = this.auth.me();
    if (done !== undefined && me) {
      this.auth.setMe({ ...me, ownershipEnabled: false });
      this.notice.set('Owner Seed turned off.');
    }
  }

  async deleteAccount(p: StepUpPayload): Promise<void> {
    const me = this.auth.me();
    const done = await this.run(() => firstValueFrom(this.http.post<void>('/api/account/delete', p)), (m) => this.deleteError.set(m));
    if (done !== undefined && me) {
      await this.keystore.remove(me.username).catch(() => undefined);
      this.auth.clear();
      await this.router.navigateByUrl('/register');
    }
  }
}
