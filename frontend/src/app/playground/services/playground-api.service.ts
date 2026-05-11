import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

export interface LlmConfig {
  id?: string;
  model: string;
  provider: string;
  feature: string;
  tenant_id?: string | null;
}

export interface ChatMessageDto {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface ChatCompletionRequest {
  model?: string;
  messages: ChatMessageDto[];
  temperature?: number;
  max_tokens?: number;
  stream?: boolean;
}

export interface ChatCompletionResponse {
  id?: string;
  object?: string;
  created?: number;
  model?: string;
  choices: Array<{
    index?: number;
    message: { role: string; content: string };
    finish_reason?: string;
  }>;
  usage?: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
  };
}

@Injectable({ providedIn: 'root' })
export class PlaygroundApiService {
  constructor(private http: HttpClient) {}

  getConfigs(): Observable<LlmConfig[]> {
    return this.http.get<LlmConfig[]>('/admin/api/llm-configs');
  }

  getGlobalConfigs(): Observable<LlmConfig[]> {
    return this.getConfigs().pipe(
      map((configs) => configs.filter((c) => !c.tenant_id))
    );
  }

  sendMessage(
    tenantId: string,
    feature: string,
    request: ChatCompletionRequest
  ): Observable<ChatCompletionResponse> {
    let headers = new HttpHeaders({ 'X-Feature': feature });
    if (tenantId) {
      headers = headers.set('X-Tenant-ID', tenantId);
    }
    return this.http.post<ChatCompletionResponse>(
      '/v1/chat/completions',
      request,
      { headers }
    );
  }
}
