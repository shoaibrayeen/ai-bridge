import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../shared/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="login-page">
      <div class="login-card">
        <h1 class="login-title">AI Bridge</h1>
        <p class="login-desc">Enter your API key to continue</p>
        <form (ngSubmit)="onSubmit()" class="login-form">
          <label class="label" for="apiKey">API Key</label>
          <input
            id="apiKey"
            type="password"
            class="input"
            [(ngModel)]="apiKey"
            name="apiKey"
            placeholder="Enter API key"
            autocomplete="off"
            [disabled]="loading()"
          />
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
          <button type="submit" class="btn" [disabled]="loading() || !apiKey">
            {{ loading() ? 'Authenticating...' : 'Sign In' }}
          </button>
        </form>
      </div>
    </div>
  `,
  styles: `
    .login-page {
      display: flex; align-items: center; justify-content: center;
      min-height: calc(100vh - var(--header-h) - var(--footer-h));
      padding: 2rem 0;
    }
    .login-card {
      width: 100%; max-width: 380px; padding: 2rem;
      border-radius: var(--radius-lg); background: var(--bg-surface);
      border: 1px solid var(--border); box-shadow: var(--shadow-md);
      text-align: center;
    }
    .login-title {
      font-size: 1.75rem; font-weight: 800; margin: 0 0 0.25rem;
      background: linear-gradient(135deg, var(--primary), var(--success));
      -webkit-background-clip: text; -webkit-text-fill-color: transparent;
      background-clip: text;
    }
    .login-desc {
      font-size: 0.875rem; color: var(--text-secondary); margin: 0 0 1.5rem;
    }
    .login-form { display: flex; flex-direction: column; gap: 0.75rem; text-align: left; }
    .label { font-size: 0.8125rem; font-weight: 600; color: var(--text); }
    .input {
      width: 100%; padding: 0.5rem 0.75rem; font-size: 0.875rem;
      border: 1px solid var(--border); border-radius: var(--radius-sm);
      background: var(--bg); color: var(--text); outline: none;
      transition: border-color 0.15s;
      box-sizing: border-box;
    }
    .input:focus { border-color: var(--primary); }
    .input:disabled { opacity: 0.6; }
    .btn {
      padding: 0.6rem; font-size: 0.875rem; font-weight: 600;
      border: none; border-radius: var(--radius-sm); cursor: pointer;
      background: var(--primary); color: #fff; transition: opacity 0.15s;
    }
    .btn:hover:not(:disabled) { opacity: 0.9; }
    .btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .error { font-size: 0.8125rem; color: var(--danger); margin: 0; }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  apiKey = '';
  loading = signal(false);
  error = signal('');

  async onSubmit(): Promise<void> {
    if (!this.apiKey) return;
    this.loading.set(true);
    this.error.set('');
    try {
      await this.auth.login(this.apiKey);
      this.router.navigate(['/']);
    } catch {
      this.error.set('Invalid API key or authentication failed');
    } finally {
      this.loading.set(false);
    }
  }
}
