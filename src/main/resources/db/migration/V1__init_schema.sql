-- LLM provider registry
CREATE TABLE llm_provider (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    auth_type VARCHAR(50) NOT NULL,
    auth_endpoint VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_llm_provider_auth_type CHECK (
        auth_type IN ('API_KEY', 'IAM_TOKEN', 'AWS_SIGV4', 'OAUTH2')
    )
);

-- Per-tenant (or global) LLM configuration
CREATE TABLE llm_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT,
    provider_id UUID NOT NULL REFERENCES llm_provider (id),
    model_name VARCHAR(200) NOT NULL,
    endpoint_url VARCHAR(500) NOT NULL,
    credentials_encrypted TEXT NOT NULL,
    rps_limit INTEGER,
    rpm_limit INTEGER,
    tpm_limit INTEGER,
    default_temperature DOUBLE PRECISION,
    default_max_tokens INTEGER,
    default_top_p DOUBLE PRECISION,
    default_n INTEGER,
    default_stop TEXT[],
    default_presence_penalty DOUBLE PRECISION,
    default_frequency_penalty DOUBLE PRECISION,
    queue_timeout_ms INTEGER NOT NULL DEFAULT 5000,
    is_fallback BOOLEAN NOT NULL DEFAULT false,
    priority INTEGER NOT NULL DEFAULT 0,
    extra_params JSONB,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- NULL tenant_id treated as global for uniqueness (expression unique index)
CREATE UNIQUE INDEX uq_llm_config_tenant_model_fallback
    ON llm_config (COALESCE(tenant_id, '__GLOBAL__'), model_name, is_fallback);

CREATE INDEX idx_llm_config_resolution
    ON llm_config (tenant_id, is_fallback, is_active, priority);

-- Feature tags per LLM config
CREATE TABLE features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    llm_config_id UUID NOT NULL REFERENCES llm_config (id) ON DELETE CASCADE,
    feature VARCHAR(200) NOT NULL,
    CONSTRAINT uq_features_llm_config_feature UNIQUE (llm_config_id, feature)
);

CREATE INDEX idx_features_feature ON features (feature);
