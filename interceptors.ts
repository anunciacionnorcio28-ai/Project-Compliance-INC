import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Adds the anti-CSRF header to every API call and drops the session state when the API says 401. */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const request = req.url.startsWith('/api/')
    ? req.clone({ setHeaders: { 'X-Requested-With': 'XMLHttpRequest' }, withCredentials: true })
    : req;
  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthCall = req.url.startsWith('/api/auth/');
      if (err.status === 401 && !isAuthCall && auth.me()) {
        auth.clear();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};

export function errorMessage(err: unknown, fallback = 'Something went wrong. Try again.'): string {
  const e = err as HttpErrorResponse;
  if (e?.status === 0) return 'Can\'t reach the server.';
  return e?.error?.error ?? fallback;
}
