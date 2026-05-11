import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ThemeService } from './shared/services/theme.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="hdr">
      <div class="hdr-inner container">
        <a routerLink="/" class="brand">AIBridge</a>
        <nav class="nav" aria-label="Main">
          <a routerLink="/" routerLinkActive="active"
             [routerLinkActiveOptions]="{ exact: true }" class="nav-link">Home</a>
          <a routerLink="/playground" routerLinkActive="active" class="nav-link">Playground</a>
          <a routerLink="/admin" routerLinkActive="active" class="nav-link">Admin</a>
        </nav>
        <button class="theme-btn" (click)="theme.toggle()" [attr.aria-label]="'Switch to ' + (theme.current() === 'light' ? 'dark' : 'light') + ' mode'">
          {{ theme.current() === 'light' ? '◑' : '◐' }}
        </button>
      </div>
    </header>
    <main class="main"><router-outlet /></main>
    <footer class="ftr">
      <div class="ftr-inner container">\u00a9 Shoaib Rayeen 2026 - Present</div>
    </footer>
  `,
  styles: `
    .hdr {
      position: sticky; top: 0; z-index: 40;
      background: var(--header-bg);
      border-bottom: 1px solid var(--border);
      box-shadow: var(--shadow-sm);
    }
    .hdr-inner {
      display: flex; align-items: center; gap: 1rem;
      min-height: var(--header-h);
    }
    .brand {
      font-weight: 700; font-size: 1rem; letter-spacing: -0.02em;
      color: var(--text); text-decoration: none;
    }
    .brand:hover { color: var(--primary); }
    .nav { display: flex; align-items: center; gap: 0.125rem; margin-right: auto; }
    .nav-link {
      padding: 0.35rem 0.6rem; font-size: 0.8125rem; font-weight: 500;
      color: var(--text-secondary); text-decoration: none;
      border-radius: var(--radius-sm); transition: all 0.15s ease;
    }
    .nav-link:hover { color: var(--text); background: var(--bg-surface-hover); }
    .nav-link.active { color: var(--primary); background: var(--primary-muted); }
    .theme-btn {
      width: 2rem; height: 2rem; border-radius: 50%;
      border: 1px solid var(--border); background: var(--bg-surface);
      color: var(--text); font-size: 1rem; cursor: pointer;
      display: grid; place-items: center; transition: all 0.15s;
    }
    .theme-btn:hover { background: var(--bg-surface-hover); border-color: var(--border-strong); }
    .main { min-height: calc(100vh - var(--header-h) - var(--footer-h)); }
    .ftr {
      background: var(--bg-inset);
      border-top: 1px solid var(--border);
      height: var(--footer-h);
      display: flex; align-items: center;
    }
    .ftr-inner {
      font-size: 0.75rem; color: var(--text-muted); text-align: center;
    }
  `,
})
export class AppComponent {
  readonly theme = inject(ThemeService);
}
