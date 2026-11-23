import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'feed' },
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./features/login.component').then((m) => m.LoginComponent) },
  { path: 'register', canActivate: [guestGuard], loadComponent: () => import('./features/register.component').then((m) => m.RegisterComponent) },
  { path: 'recover', canActivate: [guestGuard], loadComponent: () => import('./features/recover.component').then((m) => m.RecoverComponent) },
  { path: 'feed', canActivate: [authGuard], loadComponent: () => import('./features/feed.component').then((m) => m.FeedComponent) },
  { path: 'discover', canActivate: [authGuard], loadComponent: () => import('./features/discover.component').then((m) => m.DiscoverComponent) },
  { path: 'u/:username', canActivate: [authGuard], loadComponent: () => import('./features/profile.component').then((m) => m.ProfileComponent) },
  { path: 'settings', canActivate: [authGuard], loadComponent: () => import('./features/settings.component').then((m) => m.SettingsComponent) },
  { path: 'security', loadComponent: () => import('./features/security.component').then((m) => m.SecurityPageComponent) },
  { path: '**', redirectTo: 'feed' },
];
