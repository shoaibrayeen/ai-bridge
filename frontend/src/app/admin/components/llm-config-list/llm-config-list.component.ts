import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LlmConfig } from '../../../shared/models';
import { AdminApiService } from '../../services/admin-api.service';

@Component({
  selector: 'app-llm-config-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="page-header">
      <h1 class="page-title">LLM Configs</h1>
      <a routerLink="/admin/llm-configs/new" class="btn btn-primary">Add Config</a>
    </div>

    <div class="filters">
      <label class="filter">
        <span class="form-label">Tenant</span>
        <input type="text" class="form-input" [(ngModel)]="tenantFilter"
               (ngModelChange)="onFilterChange()" placeholder="Filter by tenant" />
      </label>
      <label class="filter">
        <span class="form-label">Feature</span>
        <input type="text" class="form-input" [(ngModel)]="featureFilter"
               (ngModelChange)="onFilterChange()" placeholder="Filter by feature" />
      </label>
    </div>

    @if (error) { <p class="text-danger">{{ error }}</p> }
    @if (loading) { <p class="text-muted">Loading…</p> }
    @else {
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>Gateway model</th><th>Model</th><th>Provider</th><th>Tenant</th><th>Features</th>
              <th>Fallback</th><th>Active</th><th>Priority</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (c of configs; track c.id) {
              <tr>
                <td>
                  <code class="gateway-model" [title]="'Send this in the OpenAI model field to pin a request to this config'">{{ c.gateway_model_name ?? '—' }}</code>
                </td>
                <td>{{ c.model_name }}</td>
                <td>{{ c.provider_name ?? '—' }}</td>
                <td>{{ c.tenant_id ?? 'Global' }}</td>
                <td>
                  @for (f of c.features ?? []; track f) {
                    <span class="badge badge-primary">{{ f }}</span>
                  }
                  @if (!(c.features?.length)) { <span class="text-muted">—</span> }
                </td>
                <td>{{ c.is_fallback ? 'Yes' : 'No' }}</td>
                <td>{{ c.is_active !== false ? 'Yes' : 'No' }}</td>
                <td>{{ c.priority ?? '—' }}</td>
                <td class="actions">
                  <a [routerLink]="['/admin/llm-configs', c.id]" class="btn btn-secondary btn-sm">Edit</a>
                  <button type="button" class="btn btn-danger btn-sm" (click)="deleteConfig(c)">Delete</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
        @if (!configs.length) {
          <p class="text-muted empty-msg">No configs match the current filters.</p>
        }
      </div>
    }
  `,
  styles: [`
    :host { display: block; padding: 1.25rem; max-width: 1240px; }
    .gateway-model { font-size: 0.82rem; white-space: nowrap; }
    .filters { display: flex; flex-wrap: wrap; gap: 0.75rem; margin-bottom: 1rem; }
    .filter { display: flex; flex-direction: column; gap: 0.15rem; min-width: 180px; }
    .actions { display: flex; gap: 0.25rem; }
    .empty-msg { padding: 0.75rem 0.875rem; }
  `],
})
export class LlmConfigListComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  configs: LlmConfig[] = [];
  loading = true;
  error: string | null = null;
  tenantFilter = '';
  featureFilter = '';

  ngOnInit(): void { this.load(); }
  onFilterChange(): void { this.load(); }

  private load(): void {
    this.loading = true; this.error = null;
    const tenant = this.tenantFilter.trim();
    const feature = this.featureFilter.trim();
    this.api.getConfigs({ tenant: tenant || undefined, feature: feature || undefined }).subscribe({
      next: (rows) => { this.configs = rows; this.loading = false; },
      error: (e) => { this.error = e?.error?.message ?? e?.message ?? 'Failed to load configs'; this.loading = false; },
    });
  }

  deleteConfig(c: LlmConfig): void {
    if (!confirm(`Delete config for model "${c.model_name}" (${c.tenant_id ?? 'Global'})?`)) return;
    this.api.deleteConfig(c.id).subscribe({
      next: () => this.load(),
      error: (e) => { alert(e?.error?.message ?? e?.message ?? 'Delete failed'); },
    });
  }
}
