import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { CryptoService } from '../core/crypto.service';
import { IdentityService } from '../core/identity.service';
import { errorMessage } from '../core/interceptors';
import { Me, RegisterResponse } from '../core/models';
import { SecurityBannerComponent } from '../shared/security-guide.component';

type Step = 'handle' | 'password' | 'totp' | 'seed' | 'save';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink, SecurityBannerComponent],
  template: `
    <div class="col">
      <div class="page-head">
        <h1>Create your account</h1>
        <p class="lede">No email, no phone number. Hush generates your credentials; you keep them.</p>
      </div>

      <ol class="steps" aria-label="Registration progress">
        @for (s of stepList(); track s.id) {
          <li [class.on]="s.id === step()" [class.done]="s.done">{{ s.label }}</li>
        }
      </ol>

      @switch (step()) {
        @case ('handle') {
          <div class="field">
            <label class="f" for="h">Choose a username</label>
            <input id="h" type="text" autocomplete="off" autocapitalize="none" spellcheck="false" maxlength="24" [(ngModel)]="username"
                   (keydown.enter)="create()">
            <div class="hint">3 to 24 characters: lowercase letters, numbers and underscore. It's public.</div>
          </div>
          <div class="field">
            <label><input type="checkbox" [(ngModel)]="wantSeed"> Also create an Owner Seed (advanced)</label>
            <div class="hint">A 32-word master key for high-privilege actions: resetting your authenticator, deleting the account and recovering a
              lost password. Skip it and those actions rely on your password and authenticator alone. You can add it later in Settings.</div>
          </div>
          @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary" type="button" (click)="create()" [disabled]="busy() || username.length < 3">
            {{ busy() ? 'Generating keys…' : 'Generate my credentials' }}
          </button>
          <p class="small muted" style="margin-top:1rem">Already have an account? <a routerLink="/login">Sign in</a>.</p>
        }

        @case ('password') {
          <h2>Your password</h2>
          <p>Hush generated this 32-character password for <strong>{{ reg()!.username }}</strong>. It is shown once and never stored in readable form.</p>
          <div class="secret" aria-label="Generated password">{{ showPw() ? reg()!.password : mask }}</div>
          <div class="row" style="margin:.6rem 0 1rem">
            <button class="btn btn-small" type="button" (click)="showPw.set(!showPw())">{{ showPw() ? 'Hide' : 'Reveal' }}</button>
            <button class="btn btn-small" type="button" (click)="copy(reg()!.password)">{{ copied() ? 'Copied' : 'Copy' }}</button>
          </div>
          <div class="note">There is no recovery by email. If you lose this password <em>and</em> your authenticator, and you have no Owner Seed, the account is gone for good.</div>
          <div class="field">
            <label><input type="checkbox" [(ngModel)]="savedPw"> I saved this password in a safe place</label>
          </div>
          <button class="btn btn-primary" type="button" (click)="step.set('totp')" [disabled]="!savedPw">Continue to authenticator</button>
        }

        @case ('totp') {
          <h2>Set up your authenticator</h2>
          <p>Scan the code with an authenticator app (Aegis, Ente Auth, Bitwarden, KeePassXC…) or enter the key by hand. You'll need a fresh code at every sign-in.</p>
          <img class="qr" [src]="reg()!.qrCode" alt="QR code containing your authenticator secret">
          <p class="small muted" style="margin-top:.6rem">Or enter this key manually:</p>
          <div class="secret">{{ reg()!.totpSecret }}</div>
          <div class="field" style="margin-top:1.2rem">
            <label class="f" for="v">Enter the 6-digit code to confirm</label>
            <input id="v" class="code-input" type="text" inputmode="numeric" autocomplete="off" maxlength="6" [(ngModel)]="code" (keydown.enter)="verify()">
            <div class="hint">Verification is required. The account stays inactive until a valid code is entered.</div>
          </div>
          @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary" type="button" (click)="verify()" [disabled]="busy() || code.length !== 6">
            {{ busy() ? 'Verifying…' : 'Verify and activate' }}
          </button>
        }

        @case ('seed') {
          <h2>Your Owner Seed</h2>
          <p>Write these 32 words down in order and keep them offline. They are shown once. Anyone with them can reset your account's security, and without them
            nobody can.</p>
          <ol class="words">
            @for (w of seedWords(); track $index) { <li>{{ w }}</li> }
          </ol>
          <div class="row" style="margin-bottom:1rem">
            <button class="btn btn-small" type="button" (click)="copy(reg()!.ownershipMnemonic!)">{{ copied() ? 'Copied' : 'Copy words' }}</button>
          </div>
          <div class="field"><label><input type="checkbox" [(ngModel)]="savedSeed"> I stored my Owner Seed safely</label></div>
          <button class="btn btn-primary" type="button" (click)="step.set('save')" [disabled]="!savedSeed">Continue</button>
        }

        @case ('save') {
          <h2>Save your identity</h2>
          <p>The Secure Identity File holds your username, password, authenticator secret, encryption key
            @if (reg()!.ownershipMnemonic) { and Owner Seed }. Download it, move it into your password manager, then delete the download.</p>
          <div class="row" style="margin-bottom:1rem">
            <button class="btn btn-primary" type="button" (click)="download()">Download Secure Identity File</button>
            @if (downloaded()) { <span class="muted small">Downloaded</span> }
          </div>
          @if (!bannerOff()) { <app-security-banner (dismissed)="bannerOff.set(true)" /> }
          <button class="btn btn-primary" type="button" (click)="finish()">Enter Hush</button>
        }
      }
    </div>
  `,
})
export class RegisterComponent {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private crypto = inject(CryptoService);
  private identity = inject(IdentityService);
  private router = inject(Router);

  step = signal<Step>('handle');
  reg = signal<RegisterResponse | null>(null);
  username = '';
  wantSeed = false;
  code = '';
  savedPw = false;
  savedSeed = false;
  readonly mask = '•'.repeat(32);
  showPw = signal(false);
  copied = signal(false);
  downloaded = signal(false);
  bannerOff = signal(false);
  busy = signal(false);
  error = signal('');
  private privateJwk: JsonWebKey | null = null;
  private publicKey = '';

  stepList = () => {
    const order: { id: Step; label: string }[] = [
      { id: 'handle', label: 'Username' },
      { id: 'password', label: 'Password' },
      { id: 'totp', label: 'Authenticator' },
      ...(this.wantSeed ? [{ id: 'seed' as Step, label: 'Owner Seed' }] : []),
      { id: 'save', label: 'Save' },
    ];
    const idx = order.findIndex((s) => s.id === this.step());
    return order.map((s, i) => ({ ...s, done: i < idx }));
  };

  seedWords = () => (this.reg()?.ownershipMnemonic ?? '').split(' ');

  async create(): Promise<void> {
    if (this.busy() || this.username.length < 3) return;
    this.busy.set(true);
    this.error.set('');
    try {
      // The end-to-end encryption key pair is created here, in the browser; only the public half is sent.
      const keys = await this.crypto.generateIdentityKeys();
      this.privateJwk = keys.privateJwk;
      this.publicKey = keys.publicKey;
      const res = await firstValueFrom(this.http.post<RegisterResponse>('/api/auth/register', {
        username: this.username.trim().toLowerCase(),
        publicKey: keys.publicKey,
        enableOwnership: this.wantSeed,
      }));
      await this.crypto.storePrivateKey(res.username, keys.privateJwk);
      this.reg.set(res);
      this.step.set('password');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  async verify(): Promise<void> {
    const r = this.reg();
    if (!r || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const me = await firstValueFrom(this.http.post<Me>('/api/auth/register/verify', {
        username: r.username, password: r.password, code: this.code.trim(),
      }));
      this.auth.setMe(me);
      this.step.set(r.ownershipMnemonic ? 'seed' : 'save');
    } catch (e) {
      this.error.set(errorMessage(e));
      this.code = '';
    } finally {
      this.busy.set(false);
    }
  }

  download(): void {
    const r = this.reg();
    if (!r) return;
    const json = this.identity.build({
      username: r.username,
      password: r.password,
      totpSecret: r.totpSecret,
      ownershipMnemonic: r.ownershipMnemonic ?? undefined,
      e2eePublicKey: this.publicKey,
      e2eePrivateKeyJwk: this.privateJwk ?? undefined,
    });
    this.identity.download(json, r.username);
    this.downloaded.set(true);
  }

  async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch { /* clipboard unavailable: the text is selectable */ }
  }

  finish(): void {
    sessionStorage.setItem('hush.showGuide', '1');
    this.reg.set(null);
    this.privateJwk = null;
    this.router.navigateByUrl('/feed');
  }
}
