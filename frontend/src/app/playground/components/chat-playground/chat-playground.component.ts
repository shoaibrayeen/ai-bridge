import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnInit,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ChatCompletionRequest,
  LlmConfig,
  PlaygroundApiService,
} from '../../services/playground-api.service';

@Component({
  selector: 'app-chat-playground',
  standalone: true,
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pg container">
      <div class="pg-top">
        <h1 class="pg-title">Playground</h1>
        <p class="pg-desc">Test your configured global LLMs with system prompts and user instructions</p>
      </div>

      <div class="controls">
        <label class="ctrl grow">
          <span class="ctrl-label">Model</span>
          <select class="form-select" [(ngModel)]="selectedConfigId"
                  (ngModelChange)="onConfigSelected($event)"
                  [disabled]="loading || configs.length === 0">
            <option value="" disabled>Select a model\u2026</option>
            @for (c of configs; track c.id ?? c.model + c.provider) {
              <option [value]="configKey(c)">{{ c.model }} \u2014 {{ c.provider }}</option>
            }
          </select>
        </label>
        @if (activeConfig) {
          <div class="chip">
            <span class="chip-label">Feature</span>
            <code class="chip-val">{{ activeConfig.feature }}</code>
          </div>
        }
      </div>

      @if (configError) {
        <div class="alert alert-danger">{{ configError }}</div>
      }

      <div class="workspace">
        <div class="pane">
          <label class="pane-label">System Prompt</label>
          <textarea class="form-textarea pane-textarea" rows="4"
                    [(ngModel)]="systemPrompt" [disabled]="loading"
                    placeholder="You are a helpful assistant\u2026"></textarea>
        </div>

        <div class="pane">
          <label class="pane-label">User Instructions</label>
          <textarea class="form-textarea pane-textarea" rows="6"
                    [(ngModel)]="userPrompt" [disabled]="loading"
                    placeholder="Enter your prompt here\u2026"
                    (keydown)="onKeydown($event)"></textarea>
        </div>

        <div class="actions">
          <button type="button" class="btn btn-primary run-btn" (click)="run()"
                  [disabled]="loading || !activeConfig || !userPrompt.trim()">
            @if (loading) { <span class="spinner-sm"></span> Running\u2026 }
            @else { Run }
          </button>
          @if (lastLatencyMs != null) {
            <span class="latency-badge">{{ lastLatencyMs | number:'1.0-0' }} ms</span>
          }
        </div>

        @if (output || errorMsg) {
          <div class="pane output-pane" [class.error-pane]="!!errorMsg">
            <div class="output-header">
              <span class="pane-label">Output</span>
              @if (outputMeta) {
                <div class="meta-pills">
                  @if (outputMeta.model) {
                    <span class="pill">{{ outputMeta.model }}</span>
                  }
                  @if (outputMeta.usage) {
                    <span class="pill pill-tok">{{ outputMeta.usage.prompt_tokens }} + {{ outputMeta.usage.completion_tokens }} = {{ outputMeta.usage.total_tokens }} tok</span>
                  }
                </div>
              }
            </div>
            <div class="output-body">{{ errorMsg || output }}</div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .pg { padding: 1.25rem 1.25rem 2rem; max-width: 800px; }
    .pg-top { margin-bottom: 0.75rem; }
    .pg-title { margin: 0; font-size: 1.125rem; font-weight: 600; color: var(--text); }
    .pg-desc { margin: 0.15rem 0 0; font-size: 0.8125rem; color: var(--text-muted); }

    .controls { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: flex-end; margin-bottom: 1rem; }
    .ctrl { display: flex; flex-direction: column; gap: 0.15rem; }
    .ctrl-label { font-size: 0.6875rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-muted); }
    .ctrl.grow { flex: 1 1 250px; }
    .chip {
      display: flex; flex-direction: column; gap: 0.1rem;
      padding: 0.3rem 0.5rem; border-radius: var(--radius);
      background: var(--bg-inset); border: 1px dashed var(--border-strong); font-size: 0.75rem;
    }
    .chip-label { color: var(--text-faint); font-weight: 600; text-transform: uppercase; font-size: 0.625rem; }
    .chip-val { font-family: monospace; color: var(--text-secondary); }

    .alert { padding: 0.5rem 0.65rem; border-radius: var(--radius); font-size: 0.8125rem; margin-bottom: 0.75rem; }
    .alert-danger { background: var(--danger-muted); color: var(--danger); border: 1px solid var(--danger); }

    .workspace { display: flex; flex-direction: column; gap: 0.75rem; }
    .pane { display: flex; flex-direction: column; gap: 0.25rem; }
    .pane-label {
      font-size: 0.6875rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.04em; color: var(--text-muted);
    }
    .pane-textarea {
      resize: vertical; min-height: 60px;
      font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
      font-size: 0.8125rem; line-height: 1.5;
    }

    .actions { display: flex; align-items: center; gap: 0.75rem; }
    .run-btn { padding: 0.5rem 1.5rem; font-size: 0.875rem; font-weight: 600; gap: 0.4rem; }
    .spinner-sm {
      display: inline-block; width: 14px; height: 14px;
      border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff;
      border-radius: 50%; animation: spin 0.6s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .latency-badge {
      font-size: 0.75rem; font-weight: 600; padding: 0.2rem 0.5rem;
      border-radius: 9999px; background: var(--success-muted); color: var(--success);
    }

    .output-pane {
      background: var(--bg-surface); border: 1px solid var(--border);
      border-radius: var(--radius-lg); padding: 0.75rem 1rem;
      box-shadow: var(--shadow-sm);
    }
    .error-pane { border-color: var(--danger); background: var(--danger-muted); }
    .output-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.4rem; }
    .meta-pills { display: flex; gap: 0.3rem; flex-wrap: wrap; }
    .pill { font-size: 0.6875rem; padding: 0.1rem 0.35rem; border-radius: 9999px; background: var(--primary-muted); color: var(--primary); font-weight: 600; }
    .pill-tok { background: var(--bg-inset); color: var(--text-muted); }
    .output-body {
      white-space: pre-wrap; word-break: break-word;
      font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
      font-size: 0.8125rem; line-height: 1.55; color: var(--text);
    }
    .error-pane .output-body { color: var(--danger); }
  `],
})
export class ChatPlaygroundComponent implements OnInit {
  configs: LlmConfig[] = [];
  selectedConfigId = '';
  activeConfig: LlmConfig | null = null;
  systemPrompt = '';
  userPrompt = '';
  output = '';
  errorMsg = '';
  loading = false;
  configError: string | null = null;
  lastLatencyMs: number | null = null;
  outputMeta: { model?: string; usage?: { prompt_tokens: number; completion_tokens: number; total_tokens: number } } | null = null;

  constructor(private api: PlaygroundApiService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.api.getGlobalConfigs().subscribe({
      next: (list) => {
        this.configs = list ?? [];
        if (this.configs.length === 1) {
          this.selectedConfigId = this.configKey(this.configs[0]);
          this.activeConfig = this.configs[0];
        }
        this.cdr.markForCheck();
      },
      error: (err: HttpErrorResponse) => {
        this.configError = err.error?.message ?? err.message ?? 'Could not load LLM configurations.';
        this.cdr.markForCheck();
      },
    });
  }

  configKey(c: LlmConfig): string {
    return c.id ?? `${c.provider}::${c.model}::${c.feature}`;
  }

  onConfigSelected(key: string): void {
    this.activeConfig = this.configs.find((c) => this.configKey(c) === key) ?? null;
    this.cdr.markForCheck();
  }

  onKeydown(ev: KeyboardEvent): void {
    if (ev.key === 'Enter' && (ev.metaKey || ev.ctrlKey)) {
      ev.preventDefault();
      this.run();
    }
  }

  run(): void {
    const text = this.userPrompt.trim();
    if (!this.activeConfig || !text || this.loading) return;

    this.output = '';
    this.errorMsg = '';
    this.outputMeta = null;
    this.lastLatencyMs = null;
    this.loading = true;
    this.cdr.markForCheck();

    const messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string }> = [];
    if (this.systemPrompt.trim()) {
      messages.push({ role: 'system', content: this.systemPrompt.trim() });
    }
    messages.push({ role: 'user', content: text });

    const request: ChatCompletionRequest = { model: this.activeConfig.model, messages };
    const t0 = performance.now();

    this.api.sendMessage('', this.activeConfig.feature, request).subscribe({
      next: (res) => {
        this.lastLatencyMs = performance.now() - t0;
        this.output = res.choices?.[0]?.message?.content ?? '(empty response)';
        this.outputMeta = { model: res.model, usage: res.usage };
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err: HttpErrorResponse) => {
        this.lastLatencyMs = performance.now() - t0;
        this.errorMsg = typeof err.error === 'string'
          ? err.error
          : err.error?.message ?? err.message ?? 'Request failed.';
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }
}
