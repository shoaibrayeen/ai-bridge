import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="hero">
      <div class="hero-inner container">
        <h1 class="hero-title">AI Bridge</h1>
        <p class="hero-desc">
          A unified LLM gateway that manages multiple providers, standardizes
          input/output to the OpenAI format, and delivers multi-tenant routing
          with built-in rate pacing and automatic failover.
        </p>
        <div class="tiles">
          <a routerLink="/admin" class="tile">
            <span class="tile-icon">&#9881;</span>
            <span class="tile-label">Configure</span>
            <span class="tile-desc">Manage LLM providers, configs, and features through the admin dashboard</span>
          </a>
          <a routerLink="/admin/load-test" class="tile">
            <span class="tile-icon">&#9889;</span>
            <span class="tile-label">Load Test</span>
            <span class="tile-desc">Stress test your LLMs with parallel requests and inspect per-request latency</span>
          </a>
          <a routerLink="/playground" class="tile">
            <span class="tile-icon">&#9654;</span>
            <span class="tile-label">Playground</span>
            <span class="tile-desc">Try out configured models with system prompts and user instructions</span>
          </a>
        </div>
      </div>
    </div>
  `,
  styles: `
    .hero {
      display: flex; align-items: center; justify-content: center;
      min-height: calc(100vh - var(--header-h) - var(--footer-h));
      padding: 2rem 0;
    }
    .hero-inner { text-align: center; max-width: 680px; }
    .hero-title {
      font-size: 2.5rem; font-weight: 800; letter-spacing: -0.03em;
      color: var(--text); margin: 0 0 0.5rem;
      background: linear-gradient(135deg, var(--primary), var(--success));
      -webkit-background-clip: text; -webkit-text-fill-color: transparent;
      background-clip: text;
    }
    .hero-desc {
      font-size: 1rem; line-height: 1.65; color: var(--text-secondary);
      margin: 0 auto 2rem; max-width: 560px;
    }
    .tiles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
    .tile {
      display: flex; flex-direction: column; align-items: center; gap: 0.4rem;
      padding: 1.5rem 1.25rem; border-radius: var(--radius-lg);
      background: var(--bg-surface); border: 1px solid var(--border);
      text-decoration: none; transition: all 0.2s ease;
      box-shadow: var(--shadow-sm);
    }
    .tile:hover {
      border-color: var(--primary); box-shadow: var(--shadow-md);
      transform: translateY(-2px);
    }
    .tile-icon { font-size: 2rem; line-height: 1; }
    .tile-label { font-weight: 700; font-size: 1rem; color: var(--text); }
    .tile-desc { font-size: 0.8125rem; color: var(--text-muted); line-height: 1.4; }
    @media (max-width: 640px) {
      .tiles { grid-template-columns: 1fr; }
      .hero-title { font-size: 1.75rem; }
    }
  `,
})
export class HomeComponent {}
