import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { LlmConfigWritePayload, LlmProvider } from '../../../shared/models';
import { AdminApiService } from '../../services/admin-api.service';

const CREDENTIALS_PLACEHOLDER = '****';

function jsonObjectValidator(control: AbstractControl): ValidationErrors | null {
  const v = control.value;
  if (v == null || String(v).trim() === '') return null;
  try {
    const parsed = JSON.parse(String(v));
    if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) return null;
    return { jsonObject: true };
  } catch { return { jsonInvalid: true }; }
}

@Component({
  selector: 'app-llm-config-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="page-header">
      <h1 class="page-title">{{ isCreate ? 'New LLM Config' : 'Edit LLM Config' }}</h1>
      <a routerLink="/admin/llm-configs" class="btn btn-secondary btn-sm">← Back</a>
    </div>

    @if (loadError) { <p class="text-danger mb-1">{{ loadError }}</p> }

    <form [formGroup]="form" (ngSubmit)="onSave()" class="card" [class.disabled]="loadingConfig">
      <div class="grid">
        <div class="form-group">
          <label class="form-label">Tenant ID *</label>
          <input type="text" class="form-input" formControlName="tenant_id" />
          @if (form.controls.tenant_id.touched && form.controls.tenant_id.invalid) {
            <span class="form-error">Required</span>
          }
        </div>
        <div class="form-group">
          <label class="form-label">Provider *</label>
          <select class="form-select" formControlName="provider">
            <option value="">Select provider</option>
            @for (p of providers; track p.id) {
              <option [value]="p.name">{{ p.name }}</option>
            }
          </select>
          @if (form.controls.provider.touched && form.controls.provider.invalid) {
            <span class="form-error">Required</span>
          }
        </div>
        <div class="form-group">
          <label class="form-label">Model name *</label>
          <input type="text" class="form-input" formControlName="model_name" />
          @if (gatewayModelName()) {
            <small class="form-hint">
              Gateway model name: <code>{{ gatewayModelName() }}</code>
              — send this in the OpenAI <code>model</code> field to pin a request to this config.
              Changing the model above assigns a new one.
            </small>
          }
          @if (form.controls.model_name.touched && form.controls.model_name.invalid) {
            <span class="form-error">Required</span>
          }
        </div>
        <div class="form-group full">
          <label class="form-label">Endpoint URL *</label>
          <input type="url" class="form-input" formControlName="endpoint_url" />
          @if (form.controls.endpoint_url.touched && form.controls.endpoint_url.invalid) {
            <span class="form-error">Required</span>
          }
        </div>
        <div class="form-group full">
          <label class="form-label">Credentials</label>
          <textarea class="form-textarea" formControlName="credentials" rows="3"></textarea>
          <span class="form-hint">Leave masked value unchanged to keep existing credentials.</span>
        </div>

        <div class="form-group"><label class="form-label">RPS limit</label>
          <input type="number" class="form-input" formControlName="rps_limit" /></div>
        <div class="form-group"><label class="form-label">RPM limit</label>
          <input type="number" class="form-input" formControlName="rpm_limit" /></div>
        <div class="form-group"><label class="form-label">TPM limit</label>
          <input type="number" class="form-input" formControlName="tpm_limit" /></div>

        <div class="form-group"><label class="form-label">Default temperature</label>
          <input type="number" step="0.01" class="form-input" formControlName="default_temperature" /></div>
        <div class="form-group"><label class="form-label">Default max tokens</label>
          <input type="number" class="form-input" formControlName="default_max_tokens" /></div>
        <div class="form-group"><label class="form-label">Default top_p</label>
          <input type="number" step="0.01" class="form-input" formControlName="default_top_p" /></div>

        <div class="form-group full">
          <label class="form-label">Features (comma-separated)</label>
          <input type="text" class="form-input" formControlName="features" placeholder="e.g. chat, embed" />
        </div>

        <div class="form-group check-row">
          <label><input type="checkbox" formControlName="is_fallback" /> Fallback provider</label>
        </div>
        <div class="form-group"><label class="form-label">Priority</label>
          <input type="number" class="form-input" formControlName="priority" /></div>
        <div class="form-group"><label class="form-label">Queue timeout (ms)</label>
          <input type="number" class="form-input" formControlName="queue_timeout_ms" /></div>

        <div class="form-group full">
          <label class="form-label">Extra params (JSON object)</label>
          <textarea class="form-textarea" formControlName="extra_params" rows="3" placeholder="{}"></textarea>
          @if (form.controls.extra_params.touched && form.controls.extra_params.errors?.['jsonInvalid']) {
            <span class="form-error">Invalid JSON</span>
          }
          @if (form.controls.extra_params.touched && form.controls.extra_params.errors?.['jsonObject']) {
            <span class="form-error">Must be a JSON object</span>
          }
        </div>
      </div>

      @if (testMessage) {
        <div class="test-result" [class.ok]="testOk" [class.fail]="!testOk">{{ testMessage }}</div>
      }
      @if (saveError) { <p class="text-danger mt-1">{{ saveError }}</p> }

      <div class="btn-row mt-2">
        <button type="button" class="btn btn-secondary" (click)="onTest()" [disabled]="form.invalid || busy">Test Connection</button>
        <button type="submit" class="btn btn-primary" [disabled]="form.invalid || busy">Save</button>
      </div>
    </form>
  `,
  styles: [`
    :host { display: block; padding: 1.25rem; max-width: 700px; }
    .card.disabled { opacity: 0.6; pointer-events: none; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem 0.75rem; }
    .full { grid-column: 1 / -1; }
    .check-row label { display: flex; align-items: center; gap: 0.4rem; font-size: 0.8125rem; color: var(--text-secondary); cursor: pointer; }
    .btn-row { display: flex; gap: 0.5rem; }
    .test-result { margin-top: 0.75rem; padding: 0.45rem 0.6rem; border-radius: var(--radius); font-size: 0.8125rem; }
    .test-result.ok { background: var(--success-muted); color: var(--success); }
    .test-result.fail { background: var(--danger-muted); color: var(--danger); }
  `],
})
export class LlmConfigFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    tenant_id: ['', Validators.required],
    provider: ['', Validators.required],
    model_name: ['', Validators.required],
    endpoint_url: ['', Validators.required],
    credentials: [''],
    rps_limit: this.fb.control<number | null>(null),
    rpm_limit: this.fb.control<number | null>(null),
    tpm_limit: this.fb.control<number | null>(null),
    default_temperature: this.fb.control<number | null>(null),
    default_max_tokens: this.fb.control<number | null>(null),
    default_top_p: this.fb.control<number | null>(null),
    features: [''],
    is_fallback: [false],
    priority: [0],
    queue_timeout_ms: this.fb.control<number | null>(null),
    extra_params: ['', jsonObjectValidator],
  });

  providers: LlmProvider[] = [];
  configId: string | null = null;

  /** Assigned by the backend on save; read-only here. Empty until an existing config loads. */
  readonly gatewayModelName = signal<string | null>(null);
  isCreate = true;
  loadingConfig = false;
  loadError: string | null = null;
  busy = false;
  testMessage: string | null = null;
  testOk = false;
  saveError: string | null = null;

  ngOnInit(): void {
    this.api.getProviders().subscribe({ next: (list) => { this.providers = list; }, error: () => { this.providers = []; } });
    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      this.configId = id && id !== 'new' ? id : null;
      this.isCreate = !this.configId;
      if (this.configId) this.fetchConfig(this.configId);
      else this.form.reset({ tenant_id: '', provider: '', model_name: '', endpoint_url: '', credentials: '', rps_limit: null, rpm_limit: null, tpm_limit: null, default_temperature: null, default_max_tokens: null, default_top_p: null, features: '', is_fallback: false, priority: 0, queue_timeout_ms: null, extra_params: '' });
    });
  }

  private fetchConfig(id: string): void {
    this.loadingConfig = true; this.loadError = null;
    this.api.getConfig(id).pipe(finalize(() => (this.loadingConfig = false))).subscribe({
      next: (c) => {
        this.gatewayModelName.set(c.gateway_model_name ?? null);
        this.form.patchValue({
          tenant_id: c.tenant_id ?? '', provider: c.provider_name ?? '', model_name: c.model_name,
          endpoint_url: c.endpoint_url, credentials: CREDENTIALS_PLACEHOLDER,
          rps_limit: c.rps_limit ?? null, rpm_limit: c.rpm_limit ?? null, tpm_limit: c.tpm_limit ?? null,
          default_temperature: c.default_temperature ?? null, default_max_tokens: c.default_max_tokens ?? null,
          default_top_p: c.default_top_p ?? null, features: (c.features ?? []).join(', '),
          is_fallback: !!c.is_fallback, priority: c.priority ?? 0,
          queue_timeout_ms: c.queue_timeout_ms ?? null,
          extra_params: c.extra_params && typeof c.extra_params === 'object' ? JSON.stringify(c.extra_params, null, 2) : '',
        });
      },
      error: (e) => { this.loadError = e?.error?.message ?? e?.message ?? 'Failed to load config'; },
    });
  }

  onTest(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const payload = this.buildPayload();
    if (!payload) { this.form.controls.extra_params.setErrors({ jsonInvalid: true }); this.form.controls.extra_params.markAsTouched(); return; }
    this.busy = true; this.testMessage = null; this.saveError = null;
    this.api.testConfig(payload).subscribe({
      next: (r) => { this.busy = false; this.testOk = !!r.valid; this.testMessage = r.valid ? (r.message ?? 'Connection OK') : (r.message ?? 'Validation failed'); },
      error: (e) => { this.busy = false; this.testOk = false; this.testMessage = e?.error?.message ?? e?.message ?? 'Test request failed'; },
    });
  }

  onSave(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const payload = this.buildPayload();
    if (!payload) { this.form.controls.extra_params.setErrors({ jsonInvalid: true }); this.form.controls.extra_params.markAsTouched(); return; }
    this.busy = true; this.saveError = null; this.testMessage = null;
    this.api.testConfig(payload).subscribe({
      next: (r) => {
        if (!r.valid) { this.busy = false; this.testOk = false; this.testMessage = r.message ?? 'Validation failed'; return; }
        this.testOk = true; this.testMessage = r.message ?? 'Connection OK — saving…';
        const save$ = this.isCreate ? this.api.createConfig(payload) : this.api.updateConfig(this.configId!, payload);
        save$.pipe(finalize(() => (this.busy = false))).subscribe({
          next: () => { void this.router.navigate(['/admin/llm-configs']); },
          error: (e) => { this.saveError = e?.error?.message ?? e?.message ?? 'Save failed'; },
        });
      },
      error: (e) => { this.busy = false; this.testOk = false; this.testMessage = e?.error?.message ?? e?.message ?? 'Test request failed'; },
    });
  }

  private buildPayload(): LlmConfigWritePayload | null {
    const raw = this.form.getRawValue();
    const features = raw.features.split(',').map((s) => s.trim()).filter(Boolean);
    let extra: Record<string, unknown> | null = null;
    const ep = raw.extra_params?.trim();
    if (ep) { try { extra = JSON.parse(ep) as Record<string, unknown>; } catch { return null; } }
    const cred = raw.credentials.trim();
    const omitCredentials = !this.isCreate && (cred === '' || cred === CREDENTIALS_PLACEHOLDER);
    const payload: LlmConfigWritePayload = {
      tenant_id: raw.tenant_id, provider: raw.provider, model_name: raw.model_name,
      endpoint_url: raw.endpoint_url, rps_limit: raw.rps_limit, rpm_limit: raw.rpm_limit,
      tpm_limit: raw.tpm_limit, default_temperature: raw.default_temperature,
      default_max_tokens: raw.default_max_tokens, default_top_p: raw.default_top_p,
      features, is_fallback: raw.is_fallback, priority: raw.priority,
      queue_timeout_ms: raw.queue_timeout_ms, extra_params: extra,
    };
    if (!omitCredentials) payload.credentials = cred || null;
    return payload;
  }
}
