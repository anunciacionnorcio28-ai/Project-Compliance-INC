import { Component, input, output } from '@angular/core';

/** "Security Best Practices": recommends FOSS password managers for the generated credentials and TOTP secret. */
@Component({
  selector: 'app-security-banner',
  template: `
    <aside class="banner" role="note" aria-label="Security recommendation">
      <div>
        <p><strong>Keep your credentials in a password manager.</strong></p>
        <p class="small">Hush can't recover a lost password or authenticator. Store them in a free, open-source manager such as
          Bitwarden or KeePassXC. <a href="/security">Read the security guide</a>.</p>
      </div>
      <button class="btn btn-quiet btn-small" type="button" (click)="dismissed.emit()">Dismiss</button>
    </aside>
  `,
})
export class SecurityBannerComponent {
  dismissed = output<void>();
}

@Component({
  selector: 'app-security-guide',
  template: `
    <div class="stack">
      <section>
        <h2>Store everything in a free and open-source password manager</h2>
        <p>Hush generates a 32-character password for you and gives you a TOTP secret. Together they are your only way in. Save both
          somewhere safe the moment you see them.</p>
        <ul>
          <li><strong>Bitwarden</strong>: open-source, can be self-hosted, has built-in TOTP storage.</li>
          <li><strong>KeePassXC</strong>: offline, no account, stores the password and the TOTP secret in one encrypted file.</li>
        </ul>
        <p class="small muted">Prefer two separate places for the password and the TOTP secret if you can. Then losing one device doesn't lose both.</p>
      </section>
      <section>
        <h3>What to save</h3>
        <ul>
          <li>Your username and generated password.</li>
          <li>The TOTP secret (or the QR code). Add it to your authenticator app and to your password manager.</li>
          <li>Your Owner Seed words, if you created them. Keep them offline, ideally on paper.</li>
          <li>The Secure Identity File. It contains all of the above, so treat it like a password and delete it from Downloads once it's stored.</li>
        </ul>
      </section>
      <section>
        <h3>No recovery by design</h3>
        <p>Hush stores no email address or phone number. If you lose both your password and your authenticator and you have no Owner Seed,
          the account is gone for good. With an Owner Seed you can reset your password and authenticator from the Recover page.</p>
      </section>
      <section>
        <h3>Good habits</h3>
        <ul>
          <li>Use end-to-end encrypted posts for anything you only want specific followers to read. Keep the identity file so you can read them on a new device.</li>
          <li>Use Tor Browser or a VPN if you want to hide your network location. Hush never logs IP addresses, but your network still sees you connect.</li>
          <li>Set a time-to-live on posts you don't want to exist forever.</li>
        </ul>
      </section>
    </div>
  `,
})
export class SecurityGuideComponent {
  readonly compact = input(false);
}
