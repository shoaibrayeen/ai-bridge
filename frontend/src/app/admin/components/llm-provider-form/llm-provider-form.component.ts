import { CommonModule } from '@angular/common';
import {
  Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LlmProvider, LlmProviderAuthType, LlmProviderUpsertRequest } from '../../../shared/models';

const PRESET_NAMES = ['openai', 'bedrock', 'watsonx', 'cerebras', 'claude'] as const;

@Component({
  selector: 'app-llm-provider-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="card form-card">
      <h3 class="card-title">{{ provider ? 'Edit provider' : 'New provider' }}</h3>

      <div class="form-group">
        <label class="form-label">Name *</label>
        <div class="name-row">
          <select class="form-select" formControlName="namePreset" (change)="onPresetChange()">
            @for (n of presetNames; track n) { <option [value]="n">{{ n }}</option> }
            <option value="custom">Custom…</option>
          </select>
          @if (form.controls.namePreset.value === 'custom') {
            <input type="text" class="form-input" formControlName="nameCustom" placeholder="Provider name" />
          }
        </div>
        @if (form.touched && nameInvalid) { <span class="form-error">Name is required</span> }
      </div>

      <div class="form-group">
        <label class="form-label">Auth type *</label>
        <select class="form-select" formControlName="auth_type" (change)="onAuthTypeChange()">
          @for (t of authTypes; track t) { <option [value]="t">{{ t }}</option> }
        </select>
      </div>

      @if (showAuthEndpoint) {
        <div class="form-group">
          <label class="form-label">Auth endpoint @if (authEndpointRequired) { * }</label>
          <input type="url" class="form-input" formControlName="auth_endpoint" placeholder="https://…" />
          @if (form.controls.auth_endpoint.touched && form.controls.auth_endpoint.invalid) {
            <span class="form-error">Valid URL required</span>
          }
        </div>
      }

      @if (error) { <p class="text-danger">{{ error }}</p> }

      <div class="btn-row">
        <button type="button" class="btn btn-secondary" (click)="cancel.emit()">Cancel</button>
        <button type="submit" class="btn btn-primary" [disabled]="form.invalid || submitting">Save</button>
      </div>
    </form>
  `,
  styles: [`
    .form-card { max-width: 440px; }
    .name-row { display: flex; gap: 0.4rem; flex-wrap: wrap; }
    .name-row select, .name-row input { flex: 1; min-width: 130px; }
    .btn-row { display: flex; justify-content: flex-end; gap: 0.4rem; margin-top: 0.5rem; }
  `],
})
export class LlmProviderFormComponent implements OnInit, OnChanges {
  private readonly fb = inject(FormBuilder);

  @Input() provider: LlmProvider | null = null;
  @Input() submitting = false;
  @Output() readonly save = new EventEmitter<LlmProviderUpsertRequest & { id?: string }>();
  @Output() readonly cancel = new EventEmitter<void>();

  readonly presetNames = [...PRESET_NAMES];
  readonly authTypes: LlmProviderAuthType[] = ['API_KEY', 'IAM_TOKEN', 'AWS_SIGV4', 'OAUTH2'];

  readonly form = this.fb.nonNullable.group({
    namePreset: this.fb.nonNullable.control<(typeof PRESET_NAMES)[number] | 'custom'>('openai'),
    nameCustom: [''],
    auth_type: this.fb.nonNullable.control<LlmProviderAuthType>('API_KEY'),
    auth_endpoint: [''],
  });

  error: string | null = null;
  showAuthEndpoint = false;
  authEndpointRequired = false;

  ngOnInit(): void { this.patchFromProvider(); this.syncAuthEndpointUi(); this.syncNameCustomValidators(); }
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['provider']) { this.patchFromProvider(); this.syncAuthEndpointUi(); this.syncNameCustomValidators(); }
  }

  get nameInvalid(): boolean {
    const preset = this.form.controls.namePreset.value;
    return preset === 'custom' ? !this.form.controls.nameCustom.value.trim() : !preset;
  }

  onPresetChange(): void {
    if (this.form.controls.namePreset.value !== 'custom') this.form.controls.nameCustom.setValue('');
    this.syncNameCustomValidators();
  }

  onAuthTypeChange(): void { this.syncAuthEndpointUi(); }

  private patchFromProvider(): void {
    this.error = null;
    const p = this.provider;
    if (!p) { this.form.reset({ namePreset: 'openai', nameCustom: '', auth_type: 'API_KEY', auth_endpoint: '' }); return; }
    const preset = PRESET_NAMES.includes(p.name as (typeof PRESET_NAMES)[number]) ? (p.name as (typeof PRESET_NAMES)[number]) : 'custom';
    this.form.patchValue({ namePreset: preset, nameCustom: preset === 'custom' ? p.name : '', auth_type: (p.auth_type as LlmProviderAuthType) ?? 'API_KEY', auth_endpoint: p.auth_endpoint ?? '' });
    this.syncNameCustomValidators();
  }

  private syncAuthEndpointUi(): void {
    const t = this.form.controls.auth_type.value;
    this.showAuthEndpoint = t === 'OAUTH2' || t === 'IAM_TOKEN' || t === 'AWS_SIGV4';
    this.authEndpointRequired = t === 'OAUTH2' || t === 'IAM_TOKEN';
    const ctrl = this.form.controls.auth_endpoint;
    ctrl.setValidators(this.authEndpointRequired ? [Validators.required] : []);
    if (!this.showAuthEndpoint) ctrl.setValue('');
    ctrl.updateValueAndValidity({ emitEvent: false });
  }

  private syncNameCustomValidators(): void {
    const custom = this.form.controls.nameCustom;
    if (this.form.controls.namePreset.value === 'custom') custom.setValidators([Validators.required]);
    else custom.clearValidators();
    custom.updateValueAndValidity({ emitEvent: false });
  }

  onSubmit(): void {
    this.error = null; this.form.markAllAsTouched();
    const name = this.form.controls.namePreset.value === 'custom' ? this.form.controls.nameCustom.value.trim() : this.form.controls.namePreset.value;
    if (!name || this.form.invalid) return;
    const authType = this.form.controls.auth_type.value;
    const endpoint = this.showAuthEndpoint ? this.form.controls.auth_endpoint.value.trim() : '';
    const body: LlmProviderUpsertRequest & { id?: string } = { name, auth_type: authType, auth_endpoint: this.showAuthEndpoint ? (endpoint || null) : null };
    if (this.provider) body.id = this.provider.id;
    this.save.emit(body);
  }
}
