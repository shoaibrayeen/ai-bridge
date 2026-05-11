import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="container dash">
      <h1 class="page-title">Admin Dashboard</h1>
      <p class="subtitle">Manage LLM configurations, providers, and run diagnostics.</p>
      <div class="grid">
        <a routerLink="/admin/llm-configs" class="tile">
          <span class="tile-icon">⚙</span>
          <span class="tile-label">LLM Configs</span>
          <span class="tile-desc">Create, edit, and test model configurations</span>
        </a>
        <a routerLink="/admin/llm-providers" class="tile">
          <span class="tile-icon">🔌</span>
          <span class="tile-label">Providers</span>
          <span class="tile-desc">Manage LLM provider definitions</span>
        </a>
        <a routerLink="/admin/load-test" class="tile">
          <span class="tile-icon">⚡</span>
          <span class="tile-label">Load Test</span>
          <span class="tile-desc">Stress test models with parallel requests</span>
        </a>
        <a routerLink="/admin/health" class="tile">
          <span class="tile-icon">♥</span>
          <span class="tile-label">Health</span>
          <span class="tile-desc">View dependency status and latency</span>
        </a>
      </div>
    </div>
  `,
  styles: `
    .dash { padding: 1.5rem 1.25rem 2rem; }
    .page-title { font-size: 1.25rem; font-weight: 600; color: var(--text); margin: 0; }
    .subtitle { color: var(--text-muted); font-size: 0.8125rem; margin: 0.25rem 0 1.25rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.75rem; }
    .tile {
      display: flex; flex-direction: column; gap: 0.25rem;
      padding: 1rem 1.1rem; border-radius: var(--radius-lg);
      background: var(--bg-surface); border: 1px solid var(--border);
      text-decoration: none; transition: all 0.15s;
    }
    .tile:hover { border-color: var(--primary); box-shadow: var(--shadow-md); }
    .tile-icon { font-size: 1.5rem; }
    .tile-label { font-weight: 600; font-size: 0.875rem; color: var(--text); }
    .tile-desc { font-size: 0.75rem; color: var(--text-muted); }
  `,
})
export class AdminDashboardComponent {}
