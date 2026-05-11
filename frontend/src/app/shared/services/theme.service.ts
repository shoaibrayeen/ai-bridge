import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'aibridge-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly current = signal<Theme>(this.resolve());

  toggle(): void {
    const next: Theme = this.current() === 'light' ? 'dark' : 'light';
    this.apply(next);
  }

  private resolve(): Theme {
    const stored = localStorage.getItem(STORAGE_KEY) as Theme | null;
    if (stored === 'light' || stored === 'dark') {
      this.applyDom(stored);
      return stored;
    }
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme: Theme = prefersDark ? 'dark' : 'light';
    this.applyDom(theme);
    return theme;
  }

  private apply(theme: Theme): void {
    localStorage.setItem(STORAGE_KEY, theme);
    this.applyDom(theme);
    this.current.set(theme);
  }

  private applyDom(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}
