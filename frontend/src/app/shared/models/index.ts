/**
 * Wire-format shapes match Jackson JSON from the AIBridge Java API (snake_case fields).
 */

/** Admin: {@link com.aibridge.dto.admin.LlmProviderResponse} */
export interface LlmProvider {
  id: string;
  name: string;
  auth_type: string;
  auth_endpoint?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
}

/** Admin: {@link com.aibridge.dto.admin.LlmProviderRequest} */
export interface LlmProviderRequest {
  name: string;
  auth_type: string;
  auth_endpoint?: string | null;
}

export type LlmProviderUpsertRequest = LlmProviderRequest;
export type LlmProviderAuthType = 'API_KEY' | 'IAM_TOKEN' | 'AWS_SIGV4' | 'OAUTH2';

/** Admin: {@link com.aibridge.dto.admin.LlmConfigResponse} */
export interface LlmConfig {
  id: string;
  tenant_id?: string | null;
  provider_id: string;
  model_name: string;
  endpoint_url: string;
  credentials?: string | null;
  rps_limit?: number | null;
  rpm_limit?: number | null;
  tpm_limit?: number | null;
  default_temperature?: number | null;
  default_max_tokens?: number | null;
  default_top_p?: number | null;
  default_n?: number | null;
  default_stop?: string[] | null;
  default_presence_penalty?: number | null;
  default_frequency_penalty?: number | null;
  queue_timeout_ms?: number | null;
  is_fallback?: boolean | null;
  priority?: number | null;
  extra_params?: string | null;
  features?: string[] | null;
  is_active?: boolean | null;
  created_at?: string | null;
  updated_at?: string | null;
  provider_name?: string | null;
}

/** Admin: {@link com.aibridge.dto.admin.LlmConfigRequest} */
export interface LlmConfigRequest {
  tenant_id?: string | null;
  provider_id: string;
  model_name: string;
  endpoint_url: string;
  credentials: string;
  rps_limit?: number | null;
  rpm_limit?: number | null;
  tpm_limit?: number | null;
  default_temperature?: number | null;
  default_max_tokens?: number | null;
  default_top_p?: number | null;
  default_n?: number | null;
  default_stop?: string[] | null;
  default_presence_penalty?: number | null;
  default_frequency_penalty?: number | null;
  queue_timeout_ms?: number | null;
  is_fallback?: boolean | null;
  priority?: number | null;
  extra_params?: string | null;
  features: string[];
}

/** OpenAI-style: {@link com.aibridge.dto.openai.ChatCompletionRequest} */
export interface ChatCompletionRequest {
  messages: ChatMessage[];
  model?: string | null;
  temperature?: number | null;
  max_tokens?: number | null;
  top_p?: number | null;
  n?: number | null;
  stop?: string[] | null;
  presence_penalty?: number | null;
  frequency_penalty?: number | null;
  stream?: boolean;
}

/** OpenAI-style: {@link com.aibridge.dto.openai.ChatCompletionResponse} */
export interface ChatCompletionResponse {
  id?: string | null;
  object?: string | null;
  created?: number | null;
  model?: string | null;
  choices?: Choice[] | null;
  usage?: Usage | null;
}

/** {@link com.aibridge.dto.openai.ChatMessage} */
export interface ChatMessage {
  role: string;
  content: string;
  name?: string | null;
}

/** {@link com.aibridge.dto.openai.Choice} */
export interface Choice {
  index?: number | null;
  message?: ChatMessage | null;
  finish_reason?: string | null;
}

/** {@link com.aibridge.dto.openai.Usage} */
export interface Usage {
  prompt_tokens?: number | null;
  completion_tokens?: number | null;
  total_tokens?: number | null;
}

/** {@link com.aibridge.dto.loadtest.LoadTestRequest} */
export interface LoadTestRequest {
  llm_config_id: string;
  parallel_requests: number;
  prompt: string;
  max_tokens?: number | null;
}

/** {@link com.aibridge.dto.loadtest.LoadTestResponse} */
export interface LoadTestResponse {
  run_id: string;
  status: string;
  llm_config_id: string;
  model_name?: string | null;
  parallel_requests: number;
  started_at?: string | null;
  completed_at?: string | null;
  summary?: LoadTestSummary | null;
  results?: LoadTestResultItem[] | null;
}

/** {@link com.aibridge.dto.loadtest.LoadTestResultItem} */
export interface LoadTestResultItem {
  request_index: number;
  status: string;
  latency_ms: number;
  http_status: number;
  tokens_used: number;
  error?: string | null;
}

/** {@link com.aibridge.dto.loadtest.LoadTestSummary} */
export interface LoadTestSummary {
  total: number;
  success: number;
  failed: number;
  avg_latency_ms: number;
  p50_latency_ms: number;
  p95_latency_ms: number;
  p99_latency_ms: number;
  max_latency_ms: number;
  min_latency_ms: number;
}

/** Admin: {@link com.aibridge.dto.admin.ValidationResultResponse} */
export interface ValidationResult {
  valid: boolean;
  message?: string | null;
  latency_ms?: number | null;
}

/** {@link com.aibridge.resource.HealthResource} JSON body */
export interface HealthComponentStatus {
  status: string;
  latency_ms: number;
}

export interface HealthStatus {
  status: string;
  components: {
    database: HealthComponentStatus;
    cache: HealthComponentStatus;
  };
}

export interface LlmConfigListParams {
  tenant?: string;
  feature?: string;
}

export interface LlmConfigWritePayload {
  tenant_id?: string | null;
  provider?: string;
  provider_id?: string;
  model_name: string;
  endpoint_url: string;
  credentials?: string | null;
  rps_limit?: number | null;
  rpm_limit?: number | null;
  tpm_limit?: number | null;
  default_temperature?: number | null;
  default_max_tokens?: number | null;
  default_top_p?: number | null;
  features?: string[];
  is_fallback?: boolean;
  priority?: number;
  queue_timeout_ms?: number | null;
  extra_params?: Record<string, unknown> | null;
}
