import { Routes } from '@angular/router';
import { authGuard } from './shared/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'playground',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./playground/components/chat-playground/chat-playground.component').then(
        (m) => m.ChatPlaygroundComponent
      ),
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./admin/admin-shell.component').then((m) => m.AdminShellComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./admin/admin-dashboard.component').then((m) => m.AdminDashboardComponent),
      },
      {
        path: 'llm-configs',
        loadComponent: () =>
          import('./admin/components/llm-config-list/llm-config-list.component').then(
            (m) => m.LlmConfigListComponent
          ),
      },
      {
        path: 'llm-configs/new',
        loadComponent: () =>
          import('./admin/components/llm-config-form/llm-config-form.component').then(
            (m) => m.LlmConfigFormComponent
          ),
      },
      {
        path: 'llm-configs/:id',
        loadComponent: () =>
          import('./admin/components/llm-config-form/llm-config-form.component').then(
            (m) => m.LlmConfigFormComponent
          ),
      },
      {
        path: 'llm-providers',
        loadComponent: () =>
          import('./admin/components/llm-provider-list/llm-provider-list.component').then(
            (m) => m.LlmProviderListComponent
          ),
      },
      {
        path: 'load-test',
        loadComponent: () =>
          import('./admin/components/load-test/load-test.component').then(
            (m) => m.LoadTestComponent
          ),
      },
      {
        path: 'health',
        loadComponent: () =>
          import('./shared/components/health-status/health-status.component').then(
            (m) => m.HealthStatusComponent
          ),
      },
    ],
  },
];
