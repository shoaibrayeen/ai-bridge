import { ScrollingModule } from '@angular/cdk/scrolling';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { interval, Subscription } from 'rxjs';
import { startWith, switchMap, takeWhile } from 'rxjs/operators';
import {
  LoadTestApiService, LoadTestResponse, LoadTestResultItem,
} from '../../services/load-test-api.service';
import { LlmConfig, PlaygroundApiService } from '../../../playground/services/playground-api.service';

@Component({
  selector: 'app-load-test',
  standalone: true,
  imports: [CommonModule, FormsModule, ScrollingModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="container pg">
      <div class="page-header">
        <div>
          <h1 class="page-title">Load Testing</h1>
          <p class="text-muted">Stress your LLM route with parallel requests and inspect latency.</p>
        </div>
      </div>

      <section class="card">
        <h2 class="card-title">Run Configuration</h2>
        <div class="grid">
          <div class="form-group span-2">
            <label class="form-label">LLM Configuration</label>
            <select class="form-select" [(ngModel)]="selectedConfigId"
                    (ngModelChange)="onConfigChange($event)" [disabled]="running || configs.length === 0">
              <option value="" disabled>Select configuration…</option>
              @for (c of configs; track configKey(c)) {
                <option [value]="configKey(c)">{{ c.model }} — {{ c.provider }}</option>
              }
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Parallel requests (1–500)</label>
            <input type="number" class="form-input" min="1" max="500"
                   [(ngModel)]="parallel" [disabled]="running" />
          </div>
          <div class="form-group span-3">
            <label class="form-label">Prompt</label>
            <textarea class="form-textarea" rows="3" [(ngModel)]="prompt"
                      [disabled]="running" placeholder="Enter the prompt…"></textarea>
          </div>
        </div>
        @if (configLoadError) { <p class="text-danger mt-1">{{ configLoadError }}</p> }
        <div class="btn-row mt-1">
          <button type="button" class="btn btn-primary"
                  (click)="runTest()" [disabled]="running || !selectedConfig || parallel < 1 || parallel > 500">Run Test</button>
          @if (lastRunId) { <span class="text-muted" style="font-size:0.75rem">Last run: {{ lastRunId }}</span> }
        </div>
      </section>

      @if (running) {
        <section class="card mt-2">
          <div class="spinner-row">
            <div class="spinner"></div>
            <div>
              <p style="font-weight:600">Running… polling for results</p>
              @if (pollSnapshot?.status) {
                <span class="badge badge-neutral">{{ pollSnapshot?.status }}</span>
              }
            </div>
          </div>
        </section>
      }

      @if (runError && !running) {
        <section class="card mt-2" style="border-color:var(--danger)">
          <h3 class="card-title text-danger">Run Failed</h3>
          <p>{{ runError }}</p>
        </section>
      }

      @if (finalResult && !running) {
        <section class="card mt-2">
          <div class="page-header mb-1">
            <h2 class="card-title" style="margin:0">Summary</h2>
            <button type="button" class="btn btn-secondary btn-sm" (click)="exportCsv()">Export CSV</button>
          </div>
          @if (finalResult.summary; as s) {
            <div class="stat-grid">
              <div class="stat"><span class="stat-label">Total</span><span class="stat-value">{{ s.total | number }}</span></div>
              <div class="stat stat-ok"><span class="stat-label">Success</span><span class="stat-value">{{ s.success | number }}</span></div>
              <div class="stat stat-bad"><span class="stat-label">Failed</span><span class="stat-value">{{ s.failed | number }}</span></div>
              <div class="stat"><span class="stat-label">Avg</span><span class="stat-value">{{ s.avg_latency_ms | number:'1.0-0' }} ms</span></div>
              <div class="stat"><span class="stat-label">P50</span><span class="stat-value">{{ s.p50_ms | number:'1.0-0' }} ms</span></div>
              <div class="stat"><span class="stat-label">P95</span><span class="stat-value">{{ s.p95_ms | number:'1.0-0' }} ms</span></div>
              <div class="stat"><span class="stat-label">P99</span><span class="stat-value">{{ s.p99_ms | number:'1.0-0' }} ms</span></div>
              <div class="stat"><span class="stat-label">Max</span><span class="stat-value">{{ s.max_latency_ms | number:'1.0-0' }} ms</span></div>
            </div>
          }
        </section>

        <section class="card mt-2">
          <div class="page-header mb-1">
            <h2 class="card-title" style="margin:0">Results</h2>
            <span class="text-muted" style="font-size:0.75rem">{{ tableRows.length | number }} rows</span>
          </div>
          <div class="results-table-wrap">
            <div class="results-header">
              <div class="col col-idx">#</div><div class="col col-st">Status</div>
              <div class="col col-lat">Latency</div><div class="col col-http">HTTP</div>
              <div class="col col-tok">Tokens</div><div class="col col-err">Error</div>
            </div>
            <cdk-virtual-scroll-viewport class="results-vp" [itemSize]="40">
              <div *cdkVirtualFor="let row of tableRows; let idx = index; trackBy: trackByRow" class="results-row">
                <div class="col col-idx">{{ idx + 1 }}</div>
                <div class="col col-st">
                  @if (row.status === 'SUCCESS') { <span class="text-success">✓</span> }
                  @else { <span class="text-danger">✕</span> }
                </div>
                <div class="col col-lat" [class.text-success]="row.latency_ms < 1000"
                     [class.text-warning]="row.latency_ms >= 1000 && row.latency_ms <= 3000"
                     [class.text-danger]="row.latency_ms > 3000">
                  {{ row.latency_ms | number:'1.0-0' }} ms
                </div>
                <div class="col col-http">{{ row.http_status ?? '—' }}</div>
                <div class="col col-tok">{{ row.tokens != null ? (row.tokens | number) : '—' }}</div>
                <div class="col col-err" [title]="row.error || ''">{{ row.error || '—' }}</div>
              </div>
            </cdk-virtual-scroll-viewport>
          </div>
        </section>
      }

      <section class="card mt-2 collapse-card">
        <button type="button" class="collapse-toggle" (click)="recentOpen = !recentOpen"
                [attr.aria-expanded]="recentOpen">
          <span>Recent Runs</span>
          <span class="chev" [class.open]="recentOpen">▾</span>
        </button>
        @if (recentOpen) {
          @if (recentLoading) { <p class="text-muted" style="padding:0.75rem">Loading…</p> }
          @else if (recentError) { <p class="text-danger" style="padding:0.75rem">{{ recentError }}</p> }
          @else if (recentRuns.length === 0) { <p class="text-muted" style="padding:0.75rem">No recent runs.</p> }
          @else {
            <ul class="recent-list">
              @for (r of recentRuns; track r.run_id) {
                <li class="recent-item">
                  <div class="gap-row">
                    <code style="font-size:0.75rem">{{ r.run_id }}</code>
                    <span class="badge" [class.badge-success]="r.status === 'completed'"
                          [class.badge-neutral]="r.status !== 'completed'">{{ r.status }}</span>
                  </div>
                  @if (r.summary) {
                    <div class="text-muted" style="font-size:0.75rem; margin-top:0.15rem">
                      {{ r.summary.success }}/{{ r.summary.total }} ok · p95 {{ r.summary.p95_ms | number:'1.0-0' }} ms
                    </div>
                  }
                </li>
              }
            </ul>
          }
        }
      </section>
    </div>
  `,
  styles: [`
    .pg { padding: 1.25rem 1.25rem 2rem; max-width: 1100px; }
    .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.6rem 0.75rem; }
    .span-2 { grid-column: span 2; } .span-3 { grid-column: 1 / -1; }
    .btn-row { display: flex; align-items: center; gap: 0.5rem; margin-top: 0.75rem; }
    .spinner-row { display: flex; align-items: center; gap: 0.75rem; }

    .stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 0.5rem; }
    .stat {
      padding: 0.6rem 0.7rem; border-radius: var(--radius);
      background: var(--bg-inset); border: 1px solid var(--border);
    }
    .stat-ok { border-color: var(--success); }
    .stat-bad { border-color: var(--danger); }
    .stat-label { display: block; font-size: 0.625rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--text-muted); margin-bottom: 0.15rem; }
    .stat-value { font-size: 1rem; font-weight: 650; color: var(--text); }
    .stat-ok .stat-value { color: var(--success); }
    .stat-bad .stat-value { color: var(--danger); }

    .results-table-wrap { border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden; }
    .results-header, .results-row {
      display: grid; grid-template-columns: 48px 64px 100px 70px 70px 1fr;
      align-items: center; gap: 0.3rem; padding: 0 0.5rem;
    }
    .results-header {
      height: 36px; font-size: 0.625rem; text-transform: uppercase; letter-spacing: 0.06em;
      color: var(--text-muted); background: var(--bg-inset); border-bottom: 1px solid var(--border);
    }
    .results-row { height: 40px; border-bottom: 1px solid var(--border); font-size: 0.8125rem; }
    .results-row:last-child { border-bottom: none; }
    .results-vp { height: 320px; }
    .col-err { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    .collapse-card { padding: 0; overflow: hidden; }
    .collapse-toggle {
      width: 100%; border: none; background: var(--bg-inset); color: var(--text);
      padding: 0.7rem 0.9rem; display: flex; justify-content: space-between; align-items: center;
      cursor: pointer; font-size: 0.875rem; font-weight: 600;
    }
    .collapse-toggle:hover { background: var(--bg-surface-hover); }
    .chev { transition: transform 0.15s; color: var(--text-muted); }
    .chev.open { transform: rotate(180deg); }
    .recent-list { list-style: none; margin: 0; padding: 0.3rem 0; border-top: 1px solid var(--border); }
    .recent-item { padding: 0.5rem 0.9rem; border-bottom: 1px solid var(--border); }
    .recent-item:last-child { border-bottom: none; }
  `],
})
export class LoadTestComponent implements OnInit, OnDestroy {
  configs: LlmConfig[] = [];
  selectedConfigId = '';
  selectedConfig: LlmConfig | null = null;
  parallel = 10;
  prompt = 'You are a concise assistant. Reply with a single short sentence of acknowledgement.';
  running = false;
  lastRunId: string | null = null;
  pollSnapshot: LoadTestResponse | null = null;
  finalResult: LoadTestResponse | null = null;
  tableRows: LoadTestResultItem[] = [];
  runError: string | null = null;
  configLoadError: string | null = null;

  recentRuns: LoadTestResponse[] = [];
  recentLoading = false;
  recentError: string | null = null;

  private pollSub?: Subscription;

  constructor(
    private loadTestApi: LoadTestApiService,
    private playgroundApi: PlaygroundApiService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnDestroy(): void { this.pollSub?.unsubscribe(); }

  ngOnInit(): void {
    this.playgroundApi.getGlobalConfigs().subscribe({
      next: (list) => { this.configs = list ?? []; if (this.configs.length === 1) { this.selectedConfigId = this.configKey(this.configs[0]); this.selectedConfig = this.configs[0]; } this.cdr.markForCheck(); },
      error: (err: HttpErrorResponse) => { this.configLoadError = err.error?.message ?? err.message ?? 'Could not load configurations.'; this.cdr.markForCheck(); },
    });
  }

  configKey(c: LlmConfig): string { return c.id ?? `${c.provider}::${c.model}::${c.feature}`; }
  onConfigChange(key: string): void { this.selectedConfig = this.configs.find((c) => this.configKey(c) === key) ?? null; this.cdr.markForCheck(); }
  trackByRow(index: number, row: LoadTestResultItem): string { return `${row.index}-${index}-${row.status}-${row.latency_ms}`; }

  runTest(): void {
    if (!this.selectedConfig || this.parallel < 1 || this.parallel > 500) return;
    this.runError = null; this.finalResult = null; this.tableRows = []; this.running = true; this.pollSnapshot = null; this.cdr.markForCheck();
    this.pollSub?.unsubscribe();
    this.loadTestApi.startLoadTest({ llm_config_id: this.selectedConfig.id, parallel_requests: this.parallel, prompt: this.prompt }).subscribe({
      next: ({ run_id }) => {
        this.lastRunId = run_id;
        this.pollSub = interval(2000).pipe(startWith(0), switchMap(() => this.loadTestApi.getLoadTestResult(run_id)), takeWhile((r) => r.status === 'pending' || r.status === 'running', true)).subscribe({
          next: (res) => {
            this.pollSnapshot = res; this.cdr.markForCheck();
            if (res.status === 'completed' || res.status === 'failed') { this.running = false; this.finalResult = res; this.tableRows = res.results ?? []; this.pollSub?.unsubscribe(); this.refreshRecentIfOpen(); this.cdr.markForCheck(); }
          },
          error: (err: HttpErrorResponse) => { this.running = false; this.runError = err.error?.message ?? err.message ?? 'Polling failed.'; this.pollSub?.unsubscribe(); this.cdr.markForCheck(); },
        });
        this.cdr.markForCheck();
      },
      error: (err: HttpErrorResponse) => { this.running = false; this.runError = err.error?.message ?? err.message ?? 'Failed to start.'; this.cdr.markForCheck(); },
    });
  }

  exportCsv(): void {
    if (!this.tableRows.length) return;
    const header = ['index', 'status', 'latency_ms', 'http_status', 'tokens', 'error'];
    const lines = [header.join(','), ...this.tableRows.map((r) => [r.index, r.status, r.latency_ms, r.http_status ?? '', r.tokens ?? '', this.escCsv(r.error ?? '')].join(','))];
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = `load-test-${this.finalResult?.run_id ?? 'export'}.csv`; a.click();
    URL.revokeObjectURL(url);
  }

  private escCsv(value: string): string { return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value; }
  private refreshRecentIfOpen(): void { if (this._recentOpen) this.loadRecent(); }

  loadRecent(): void {
    this.recentLoading = true; this.recentError = null;
    this.loadTestApi.listRecentRuns().subscribe({
      next: (rows) => { this.recentRuns = rows ?? []; this.recentLoading = false; this.cdr.markForCheck(); },
      error: (err: HttpErrorResponse) => { this.recentError = err.error?.message ?? err.message ?? 'Could not load recent runs.'; this.recentLoading = false; this.cdr.markForCheck(); },
    });
  }

  set recentOpen(value: boolean) { this._recentOpen = value; if (value) this.loadRecent(); }
  get recentOpen(): boolean { return this._recentOpen; }
  private _recentOpen = false;
}
