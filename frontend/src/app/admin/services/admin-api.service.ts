import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  HealthStatus,
  LlmConfig,
  LlmConfigListParams,
  LlmConfigWritePayload,
  LlmProvider,
  LlmProviderUpsertRequest,
  ValidationResult,
} from '../../shared/models';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  constructor(private http: HttpClient) {}

  getProviders(): Observable<LlmProvider[]> {
    return this.http.get<LlmProvider[]>('/admin/api/llm-providers');
  }

  createProvider(req: LlmProviderUpsertRequest): Observable<LlmProvider> {
    return this.http.post<LlmProvider>('/admin/api/llm-providers', req);
  }

  updateProvider(id: string, req: LlmProviderUpsertRequest): Observable<LlmProvider> {
    return this.http.put<LlmProvider>(`/admin/api/llm-providers/${id}`, req);
  }

  deleteProvider(id: string): Observable<void> {
    return this.http.delete<void>(`/admin/api/llm-providers/${id}`);
  }

  getConfigs(params?: LlmConfigListParams): Observable<LlmConfig[]> {
    let httpParams = new HttpParams();
    if (params?.tenant) {
      httpParams = httpParams.set('tenant', params.tenant);
    }
    if (params?.feature) {
      httpParams = httpParams.set('feature', params.feature);
    }
    return this.http.get<LlmConfig[]>('/admin/api/llm-configs', { params: httpParams });
  }

  getConfig(id: string): Observable<LlmConfig> {
    return this.http.get<LlmConfig>(`/admin/api/llm-configs/${id}`);
  }

  createConfig(req: LlmConfigWritePayload): Observable<LlmConfig> {
    return this.http.post<LlmConfig>('/admin/api/llm-configs', req);
  }

  updateConfig(id: string, req: LlmConfigWritePayload): Observable<LlmConfig> {
    return this.http.put<LlmConfig>(`/admin/api/llm-configs/${id}`, req);
  }

  deleteConfig(id: string): Observable<void> {
    return this.http.delete<void>(`/admin/api/llm-configs/${id}`);
  }

  testConfig(req: LlmConfigWritePayload): Observable<ValidationResult> {
    return this.http.post<ValidationResult>('/admin/api/llm-configs/test', req);
  }

  getFeatures(): Observable<string[]> {
    return this.http.get<string[]>('/admin/api/features');
  }

  getHealth(): Observable<HealthStatus> {
    return this.http.get<HealthStatus>('/admin/api/health');
  }
}
