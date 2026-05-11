import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

interface AuthTokenResponse {
  token: string;
  expires_in_minutes: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private static readonly TOKEN_KEY = 'aibridge_token';
  private static readonly EXPIRY_KEY = 'aibridge_token_expiry';

  async login(apiKey: string): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<AuthTokenResponse>('/api/auth/token', { api_key: apiKey })
    );
    const expiresAt = Date.now() + res.expires_in_minutes * 60_000;
    localStorage.setItem(AuthService.TOKEN_KEY, res.token);
    localStorage.setItem(AuthService.EXPIRY_KEY, String(expiresAt));
  }

  getToken(): string | null {
    const token = localStorage.getItem(AuthService.TOKEN_KEY);
    const expiry = localStorage.getItem(AuthService.EXPIRY_KEY);
    if (!token || !expiry) return null;
    if (Date.now() > Number(expiry)) {
      this.clearStorage();
      return null;
    }
    return token;
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  logout(): void {
    this.clearStorage();
    this.router.navigate(['/login']);
  }

  private clearStorage(): void {
    localStorage.removeItem(AuthService.TOKEN_KEY);
    localStorage.removeItem(AuthService.EXPIRY_KEY);
  }
}
