import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { LlmProvider, LlmProviderUpsertRequest } from '../../../shared/models';
import { AdminApiService } from '../../services/admin-api.service';
import { LlmProviderFormComponent } from '../llm-provider-form/llm-provider-form.component';

@Component({
  selector: 'app-llm-provider-list',
  standalone: true,
  imports: [CommonModule, LlmProviderFormComponent],
  template: `
    <div class="page-header">
      <h1 class="page-title">LLM Providers</h1>
      <button type="button" class="btn btn-primary" (click)="openCreate()" [disabled]="panelOpen">Add Provider</button>
    </div>

    @if (listError) { <p class="text-danger">{{ listError }}</p> }
    @if (loading) { <p class="text-muted">Loading…</p> }
    @else {
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr><th>Name</th><th>Auth type</th><th>Auth endpoint</th><th>Created</th><th>Actions</th></tr>
          </thead>
          <tbody>
            @for (p of providers; track p.id) {
              <tr [class.highlight]="editing?.id === p.id">
                <td>{{ p.name }}</td>
                <td><span class="badge badge-neutral">{{ p.auth_type }}</span></td>
                <td>{{ p.auth_endpoint || '—' }}</td>
                <td>{{ p.created_at ? (p.created_at | date:'mediumDate') : '—' }}</td>
                <td class="actions">
                  <button type="button" class="btn btn-secondary btn-sm" (click)="openEdit(p)">Edit</button>
                  <button type="button" class="btn btn-danger btn-sm" (click)="remove(p)">Delete</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
        @if (!providers.length) { <p class="text-muted empty-msg">No providers yet.</p> }
      </div>
    }

    @if (panelOpen) {
      <div class="overlay" (click)="closePanel()"></div>
      <div class="panel" role="dialog" aria-modal="true">
        <app-llm-provider-form [provider]="editing" [submitting]="formBusy"
                               (save)="onSave($event)" (cancel)="closePanel()" />
      </div>
    }
  `,
  styles: [`
    :host { display: block; padding: 1.25rem; max-width: 960px; position: relative; }
    .actions { display: flex; gap: 0.25rem; }
    .highlight td { background: var(--warning-muted); }
    .empty-msg { padding: 0.75rem 0.875rem; }
    .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.35); z-index: 40; }
    .panel { position: fixed; top: 50%; left: 50%; transform: translate(-50%,-50%); z-index: 50; max-height: 90vh; overflow: auto; }
  `],
})
export class LlmProviderListComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  providers: LlmProvider[] = [];
  loading = true;
  listError: string | null = null;
  panelOpen = false;
  editing: LlmProvider | null = null;
  formBusy = false;

  ngOnInit(): void { this.reload(); }

  openCreate(): void { this.editing = null; this.panelOpen = true; }
  openEdit(p: LlmProvider): void { this.editing = { ...p }; this.panelOpen = true; }
  closePanel(): void { if (this.formBusy) return; this.panelOpen = false; this.editing = null; }

  onSave(payload: LlmProviderUpsertRequest & { id?: string }): void {
    this.formBusy = true; this.listError = null;
    const { id, ...body } = payload;
    const req$ = id ? this.api.updateProvider(id, body) : this.api.createProvider(body);
    req$.subscribe({
      next: () => { this.formBusy = false; this.panelOpen = false; this.editing = null; this.reload(); },
      error: (e) => { this.formBusy = false; this.listError = e?.error?.message ?? e?.message ?? 'Save failed'; },
    });
  }

  remove(p: LlmProvider): void {
    if (!confirm(`Delete provider "${p.name}"?`)) return;
    this.api.deleteProvider(p.id).subscribe({
      next: () => this.reload(),
      error: (e) => { alert(e?.error?.message ?? e?.message ?? 'Delete failed'); },
    });
  }

  private reload(): void {
    this.loading = true; this.listError = null;
    this.api.getProviders().subscribe({
      next: (rows) => { this.providers = rows; this.loading = false; },
      error: (e) => { this.listError = e?.error?.message ?? e?.message ?? 'Failed to load providers'; this.loading = false; },
    });
  }
}
