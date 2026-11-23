import { Injectable } from '@angular/core';

/**
 * Keeps each user's end-to-end encryption private key as a NON-EXTRACTABLE CryptoKey in IndexedDB, so page scripts
 * can use it to decrypt but can never read its bytes.
 */
@Injectable({ providedIn: 'root' })
export class KeystoreService {
  private open(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open('hush-keys', 1);
      req.onupgradeneeded = () => req.result.createObjectStore('keys');
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async save(username: string, key: CryptoKey): Promise<void> {
    const db = await this.open();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('keys', 'readwrite');
      tx.objectStore('keys').put(key, username);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    db.close();
  }

  async load(username: string): Promise<CryptoKey | null> {
    const db = await this.open();
    const key = await new Promise<CryptoKey | null>((resolve, reject) => {
      const req = db.transaction('keys').objectStore('keys').get(username);
      req.onsuccess = () => resolve((req.result as CryptoKey) ?? null);
      req.onerror = () => reject(req.error);
    });
    db.close();
    return key;
  }

  async remove(username: string): Promise<void> {
    const db = await this.open();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('keys', 'readwrite');
      tx.objectStore('keys').delete(username);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    db.close();
  }
}
