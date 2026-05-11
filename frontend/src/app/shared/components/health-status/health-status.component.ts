import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { startWith, switchMap } from 'rxjs/operators';

export interface HealthCard {
  id: string; label: string; status: 'UP' | 'DOWN'; latencyMs: number | null;
}

interface HealthApiPayload {
  database?: { status?: string; latency_ms?: number };
  cache?: { status?: string; latency_ms?: number };
  components?: Array<{ name?: string; status?: string; latency_ms?: number }>;
  checks?: Record<string, { status?: string; latency_ms?: number; healthy?: boolean }>;
}

@Component({
  selector: 'app-health-status',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="container pg">
      <div class="page-header">
        <div>
          <h1 class="page-title">System Health</h1>
          <p class="text-muted">Live dependency checks from <code class="code-inline">/admin/api/health</code></p>
        </div>
        <div class="meta">
          @if (lastUpdated) { <span class="badge badge-primary">Updated {{ lastUpdated | date:'medium' }}</span> }
          <span class="badge badge-neutral">Auto-refresh 30s</span>
        </div>
      </div>

      @if (loadError) {
        <div class="alert alert-danger">{{ loadError }}</div>
      }

      <div class="grid">
        @for (c of cards; track c.id) {
          <div class="health-card" [class.up]="c.status === 'UP'" [class.down]="c.status === 'DOWN'">
            <div class="hc-top">
              <h2 class="hc-name">{{ c.label }}</h2>
              <span class="badge" [class.badge-success]="c.status === 'UP'" [class.badge-danger]="c.status === 'DOWN'">{{ c.status }}</span>
            </div>
            <p class="hc-latency">
              @if (c.latencyMs != null) {
                <span class="hc-val">{{ c.latencyMs | number:'1.0-0' }}</span>
                <span class="hc-unit">ms</span>
              } @else {
                <span class="text-muted">Latency n/a</span>
              }
            </p>
          </div>
        } @empty {
          @if (!loading && !loadError) { <p class="text-muted">No health checks returned.</p> }
        }
      </div>

      @if (loading && cards.length === 0) {
        <div class="spinner-row mt-2"><div class="spinner"></div><span class="text-muted">Fetching health…</span></div>
      }
    </div>
  `,
  styles: [`
    .pg { padding: 1.25rem 1.25rem 2rem; max-width: 960px; }
    .meta { display: flex; flex-wrap: wrap; gap: 0.3rem; }
    .code-inline { font-size: 0.75rem; padding: 0.1rem 0.3rem; border-radius: var(--radius-sm); background: var(--bg-inset); color: var(--text); }
    .alert { padding: 0.5rem 0.65rem; border-radius: var(--radius); font-size: 0.8125rem; margin-bottom: 0.75rem; }
    .alert-danger { background: var(--danger-muted); color: var(--danger); border: 1px solid var(--danger); }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.75rem; }
    .health-card {
      border-radius: var(--radius-lg); padding: 0.85rem 1rem;
      border: 1px solid var(--border); background: var(--bg-surface);
      box-shadow: var(--shadow-sm); position: relative; overflow: hidden;
    }
    .health-card::before {
      content: ''; position: absolute; inset: 0 0 auto; height: 3px; background: var(--border);
    }
    .health-card.up::before { background: var(--success); }
    .health-card.down::before { background: var(--danger); }
    .hc-top { display: flex; align-items: center; justify-content: space-between; gap: 0.4rem; }
    .hc-name { margin: 0; font-size: 0.9375rem; font-weight: 600; color: var(--text); }
    .hc-latency { margin: 0.5rem 0 0; color: var(--text-muted); font-size: 0.8125rem; }
    .hc-val { font-size: 1.375rem; font-weight: 700; color: var(--text); letter-spacing: -0.02em; }
    .hc-unit { margin-left: 0.1rem; font-size: 0.8125rem; font-weight: 600; color: var(--text-faint); }
    .spinner-row { display: flex; align-items: center; gap: 0.5rem; }
  `],
})
export class HealthStatusComponent implements OnInit {
  cards: HealthCard[] = [];
  lastUpdated: Date | null = null;
  loadError: string | null = null;
  loading = false;

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef, private destroyRef: DestroyRef) {}

  ngOnInit(): void {
    interval(30_000).pipe(
      takeUntilDestroyed(this.destroyRef), startWith(0),
      switchMap(() => { this.loading = this.cards.length === 0; this.cdr.markForCheck(); return this.http.get<HealthApiPayload>('/admin/api/health'); })
    ).subscribe({
      next: (payload) => { this.loadError = null; this.cards = this.normalize(payload); this.lastUpdated = new Date(); this.loading = false; this.cdr.markForCheck(); },
      error: (err: HttpErrorResponse) => { this.loading = false; this.loadError = err.error?.message ?? err.message ?? 'Could not load health status.'; this.cdr.markForCheck(); },
    });
  }

  private normalize(payload: HealthApiPayload): HealthCard[] {
    const out: HealthCard[] = [];
    const seen = new Set<string>();
    const push = (id: string, label: string, raw?: { status?: string; latency_ms?: number; healthy?: boolean }) => {
      if (!raw || seen.has(id)) return;
      seen.add(id);
      const status: 'UP' | 'DOWN' = this.isUp(raw) ? 'UP' : 'DOWN';
      out.push({ id, label, status, latencyMs: typeof raw.latency_ms === 'number' ? raw.latency_ms : null });
    };
    push('database', 'Database', payload.database);
    push('cache', 'Cache', payload.cache);
    if (payload.components?.length) for (const c of payload.components) push((c.name ?? 'Component').toLowerCase().replace(/\s+/g, '-'), c.name ?? 'Component', c);
    if (payload.checks) for (const [key, val] of Object.entries(payload.checks)) push(`check-${key}`, key.charAt(0).toUpperCase() + key.slice(1), val);
    return out;
  }

  private isUp(raw: { status?: string; healthy?: boolean }): boolean {
    if (raw.healthy === true) return true;
    if (raw.healthy === false) return false;
    const s = (raw.status ?? '').toString().trim().toLowerCase();
    return ['up', 'healthy', 'ok', 'online', 'pass', 'passed'].includes(s);
  }
}
