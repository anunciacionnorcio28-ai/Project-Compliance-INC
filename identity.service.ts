import { Injectable } from '@angular/core';
import { IdentityFile } from './models';

/** Builds, downloads and parses the "Secure Identity File" (everything needed to get back into the account). */
@Injectable({ providedIn: 'root' })
export class IdentityService {
  build(data: Omit<IdentityFile, 'format'>): string {
    const file: IdentityFile = { format: 'hush-identity-v1', ...data };
    return JSON.stringify(file, null, 2);
  }

  download(json: string, username: string): void {
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `hush-identity-${username}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  parse(text: string): IdentityFile {
    const data = JSON.parse(text) as IdentityFile;
    if (data?.format !== 'hush-identity-v1' || !data.username) throw new Error('This is not a Hush identity file.');
    return data;
  }
}
