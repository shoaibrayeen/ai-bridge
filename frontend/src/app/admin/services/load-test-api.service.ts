import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface LoadTestStartRequest {
  llm_config_id?: string;
  parallel_requests: number;
  prompt: string;
}

export interface LoadTestResultItem {
  index: number;
  status: 'SUCCESS' | 'FAILED';
  latency_ms: number;
  http_status?: number;
  tokens?: number;
  error?: string;
}

export interface LoadTestResponse {
  run_id: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  summary?: {
    total: number;
    success: number;
    failed: number;
    avg_latency_ms: number;
    p50_ms: number;
    p95_ms: number;
    p99_ms: number;
    max_latency_ms: number;
  };
  results?: LoadTestResultItem[];
  started_at?: string;
  finished_at?: string;
}

@Injectable({ providedIn: 'root' })
export class LoadTestApiService {
  constructor(private http: HttpClient) {}

  startLoadTest(
    request: LoadTestStartRequest
  ): Observable<{ run_id: string }> {
    return this.http.post<{ run_id: string }>('/admin/api/load-test', request);
  }

  getLoadTestResult(runId: string): Observable<LoadTestResponse> {
    return this.http.get<LoadTestResponse>(`/admin/api/load-test/${runId}`);
  }

  listRecentRuns(): Observable<LoadTestResponse[]> {
    return this.http.get<LoadTestResponse[]>('/admin/api/load-test');
  }
}
