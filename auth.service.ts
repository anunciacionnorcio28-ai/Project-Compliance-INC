import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Me } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  /** undefined = not loaded yet, null = signed out */
  readonly me = signal<Me | null | undefined>(undefined);

  async ensureLoaded(): Promise<Me | null> {
    if (this.me() !== undefined) return this.me() ?? null;
    try {
      this.me.set(await firstValueFrom(this.http.get<Me>('/api/account/me')));
    } catch {
      this.me.set(null);
    }
    return this.me() ?? null;
  }

  async login(username: string, password: string, code: string): Promise<Me> {
    const me = await firstValueFrom(this.http.post<Me>('/api/auth/login', { username, password, code }));
    this.me.set(me);
    return me;
  }

  setMe(me: Me): void {
    this.me.set(me);
  }

  clear(): void {
    this.me.set(null);
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', {}));
    } finally {
      this.clear();
    }
  }
}
